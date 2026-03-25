package com.example.finalyearproject.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.finalyearproject.data.model.Recipe
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * OfflineManager — Persists critical data to SharedPreferences.
 *
 * Unlike AppCache (RAM only, cleared on process kill), OfflineManager
 * survives app restarts. Use for small, critical datasets only.
 *
 * Stored data:
 *  - Last fetched recipes list (for cold start with no internet)
 *  - Last known user ID (to pre-fill UI before auth check)
 *
 * Location: utils/OfflineManager.kt
 *
 * Initialise once in AIFoodApp.onCreate():
 *   OfflineManager.init(applicationContext)
 */
object OfflineManager {

    private const val PREFS_NAME           = "offline_store"
    private const val KEY_CACHED_RECIPES   = "cached_recipes"
    private const val KEY_LAST_USER_ID     = "last_user_id"
    private const val KEY_LAST_ONLINE_MS   = "last_online_ms"
    private const val MAX_PERSISTED_RECIPES = 20

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AppLogger.d(AppLogger.TAG_CACHE, "OfflineManager initialised")
    }

    // ── Recipes ───────────────────────────────────────────────────────────────

    /**
     * Saves the first [MAX_PERSISTED_RECIPES] recipes to SharedPreferences.
     * Called every time a successful Firestore fetch returns results.
     */
    fun persistRecipes(recipes: List<Recipe>) {
        val toSave = recipes.take(MAX_PERSISTED_RECIPES)
        val json = gson.toJson(toSave)
        prefs.edit().putString(KEY_CACHED_RECIPES, json).apply()
        AppLogger.d(AppLogger.TAG_CACHE, "Persisted ${toSave.size} recipes to disk")
    }

    /**
     * Returns the last persisted recipes, or empty list if none.
     * Used as the offline fallback when Firestore is unreachable.
     */
    fun getPersistedRecipes(): List<Recipe> {
        val json = prefs.getString(KEY_CACHED_RECIPES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Recipe>>() {}.type
            val recipes: List<Recipe> = gson.fromJson(json, type) ?: emptyList()
            AppLogger.d(AppLogger.TAG_CACHE, "Loaded ${recipes.size} persisted recipes from disk")
            recipes
        } catch (e: Exception) {
            AppLogger.e(AppLogger.TAG_CACHE, "Failed to parse persisted recipes", e)
            emptyList()
        }
    }

    // ── User ID ───────────────────────────────────────────────────────────────

    fun saveLastUserId(uid: String) {
        prefs.edit().putString(KEY_LAST_USER_ID, uid).apply()
    }

    fun getLastUserId(): String? = prefs.getString(KEY_LAST_USER_ID, null)

    fun clearLastUserId() {
        prefs.edit().remove(KEY_LAST_USER_ID).apply()
    }

    // ── Online tracking ───────────────────────────────────────────────────────

    fun markOnline() {
        prefs.edit().putLong(KEY_LAST_ONLINE_MS, System.currentTimeMillis()).apply()
    }

    /**
     * Returns how many milliseconds since the device was last seen online.
     * Useful for showing "Data may be outdated" warnings in the UI.
     */
    fun millisSinceLastOnline(): Long {
        val last = prefs.getLong(KEY_LAST_ONLINE_MS, 0L)
        return if (last == 0L) Long.MAX_VALUE else System.currentTimeMillis() - last
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /** Call on logout to clear personal offline data. */
    fun clearUserData() {
        prefs.edit()
            .remove(KEY_LAST_USER_ID)
            .apply()
        AppLogger.d(AppLogger.TAG_CACHE, "OfflineManager user data cleared")
    }

    /** Full wipe of all persisted data. */
    fun clearAll() {
        prefs.edit().clear().apply()
        AppLogger.d(AppLogger.TAG_CACHE, "OfflineManager cleared all data")
    }
}