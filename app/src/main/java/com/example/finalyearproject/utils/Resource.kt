package com.example.finalyearproject.utils

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Resource.kt  (Enhanced — replace Day 1 version)
// ─────────────────────────────────────────────────────────────────────────────

sealed class Resource<out T> {

    data class Success<out T>(val data: T) : Resource<T>()

    data class Error(
        val message: String,
        val exception: Exception? = null,
        val errorType: ErrorType = ErrorType.UNKNOWN
    ) : Resource<Nothing>()

    data class Loading(val message: String? = null) : Resource<Nothing>()

    // ── Convenience ───────────────────────────────────────────────────────────
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean   get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorMessage(): String? = (this as? Error)?.message

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error   -> this
        is Loading -> this
    }
}

/**
 * Categorises errors so the UI can show contextual messages.
 */
enum class ErrorType {
    NETWORK,          // no internet / timeout
    AUTH,             // not logged in / permission denied
    NOT_FOUND,        // document doesn't exist
    VALIDATION,       // input failed validation
    SERVER,           // Firebase / API server error
    UNKNOWN
}


// ─────────────────────────────────────────────────────────────────────────────
// SafeCall.kt — retry + safe wrapper utilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps any suspend block in a try/catch and maps exceptions
 * to the correct [ErrorType].
 *
 * Usage:
 *   val result = safeCall { firestoreService.getRecipes() }
 */
suspend fun <T> safeCall(block: suspend () -> Resource<T>): Resource<T> {
    return try {
        block()
    } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
        Resource.Error(
            message = e.localizedMessage ?: "Firestore error.",
            exception = e,
            errorType = mapFirestoreError(e.code)
        )
    } catch (e: com.google.firebase.auth.FirebaseAuthException) {
        Resource.Error(
            message = e.localizedMessage ?: "Authentication error.",
            exception = e,
            errorType = ErrorType.AUTH
        )
    } catch (e: java.io.IOException) {
        Resource.Error(
            message = "Network error. Please check your connection.",
            exception = e,
            errorType = ErrorType.NETWORK
        )
    } catch (e: Exception) {
        Resource.Error(
            message = e.localizedMessage ?: "An unexpected error occurred.",
            exception = e,
            errorType = ErrorType.UNKNOWN
        )
    }
}

/**
 * Retries a suspend block up to [maxRetries] times with exponential backoff.
 * Only retries on NETWORK errors — not on AUTH or VALIDATION errors.
 *
 * Usage:
 *   val result = retryCall(maxRetries = 3) { apiService.suggestMeal(prompt) }
 */
suspend fun <T> retryCall(
    maxRetries: Int = 3,
    initialDelayMs: Long = 500L,
    factor: Double = 2.0,
    block: suspend () -> Resource<T>
): Resource<T> {
    var currentDelay = initialDelayMs
    repeat(maxRetries - 1) { attempt ->
        val result = safeCall { block() }
        if (result is Resource.Success) return result
        if (result is Resource.Error && result.errorType != ErrorType.NETWORK) {
            // Don't retry auth/validation errors
            return result
        }
        delay(currentDelay)
        currentDelay = (currentDelay * factor).toLong()
    }
    // Final attempt
    return safeCall { block() }
}

private fun mapFirestoreError(
    code: com.google.firebase.firestore.FirebaseFirestoreException.Code
): ErrorType = when (code) {
    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED -> ErrorType.AUTH
    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND       -> ErrorType.NOT_FOUND
    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE     -> ErrorType.NETWORK
    else -> ErrorType.SERVER
}