# Share Internet with Consoles and Other Devices

A PS4, PS5, Xbox, smart TV, or other device can use the Android host without installing an app. A Windows 10/11 laptop acts as the bridge:

`Android SOCKS5 host -> Windows VPN tunnel -> Windows Internet Connection Sharing -> console`

The phone's Wi-Fi Direct connection is a SOCKS5 proxy, so connecting a console directly to it does not provide a normal internet connection. The Windows client creates a VPN adapter and shares that adapter over one selected Ethernet port or Windows Mobile Hotspot.

## Before You Start

1. Start **Share (Host)** on the Android phone.
2. Connect the Windows laptop to the phone's Wi-Fi Direct network.
3. Run the [Windows client](/guide/windows) as Administrator and click **Connect VPN**. Wait for **Connected**.
4. For Ethernet, connect the console to a laptop Ethernet port or USB Ethernet adapter. For Wi-Fi, the laptop must support Mobile Hotspot while connected to the phone.

## Ethernet Cable

1. Select **Ethernet cable** in **Share with consoles & devices**.
2. Connect the cable, click **Refresh**, and select the connected Ethernet adapter.
3. Click **Start Sharing Network** and wait for the success message.
4. Set up a wired connection on the console. Keep IP and DNS automatic, and leave proxy settings off.
5. Test the console connection, then try a download and an online game.

## Wi-Fi Hotspot

1. Leave the default **Wi-Fi hotspot** method selected in **Share with consoles & devices** and click **Start Sharing Network**.
2. Wait for confirmation that the hotspot and sharing are active. Join the displayed network using the displayed password. You can also find them under **Windows Settings > Network & internet > Mobile hotspot**.
3. Keep the console's IP and DNS automatic, and leave proxy settings off.
4. Test the console connection, then try a download and an online game.

If the hotspot cannot start or the Wi-Fi adapter cannot stay connected to the phone while hosting, use Ethernet. A second Wi-Fi adapter may also help.

## Stop and Troubleshooting

Click **Stop Sharing Network** before stopping the VPN. The client also removes its sharing configuration on VPN stop or tunnel loss and restores the Windows sharing settings it changed. If cleanup fails, retry **Stop Sharing Network** and check the adapter's **Sharing** tab in Windows Network Connections before closing the app.

| Issue | What to check |
|-------|---------------|
| Ethernet adapter is missing | Connect the cable, wait for Windows to show the port as connected, then click **Refresh**. |
| Sharing already in use | The app leaves sharing on unrelated adapters alone. Disable the other sharing first. |
| Hotspot will not start | Check Windows Mobile Hotspot settings, try a second Wi-Fi adapter, or use Ethernet. |
| Console has no IP or DNS | Restart console sharing and reconnect the console. |
| Downloads work but games fail | Mobile-carrier NAT can restrict inbound traffic. Outbound TCP and UDP are forwarded, but NAT Type 2 and every peer-to-peer game cannot be guaranteed. |

A Mac or other computer can also join the Windows bridge. The Windows client itself runs only on Windows.
