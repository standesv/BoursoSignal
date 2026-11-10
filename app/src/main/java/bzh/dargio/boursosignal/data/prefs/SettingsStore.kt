package bzh.dargio.boursosignal.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import bzh.dargio.boursosignal.domain.signals.FeeProfile
import bzh.dargio.boursosignal.domain.signals.Fees
import bzh.dargio.boursosignal.domain.signals.NotifyWindow
import java.time.LocalDateTime

/**
 * Réglages de l'application, stockés de façon chiffrée via
 * EncryptedSharedPreferences + Android Keystore (§11).
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

    // --- Planification des notifications (jours + plage horaire) ---

    /** Jours autorisés (masque de bits : bit 0 = lundi … bit 6 = dimanche). */
    var notifyDaysMask: Int
        get() = prefs.getInt(KEY_NOTIFY_DAYS, NotifyWindow.DEFAULT_DAYS)
        set(value) = prefs.edit().putInt(KEY_NOTIFY_DAYS, value).apply()

    var notifyStartHour: Int
        get() = prefs.getInt(KEY_NOTIFY_START, NotifyWindow.DEFAULT_START)
        set(value) = prefs.edit().putInt(KEY_NOTIFY_START, value.coerceIn(0, 23)).apply()

    var notifyEndHour: Int
        get() = prefs.getInt(KEY_NOTIFY_END, NotifyWindow.DEFAULT_END)
        set(value) = prefs.edit().putInt(KEY_NOTIFY_END, value.coerceIn(0, 24)).apply()

    /** Les notifications sont-elles autorisées à cet instant (jour + heure) ? */
    fun isWithinNotifyWindow(now: LocalDateTime): Boolean =
        NotifyWindow.isWithin(notifyDaysMask, notifyStartHour, notifyEndHour, now)

    // --- Frais de courtage simulés (mode test) ---

    var paperFeeProfileKey: String
        get() = prefs.getString(KEY_FEE_PROFILE, Fees.DECOUVERTE.key) ?: Fees.DECOUVERTE.key
        set(value) = prefs.edit().putString(KEY_FEE_PROFILE, value).apply()

    fun feeProfile(key: String): FeeProfile {
        val def = Fees.byKey(key)
        val pct = prefs.getFloat("fee_${key}_pct", def.percent.toFloat()).toDouble()
        val min = prefs.getFloat("fee_${key}_min", def.minFee.toFloat()).toDouble()
        return def.copy(percent = pct, minFee = min)
    }

    fun allFeeProfiles(): List<FeeProfile> = Fees.DEFAULTS.map { feeProfile(it.key) }

    fun activeFeeProfile(): FeeProfile = feeProfile(paperFeeProfileKey)

    fun saveFeeProfile(profile: FeeProfile) {
        prefs.edit()
            .putFloat("fee_${profile.key}_pct", profile.percent.toFloat())
            .putFloat("fee_${profile.key}_min", profile.minFee.toFloat())
            .apply()
    }

    /** Cours du dernier conseil « Alléger » pour une valeur (null = aucun). */
    fun trimAnchor(ticker: String): Double? =
        prefs.getFloat("trim_anchor_$ticker", Float.NaN).let { if (it.isNaN()) null else it.toDouble() }

    /** Mémorise (ou efface si null) le cours du dernier allègement conseillé. */
    fun setTrimAnchor(ticker: String, price: Double?) {
        prefs.edit().apply {
            if (price == null) remove("trim_anchor_$ticker") else putFloat("trim_anchor_$ticker", price.toFloat())
        }.apply()
    }

    private companion object {
        const val KEY_INTERVAL = "refresh_interval_hours"
        const val KEY_NOTIF = "notifications_enabled"
        const val KEY_NOTIFY_DAYS = "notify_days_mask"
        const val KEY_NOTIFY_START = "notify_start_hour"
        const val KEY_NOTIFY_END = "notify_end_hour"
        const val KEY_FEE_PROFILE = "paper_fee_profile_key"
    }
}
