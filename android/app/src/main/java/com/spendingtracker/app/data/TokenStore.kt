package com.spendingtracker.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun saveSession(token: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    /** "system", "light", or "dark" */
    fun getThemeMode(): String = prefs.getString(KEY_THEME, "system") ?: "system"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun isPrivacyMode(): Boolean = prefs.getBoolean(KEY_PRIVACY, false)

    fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY, enabled).apply()
    }

    fun isBankAlertsEnabled(): Boolean = prefs.getBoolean(KEY_BANK_ALERTS, false)

    fun setBankAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BANK_ALERTS, enabled).apply()
    }

    /** Cached snapshot for the Android Home Screen Widget (zero extra API calls) */
    fun saveWidgetSnapshot(balance: String, monthSpent: String, currency: String) {
        prefs.edit()
            .putString(KEY_WIDGET_BAL, balance)
            .putString(KEY_WIDGET_SPENT, monthSpent)
            .putString(KEY_WIDGET_CUR, currency)
            .apply()
    }

    fun getWidgetBalance(): String = prefs.getString(KEY_WIDGET_BAL, "0") ?: "0"
    fun getWidgetMonthSpent(): String = prefs.getString(KEY_WIDGET_SPENT, "0") ?: "0"
    fun getWidgetCurrency(): String = prefs.getString(KEY_WIDGET_CUR, "VND") ?: "VND"

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "spending_tracker_auth"
        private const val KEY_TOKEN = "jwt_access_token"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_BIOMETRIC = "biometric_lock_enabled"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PRIVACY = "privacy_mode"
        private const val KEY_BANK_ALERTS = "bank_alerts_enabled"
        private const val KEY_WIDGET_BAL = "widget_balance"
        private const val KEY_WIDGET_SPENT = "widget_month_spent"
        private const val KEY_WIDGET_CUR = "widget_currency"
    }
}
