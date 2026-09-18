# Share Internet with Consoles and Other Devices Without the App

A PS4, PS5, Xbox, smart TV, or other device can use the Android host through a
Windows laptop. The device does not need this app or a manually configured
proxy.

`Android host -> Windows VPN client -> Windows Internet Connection Sharing -> console`

The Android phone exposes a SOCKS5 proxy, not a general-purpose Wi-Fi router.
The Windows client converts the phone connection into a VPN adapter and shares
that adapter with the console.

## Before You Start

- Start **Share (Host)** on the Android phone.
- Connect the Windows laptop to the phone's Wi-Fi Direct network.
- Run the Windows client as Administrator, enter the phone's proxy address, and
  click **Connect VPN**. Wait for **Connected**.
- If using Ethernet, connect the console to a laptop Ethernet port or USB
  Ethernet adapter with a cable. If using Wi-Fi, the laptop must support
  Windows Mobile Hotspot while connected to the phone.
- Install the Windows client dependencies on first use before relying on the
  bridge. The current client downloads tun2socks and Wintun through the phone's
  proxy when needed.

## Ethernet Cable

1. In the Windows client, select **Ethernet cable** under **Share with consoles & devices**.
2. Connect the cable and click **Refresh**. Select the connected Ethernet
   adapter.
3. Click **Start Sharing Network**. The app shares its VPN adapter with the
   selected Ethernet adapter and displays a success message.
4. On PS4/PS5, set up a **wired** internet connection. Leave IP address and DNS
   on automatic and turn the console proxy setting off.
5. Run the console's internet test, then test a download and an online game.

## Wi-Fi Hotspot

1. In the Windows client, leave the default **Wi-Fi hotspot** method selected
   and click **Start Sharing Network**.
2. Wait for the app to confirm that Windows Mobile Hotspot started and the VPN
   is shared through it. The app displays its network name and password. If
   Windows cannot provide those details, find them under
   **Settings > Network & internet > Mobile hotspot**.
3. On PS4/PS5, join that Windows hotspot. Leave IP address and DNS on automatic
   and turn the console proxy setting off.
4. Run the console's internet test, then test a download and an online game.

If Windows cannot start its hotspot or the laptop cannot use Wi-Fi and Mobile
Hotspot together, use the Ethernet method. This depends on the laptop's Wi-Fi
adapter and driver.

## Stopping and Troubleshooting

Click **Stop Sharing Network** before stopping the VPN. The app also stops
sharing when the VPN stops or loses its tunnel, and restores the Windows
sharing configuration it changed. If Windows reports a cleanup error, keep
the VPN running and check the adapter's **Sharing** tab in Network Connections
before closing the app.

- **No connected Ethernet adapter:** Connect the cable, wait for Windows to
  show the adapter as connected, and click **Refresh**.
- **Sharing already in use:** The app will not replace sharing on an unrelated
  adapter. Disable that other sharing in Windows first.
- **Hotspot will not start:** Check Windows Mobile Hotspot settings or use
  Ethernet.
- **Console gets no IP or DNS:** Stop and restart console sharing, then renew
  the console connection.
- **Downloads work but a game cannot join:** Mobile carriers may restrict
  inbound traffic. The app can forward outbound TCP and UDP, but cannot
  guarantee NAT Type 2 or compatibility with every peer-to-peer game.

A Mac or other computer can also join the Windows bridge. For web-only use on
a Mac, a manually configured SOCKS5 proxy to the Android host is an
alternative; it does not provide full-system gaming connectivity.
