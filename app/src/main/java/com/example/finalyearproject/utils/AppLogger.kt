package com.example.finalyearproject.utils

import android.util.Log

/**
 * AppLogger — Centralised logging for the entire app.
 *
 * Wraps Android's Log with:
 *  - Consistent tag prefixes per layer
 *  - Automatic log suppression in release builds
 *  - Structured Firestore + AI + cache logging helpers
 *
 * Location: utils/AppLogger.kt
 *
 * Usage:
 *   AppLogger.d(AppLogger.TAG_FIRESTORE, "Fetching recipes")
 *   AppLogger.apiRequest("POST", url, body)
 *   AppLogger.firestoreRead("recipes", "getRecipes()")
 *   AppLogger.error(TAG, "Something went wrong", exception)
 */
object AppLogger {

    // ── Log tags ──────────────────────────────────────────────────────────────
    const val TAG_AUTH      = "AIFood_Auth"
    const val TAG_FIRESTORE = "AIFood_Firestore"
    const val TAG_STORAGE   = "AIFood_Storage"
    const val TAG_AI        = "AIFood_AI"
    const val TAG_REPO      = "AIFood_Repository"
    const val TAG_USECASE   = "AIFood_UseCase"
    const val TAG_CACHE     = "AIFood_Cache"
    const val TAG_NETWORK   = "AIFood_Network"
    const val TAG_UI        = "AIFood_UI"

    /**
     * Set to false in release builds to suppress verbose logs.
     * In production, set this to BuildConfig.DEBUG.
     */
    var isDebugEnabled: Boolean = true

    // ── Generic log methods ───────────────────────────────────────────────────

    fun d(tag: String, message: String) {
        if (isDebugEnabled) Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable)
        else Log.e(tag, message)
    }

    // ── Structured Firestore logs ─────────────────────────────────────────────

    fun firestoreRead(collection: String, operation: String) {
        d(TAG_FIRESTORE, "READ  [$collection] → $operation")
    }

    fun firestoreWrite(collection: String, operation: String, docId: String = "") {
        d(TAG_FIRESTORE, "WRITE [$collection${if (docId.isNotEmpty()) "/$docId" else ""}] → $operation")
    }

    fun firestoreDelete(collection: String, docId: String) {
        d(TAG_FIRESTORE, "DELETE [$collection/$docId]")
    }

    fun firestoreError(collection: String, operation: String, error: Exception) {
        e(TAG_FIRESTORE, "ERROR [$collection] $operation: ${error.localizedMessage}", error)
    }

    // ── Structured AI/API logs ────────────────────────────────────────────────

    fun apiRequest(method: String, url: String, bodyPreview: String = "") {
        if (!isDebugEnabled) return
        val preview = if (bodyPreview.length > 200) bodyPreview.take(200) + "…" else bodyPreview
        d(TAG_AI, "→ $method $url${if (preview.isNotEmpty()) "\nBody: $preview" else ""}")
    }

    fun apiResponse(statusCode: Int, bodyPreview: String = "") {
        if (!isDebugEnabled) return
        val preview = if (bodyPreview.length > 300) bodyPreview.take(300) + "…" else bodyPreview
        val tag = if (statusCode in 200..299) TAG_AI else TAG_NETWORK
        d(tag, "← HTTP $statusCode${if (preview.isNotEmpty()) "\nResponse: $preview" else ""}")
    }

    fun apiError(url: String, error: Exception) {
        e(TAG_AI, "AI API ERROR for $url: ${error.localizedMessage}", error)
    }

    // ── Auth logs ─────────────────────────────────────────────────────────────

    fun authEvent(event: String, uid: String = "") {
        i(TAG_AUTH, "AUTH [$event]${if (uid.isNotEmpty()) " uid=$uid" else ""}")
    }

    // ── Repository logs ───────────────────────────────────────────────────────

    fun repoCall(repository: String, method: String) {
        d(TAG_REPO, "$repository.$method()")
    }

    fun repoCacheHit(repository: String, key: String) {
        d(TAG_CACHE, "CACHE HIT  [$repository] key=$key")
    }

    fun repoCacheMiss(repository: String, key: String) {
        d(TAG_CACHE, "CACHE MISS [$repository] key=$key → fetching from Firestore")
    }

    fun repoError(repository: String, method: String, error: Exception) {
        e(TAG_REPO, "$repository.$method() FAILED: ${error.localizedMessage}", error)
    }

    // ── Resource result log ───────────────────────────────────────────────────

    fun <T> logResult(tag: String, label: String, resource: Resource<T>) {
        when (resource) {
            is Resource.Success -> d(tag, "✓ $label → Success")
            is Resource.Error   -> e(tag, "✗ $label → Error: ${resource.message}")
            is Resource.Loading -> d(tag, "⟳ $label → Loading")
        }
    }

    // ── Retry log ─────────────────────────────────────────────────────────────

    fun retryAttempt(tag: String, attempt: Int, maxRetries: Int, delayMs: Long) {
        w(tag, "Retry $attempt/$maxRetries in ${delayMs}ms…")
    }
}