package com.example.hotspot_bypass_vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

const val HOST_PROXY_IP = "192.168.49.1"
const val HOST_PROXY_PORT = "8080"

@Composable
fun MainScreen(vm: MainViewModel, actions: MainActions) {
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }
    val clipboard = LocalClipboardManager.current
    val copy: (String, String) -> Unit = { label, value ->
        clipboard.setText(AnnotatedString(value))
        toast = "$label copied"
    }

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        if (vm.showSettings.value) {
            SettingsScreen(vm, actions, onBack = { vm.showSettings.value = false })
        } else {
            HomeScreen(vm, actions, copy)
        }
        toast?.let {
            CopyToast(
                it,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 22.dp)
            )
        }
    }
}

@Composable
private fun HomeScreen(vm: MainViewModel, actions: MainActions, copy: (String, String) -> Unit) {
    var logExpanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Fixed header and tabs; only the content below scrolls.
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bypass VPN", style = AppText.Brand)
                Spacer(Modifier.weight(1f))
                when {
                    vm.isHostRunning.value -> LiveLabel("Sharing")
                    vm.isClientRunning.value -> LiveLabel("Connected")
                }
                IconButton(onClick = { vm.showSettings.value = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = AppColors.TabInactive)
                }
            }
            Gap(18.dp)
            ModeTabs(listOf("Share", "Connect"), vm.selectedTab.intValue) { vm.selectedTab.intValue = it }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Gap(28.dp)
            if (vm.selectedTab.intValue == 0) ShareView(vm, actions, copy) else ConnectView(vm, actions)
            Gap(20.dp)
            ActivityLog(vm.logState, logExpanded, onToggle = { logExpanded = !logExpanded })
            Gap(32.dp)
        }
    }
}

@Composable
fun ShareView(vm: MainViewModel, actions: MainActions, copy: (String, String) -> Unit) {
    val sharing = vm.isHostRunning.value
    val clientActive = vm.isClientRunning.value
    val band = if (vm.selectedBand.intValue == 2) "5" else "2.4"
    var revealed by rememberSaveable { mutableStateOf(true) }

    Text(if (sharing) "Your connection is live" else "Share your connection", style = AppText.Headline)
    Gap(13.dp)
    Text(
        if (sharing) "Sharing on $band GHz. Use the details below on each device."
        else "Create a private Wi-Fi network that your other devices use to reach this phone's data.",
        style = AppText.Lede
    )
    Gap(24.dp)

    if (clientActive) {
        Notice("This phone is connected to another host. Disconnect on the Connect tab before sharing.")
        Gap(16.dp)
    }

    if (!sharing) {
        Text("Wi-Fi band", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextSubtle)
        Gap(13.dp)
        SegmentedControl(
            options = listOf("2.4 GHz", "5 GHz"),
            selected = vm.selectedBand.intValue - 1,
            onSelect = { actions.selectBand(it + 1) },
            enabled = !clientActive
        )
        Gap(10.dp)
        Text("5 GHz is faster. 2.4 GHz reaches further and suits older devices.", style = AppText.Body, fontSize = 12.sp)
        Gap(28.dp)
        PrimaryButton("Start sharing", onClick = actions::startHost, enabled = !clientActive)
    } else {
        SecondaryButton("Stop sharing", onClick = actions::stopHost)
    }

    Gap(32.dp)
    val info = vm.hostInfoState.value
    val creds = vm.savedCredentials.value
    val details = listOf(
        "Network" to (info?.ssid ?: creds.ssid),
        "Password" to (info?.pass ?: creds.password),
        "Proxy IP" to (info?.ip ?: HOST_PROXY_IP),
        "Port" to (info?.port ?: HOST_PROXY_PORT),
    )
    SectionHeading("Connection details") {
        TextAction("Copy all", onClick = {
            copy("All details", details.joinToString("\n") { (label, value) -> "$label: $value" })
        })
    }
    if (!sharing) {
        Text("Devices can join once sharing starts.", style = AppText.Body)
        Gap(12.dp)
    }
    Column(Modifier.fillMaxWidth().hairlines(top = true, bottom = false).alpha(if (sharing) 1f else 0.62f)) {
        details.forEach { (label, value) ->
            val secret = label == "Password"
            DetailRow(
                label = label,
                value = value,
                onCopy = { copy(label, value) },
                secret = secret,
                revealed = revealed,
                onToggleReveal = { revealed = !revealed }
            )
        }
    }
}

