# Windows Client Guide

## Overview

The Windows desktop client connects your laptop to the phone's SOCKS5 proxy and creates a TUN virtual adapter, routing **all** system traffic through the phone's connection.

---

## Installation

### Option 1: Portable Windows Client

1. Download the Windows portable ZIP from a release that includes it, and extract the entire folder.
2. Run `Hotspot_Bypass_VPN_Windows.exe` from the extracted folder. Keep the adjacent `_internal` files with it.
3. The app will request **Administrator privileges** for the VPN adapter and connection sharing.

### Option 2: From Source

```bash
git clone https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot.git
cd Hotspot-Bypass-VPN-Unlimited-Hotspot/laptop_proxy
pip install -r requirements.txt
python main.py
```

---

## Quick Start

![Windows Client Screenshot](/images/screenshot-windows.png)
*Windows client main interface*

### Step 1: Phone Setup

Make sure your phone is running in **Host mode** (see [Android Guide](/guide/android)).

Note the connection details:
- **SSID:** `DIRECT-HotspotBypass`
- **Password:** `87654321`
- **Proxy:** `192.168.49.1:8080`

### Step 2: Connect Laptop to Wi-Fi Direct

1. Open Wi-Fi settings on your laptop.
2. Find and connect to **DIRECT-HotspotBypass**.
3. Enter password **87654321**.

### Step 3: Launch the Windows Client

1. Open the Windows client application.
2. Enter proxy details:
   - **Proxy IP:** `192.168.49.1`
   - **Proxy Port:** `8080`
3. Click **Start VPN**.
4. The app will automatically download tun2socks and wintun dependencies on first run, then create a TUN virtual adapter.

### Step 4: Verify

- Check the status indicator — it should show **Connected**.
- Open a browser and visit any website — traffic should be routed through your phone.
- Check the live log for connection details.

---

## Share with PS4, PS5, or Other Devices

After the VPN shows **Connected**, use **Share with consoles & devices**. The default is **Wi-Fi hotspot**; choose **Ethernet cable** if you prefer a wire, then refresh and select the connected adapter. Click **Start Sharing Network** and follow the connection details shown in the app. Set the console's IP and DNS to automatic and leave its proxy setting off. See the [console setup guide](/guide/non-app-devices) for troubleshooting and NAT limitations.

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "Administrator privileges required" | Right-click the EXE → **Run as administrator** |
| tun2socks download fails | Check your internet connection. The app can connect through the phone's proxy first. |
| TUN adapter not created | Run the app as Administrator. Disable any other VPN software. |
| No internet after connecting | Try restarting the VPN. Check the phone is still in Host mode. |
| Game lag / high ping | Switch phone to 5GHz Wi-Fi Direct band. Reduce distance between devices. |
| Console has no connection | Check the console setup guide, verify the selected adapter, and retry sharing. |
