package bzh.dargio.boursosignal.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Réglages de l'application, stockés de façon chiffrée via
 * EncryptedSharedPreferences + Android Keystore (§11).
 *
 * La source de cours est Yahoo Finance (gratuit, couvre Euronext Paris) ; il n'y
 * a plus de mode démo ni de clé API à gérer.
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

    /** Fréquence de rafraîchissement en arrière-plan, en heures. */
    var refreshIntervalHours: Int
        get() = prefs.getInt(KEY_INTERVAL, 12)
        set(value) = prefs.edit().putInt(KEY_INTERVAL, value).apply()

    /** Notifications activées. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIF, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIF, value).apply()

    private companion object {
        const val KEY_INTERVAL = "refresh_interval_hours"
        const val KEY_NOTIF = "notifications_enabled"
    }
}
