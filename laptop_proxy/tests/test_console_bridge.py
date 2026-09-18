import ast
import json
from pathlib import Path
import subprocess
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from console_bridge import (
    BridgeError,
    DISABLE_ICS,
    ENABLE_ICS,
    HOTSPOT_INFO,
    IcsManager,
    LIST_ETHERNET,
    START_HOTSPOT,
    STOP_HOTSPOT,
)


GUID = "4676621A-5C20-4942-A1C1-584C7F7BD4C2"
VPN_GUID = "4B87E160-0000-4000-8000-000000000001"
OLD_GUID = "4B87E160-0000-4000-8000-000000000002"


class FakePowerShell:
    def __init__(self, *, hotspot_started=False):
        self.hotspot_started = hotspot_started
        self.calls = []
        self.fail_on = set()

    def __call__(self, command, *, capture_output, text, timeout, env):
        script = command[-1]
        self.calls.append((script, dict(env)))
        if script in self.fail_on:
            return subprocess.CompletedProcess(command, 1, "", "Windows operation failed")
        if script == LIST_ETHERNET:
            result = [{"guid": GUID, "name": "Ethernet"}]
        elif script == HOTSPOT_INFO:
            result = {"ssid": "Console Bridge", "password": "test-password"}
        elif script == START_HOTSPOT:
            result = {
                "guid": GUID, "name": "Local Area Connection* 4",
                "ssid": "Console Bridge", "password": "test-password",
                "started": self.hotspot_started,
            }
        elif script == ENABLE_ICS:
            result = {
                "source_guid": VPN_GUID,
                "output_guid": GUID,
                "output_name": "Ethernet",
                "previous": [
                    {"guid": OLD_GUID, "type": 0},
                    {"guid": GUID, "type": 1},
                ],
                "owned": True,
            }
        elif script == DISABLE_ICS:
            result = {"restored": True}
        elif script == STOP_HOTSPOT:
            result = {"stopped": True}
        else:
            raise AssertionError("Unexpected PowerShell script")
        return subprocess.CompletedProcess(command, 0, json.dumps(result), "")


def test_ethernet_bridge_restores_previous_sharing():
    runner = FakePowerShell()
    bridge = IcsManager(runner=runner)
    assert bridge.list_ethernet()[0].name == "Ethernet"

    details = bridge.start("ethernet", GUID)
    assert details.output_name == "Ethernet"
    assert bridge.active
    assert [call[0] for call in runner.calls].count(ENABLE_ICS) == 1

    bridge.stop()
    assert not bridge.needs_cleanup
    restore_env = [env for script, env in runner.calls if script == DISABLE_ICS][0]
    previous = json.loads(restore_env["CONSOLE_BRIDGE_STATE"])["previous"]
    assert previous == [{"guid": OLD_GUID, "type": 0}, {"guid": GUID, "type": 1}]
    assert not any(script == STOP_HOTSPOT for script, _ in runner.calls)


def test_disconnected_ethernet_cannot_change_ics():
    runner = FakePowerShell()
    bridge = IcsManager(runner=runner)
    with pytest.raises(BridgeError, match="not connected"):
        bridge.start("ethernet", "00000000-0000-4000-8000-000000000001")
    assert all(script != ENABLE_ICS for script, _ in runner.calls)


def test_hotspot_is_stopped_if_ics_fails():
    runner = FakePowerShell(hotspot_started=True)
    runner.fail_on.add(ENABLE_ICS)
    bridge = IcsManager(runner=runner)
    with pytest.raises(BridgeError, match="Windows operation failed"):
        bridge.start("wifi")
    assert not bridge.needs_cleanup
    assert [script for script, _ in runner.calls][-1] == STOP_HOTSPOT


def test_existing_hotspot_is_left_running_on_stop():
    runner = FakePowerShell(hotspot_started=False)
    bridge = IcsManager(runner=runner)
    details = bridge.start("wifi")
    assert details.ssid == "Console Bridge"
    bridge.stop()
    assert not any(script == STOP_HOTSPOT for script, _ in runner.calls)


def test_failed_restore_can_be_retried_before_hotspot_stops():
    runner = FakePowerShell(hotspot_started=True)
    bridge = IcsManager(runner=runner)
    bridge.start("wifi")
    runner.fail_on.add(DISABLE_ICS)
    with pytest.raises(BridgeError):
        bridge.stop()
    assert bridge.needs_cleanup
    assert not any(script == STOP_HOTSPOT for script, _ in runner.calls)
    runner.fail_on.clear()
    bridge.stop()
    assert not bridge.needs_cleanup
    assert [script for script, _ in runner.calls][-1] == STOP_HOTSPOT


def test_windows_client_source_parses():
    ast.parse((Path(__file__).resolve().parents[1] / "main.py").read_text(encoding="utf-8"))


