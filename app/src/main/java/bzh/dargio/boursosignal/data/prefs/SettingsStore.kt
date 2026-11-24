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

    /** Horodatage (epoch ms) du dernier scan de fond réussi (0 = jamais). */
    var lastScanAt: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    // ---- v3 (§23) ----

    private fun str(key: String): String? = prefs.getString(key, null)
    private fun putStr(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    /** Idées : ne proposer que des valeurs éligibles au PEA. */
    var peaOnly: Boolean
        get() = prefs.getBoolean("pea_only", false)
        set(value) = prefs.edit().putBoolean("pea_only", value).apply()

    /** Semaine (AAAA-Wnn) du dernier bilan hebdomadaire notifié. */
    var lastWeeklyReport: String?
        get() = str("last_weekly_report")
        set(value) = putStr("last_weekly_report", value)

    /** Lignes affichées par le widget d'écran d'accueil. */
    var widgetRegime: String?
        get() = str("widget_regime")
        set(value) = putStr("widget_regime", value)
    var widgetTest: String?
        get() = str("widget_test")
        set(value) = putStr("widget_test", value)
    var widgetReal: String?
        get() = str("widget_real")
        set(value) = putStr("widget_real", value)
    var widgetPending: String?
        get() = str("widget_pending")
        set(value) = putStr("widget_pending", value)

    /** Cache du calendrier d'une valeur : "OK|résultats|dividende|horodatage" ou "ERR|||horodatage". */
    fun eventsCache(ticker: String): String? = str("events_$ticker")
    fun setEventsCache(ticker: String, value: String) = putStr("events_$ticker", value)

    /** Date de publication déjà signalée pour une position (une alerte par publication). */
    fun earningsNotified(ticker: String): String? = str("earn_notif_$ticker")
    fun setEarningsNotified(ticker: String, date: String) = putStr("earn_notif_$ticker", date)

    /** Dernière météo du marché notifiée (RISK_ON / CAUTION / RISK_OFF), null = jamais. */
    var lastRegime: String?
        get() = prefs.getString(KEY_LAST_REGIME, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_LAST_REGIME) else editor.putString(KEY_LAST_REGIME, value)
            editor.apply()
        }

    /** Retuning unique de la stratégie (prop20) déjà appliqué aux installations existantes ? */
    var strategyRetunedV2: Boolean
        get() = prefs.getBoolean(KEY_RETUNED_V2, false)
        set(value) = prefs.edit().putBoolean(KEY_RETUNED_V2, value).apply()

    /** Dernière action de sortie déjà notifiée pour une valeur (déduplication des alertes). */
    fun lastExitNotified(ticker: String): String? = prefs.getString("exit_notif_$ticker", null)
    fun setLastExitNotified(ticker: String, action: String?) {
        val editor = prefs.edit()
        if (action == null) editor.remove("exit_notif_$ticker") else editor.putString("exit_notif_$ticker", action)
        editor.apply()
    }

    /** Cours du dernier conseil « Alléger » pour une valeur (null = aucun). */
    fun trimAnchor(ticker: String): Double? =
        prefs.getFloat("trim_anchor_$ticker", Float.NaN).let { if (it.isNaN()) null else it.toDouble() }

    /** Mémorise (ou efface si null) le cours du dernier allègement conseillé. */
    fun setTrimAnchor(ticker: String, price: Double?) {
        val editor = prefs.edit()
        if (price == null) editor.remove("trim_anchor_$ticker") else editor.putFloat("trim_anchor_$ticker", price.toFloat())
        editor.apply()
    }

    private companion object {
        const val KEY_INTERVAL = "refresh_interval_hours"
        const val KEY_NOTIF = "notifications_enabled"
        const val KEY_NOTIFY_DAYS = "notify_days_mask"
        const val KEY_NOTIFY_START = "notify_start_hour"
        const val KEY_NOTIFY_END = "notify_end_hour"
        const val KEY_FEE_PROFILE = "paper_fee_profile_key"
        const val KEY_LAST_SCAN = "last_scan_at"
        const val KEY_RETUNED_V2 = "strategy_retuned_v2"
        const val KEY_LAST_REGIME = "last_regime"
    }
}
