"""Windows Internet Connection Sharing for devices without a client app.

The Android host speaks SOCKS5. Console traffic reaches it through the
Windows TUN adapter, with ICS providing DHCP/NAT on one selected output.
"""
from dataclasses import dataclass
import json
import os
import subprocess
import uuid


class BridgeError(RuntimeError):
    """A console bridge operation could not be completed safely."""


@dataclass(frozen=True)
class EthernetAdapter:
    guid: str
    name: str


@dataclass(frozen=True)
class BridgeDetails:
    mode: str
    output_name: str
    ssid: str = ""
    password: str = ""


LIST_ETHERNET = r"""
$ErrorActionPreference = 'Stop'
$adapters = @(
    Get-NetAdapter -Physical |
    Where-Object { $_.Status -eq 'Up' -and $_.MediaType -eq '802.3' } |
    ForEach-Object {
        @{ guid = $_.InterfaceGuid.ToString(); name = $_.Name }
    }
)
ConvertTo-Json -InputObject $adapters -Compress -Depth 3
"""

HOTSPOT_INFO = r"""
$ErrorActionPreference = 'Stop'
$profile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType=WindowsRuntime]::GetInternetConnectionProfile()
if (-not $profile) {
    ConvertTo-Json -InputObject @{ ssid = ''; password = '' } -Compress
    return
}
$manager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType=WindowsRuntime]::CreateFromConnectionProfile($profile)
if (-not $manager) {
    ConvertTo-Json -InputObject @{ ssid = ''; password = '' } -Compress
    return
}
$config = $manager.GetCurrentAccessPointConfiguration()
ConvertTo-Json -InputObject @{
    ssid = [string]$config.Ssid
    password = [string]$config.Passphrase
} -Compress
"""


START_HOTSPOT = r"""
$ErrorActionPreference = 'Stop'
$started = $false
$manager = $null
try {
    $profile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType=WindowsRuntime]::GetInternetConnectionProfile()
    if (-not $profile) { throw 'Windows has no internet connection profile. Turn on Mobile Hotspot in Windows Settings, then retry.' }
    $manager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType=WindowsRuntime]::CreateFromConnectionProfile($profile)
    if (-not $manager) { throw 'Windows Mobile Hotspot is unavailable. Use Ethernet or turn on Mobile Hotspot in Windows Settings.' }
    if ($manager.TetheringOperationalState.ToString() -ne 'On') {
        $manager.StartTetheringAsync() | Out-Null
        $started = $true
        for ($i = 0; $i -lt 30 -and $manager.TetheringOperationalState.ToString() -ne 'On'; $i++) {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($manager.TetheringOperationalState.ToString() -ne 'On') {
        throw 'Windows Mobile Hotspot did not start. Check Wi-Fi adapter support or use Ethernet.'
    }
    $adapters = @(
        Get-NetAdapter -IncludeHidden |
        Where-Object {
            $_.Status -eq 'Up' -and
            $_.InterfaceDescription -like '*Microsoft Wi-Fi Direct Virtual Adapter*'
        }
    )
    if ($adapters.Count -ne 1) {
        throw 'Could not identify one active Windows Mobile Hotspot adapter. Use Ethernet or configure the hotspot in Windows Settings.'
    }
    $config = $manager.GetCurrentAccessPointConfiguration()
    ConvertTo-Json -InputObject @{
        guid = $adapters[0].InterfaceGuid.ToString()
        name = $adapters[0].Name
        ssid = [string]$config.Ssid
        password = [string]$config.Passphrase
        started = $started
    } -Compress -Depth 3
} catch {
    $errorText = $_.Exception.Message
    if ($started -and $manager) {
        try {
            $manager.StopTetheringAsync() | Out-Null
            for ($i = 0; $i -lt 30 -and $manager.TetheringOperationalState.ToString() -ne 'Off'; $i++) {
                Start-Sleep -Milliseconds 500
            }
            if ($manager.TetheringOperationalState.ToString() -eq 'Off') {
                $started = $false
            } else {
                $errorText += ' Hotspot cleanup did not finish.'
            }
        } catch { $errorText += " Hotspot cleanup failed: $($_.Exception.Message)" }
    }
    ConvertTo-Json -InputObject @{ error = $errorText; started = $started } -Compress
}
"""

