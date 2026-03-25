package com.example.finalyearproject.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * LanguageManager
 *
 * Handles persisting and applying the user's chosen locale.
 * Call [wrap] in every Activity's [attachBaseContext] to apply
 * the saved language before the layout inflates.
 *
 * Supported codes: "en", "vi"
 */
object LanguageManager {

    const val LANG_EN = "en"
    const val LANG_VI = "vi"
    const val DEFAULT_LANG = LANG_EN

    private const val PREFS_NAME = "lang_prefs"
    private const val KEY_LANG   = "selected_language"

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedLanguage(context: Context): String =
        prefs(context).getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG

    fun saveLanguage(context: Context, langCode: String) {
        prefs(context).edit().putString(KEY_LANG, langCode).apply()
    }

    // ── Application ───────────────────────────────────────────────────────────

    /**
     * Returns a Context with the saved locale applied.
     * Call this in [android.app.Activity.attachBaseContext]:
     *
     *   override fun attachBaseContext(base: Context) {
     *       super.attachBaseContext(LanguageManager.wrap(base))
     *   }
     */
    fun wrap(context: Context): Context {
        val langCode = getSavedLanguage(context)
        return applyLocale(context, langCode)
    }

    /**
     * Applies [langCode] locale to the given context and returns a new wrapped context.
     * Does NOT persist the choice — call [saveLanguage] separately.
     */
    fun applyLocale(context: Context, langCode: String): Context {
        val locale = Locale(langCode)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Convenience: save + apply in one call, then recreate the activity.
     *
     * Usage in Activity:
     *   LanguageManager.setLanguage(this, "vi")
     */
    fun setLanguage(activity: android.app.Activity, langCode: String) {
        saveLanguage(activity, langCode)
        activity.recreate()   // fast recreate — applies new locale instantly
    }

    /**
     * Returns the other language code (toggles between EN ↔ VI).
     */
    fun toggle(current: String): String = if (current == LANG_EN) LANG_VI else LANG_EN

    /**
     * Returns the display label for the *other* language (what the button should show).
     * Button shows the language you will switch TO.
     */
    fun toggleLabel(current: String): String = if (current == LANG_EN) "VI" else "EN"

    /**
     * Returns the display label for the *current* language.
     */
    fun currentLabel(current: String): String = current.uppercase()
}