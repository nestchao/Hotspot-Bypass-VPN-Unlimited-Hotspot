package com.example.hotspot_bypass_vpn

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, actions: MainActions) {
    Scaffold(
        topBar = {
            if (vm.showSettings.value) {
                CenterAlignedTopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                    navigationIcon = {
                        IconButton(onClick = { vm.showSettings.value = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo),
                                contentDescription = "App Logo",
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("BYPASS VPN", fontWeight = FontWeight.Black, fontSize = 18.sp)
                                Text(
                                    when {
                                        vm.isHostRunning.value -> "Host service running"
                                        vm.isClientRunning.value -> "VPN service running"
                                        else -> "Ready to connect"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.showSettings.value = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (vm.showSettings.value) {
                SettingsScreen(vm, actions)
            } else {
            TabRow(
                selectedTabIndex = vm.selectedTab.intValue,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},                  // Removes the thin gray line for a cleaner look
                indicator = { tabPositions ->
                    if (vm.selectedTab.intValue < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[vm.selectedTab.intValue]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                // Tab 1: Share (Host)
                Tab(
                    selected = vm.selectedTab.intValue == 0,
                    onClick = { vm.selectedTab.intValue = 0 },
                    text = {
                        Text(
                            "Share (Host)",
                            // Use Theme primary color instead of hardcoded CyberTeal
                            color = if (vm.selectedTab.intValue == 0) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = if (vm.selectedTab.intValue == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = if (vm.selectedTab.intValue == 0) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                )

                // Tab 2: Connect (Client)
                Tab(
                    selected = vm.selectedTab.intValue == 1,
                    onClick = { vm.selectedTab.intValue = 1 },
                    text = {
                        Text(
                            "Connect (Client)",
                            color = if (vm.selectedTab.intValue == 1) MaterialTheme.colorScheme.primary else Color.Gray,
                            fontWeight = if (vm.selectedTab.intValue == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.VpnLock,
                            contentDescription = null,
                            tint = if (vm.selectedTab.intValue == 1) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (vm.selectedTab.intValue == 0) {
                    HostModeView(vm, actions)
                } else {
                    ClientModeView(vm, actions)
                }

                Spacer(modifier = Modifier.height(24.dp))

                DebugLogSection(vm.logState)
            }
            }
        }
    }
}


@Composable
fun SettingsScreen(vm: MainViewModel, actions: MainActions) {
    val context = LocalContext.current
    val saved = remember { HotspotCredentials.load(context) }
    var ssid by remember { mutableStateOf(saved.ssid) }
    var password by remember { mutableStateOf(saved.password) }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordValid = HotspotCredentials.isValidPassword(password)
    var dnsText by remember { mutableStateOf(ClientDnsSettings.format(ClientDnsSettings.load(context))) }
    val dnsServers = ClientDnsSettings.parse(dnsText)

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wi-Fi Settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("SSID") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Wifi, null) },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = !passwordValid,
                supportingText = {
                    if (!passwordValid) {
                        Text(
                            "${HotspotCredentials.MIN_PASSWORD_LENGTH}-${HotspotCredentials.MAX_PASSWORD_LENGTH} " +
                                "characters (letters, digits, symbols)"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text("Connect (Client) DNS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = dnsText,
                onValueChange = { dnsText = it },
                label = { Text("DNS servers") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Dns, null) },
                isError = dnsServers == null,
                supportingText = {
                    Text(
                        if (dnsServers == null) {
                            "1-${ClientDnsSettings.MAX_SERVERS} IPv4 addresses, separated by commas"
                        } else {
                            "Used the next time you start the VPN"
                        }
                    )
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (dnsServers != null) {
                        actions.saveSettings(
                            HotspotCredentials.Credentials(HotspotCredentials.resolveSsid(ssid.trim()), password),
                            dnsServers
                        )
                    }
                },
                enabled = passwordValid && dnsServers != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.background
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("SAVE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
        }
    }
}


@Composable
fun HostModeView(vm: MainViewModel, actions: MainActions) {
    Text("Share your connection", modifier = Modifier.fillMaxWidth(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("Use this phone as the host for other devices.", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(20.dp))
    StatusCard(
        title = "Hotspot Sharing",
        isActive = vm.isHostRunning.value,
        activeColor = Color(0xFF4CAF50),
        icon = Icons.Default.CellTower
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupSteps(listOf(
        "Turn on mobile data and Wi-Fi on this phone.",
        "Start sharing, then copy the network and proxy details below.",
        "On each client, join this Wi-Fi network and start its VPN."
    ))
    Spacer(modifier = Modifier.height(16.dp))

    if (vm.isClientRunning.value) {
        ConflictCard(message = "VPN Client is currently active. You cannot share while connected to another proxy.")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // FIXED: Uses the theme surface color instead of hardcoded CardPurple
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Wi-Fi settings", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wi-Fi Band:", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                FilterChip(
                    selected = vm.selectedBand.intValue == 1,
                    onClick = { actions.selectBand(1) },
                    label = { Text("2.4 GHz") },
                    enabled = !vm.isHostRunning.value && !vm.isClientRunning.value,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.background, // Adapts to Light or Dark
                        labelColor = MaterialTheme.colorScheme.onSurface      // Adapts to Black or White text
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = vm.selectedBand.intValue == 2,
                    onClick = { actions.selectBand(2) },
                    label = { Text("5 GHz") },
                    enabled = !vm.isHostRunning.value && !vm.isClientRunning.value,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.background, // Adapts to Light or Dark
                        labelColor = MaterialTheme.colorScheme.onSurface      // Adapts to Black or White text
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!vm.isHostRunning.value) {
                Button(
                    onClick = { actions.startHost() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,    // Dynamic
                        contentColor = MaterialTheme.colorScheme.background    // Dynamic
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !vm.isClientRunning.value
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("START SERVICE", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            } else {
                Button(
                    onClick = { actions.stopHost() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB00020), // Standard Red
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("STOP SERVICE", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    // Host Info Card (Update for contrast)
    AnimatedVisibility(visible = vm.hostInfoState.value != null) {
        vm.hostInfoState.value?.let { info ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkTheme()) Color(0xFF1B2E1C) else Color(0xFFE8F5E9)
                ),
                border = BorderStroke(1.dp, Color(0xFF4CAF50))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Details for every client", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF4CAF50).copy(alpha = 0.3f))
                    InfoRow(label = "SSID", value = info.ssid)
                    InfoRow(label = "Password", value = info.pass)
                    InfoRow(label = "Proxy IP", value = info.ip)
                    InfoRow(label = "Proxy Port", value = info.port)
                }
            }
        }
    }
}


@Composable
fun ClientModeView(vm: MainViewModel, actions: MainActions) {
    Text("Connect to a host", modifier = Modifier.fillMaxWidth(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("Route this device through the host phone.", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(20.dp))
    StatusCard(
        title = "VPN Tunnel",
        isActive = vm.isClientRunning.value,
        activeColor = Color(0xFF2196F3),
        icon = Icons.Default.Security
    )

    Spacer(modifier = Modifier.height(16.dp))

    SetupSteps(listOf(
        "Join the host phone's Wi-Fi network in Android settings.",
        "Enter the proxy IP and port shown on the host.",
        "Start the VPN and approve Android's connection prompt."
    ))
    Spacer(modifier = Modifier.height(16.dp))

    if (vm.isHostRunning.value) {
        ConflictCard(message = "Hotspot Sharing is currently active. You cannot start a VPN while sharing your own connection.")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // FIXED: Uses the theme surface color
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Host address", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = vm.clientIp.value,
                onValueChange = { vm.clientIp.value = it },
                label = { Text("Host IP Address") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lan, null) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !vm.isClientRunning.value && !vm.isHostRunning.value
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = vm.clientPort.value,
                onValueChange = { vm.clientPort.value = it },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = !vm.isClientRunning.value && !vm.isHostRunning.value
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!vm.isClientRunning.value) {
                Button(
                    onClick = { actions.connectClient() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !vm.isHostRunning.value
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("START VPN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            } else {
                Button(
                    onClick = { actions.stopClient() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB00020),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("STOP VPN", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }

    if (vm.isClientRunning.value) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { actions.reconnectVpn() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("RECONNECT VPN", fontWeight = FontWeight.Bold)
        }
    }
}
