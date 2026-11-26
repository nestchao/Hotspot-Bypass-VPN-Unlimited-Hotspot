package com.example.hotspot_bypass_vpn

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

data class HostInfo(
    val ssid: String,
    val pass: String,
    val ip: String,
    val port: String
)

/**
 * UI state for [MainActivity]. Kept in a ViewModel so it survives configuration
 * changes such as rotation; running states are re-synced from the services in
 * MainActivity.onResume.
 */
class MainViewModel : ViewModel() {
    val hostInfoState = mutableStateOf<HostInfo?>(null)
    val logState = mutableStateListOf<String>()
    val clientIp = mutableStateOf("192.168.49.1")
    val clientPort = mutableStateOf("8080")
    val selectedBand = mutableIntStateOf(1)
    val selectedTab = mutableIntStateOf(0)
    val showSettings = mutableStateOf(false)

    val isHostRunning = mutableStateOf(false)
    val isClientRunning = mutableStateOf(false)
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