@Composable
fun ConnectView(vm: MainViewModel, actions: MainActions) {
    val connected = vm.isClientRunning.value
    val hostActive = vm.isHostRunning.value

    Eyebrow("Client mode")
    Text(if (connected) "You’re connected" else "Connect through a host", style = AppText.Headline)
    Gap(13.dp)
    Text(
        if (connected) "This phone’s traffic now goes through the host’s mobile data."
        else "Enter the proxy details shown on the host phone.",
        style = AppText.Lede
    )
    Gap(24.dp)

    if (hostActive) {
        Notice("This phone is sharing its connection. Stop sharing on the Share tab before connecting.")
        Gap(16.dp)
    }

    if (connected) {
        Row(
            Modifier.fillMaxWidth().hairlines(top = true).padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text("HOST", style = AppText.RowLabel)
                Gap(4.dp)
                Text("${vm.clientIp.value} : ${vm.clientPort.value}", style = AppText.RowValue, fontSize = 18.sp)
            }
        }
        Gap(28.dp)
        SecondaryButton("Disconnect", onClick = actions::stopClient)
        Gap(4.dp)
        TextAction("Reconnect", onClick = actions::reconnectVpn)
    } else {
        LabeledField(
            label = "Host address",
            value = vm.clientIp.value,
            onValueChange = { vm.clientIp.value = it.trim() },
            keyboardType = KeyboardType.Decimal,
            enabled = !hostActive,
            placeholder = HOST_PROXY_IP
        )
        Gap(22.dp)
        LabeledField(
            label = "Port",
            value = vm.clientPort.value,
            onValueChange = { vm.clientPort.value = it.filter(Char::isDigit).take(5) },
            keyboardType = KeyboardType.Number,
            enabled = !hostActive,
            placeholder = HOST_PROXY_PORT
        )
        Gap(30.dp)
        PrimaryButton("Connect", onClick = actions::connectClient, enabled = !hostActive)
    }

    Gap(48.dp)
    Column(Modifier.fillMaxWidth().hairlines(top = true, bottom = false).padding(top = 19.dp)) {
        Eyebrow("Before you connect")
        Text(
            "1. Join the host phone’s Wi-Fi network in Android settings.\n" +
                "2. Enter the proxy address and port shown on the host.\n" +
                "3. Tap Connect and approve Android’s VPN request.",
            style = AppText.Body
        )
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, actions: MainActions, onBack: () -> Unit) {
    val saved = vm.savedCredentials.value
    var ssid by rememberSaveable { mutableStateOf(saved.ssid) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var dnsText by rememberSaveable { mutableStateOf(ClientDnsSettings.format(vm.dnsServers.value)) }
    val passwordValid = HotspotCredentials.isValidPassword(password)
    val dnsServers = ClientDnsSettings.parse(dnsText)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppColors.TextPrimary)
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = AppText.Brand, color = AppColors.TextPrimary)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Gap(16.dp)
            Eyebrow("Share mode")
            Text("Hotspot network", style = AppText.Section)
            Gap(6.dp)
            Text(
                "The Wi-Fi network other devices join. Saving restarts sharing if it is running.",
                style = AppText.Body
            )
            Gap(20.dp)
            LabeledField(label = "Network name", value = ssid, onValueChange = { ssid = it })
            Gap(20.dp)
            LabeledField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                secret = true,
                revealed = revealed,
                onToggleReveal = { revealed = !revealed },
                isError = !passwordValid,
                supportingText = if (passwordValid) null else
                    "${HotspotCredentials.MIN_PASSWORD_LENGTH}–${HotspotCredentials.MAX_PASSWORD_LENGTH} characters: letters, digits or symbols"
            )
            TextAction("Generate a new password", onClick = {
                password = HotspotCredentials.generatePassword()
                revealed = true
            })

            Gap(36.dp)
            Eyebrow("Connect mode")
            Text("DNS servers", style = AppText.Section)
            Gap(6.dp)
            Text("Used for name lookups while the VPN is on. Takes effect the next time you connect.", style = AppText.Body)
            Gap(20.dp)
            LabeledField(
                label = "Servers",
                value = dnsText,
                onValueChange = { dnsText = it },
                keyboardType = KeyboardType.Uri,
                isError = dnsServers == null,
                supportingText = if (dnsServers == null)
                    "1–${ClientDnsSettings.MAX_SERVERS} IPv4 addresses, separated by commas" else null
            )

            Gap(32.dp)
            PrimaryButton(
                "Save",
                enabled = passwordValid && dnsServers != null,
                onClick = {
                    if (dnsServers != null) {
                        actions.saveSettings(
                            HotspotCredentials.Credentials(HotspotCredentials.resolveSsid(ssid.trim()), password),
                            dnsServers
                        )
                    }
                }
            )
            Gap(32.dp)
        }
    }
}