STOP_HOTSPOT = r"""
$ErrorActionPreference = 'Stop'
$profile = [Windows.Networking.Connectivity.NetworkInformation, Windows.Networking.Connectivity, ContentType=WindowsRuntime]::GetInternetConnectionProfile()
if (-not $profile) { throw 'Cannot find the Windows Mobile Hotspot connection profile.' }
$manager = [Windows.Networking.NetworkOperators.NetworkOperatorTetheringManager, Windows.Networking.NetworkOperators, ContentType=WindowsRuntime]::CreateFromConnectionProfile($profile)
if (-not $manager) { throw 'Cannot access Windows Mobile Hotspot.' }
if ($manager.TetheringOperationalState.ToString() -eq 'On') {
    $manager.StopTetheringAsync() | Out-Null
    for ($i = 0; $i -lt 30 -and $manager.TetheringOperationalState.ToString() -ne 'Off'; $i++) {
        Start-Sleep -Milliseconds 500
    }
    if ($manager.TetheringOperationalState.ToString() -ne 'Off') {
        throw 'Windows Mobile Hotspot did not stop. Turn it off in Windows Settings.'
    }
}
ConvertTo-Json -InputObject @{ stopped = $true } -Compress
"""

ICS_COMMON = r"""
$ErrorActionPreference = 'Stop'
function Normalize-Guid($value) {
    return ([string]$value).Trim('{}').ToUpperInvariant()
}
$manager = New-Object -ComObject HNetCfg.HNetShare
$entries = @(
    foreach ($connection in $manager.EnumEveryConnection) {
        $properties = $manager.NetConnectionProps($connection)
        $configuration = $manager.INetSharingConfigurationForINetConnection($connection)
        [pscustomobject]@{
            Guid = Normalize-Guid $properties.Guid
            Name = [string]$properties.Name
            Configuration = $configuration
            Enabled = [bool]$configuration.SharingEnabled
            Type = $(if ($configuration.SharingEnabled) { [int]$configuration.SharingConnectionType } else { -1 })
        }
    }
)
if ($entries.Count -eq 0) { throw 'Cannot enumerate Windows sharing adapters. Run the app as Administrator.' }
"""

