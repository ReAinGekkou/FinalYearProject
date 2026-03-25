package com.example.finalyearproject.ui

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.finalyearproject.utils.LanguageManager
import com.google.android.material.snackbar.Snackbar

/**
 * BaseActivity
 *
 * Every Activity extends this to get:
 *  - Locale applied before layout inflates (via attachBaseContext)
 *  - Common helpers: showToast, showSnackbar, showLoading
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    // ── Common UI helpers ─────────────────────────────────────────────────────

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun showSnackbar(root: View, message: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        Snackbar.make(root, message, Snackbar.LENGTH_LONG).apply {
            if (actionLabel != null && action != null) setAction(actionLabel) { action() }
            show()
        }
    }
}