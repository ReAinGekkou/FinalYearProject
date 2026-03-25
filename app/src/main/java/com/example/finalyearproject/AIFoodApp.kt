package com.example.finalyearproject

import android.app.Application
import com.example.finalyearproject.utils.AppLogger
import com.example.finalyearproject.utils.OfflineManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * AIFoodApp — Day 4 Final
 *
 * Initialises:
 *  1. Firebase with offline persistence
 *  2. OfflineManager (SharedPreferences)
 *  3. AppLogger debug flag
 *
 * Register in AndroidManifest.xml:
 *   <application android:name=".AIFoodApp" ...>
 */
class AIFoodApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── AppLogger ─────────────────────────────────────────────────────────
        AppLogger.isDebugEnabled = BuildConfig.DEBUG
        AppLogger.i(AppLogger.TAG_AUTH, "AIFoodApp starting — debug=${BuildConfig.DEBUG}")

        // ── Firebase ──────────────────────────────────────────────────────────
        FirebaseApp.initializeApp(this)

        val firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)      // Firestore offline disk cache
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = firestoreSettings
        AppLogger.d(AppLogger.TAG_FIRESTORE, "Firestore offline persistence enabled")

        // ── OfflineManager ────────────────────────────────────────────────────
        OfflineManager.init(this)
        AppLogger.d(AppLogger.TAG_CACHE, "OfflineManager ready")
    }
}