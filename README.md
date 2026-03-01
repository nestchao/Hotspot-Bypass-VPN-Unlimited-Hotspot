This repository contains a specialized networking toolset designed to bypass carrier-imposed hotspot data limits and throttling. It allows you to share your phone's internet connection (including VPN data) with other Android devices or Windows PCs using Wi-Fi Direct and SOCKS5 proxying.

### **Project Overview**
Unlike standard Android tethering, which flags data as "Hotspot," this project uses **Wi-Fi Direct (P2P)** to create a private network. It then runs a local **SOCKS5 Proxy Server** on the host device and a **VPN Client** on the connected devices to route all traffic through the proxy, making the tethered data indistinguishable from on-device data.

### **Core Components**

#### **1. Android App (The Host & Client)**
*   **Host Mode:** Creates a Wi-Fi Direct group and starts a high-performance SOCKS5 server (TCP/UDP supported) to act as a gateway.
*   **Client Mode:** Uses `tun2socks` to create a system-wide VPN tunnel that routes all device traffic (including games and apps) to the Host's proxy.
*   **Optimized Engine:** Includes a custom-built VPN service with DNS caching and multi-threaded packet processing to ensure low latency for gaming and streaming.
*   **Modern UI:** Built with Jetpack Compose and Material 3, featuring real-time connection logs and hardware status monitoring.

#### **2. Windows Companion (Phone Proxy Manager)**
*   **Global VPN (TUN) Mode:** Uses `wintun` and `tun2socks` to route 100% of Windows traffic (not just the browser) through the phone.
*   **HTTP/SOCKS Bridge:** A built-in bridge that allows legacy applications to connect via local port forwarding.
*   **Automatic Setup:** Automatically downloads required binaries and configures Windows routing tables/system proxy settings with a single click.

### **Key Features**
*   **Bypass Restrictions:** Share your mobile data without hitting hotspot caps or "tethering" speed limits.
*   **Share VPNs:** Share an active VPN connection from your phone to other devices that don't support VPNs.
*   **UDP Support:** Dedicated handling for UDP associations, enabling online gaming (Roblox, Discord, etc.) over the proxied connection.
*   **Battery Optimized:** Includes requests for battery optimization exemptions to prevent the service from being killed in the background.
*   **No Root Required:** Operates entirely within the Android VpnService and Wi-Fi Direct APIs.

### **Technology Stack**
*   **Android:** Kotlin, Jetpack Compose, Coroutines, VpnService API, Wi-Fi P2P API.
*   **Windows:** Python 3, Tkinter, WinReg, Subprocess (routing).
*   **Networking:** SOCKS5 Protocol, tun2socks (Go engine), Wintun (L3 TUN driver).
