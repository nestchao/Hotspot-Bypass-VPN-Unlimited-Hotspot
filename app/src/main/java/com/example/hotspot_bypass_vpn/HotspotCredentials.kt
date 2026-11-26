package com.example.hotspot_bypass_vpn

import android.content.Context
import android.content.Intent
import java.security.SecureRandom

/**
 * Wi-Fi Direct network name and password for Share (host) mode.
 *
 * Older versions used the same hard-coded password on every phone, so anyone
 * nearby who knew it could join and use the host's data. New installs now get a
 * random password. Installs that already used Share mode keep the old default,
 * so devices that saved the network still reconnect after the update.
 *
 * The credentials live in their own preferences file that only the UI process
 * writes. HostService runs in the separate :proxy process, and SharedPreferences
 * caches per process and rewrites the whole file on save, so sharing a file with
 * HostService's own flags could silently overwrite the password. HostService
 * receives the credentials in its start intent and only reads this file as a
 * fallback when the system restarts it without one.
 */
object HotspotCredentials {
    data class Credentials(val ssid: String, val password: String)

    const val EXTRA_SSID = "WIFI_SSID"
    const val EXTRA_PASSWORD = "WIFI_PASSWORD"

    const val DEFAULT_SSID = "DIRECT-HotspotBypass"
    const val LEGACY_DEFAULT_PASSWORD = "87654321"

    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 63

    private const val CREDENTIALS_PREFS = "hotspot_credentials"
    private const val KEY_SSID = "ssid"
    private const val KEY_PASSWORD = "password"

    /** Shared app preferences used by earlier versions for everything. */
    private const val LEGACY_PREFS = "bypass_vpn_prefs"
    private const val LEGACY_KEY_SSID = "wifi_ssid"
    private const val LEGACY_KEY_PASSWORD = "wifi_password"

    /** Keys that only exist once Share mode or its settings have been used. */
    private val LEGACY_HOST_KEYS = listOf(
        "host_is_running_flag", "host_was_running", "last_wifi_band", "selected_band",
        LEGACY_KEY_SSID, LEGACY_KEY_PASSWORD
    )

    private const val GENERATED_PASSWORD_LENGTH = 10
    // No 0/o or 1/l/i, so the password is easy to read off one screen and type on another.
    private const val PASSWORD_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    private val random = SecureRandom()

    /** Returns the saved credentials, creating and saving them on first use. */
    @Synchronized
    fun load(context: Context): Credentials {
        val prefs = context.getSharedPreferences(CREDENTIALS_PREFS, Context.MODE_PRIVATE)
        val storedSsid = prefs.getString(KEY_SSID, null)
        val storedPassword = prefs.getString(KEY_PASSWORD, null)
        if (storedSsid != null && storedPassword != null && isValidPassword(storedPassword)) {
            return Credentials(storedSsid, storedPassword)
        }

        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val credentials = Credentials(
            ssid = resolveSsid(storedSsid ?: legacy.getString(LEGACY_KEY_SSID, null)),
            password = resolvePassword(
                stored = storedPassword ?: legacy.getString(LEGACY_KEY_PASSWORD, null),
                usedHostBefore = LEGACY_HOST_KEYS.any { legacy.contains(it) },
                generate = { generatePassword() }
            )
        )
        save(context, credentials)
        return credentials
    }

    @Synchronized
    fun save(context: Context, credentials: Credentials) {
        context.getSharedPreferences(CREDENTIALS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SSID, credentials.ssid)
            .putString(KEY_PASSWORD, credentials.password)
            .commit()
    }

    fun putExtras(intent: Intent, credentials: Credentials): Intent = intent
        .putExtra(EXTRA_SSID, credentials.ssid)
        .putExtra(EXTRA_PASSWORD, credentials.password)

    /** Credentials from a HostService start intent, or null when it carried none. */
    fun fromExtras(intent: Intent?): Credentials? {
        val ssid = intent?.getStringExtra(EXTRA_SSID) ?: return null
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return null
        return Credentials(resolveSsid(ssid), password)
    }

    internal fun resolveSsid(stored: String?): String = stored?.takeIf { it.isNotBlank() } ?: DEFAULT_SSID

    internal fun resolvePassword(stored: String?, usedHostBefore: Boolean, generate: () -> String): String = when {
        stored != null && isValidPassword(stored) -> stored
        // Earlier versions fell back to the shared default when nothing was saved.
        stored == null && usedHostBefore -> LEGACY_DEFAULT_PASSWORD
        else -> generate()
    }

    /** WPA2 passphrases are 8 to 63 printable ASCII characters. */
    fun isValidPassword(password: String): Boolean =
        password.length in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH && password.all { it.code in 0x20..0x7E }

    internal fun generatePassword(source: SecureRandom = random): String =
        buildString(GENERATED_PASSWORD_LENGTH) {
            repeat(GENERATED_PASSWORD_LENGTH) { append(PASSWORD_ALPHABET[source.nextInt(PASSWORD_ALPHABET.length)]) }
        }
}