MOCK_ICS = r"""
$ErrorActionPreference = 'Stop'
function New-FakeConnection($guid, $name, $enabled, $type, $failEnable = $false) {
    $config = [pscustomobject]@{
        SharingEnabled = [bool]$enabled
        SharingConnectionType = [int]$type
        FailEnable = [bool]$failEnable
    }
    $config | Add-Member -MemberType ScriptMethod -Name EnableSharing -Value {
        param($newType)
        if ($this.FailEnable) { throw 'simulated sharing failure' }
        $this.SharingEnabled = $true
        $this.SharingConnectionType = [int]$newType
    }
    $config | Add-Member -MemberType ScriptMethod -Name DisableSharing -Value {
        $this.SharingEnabled = $false
    }
    return [pscustomobject]@{
        Guid = $guid
        Name = $name
        Config = $config
    }
}
$script:connections = @(
    New-FakeConnection $env:TEST_VPN_GUID 'LaptopProxyVPN' $false -1
    New-FakeConnection $env:TEST_OLD_GUID 'Existing connection' $true 0
    New-FakeConnection $env:TEST_OUTPUT_GUID 'Ethernet' $true 1
)
$script:manager = [pscustomobject]@{ EnumEveryConnection = $script:connections }
$script:manager | Add-Member -MemberType ScriptMethod -Name NetConnectionProps -Value {
    param($connection)
    return [pscustomobject]@{ Guid = $connection.Guid; Name = $connection.Name }
}
$script:manager | Add-Member -MemberType ScriptMethod -Name INetSharingConfigurationForINetConnection -Value {
    param($connection)
    return $connection.Config
}
function New-Object {
    param([string]$ComObject)
    if ($ComObject -ne 'HNetCfg.HNetShare') { throw 'Unexpected COM request' }
    return $script:manager
}
function Get-NetAdapter {
    param([string]$Name, [switch]$IncludeHidden, [string]$ErrorAction)
    if ($Name) {
        return [pscustomobject]@{ Status = 'Up'; InterfaceGuid = $env:TEST_VPN_GUID }
    }
    return [pscustomobject]@{ Status = 'Up'; InterfaceGuid = $env:TEST_OUTPUT_GUID }
}
function Get-Snapshot {
    return @($script:connections | ForEach-Object {
        @{ name = $_.Name; enabled = $_.Config.SharingEnabled; type = $_.Config.SharingConnectionType }
    })
}
"""


def run_mock_ics(*, wrong_private=False, fail_enable=False, fail_restore=False, no_prior_sharing=False):
    import os
    env = os.environ.copy()
    env.update({
        "TEST_VPN_GUID": VPN_GUID,
        "TEST_OLD_GUID": OLD_GUID,
        "TEST_OUTPUT_GUID": GUID,
        "CONSOLE_OUTPUT_GUID": GUID,
    })
    setup = MOCK_ICS
    if no_prior_sharing:
        setup += "$script:connections[1].Config.SharingEnabled = $false\n"
        setup += "$script:connections[2].Config.SharingEnabled = $false\n"
    if wrong_private:
        setup += "$script:connections[2].Guid = '00000000-0000-4000-8000-000000000003'\n"
        setup += "$script:connections += @(New-FakeConnection $env:TEST_OUTPUT_GUID 'Selected Ethernet' $false -1)\n"
        setup += "$script:manager.EnumEveryConnection = $script:connections\n"
    if fail_enable:
        setup += "$script:connections[0].Config.FailEnable = $true\n"
    if fail_restore:
        setup += "$script:connections[1].Config.FailEnable = $true\n"
    ps = setup + "\ntry {\n  $enabled = & {\n" + ENABLE_ICS + "\n  } | ConvertFrom-Json\n"
    ps += "  if ($enabled -and -not $enabled.error) {\n    $env:CONSOLE_BRIDGE_STATE = $enabled | ConvertTo-Json -Compress -Depth 5\n"
    ps += "    $disabled = & {\n" + DISABLE_ICS + "\n    } | ConvertFrom-Json\n  }\n"
    ps += "  $errorText = ''\n} catch { $errorText = $_.Exception.Message }\n"
    ps += "@{ error = $errorText; partialError = [string]$enabled.error; enabled = [bool]$enabled; "
    ps += "restored = [bool]$disabled; state = (Get-Snapshot) } | ConvertTo-Json -Compress -Depth 6\n"
    result = subprocess.run(
        ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps],
        capture_output=True, text=True, env=env, timeout=30,
    )
    assert result.returncode == 0, result.stderr
    return json.loads(result.stdout.strip())


