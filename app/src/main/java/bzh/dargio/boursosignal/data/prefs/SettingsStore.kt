package bzh.dargio.boursosignal.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Réglages et secrets de l'application, stockés de façon chiffrée via
 * EncryptedSharedPreferences + Android Keystore (§11). La clé API de la source de
 * cours ne quitte jamais l'appareil.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "boursosignal_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var apiKey: String?
        get() = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_API, value).apply()

    /** Mode démo (source de cours synthétique, sans clé ni réseau). */
    var useDemoProvider: Boolean
        get() = prefs.getBoolean(KEY_DEMO, apiKey == null)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    /** Fréquence de rafraîchissement en arrière-plan, en heures (défaut : fin de séance). */
    var refreshIntervalHours: Int
        get() = prefs.getInt(KEY_INTERVAL, 12)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value).apply()

    /** Notifications activées. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF, value).apply()

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_DEMO = "use_demo"
        const val KEY_INTERVAL = "refresh_interval_hours"
        const val KEY_NOTIF = "notifications_enabled"
    }
}
