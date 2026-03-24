package com.example.finalyearproject

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

/**
 * Application class — initialised once at app start.
 *
 * Firebase is auto-initialised via google-services.json, but we
 * configure Firestore settings here for offline persistence and
 * long-polling on older devices.
 *
 * Register in AndroidManifest.xml:
 *   <application android:name=".AIFoodApp" ...>
 */
class AIFoodApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase is already initialised by FirebaseInitProvider
        // (included via google-services plugin), but we can verify:
        FirebaseApp.initializeApp(this)

        // ── Firestore Settings ────────────────────────────────────
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)        // Offline cache
            .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}