@pytest.mark.skipif(sys.platform != "win32", reason="PowerShell COM simulation requires Windows")
def test_ics_scripts_replace_and_restore_only_selected_pair():
    result = run_mock_ics()
    assert result["error"] == ""
    assert result["enabled"] and result["restored"]
    state = {entry["name"]: entry for entry in result["state"]}
    assert not state["LaptopProxyVPN"]["enabled"]
    assert state["Existing connection"]["enabled"] and state["Existing connection"]["type"] == 0
    assert state["Ethernet"]["enabled"] and state["Ethernet"]["type"] == 1


@pytest.mark.skipif(sys.platform != "win32", reason="PowerShell COM simulation requires Windows")
def test_ics_scripts_leave_unrelated_sharing_untouched():
    result = run_mock_ics(wrong_private=True)
    assert "already in use" in result["error"]
    assert not result["enabled"]
    state = {entry["name"]: entry for entry in result["state"]}
    assert not state["LaptopProxyVPN"]["enabled"]
    assert state["Existing connection"]["enabled"]
    assert state["Ethernet"]["enabled"]


@pytest.mark.skipif(sys.platform != "win32", reason="PowerShell COM simulation requires Windows")
def test_ics_scripts_rollback_on_enable_failure():
    result = run_mock_ics(fail_enable=True)
    assert "simulated sharing failure" in result["error"]
    assert not result["enabled"]
    state = {entry["name"]: entry for entry in result["state"]}
    assert not state["LaptopProxyVPN"]["enabled"]
    assert state["Existing connection"]["enabled"] and state["Existing connection"]["type"] == 0
    assert state["Ethernet"]["enabled"] and state["Ethernet"]["type"] == 1


@pytest.mark.skipif(sys.platform != "win32", reason="PowerShell COM simulation requires Windows")
def test_ics_script_reports_recoverable_partial_rollback():
    result = run_mock_ics(fail_enable=True, fail_restore=True)
    assert "Restoration also failed" in result["partialError"]
    assert not result["restored"]


def test_failed_hotspot_cleanup_keeps_retry_state():
    runner = FakePowerShell(hotspot_started=True)
    runner.fail_on.update({ENABLE_ICS, STOP_HOTSPOT})
    bridge = IcsManager(runner=runner)
    with pytest.raises(BridgeError, match="Hotspot cleanup also failed"):
        bridge.start("wifi")
    assert bridge.needs_cleanup
    runner.fail_on.clear()
    bridge.stop()
    assert not bridge.needs_cleanup


def test_partial_ics_response_is_cleaned_up_before_error():
    class PartialRunner(FakePowerShell):
        def __call__(self, command, **kwargs):
            if command[-1] == ENABLE_ICS:
                result = {
                    "error": "Sharing setup failed; restoration also failed",
                    "source_guid": VPN_GUID,
                    "output_guid": GUID,
                    "output_name": "Ethernet",
                    "previous": [{"guid": OLD_GUID, "type": 0}, {"guid": GUID, "type": 1}],
                    "owned": True,
                }
                self.calls.append((command[-1], dict(kwargs["env"])))
                return subprocess.CompletedProcess(command, 0, json.dumps(result), "")
            return super().__call__(command, **kwargs)

    runner = PartialRunner()
    bridge = IcsManager(runner=runner)
    with pytest.raises(BridgeError, match="restoration also failed"):
        bridge.start("ethernet", GUID)
    assert [script for script, _ in runner.calls][-1] == DISABLE_ICS
    assert not bridge.needs_cleanup


@pytest.mark.skipif(sys.platform != "win32", reason="PowerShell COM simulation requires Windows")
def test_ics_scripts_start_and_stop_without_previous_sharing():
    result = run_mock_ics(no_prior_sharing=True)
    assert result["error"] == ""
    assert result["enabled"] and result["restored"]
    assert not any(entry["enabled"] for entry in result["state"])


def test_hotspot_start_error_retries_cleanup_when_windows_started_it():
    class StartupFailure(FakePowerShell):
        def __call__(self, command, **kwargs):
            if command[-1] == START_HOTSPOT:
                self.calls.append((command[-1], dict(kwargs["env"])))
                return subprocess.CompletedProcess(
                    command, 0, json.dumps({"error": "Hotspot unsupported", "started": True}), ""
                )
            return super().__call__(command, **kwargs)

    runner = StartupFailure()
    bridge = IcsManager(runner=runner)
    with pytest.raises(BridgeError, match="Hotspot unsupported"):
        bridge.start("wifi")
    assert [script for script, _ in runner.calls][-1] == STOP_HOTSPOT
    assert not bridge.needs_cleanup


def test_hotspot_credentials_are_read_without_starting_sharing():
    runner = FakePowerShell()
    bridge = IcsManager(runner=runner)
    assert bridge.hotspot_info() == ("Console Bridge", "test-password")
    assert [script for script, _ in runner.calls] == [HOTSPOT_INFO]
    assert not bridge.needs_cleanup
