package com.example.hotspot_bypass_vpn

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * Decides who may use the host's SOCKS5 proxy and where it may connect.
 *
 * The proxy listens on every interface so it keeps working when the Wi-Fi Direct
 * group is re-created, but it must only serve devices that joined that group.
 * Without this, anyone on another network the host phone is on (for example a
 * public Wi-Fi) could use the phone as an open proxy.
 */
internal object ProxyAccessPolicy {

    /** Android always gives the Wi-Fi Direct group owner 192.168.49.1/24. */
    private const val WIFI_DIRECT_SUBNET_A = 192
    private const val WIFI_DIRECT_SUBNET_B = 168
    private const val WIFI_DIRECT_SUBNET_C = 49

    /** Wi-Fi Direct group interfaces are named like p2p-wlan0-0, p2p-p2p0-0 or p2p0. */
    private const val WIFI_DIRECT_INTERFACE_PREFIX = "p2p"

    /**
     * @param localAddress the host address the client connected to
     * @param remoteAddress the client's address
     * @param interfaceName name of the host interface that owns [localAddress], if known
     */
    fun isAllowedClient(localAddress: InetAddress?, remoteAddress: InetAddress?, interfaceName: String?): Boolean {
        // Apps on the host phone itself.
        if (remoteAddress?.isLoopbackAddress == true) return true
        if (interfaceName?.startsWith(WIFI_DIRECT_INTERFACE_PREFIX) == true) return true
        return localAddress != null && isWifiDirectAddress(localAddress)
    }

    fun isAllowedClient(socket: Socket): Boolean {
        val local = socket.localAddress
        return isAllowedClient(local, socket.inetAddress, interfaceNameOf(local))
    }

    /**
     * Clients must not reach services that listen only on the host's loopback
     * interface (debug bridges, local servers of other apps).
     */
    fun isBlockedTarget(address: InetAddress?): Boolean =
        address != null && (address.isLoopbackAddress || address.isAnyLocalAddress)

    fun isWifiDirectAddress(address: InetAddress): Boolean {
        if (address !is Inet4Address) return false
        val bytes = address.address
        return (bytes[0].toInt() and 0xFF) == WIFI_DIRECT_SUBNET_A &&
            (bytes[1].toInt() and 0xFF) == WIFI_DIRECT_SUBNET_B &&
            (bytes[2].toInt() and 0xFF) == WIFI_DIRECT_SUBNET_C
    }

    fun interfaceNameOf(address: InetAddress?): String? = try {
        address?.let { NetworkInterface.getByInetAddress(it)?.name }
    } catch (_: Exception) {
        null
    }
}
