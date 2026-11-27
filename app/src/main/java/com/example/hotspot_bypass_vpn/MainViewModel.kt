package com.example.hotspot_bypass_vpn

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HostInfo(
    val ssid: String,
    val pass: String,
    val ip: String,
    val port: String
)

data class LogEntry(val time: String, val message: String)

/**
 * UI state for [MainActivity]. Kept in a ViewModel so it survives configuration
 * changes such as rotation; running states are re-synced from the services in
 * MainActivity.onResume. Screens only read this state and call [MainActions],
 * so they can be rendered in screenshot tests without Android services.
 */
class MainViewModel : ViewModel() {
    val hostInfoState = mutableStateOf<HostInfo?>(null)
    val logState = mutableStateListOf<LogEntry>()
    val clientIp = mutableStateOf("192.168.49.1")
    val clientPort = mutableStateOf("8080")
    val selectedBand = mutableIntStateOf(1)
    val selectedTab = mutableIntStateOf(0)
    val showSettings = mutableStateOf(false)

    val isHostRunning = mutableStateOf(false)
    val isClientRunning = mutableStateOf(false)

    /** Saved Wi-Fi Direct name/password, shown before sharing starts and in Settings. */
    val savedCredentials = mutableStateOf(
        HotspotCredentials.Credentials(HotspotCredentials.DEFAULT_SSID, HotspotCredentials.LEGACY_DEFAULT_PASSWORD)
    )
    val dnsServers = mutableStateOf(ClientDnsSettings.DEFAULT_SERVERS)

    fun log(message: String) {
        logState.add(LogEntry(timeFormat.format(Date()), message))
        while (logState.size > MAX_LOG_ENTRIES) logState.removeAt(0)
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 200
        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    }
}

/** User actions the screens can trigger; implemented by [MainActivity]. */
interface MainActions {
    fun startHost()
    fun stopHost()
    fun connectClient()
    fun stopClient()
    fun reconnectVpn()
    fun selectBand(band: Int)
    fun saveSettings(credentials: HotspotCredentials.Credentials, dnsServers: List<String>)
}
