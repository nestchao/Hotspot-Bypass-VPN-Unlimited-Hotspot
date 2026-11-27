package com.example.hotspot_bypass_vpn

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import kotlin.concurrent.thread
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity(), WifiP2pManager.ConnectionInfoListener, MainActions {

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private val intentFilter = IntentFilter()

    private lateinit var vm: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[MainViewModel::class.java]
        vm.savedCredentials.value = HotspotCredentials.load(this)
        vm.dnsServers.value = ClientDnsSettings.load(this)
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        intentFilter.apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        // Load saved band preference
        vm.selectedBand.intValue = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE).getInt("selected_band", 1)

        checkPermissions()

        setContent {
            BackHandler(enabled = vm.showSettings.value) { vm.showSettings.value = false }
            BypassVPNTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(vm, this@MainActivity)
                }
            }
        }
    }

    override fun reconnectVpn() {
        vm.log("Reconnecting VPN…")
        val stopIntent = Intent(this, MyVpnServiceTun2Socks::class.java).apply {
            action = MyVpnServiceTun2Socks.ACTION_STOP
        }
        startService(stopIntent)
        thread {
            Thread.sleep(2000)
            runOnUiThread {
                startVpnService(vm.clientIp.value, vm.clientPort.value.toIntOrNull() ?: 8080)
                vm.log("VPN reconnected")
            }
        }
    }

    override fun selectBand(band: Int) {
        vm.selectedBand.intValue = band
        getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE).edit().putInt("selected_band", band).apply()
    }

    override fun saveSettings(credentials: HotspotCredentials.Credentials, dnsServers: List<String>) {
        HotspotCredentials.save(this, credentials)
        ClientDnsSettings.save(this, dnsServers)
        vm.savedCredentials.value = credentials
        vm.dnsServers.value = dnsServers
        vm.log("Settings saved")
        if (vm.isHostRunning.value) {
            stopHost()
            Handler(Looper.getMainLooper()).postDelayed({
                startHost()
            }, 2000)
        }
        vm.showSettings.value = false
    }

    override fun startHost() {
        if (checkHardwareStatus()) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }
            val intent = Intent(this, HostService::class.java).apply { putExtra("WIFI_BAND", vm.selectedBand.intValue) }
            HotspotCredentials.putExtras(intent, HotspotCredentials.load(this))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            vm.isHostRunning.value = true
            vm.log("Starting to share…")
            saveServiceState(hostRunning = true)
        }
    }

    override fun stopHost() {
        val intent = Intent(this, HostService::class.java).apply { action = "STOP" }
        startService(intent)
        vm.hostInfoState.value = null
        vm.isHostRunning.value = false
        vm.log("Sharing stopped")
        saveServiceState(hostRunning = false)
    }

    override fun connectClient() {
        val address = vm.clientIp.value.trim()
        val port = vm.clientPort.value.toIntOrNull()
        val validAddress = address.split('.').let { octets ->
            octets.size == 4 && octets.all { it.isNotEmpty() && it.length <= 3 && (it.toIntOrNull()?.let { number -> number in 0..255 } == true) }
        }
        if (!validAddress || port == null || port !in 1..65535) {
            Toast.makeText(this, "Enter a valid host IP and port (1-65535).", Toast.LENGTH_LONG).show()
            return
        }
        if (checkHardwareStatus()) {
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
                return
            }
            if (getPrivateDnsMode() == "hostname") {
                Toast.makeText(this, "Disable Private DNS first!", Toast.LENGTH_LONG).show()
                navigateToPrivateDnsSettings()
                return
            }
            prepareVpn(address, port)
        }
    }

    override fun stopClient() {
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java).apply { action = MyVpnServiceTun2Socks.ACTION_STOP }
        startService(intent)
        vm.isClientRunning.value = false
        vm.log("VPN stopped")

        // FIX: Use vpnRunning = false
        saveServiceState(vpnRunning = false)
    }

    private fun startVpnService(ip: String, port: Int) {
        val intent = Intent(this, MyVpnServiceTun2Socks::class.java).apply {
            putExtra("PROXY_IP", ip)
            putExtra("PROXY_PORT", port)
            putExtra(ClientDnsSettings.EXTRA_DNS_SERVERS, ClientDnsSettings.format(ClientDnsSettings.load(this@MainActivity)))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        vm.isClientRunning.value = true
        vm.log("Starting VPN to ${ip}:${port}…")

        // FIX: Use vpnRunning instead of hostRunning
        saveServiceState(vpnRunning = true)
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo?) {
        if (info != null && info.groupFormed && info.isGroupOwner) {
            vm.isHostRunning.value = true
        }
    }

    fun updateGroupInfo(group: WifiP2pGroup?) {
        if (group != null && group.isGroupOwner) {
            vm.hostInfoState.value = HostInfo(
                ssid = group.networkName ?: "Unknown",
                pass = group.passphrase ?: "N/A",
                ip = HOST_PROXY_IP,
                port = HOST_PROXY_PORT
            )
            vm.isHostRunning.value = true
        } else if (!isServiceRunning(HostService::class.java)) {
            vm.hostInfoState.value = null
            vm.isHostRunning.value = false
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.ACCESS_WIFI_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
    }

    private fun checkHardwareStatus(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        if (!wifiManager.isWifiEnabled) {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            return false
        }
        return true
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun prepareVpn(ip: String, port: Int) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 102)
        } else {
            startVpnService(ip, port)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102 && resultCode == RESULT_OK) {
            startVpnService(vm.clientIp.value, vm.clientPort.value.toIntOrNull() ?: 8080)
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.getStringExtra("message")?.let { vm.log(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)
        registerReceiver(receiver, intentFilter)
        ContextCompat.registerReceiver(this, logReceiver, IntentFilter("VPN_LOG"), ContextCompat.RECEIVER_NOT_EXPORTED)

        // SYNC STATES ON RESUME
        syncServiceStates()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        try { unregisterReceiver(logReceiver) } catch (e: Exception) {}
    }

    private fun getPrivateDnsMode(): String {
        return try {
            Settings.Global.getString(contentResolver, "private_dns_mode") ?: "off"
        } catch (e: Exception) { "unknown" }
    }

    private fun navigateToPrivateDnsSettings() {
        try {
            startActivity(Intent("android.settings.DNS_SETTINGS"))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun syncServiceStates() {
        vm.isClientRunning.value = isServiceRunning(MyVpnServiceTun2Socks::class.java)
        vm.isHostRunning.value = isServiceRunning(HostService::class.java)

        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            PackageManager.PERMISSION_GRANTED
        }

        if (fineLocation == PackageManager.PERMISSION_GRANTED && nearbyDevices == PackageManager.PERMISSION_GRANTED) {
            try {
                manager.requestGroupInfo(channel) { group ->
                    if (group != null) {
                        updateGroupInfo(group)
                    } else {
                        if (!isServiceRunning(HostService::class.java)) {
                            vm.isHostRunning.value = false
                        }
                    }
                }
                manager.requestConnectionInfo(channel, this)
            } catch (e: SecurityException) {
                vm.log("Could not read Wi-Fi Direct state: permission denied")
            }
        }
    }

    private fun saveServiceState(hostRunning: Boolean? = null, vpnRunning: Boolean? = null) {
        val prefs = getSharedPreferences("bypass_vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            hostRunning?.let { putBoolean("host_was_running", it) }
            vpnRunning?.let { putBoolean("vpn_was_running", it) }
            apply()
        }
    }
}