ENABLE_ICS = ICS_COMMON + r"""
$outputGuid = Normalize-Guid $env:CONSOLE_OUTPUT_GUID
$source = @($entries | Where-Object { $_.Name -eq 'LaptopProxyVPN' })
$output = @($entries | Where-Object { $_.Guid -eq $outputGuid })
if ($source.Count -ne 1 -or $output.Count -ne 1) {
    throw 'VPN or selected output adapter was not found. Connect the VPN and selected adapter, then retry.'
}
if ($source[0].Guid -eq $output[0].Guid) { throw 'VPN and output adapter must be different.' }
$vpnAdapter = Get-NetAdapter -Name 'LaptopProxyVPN' -ErrorAction Stop
$outputAdapter = @(Get-NetAdapter -IncludeHidden | Where-Object { (Normalize-Guid $_.InterfaceGuid) -eq $outputGuid })
if ($vpnAdapter.Status -ne 'Up' -or $outputAdapter.Count -ne 1 -or $outputAdapter[0].Status -ne 'Up') {
    throw 'VPN and output adapter must both be connected.'
}
$active = @($entries | Where-Object { $_.Enabled })
if ($active.Count -ne 0) {
    $public = @($active | Where-Object { $_.Type -eq 0 })
    $private = @($active | Where-Object { $_.Type -eq 1 })
    if ($active.Count -ne 2 -or $public.Count -ne 1 -or $private.Count -ne 1 -or $private[0].Guid -ne $outputGuid) {
        throw 'Internet Connection Sharing is already in use by another adapter. Disable that sharing before starting Console Bridge.'
    }
}
$previous = @(
    $active | ForEach-Object { @{ guid = $_.Guid; type = $_.Type } }
)
$alreadyConfigured = $active.Count -eq 2 -and
    @($active | Where-Object { $_.Guid -eq $source[0].Guid -and $_.Type -eq 0 }).Count -eq 1
if (-not $alreadyConfigured) {
    try {
        foreach ($entry in $active) { $entry.Configuration.DisableSharing() }
        $source[0].Configuration.EnableSharing(0)
        $output[0].Configuration.EnableSharing(1)
        if (-not $source[0].Configuration.SharingEnabled -or
            [int]$source[0].Configuration.SharingConnectionType -ne 0 -or
            -not $output[0].Configuration.SharingEnabled -or
            [int]$output[0].Configuration.SharingConnectionType -ne 1) {
            throw 'Windows did not enable Internet Connection Sharing.'
        }
    } catch {
        $originalError = $_
        $rollbackErrors = @()
        foreach ($entry in @($source[0], $output[0])) {
            try {
                if ($entry.Configuration.SharingEnabled) { $entry.Configuration.DisableSharing() }
            } catch { $rollbackErrors += $_.Exception.Message }
        }
        foreach ($saved in $previous) {
            $entry = @($entries | Where-Object { $_.Guid -eq $saved.guid })
            if ($entry.Count -eq 1) {
                try { $entry[0].Configuration.EnableSharing([int]$saved.type) }
                catch { $rollbackErrors += $_.Exception.Message }
            } else { $rollbackErrors += "Could not find prior adapter $($saved.guid)" }
        }
        if ($rollbackErrors.Count -gt 0) {
            ConvertTo-Json -InputObject @{
                error = "Sharing setup failed: $originalError. Restoration also failed: $($rollbackErrors -join '; ')."
                source_guid = $source[0].Guid
                output_guid = $outputGuid
                output_name = $output[0].Name
                previous = $previous
                owned = $true
            } -Compress -Depth 5
            return
        }
        throw $originalError
    }
}
ConvertTo-Json -InputObject @{
    source_guid = $source[0].Guid
    output_guid = $outputGuid
    output_name = $output[0].Name
    previous = $previous
    owned = (-not $alreadyConfigured)
} -Compress -Depth 5
"""

DISABLE_ICS = ICS_COMMON + r"""
$state = $env:CONSOLE_BRIDGE_STATE | ConvertFrom-Json
if ($state.owned) {
    $sourceGuid = Normalize-Guid $state.source_guid
    $outputGuid = Normalize-Guid $state.output_guid
    $active = @($entries | Where-Object { $_.Enabled })
    $foreign = @($active | Where-Object {
        $current = $_
        $saved = @($state.previous | Where-Object {
            (Normalize-Guid $_.guid) -eq $current.Guid -and [int]$_.type -eq $current.Type
        })
        $current.Guid -ne $sourceGuid -and $current.Guid -ne $outputGuid -and $saved.Count -eq 0
    })
    if ($foreign.Count -gt 0) {
        throw 'Sharing was changed outside this app. Leaving those settings untouched; inspect Windows adapter Sharing settings.'
    }
    foreach ($entry in @($entries | Where-Object {
        $_.Guid -eq $sourceGuid -or $_.Guid -eq $outputGuid
    })) {
        if ($entry.Configuration.SharingEnabled) { $entry.Configuration.DisableSharing() }
    }
    foreach ($saved in @($state.previous)) {
        $guid = Normalize-Guid $saved.guid
        $entry = @($entries | Where-Object { $_.Guid -eq $guid })
        if ($entry.Count -ne 1) {
            throw "Could not restore sharing for adapter $guid. Check Windows adapter Sharing settings."
        }
        if (-not $entry[0].Configuration.SharingEnabled -or
            [int]$entry[0].Configuration.SharingConnectionType -ne [int]$saved.type) {
            $entry[0].Configuration.EnableSharing([int]$saved.type)
        }
    }
}
ConvertTo-Json -InputObject @{ restored = $true } -Compress
"""


def normalize_guid(value):
    return str(uuid.UUID(str(value).strip("{}"))).upper()


