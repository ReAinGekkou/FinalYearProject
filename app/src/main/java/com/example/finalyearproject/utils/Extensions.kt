package com.example.finalyearproject.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extensions.kt
 *
 * Kotlin extension functions and top-level helpers used across the app.
 * Keep this file focused — add extensions relevant to multiple features.
 *
 * Location: utils/Extensions.kt
 */

// ── View Extensions ───────────────────────────────────────────────────────────

fun View.show() { visibility = View.VISIBLE }
fun View.hide() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun View.showIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

fun View.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_LONG,
    actionLabel: String? = null,
    action: (() -> Unit)? = null
) {
    Snackbar.make(this, message, duration).apply {
        if (actionLabel != null && action != null) {
            setAction(actionLabel) { action() }
        }
        show()
    }
}

// ── Context Extensions ────────────────────────────────────────────────────────

fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/**
 * Returns true if the device has an active internet connection.
 */
fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

// ── String Extensions ─────────────────────────────────────────────────────────

fun String.capitalizeWords(): String =
    split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

fun String.isValidEmail(): Boolean =
    android.util.Patterns.EMAIL_ADDRESS.matcher(this).matches()

// ── Firebase Timestamp Extensions ─────────────────────────────────────────────

/**
 * Formats a Firebase [Timestamp] for display.
 *
 * @param pattern  SimpleDateFormat pattern (default: "dd MMM yyyy")
 */
fun Timestamp?.toFormattedDate(pattern: String = "dd MMM yyyy"): String {
    if (this == null) return ""
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(Date(seconds * 1000))
}

fun Timestamp?.toRelativeTime(): String {
    if (this == null) return ""
    val now = System.currentTimeMillis()
    val then = seconds * 1000
    val diff = now - then

    return when {
        diff < 60_000              -> "Just now"
        diff < 3_600_000           -> "${diff / 60_000}m ago"
        diff < 86_400_000          -> "${diff / 3_600_000}h ago"
        diff < 7 * 86_400_000L     -> "${diff / 86_400_000}d ago"
        else                       -> toFormattedDate()
    }
}

// ── Resource Extensions ───────────────────────────────────────────────────────

/**
 * Executes [onSuccess], [onError], and [onLoading] based on [Resource] state.
 * Cleaner than a when expression in the ViewModel observer.
 */
inline fun <T> Resource<T>.handle(
    onSuccess: (T) -> Unit = {},
    onError: (String) -> Unit = {},
    onLoading: (String?) -> Unit = {}
) {
    when (this) {
        is Resource.Success -> onSuccess(data)
        is Resource.Error   -> onError(message)
        is Resource.Loading -> onLoading(message)
    }
}