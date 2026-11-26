package com.example.hotspot_bypass_vpn

import android.content.Context

/**
 * DNS servers the client VPN hands to apps. Queries travel through the tunnel
 * to the host like any other UDP traffic.
 *
 * Only IPv4 servers are accepted: the tunnel carries IPv4 only (IPv6 is routed
 * into it and dropped to stop leaks), so an IPv6 DNS server would be unreachable.
 *
 * Like [HotspotCredentials], the setting is written only by the UI process and
 * passed to the VPN service (in the :vpn process) in its start intent.
 */
object ClientDnsSettings {
    const val EXTRA_DNS_SERVERS = "DNS_SERVERS"
    val DEFAULT_SERVERS = listOf("8.8.8.8", "1.1.1.1")
    const val MAX_SERVERS = 4

    private const val PREFS = "client_settings"
    private const val KEY_DNS = "dns_servers"

    fun load(context: Context): List<String> =
        parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DNS, null))
            ?: DEFAULT_SERVERS

    fun save(context: Context, servers: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DNS, format(servers))
            .commit()
    }

    /**
     * Parses a comma/space separated list of IPv4 addresses. Returns null when the
     * text is blank, contains anything that is not an IPv4 address, or lists more
     * than [MAX_SERVERS] servers.
     */
    fun parse(text: String?): List<String>? {
        val parts = text?.split(',', ' ', ';', '\n')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return null
        if (parts.isEmpty() || parts.size > MAX_SERVERS) return null
        if (!parts.all(::isIpv4Address)) return null
        return parts.distinct()
    }

    fun format(servers: List<String>): String = servers.joinToString(", ")

    fun isIpv4Address(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.isNotEmpty() && octet.length <= 3 && octet.all(Char::isDigit) && octet.toInt() in 0..255
        }
    }
}
