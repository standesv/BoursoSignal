package bzh.dargio.boursosignal.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import bzh.dargio.boursosignal.domain.signals.FeeProfile
import bzh.dargio.boursosignal.domain.signals.Fees

/**
 * Réglages de l'application, stockés de façon chiffrée via
 * EncryptedSharedPreferences + Android Keystore (§11).
 *
 * La source de cours est Yahoo Finance (gratuit, couvre Euronext Paris) ; il n'y
 * a plus de mode démo ni de clé API à gérer. Inclut la configuration des profils
 * de frais de courtage simulés (§F8).
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

    // --- Frais de courtage simulés (mode test) -------------------------------

    /** Clé du profil de frais actif (défaut : Découverte). */
    var paperFeeProfileKey: String
        get() = prefs.getString(KEY_FEE_PROFILE, Fees.DECOUVERTE.key) ?: Fees.DECOUVERTE.key
        set(value) = prefs.edit().putString(KEY_FEE_PROFILE, value).apply()

    /** Profil de frais pour une clé donnée, avec les éventuelles valeurs personnalisées. */
    fun feeProfile(key: String): FeeProfile {
        val def = Fees.byKey(key)
        val pct = prefs.getFloat("fee_${key}_pct", def.percent.toFloat()).toDouble()
        val min = prefs.getFloat("fee_${key}_min", def.minFee.toFloat()).toDouble()
        return def.copy(percent = pct, minFee = min)
    }

    /** Les trois profils (Découverte/Classic/Trader) avec leurs valeurs effectives. */
    fun allFeeProfiles(): List<FeeProfile> = Fees.DEFAULTS.map { feeProfile(it.key) }

    /** Profil de frais actuellement sélectionné. */
    fun activeFeeProfile(): FeeProfile = feeProfile(paperFeeProfileKey)

    /** Enregistre les valeurs personnalisées (pourcentage + frais minimum) d'un profil. */
    fun saveFeeProfile(profile: FeeProfile) {
        prefs.edit()
            .putFloat("fee_${profile.key}_pct", profile.percent.toFloat())
            .putFloat("fee_${profile.key}_min", profile.minFee.toFloat())
            .apply()
    }

    private companion object {
        const val KEY_INTERVAL = "refresh_interval_hours"
        const val KEY_NOTIF = "notifications_enabled"
        const val KEY_FEE_PROFILE = "paper_fee_profile_key"
    }
}
