package com.example.finalyearproject.utils

import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Resource — Final version
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
    fun errorType(): ErrorType? = (this as? Error)?.errorType

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error   -> this
        is Loading -> this
    }

    /** Runs [onSuccess], [onError], or [onLoading] based on state. */
    inline fun handle(
        onSuccess: (T) -> Unit = {},
        onError: (String, ErrorType) -> Unit = { _, _ -> },
        onLoading: (String?) -> Unit = {}
    ) {
        when (this) {
            is Success -> onSuccess(data)
            is Error   -> onError(message, errorType)
            is Loading -> onLoading(message)
        }
    }
}

enum class ErrorType {
    NETWORK,       // no internet / timeout
    AUTH,          // not logged in / permission denied
    NOT_FOUND,     // document doesn't exist
    VALIDATION,    // input failed domain validation
    SERVER,        // Firestore / API server error
    UNKNOWN
}

// ─────────────────────────────────────────────────────────────────────────────
// safeCall — wraps any suspend block, maps exceptions to Resource.Error
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Wraps a suspend block in structured error handling.
 * Maps Firebase and IO exceptions to the appropriate [ErrorType].
 *
 * Usage:
 *   val result = safeCall { firestoreService.getRecipes() }
 */
suspend fun <T> safeCall(
    tag: String = AppLogger.TAG_REPO,
    label: String = "operation",
    block: suspend () -> Resource<T>
): Resource<T> {
    return try {
        val result = block()
        AppLogger.logResult(tag, label, result)
        result
    } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
        val errorType = mapFirestoreErrorCode(e.code)
        val message = when (errorType) {
            ErrorType.AUTH      -> "You don't have permission to perform this action."
            ErrorType.NOT_FOUND -> "The requested data was not found."
            ErrorType.NETWORK   -> "Network error. Please check your connection."
            else                -> e.localizedMessage ?: "A database error occurred."
        }
        AppLogger.e(tag, "[$label] Firestore error (${e.code}): ${e.localizedMessage}", e)
        Resource.Error(message, e, errorType)
    } catch (e: com.google.firebase.auth.FirebaseAuthException) {
        AppLogger.e(tag, "[$label] Auth error: ${e.localizedMessage}", e)
        Resource.Error(e.localizedMessage ?: "Authentication error.", e, ErrorType.AUTH)
    } catch (e: java.io.IOException) {
        AppLogger.e(tag, "[$label] Network error: ${e.localizedMessage}", e)
        Resource.Error("Network error. Please check your connection.", e, ErrorType.NETWORK)
    } catch (e: Exception) {
        AppLogger.e(tag, "[$label] Unexpected error: ${e.localizedMessage}", e)
        Resource.Error(e.localizedMessage ?: "An unexpected error occurred.", e, ErrorType.UNKNOWN)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// retryCall — exponential backoff retry for network/AI calls
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Retries a suspend block up to [maxRetries] times with exponential backoff.
 *
 * Only retries on [ErrorType.NETWORK] errors.
 * Auth, validation, and server errors are returned immediately without retry.
 *
 * @param maxRetries      Maximum number of attempts (default 3)
 * @param initialDelayMs  Delay before second attempt in ms (default 500)
 * @param factor          Delay multiplier per attempt (default 2.0 → 500, 1000, 2000)
 * @param tag             Log tag for retry messages
 *
 * Usage:
 *   val result = retryCall { aiService.suggestMeal(prompt) }
 */
suspend fun <T> retryCall(
    maxRetries: Int = 3,
    initialDelayMs: Long = 500L,
    factor: Double = 2.0,
    tag: String = AppLogger.TAG_NETWORK,
    block: suspend () -> Resource<T>
): Resource<T> {
    var currentDelay = initialDelayMs
    var lastResult: Resource<T> = Resource.Error("No attempts made.")

    repeat(maxRetries) { attempt ->
        val result = safeCall(tag, "attempt ${attempt + 1}/$maxRetries") { block() }

        if (result is Resource.Success) return result

        lastResult = result

        // Don't retry non-network errors
        if (result is Resource.Error && result.errorType != ErrorType.NETWORK) {
            AppLogger.w(tag, "Not retrying — errorType=${result.errorType}")
            return result
        }

        // Don't sleep after the last attempt
        if (attempt < maxRetries - 1) {
            AppLogger.retryAttempt(tag, attempt + 1, maxRetries, currentDelay)
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }

    return lastResult
}

// ─────────────────────────────────────────────────────────────────────────────
// networkAwareCall — checks connectivity before making a remote call,
//                    falls back to cache if offline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Runs [networkBlock] if online, otherwise runs [cacheBlock].
 * If [networkBlock] fails with a NETWORK error, falls back to [cacheBlock].
 *
 * Usage:
 *   val result = networkAwareCall(
 *       isOnline = context.isNetworkAvailable(),
 *       cacheBlock = { AppCache.getRecipes()?.let { Resource.Success(it) } },
 *       networkBlock = { firestoreService.getRecipes() }
 *   )
 */
suspend fun <T> networkAwareCall(
    isOnline: Boolean,
    cacheBlock: () -> Resource<T>?,
    networkBlock: suspend () -> Resource<T>
): Resource<T> {
    if (!isOnline) {
        AppLogger.w(AppLogger.TAG_NETWORK, "Device offline — serving from cache")
        return cacheBlock()
            ?: Resource.Error("No internet connection and no cached data available.", errorType = ErrorType.NETWORK)
    }

    val networkResult = safeCall { networkBlock() }

    if (networkResult is Resource.Error && networkResult.errorType == ErrorType.NETWORK) {
        AppLogger.w(AppLogger.TAG_NETWORK, "Network call failed — falling back to cache")
        return cacheBlock() ?: networkResult
    }

    return networkResult
}

// ─────────────────────────────────────────────────────────────────────────────
// Private helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun mapFirestoreErrorCode(
    code: com.google.firebase.firestore.FirebaseFirestoreException.Code
): ErrorType = when (code) {
    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED -> ErrorType.AUTH
    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND       -> ErrorType.NOT_FOUND
    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE,
    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ErrorType.NETWORK
    else -> ErrorType.SERVER
}