package com.example.hotspot_bypass_vpn

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every main screen state with Robolectric. Run with
 * `./gradlew testDebugUnitTest --tests '*ScreenshotTest' -PrecordScreenshots`
 * to refresh app/src/test/screenshots (the UI Screenshots workflow does this in CI).
 * Heights are taller than a phone so the whole scrollable page is visible.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    private val noActions = object : MainActions {
        override fun startHost() {}
        override fun stopHost() {}
        override fun connectClient() {}
        override fun stopClient() {}
        override fun reconnectVpn() {}
        override fun selectBand(band: Int) {}
        override fun saveSettings(credentials: HotspotCredentials.Credentials, dnsServers: List<String>) {}
    }

    private fun vm(configure: MainViewModel.() -> Unit = {}) = MainViewModel().apply {
        savedCredentials.value = HotspotCredentials.Credentials("DIRECT-HotspotBypass", "k7mp2q9xra")
        log("Starting to share…")
        log("Wi-Fi Direct group created on 2.4 GHz")
        configure()
    }

    private fun capture(name: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { BypassVPNTheme { content() } }

    @Test
    @Config(qualifiers = "w412dp-h1180dp-xhdpi")
    fun shareIdle() = capture("1-share-idle") { MainScreen(vm(), noActions) }

    @Test
    @Config(qualifiers = "w412dp-h1100dp-xhdpi")
    fun shareActive() = capture("2-share-active") {
        MainScreen(vm {
            isHostRunning.value = true
            hostInfoState.value = HostInfo("DIRECT-HotspotBypass", "k7mp2q9xra", HOST_PROXY_IP, HOST_PROXY_PORT)
        }, noActions)
    }

    @Test
    @Config(qualifiers = "w412dp-h1000dp-xhdpi")
    fun shareBlockedByClient() = capture("3-share-blocked") {
        MainScreen(vm { isClientRunning.value = true }, noActions)
    }

    @Test
    @Config(qualifiers = "w412dp-h980dp-xhdpi")
    fun connectIdle() = capture("4-connect-idle") { MainScreen(vm { selectedTab.intValue = 1 }, noActions) }

    @Test
    @Config(qualifiers = "w412dp-h980dp-xhdpi")
    fun connectActive() = capture("5-connect-active") {
        MainScreen(vm { selectedTab.intValue = 1; isClientRunning.value = true }, noActions)
    }

    @Test
    @Config(qualifiers = "w412dp-h1000dp-xhdpi")
    fun settings() = capture("6-settings") { MainScreen(vm { showSettings.value = true }, noActions) }
}