class IcsManager:
    def __init__(self, log_fn=print, runner=None):
        self.log = log_fn
        self._runner = runner or subprocess.run
        self._state = None

    @property
    def active(self):
        return self._state is not None and not self._state["ics_restored"]

    @property
    def needs_cleanup(self):
        return self._state is not None

    def _run(self, script, extra_env=None, timeout=60):
        env = os.environ.copy()
        env.update(extra_env or {})
        try:
            result = self._runner(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
                capture_output=True, text=True, timeout=timeout, env=env,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            raise BridgeError(f"Windows sharing command failed: {error}") from error
        if result.returncode != 0:
            raise BridgeError((result.stderr or result.stdout).strip() or
                              "Windows sharing command failed.")
        try:
            return json.loads(result.stdout.strip())
        except (ValueError, TypeError) as error:
            raise BridgeError("Windows sharing returned an invalid response.") from error

    def list_ethernet(self):
        entries = self._run(LIST_ETHERNET)
        if not isinstance(entries, list):
            raise BridgeError("Could not list Ethernet adapters.")
        return [EthernetAdapter(normalize_guid(item["guid"]), item["name"])
                for item in entries]

    def hotspot_info(self):
        info = self._run(HOTSPOT_INFO)
        return info.get("ssid", ""), info.get("password", "")

    def start(self, mode, ethernet_guid=None):
        if self._state is not None:
            raise BridgeError("Console sharing is already active.")
        if mode not in ("ethernet", "wifi"):
            raise BridgeError("Select Ethernet or Wi-Fi hotspot.")
        wifi = None
        if mode == "ethernet":
            if ethernet_guid is None:
                raise BridgeError("Select a connected Ethernet adapter.")
            output_guid = normalize_guid(ethernet_guid)
            if output_guid not in {item.guid for item in self.list_ethernet()}:
                raise BridgeError("Selected Ethernet adapter is not connected. Plug in the cable and refresh.")
        try:
            if mode == "wifi":
                wifi = self._run(START_HOTSPOT)
                if wifi.get("error"):
                    raise BridgeError(wifi["error"])
                output_guid = normalize_guid(wifi["guid"])
            sharing = self._run(ENABLE_ICS, {"CONSOLE_OUTPUT_GUID": output_guid})
        except Exception as error:
            if wifi and wifi.get("started"):
                try:
                    self._run(STOP_HOTSPOT)
                except BridgeError as cleanup_error:
                    self._state = {
                        "mode": mode, "hotspot_started": True,
                        "ics_restored": True,
                    }
                    raise BridgeError(
                        f"{error} Hotspot cleanup also failed: {cleanup_error}"
                    ) from cleanup_error
            if isinstance(error, BridgeError):
                raise
            raise BridgeError(f"Could not configure console sharing: {error}") from error

        if sharing.get("error"):
            self._state = {
                **sharing, "mode": mode,
                "hotspot_started": bool(wifi and wifi.get("started")),
                "ics_restored": False,
            }
            try:
                self.stop()
            except BridgeError as cleanup_error:
                raise BridgeError(
                    f"{sharing['error']} Cleanup still needs attention: {cleanup_error}"
                ) from cleanup_error
            raise BridgeError(sharing["error"])

        self._state = {
            **sharing,
            "mode": mode,
            "hotspot_started": bool(wifi and wifi.get("started")),
            "ics_restored": False,
        }
        self.log(f"Console sharing enabled on {sharing['output_name']}.")
        return BridgeDetails(
            mode=mode,
            output_name=sharing["output_name"],
            ssid=wifi.get("ssid", "") if wifi else "",
            password=wifi.get("password", "") if wifi else "",
        )

    def stop(self):
        if self._state is None:
            return
        if not self._state["ics_restored"]:
            self._run(DISABLE_ICS, {
                "CONSOLE_BRIDGE_STATE": json.dumps(self._state, separators=(",", ":"))
            })
            self._state["ics_restored"] = True
        if self._state["hotspot_started"]:
            self._run(STOP_HOTSPOT)
        self._state = None
        self.log("Console sharing stopped and prior sharing settings restored.")
