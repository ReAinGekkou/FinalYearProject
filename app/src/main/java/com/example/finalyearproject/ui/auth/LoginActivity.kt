package com.example.finalyearproject.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityLoginBinding
import com.example.finalyearproject.ui.BaseActivity
import com.example.finalyearproject.ui.home.HomeActivity
import com.example.finalyearproject.utils.LanguageManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    // ── Admin credentials ─────────────────────────────────────────────────────
    // Change these to your desired admin email/password
    private val ADMIN_EMAIL    = "admin@foodai.com"
    private val ADMIN_PASSWORD = "Admin@123"

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            handleGoogleSignInResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Skip login if already signed in
        if (auth.currentUser != null) {
            goHome()
            return
        }

        setupGoogleSignIn()
        setupLanguageToggle()
        setupClickListeners()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupLanguageToggle() {
        val current = LanguageManager.getSavedLanguage(this)
        binding.btnLanguage.text = LanguageManager.toggleLabel(current)
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener          { attemptEmailLogin() }
        binding.btnGoogle.setOnClickListener         { launchGoogleSignIn() }
        binding.btnRegister.setOnClickListener       { goToRegister() }
        binding.btnForgotPassword.setOnClickListener { sendPasswordReset() }
        binding.btnLanguage.setOnClickListener       { toggleLanguage() }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    private fun toggleLanguage() {
        val current = LanguageManager.getSavedLanguage(this)
        LanguageManager.setLanguage(this, LanguageManager.toggle(current))
    }

    // ── Email / Password Login ────────────────────────────────────────────────

    private fun attemptEmailLogin() {
        val email    = binding.etEmail.text?.toString()?.trim() ?: ""
        val password = binding.etPassword.text?.toString() ?: ""

        binding.tilEmail.error    = null
        binding.tilPassword.error = null

        var valid = true
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_empty)
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            valid = false
        }
        if (!valid) return

        setEmailLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setEmailLoading(false)
                if (task.isSuccessful) {
                    goHome()
                } else {
                    // Firebase gives back generic errors — map them to friendly messages
                    val errorCode = (task.exception as? com.google.firebase.auth.FirebaseAuthException)
                        ?.errorCode ?: ""
                    val message = when (errorCode) {
                        "ERROR_USER_NOT_FOUND"  -> getString(R.string.error_user_not_found)
                        "ERROR_WRONG_PASSWORD"  -> getString(R.string.error_wrong_password)
                        "ERROR_USER_DISABLED"   -> getString(R.string.error_user_disabled)
                        "ERROR_TOO_MANY_REQUESTS" -> getString(R.string.error_too_many_requests)
                        else -> task.exception?.localizedMessage
                            ?: getString(R.string.error_generic)
                    }
                    showSnackbar(binding.root, message)
                }
            }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private fun launchGoogleSignIn() {
        // Sign out of any previous Google session first so the picker always shows
        googleSignInClient.signOut().addOnCompleteListener {
            setGoogleLoading(true)
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun handleGoogleSignInResult(result: ActivityResult) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            setGoogleLoading(false)
            Log.e("LoginActivity", "Google sign-in ApiException code=${e.statusCode}", e)
            when (e.statusCode) {
                12501 -> { /* User cancelled — do nothing */ }
                12500 -> showSnackbar(
                    binding.root,
                    getString(R.string.error_google_sha1)   // SHA-1 not registered
                )
                else  -> showSnackbar(
                    binding.root,
                    getString(R.string.error_google_sign_in) + " (code: ${e.statusCode})"
                )
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setGoogleLoading(false)
                if (task.isSuccessful) {
                    goHome()
                } else {
                    val errorCode = (task.exception as? com.google.firebase.auth.FirebaseAuthException)
                        ?.errorCode ?: ""
                    val message = when (errorCode) {
                        // If the Google account has no Firebase account linked,
                        // direct the user to register first
                        "ERROR_USER_NOT_FOUND"  -> getString(R.string.error_google_register_first)
                        else -> task.exception?.localizedMessage
                            ?: getString(R.string.error_generic)
                    }
                    showSnackbar(binding.root, message, getString(R.string.btn_create_account)) {
                        goToRegister()
                    }
                }
            }
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    private fun sendPasswordReset() {
        val email = binding.etEmail.text?.toString()?.trim() ?: ""
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_empty)
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showSnackbar(binding.root, getString(R.string.msg_reset_email_sent))
                } else {
                    showSnackbar(binding.root, task.exception?.localizedMessage
                        ?: getString(R.string.error_generic))
                }
            }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    // ── Loading states ────────────────────────────────────────────────────────

    private fun setEmailLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.text             = if (loading) "" else getString(R.string.btn_login)
        binding.btnLogin.isEnabled        = !loading
        binding.btnGoogle.isEnabled       = !loading
    }

    private fun setGoogleLoading(loading: Boolean) {
        binding.progressGoogle.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogle.text             = if (loading) "" else getString(R.string.btn_google_sign_in)
        binding.btnGoogle.isEnabled        = !loading
        binding.btnLogin.isEnabled         = !loading
    }
}