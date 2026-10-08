# Download

**Latest version: v6.0.0** — [Release notes](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/tag/v6.0.0)

## Android App

Get the latest version of the Hotspot Bypass VPN Android app.

| Item | Link |
|------|------|
| APK Download | [Hotspot-Bypass-VPN.apk](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/Hotspot-Bypass-VPN.apk) |
| Google Play | *Coming soon* |
| Source Code | [GitHub Repository](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot) |

### Requirements

- Android 7.0 (API 24) or higher
- No root access required
- Wi-Fi Direct capable device (most modern Android phones)

## Windows Client

For Windows laptops, use the dedicated desktop client to connect to the phone's proxy.

| Item | Link |
|------|------|
| Installer | [HotspotBypassVPNSetup.exe](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/HotspotBypassVPNSetup.exe) |
| Portable ZIP | [Hotspot_Bypass_VPN_Windows.zip](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/releases/download/v6.0.0/Hotspot_Bypass_VPN_Windows.zip) |
| Source Code | [laptop_proxy/](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/tree/master/laptop_proxy) |

### Requirements

- Windows 10 or 11 (64-bit)
- Administrator privileges (required for TUN virtual adapter)

::: warning Antivirus false positive
Windows Defender may flag the desktop client as `Trojan:Win32/Wacatac.H!ml`. This is a **false positive** produced by heuristic (machine-learning) detection rather than a known virus signature — the client downloads helper binaries on first run, adds default routes, and changes the interface DNS, which together resemble a VPN-hijacking trojan. Tracked in [issue #11](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/issues/11).

If you would rather avoid the packaged executable, run the client from source — see [Building from Source](#building-from-source). That path is not flagged by antivirus software.
:::

### Verify your download

```bash
# Windows
certutil -hashfile Hotspot-Bypass-VPN.apk SHA256

# macOS / Linux
sha256sum Hotspot-Bypass-VPN.apk
```

| File | SHA256 |
|------|--------|
| `Hotspot-Bypass-VPN.apk` | `8e9c1c9123e7f35cfe85c4cf4c2283c56a75c19f9fd89fd0c01f111569b63571` |
| `HotspotBypassVPNSetup.exe` | `b6c0401d978281a58e1cd71a8d43ccb9f5600da1dc81cd9e3dbc8f1bd01c5568` |
| `Hotspot_Bypass_VPN_Windows.zip` | `39ef7e3ecacd6ce92c074eed5b4fdbf238d5b5f953b253b045ff2126606ac6f6` |

> The portable ZIP must be **fully extracted** before running — do not run the executable from inside the archive.

### Upgrade notes

- The APK filename changed from `Hotspot_Bypass_VPN.apk` to `Hotspot-Bypass-VPN.apk`.
- The APK is debug-signed. If your device refuses to install over an older build, uninstall the previous version first and re-enter your host settings.
- The Windows client now ships as an installer or a portable ZIP, replacing the single standalone `.exe`.

## Building from Source

### Android App

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot
./gradlew assembleDebug
```

### Windows Client

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

To build a standalone executable:

```bash
.\build_exe.bat
```

---

## Changelog

### Version 6.0.0 (October 2026)

- **Fixed** multi-device connection failure where the second device had no internet ([#13](https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot/issues/13))
- **Fixed** UDP association isolation between concurrent clients
- **Fixed** connection accounting leaks on client disconnect
- **Added** console sharing via Windows Mobile Hotspot or a selected Ethernet adapter
- **Added** per-client connection limits and rejection metrics
- **Improved** Windows client UI — compact Connect & Share layout, light/dark theme
- **Improved** graceful shutdown and idle connection cleanup

### Version 5.0.0 (June 2026)

- Windows client with a TUN virtual adapter capturing all system traffic
- Automatic crash recovery and proxy health checks every 15 seconds
- Portable ZIP and installer distribution for the desktop client

### Version 4.0.0 (June 2026)

- Migrated the laptop client UI to CustomTkinter
- Modernized the Windows build configuration

### Version 3.0.0 (June 2026)

- Enhanced service reliability and persistence
- Optimized networking performance for gaming
- Improved Windows client stability
- New Phone Proxy Manager for Windows
- Application logo and branding updates

### Version 2.0.0 (May 2026)

- Optimized networking performance for low-latency gaming
- Improved service persistence and background reliability
- Enhanced Windows client stability and features
- Added application logo and branding
