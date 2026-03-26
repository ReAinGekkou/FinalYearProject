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

        if (auth.currentUser != null) {
            goHome()
            return
        }

        setupGoogleSignIn()
        setupLanguageToggle()
        setupClickListeners()
    }

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

    private fun toggleLanguage() {
        val current = LanguageManager.getSavedLanguage(this)
        LanguageManager.setLanguage(this, LanguageManager.toggle(current))
    }

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

        // ── Check for Admin Login ──
        if (email == ADMIN_EMAIL && password == ADMIN_PASSWORD) {
            // Option 1: Log in with Firebase if the account exists
            // Option 2: Bypass if it's a hardcoded admin
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    setEmailLoading(false)
                    if (task.isSuccessful) {
                        goHome()
                    } else {
                        // If admin account doesn't exist in Firebase yet, you can auto-create it 
                        // or show a specific error.
                        showSnackbar(binding.root, "Admin account not found in Firebase. Please create it in Console first.")
                    }
                }
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setEmailLoading(false)
                if (task.isSuccessful) {
                    goHome()
                } else {
                    val errorCode = (task.exception as? com.google.firebase.auth.FirebaseAuthException)
                        ?.errorCode ?: ""
                    val message = when (errorCode) {
                        "ERROR_USER_NOT_FOUND"  -> getString(R.string.error_user_not_found)
                        "ERROR_WRONG_PASSWORD"  -> getString(R.string.error_wrong_password)
                        else -> task.exception?.localizedMessage ?: getString(R.string.error_generic)
                    }
                    showSnackbar(binding.root, message)
                }
            }
    }

    private fun launchGoogleSignIn() {
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
            
            val message = when (e.statusCode) {
                10 -> "Google Sign-In Error 10: Please ensure SHA-1 is registered in Firebase Console."
                12501 -> null // Cancelled
                else -> getString(R.string.error_google_sign_in) + " (code: ${e.statusCode})"
            }
            message?.let { showSnackbar(binding.root, it) }
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
                    showSnackbar(binding.root, task.exception?.localizedMessage ?: getString(R.string.error_generic))
                }
            }
    }

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
                }
            }
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun goToRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    private fun setEmailLoading(loading: Boolean) {
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.text             = if (loading) "" else getString(R.string.btn_login)
        binding.btnLogin.isEnabled        = !loading
    }

    private fun setGoogleLoading(loading: Boolean) {
        binding.progressGoogle.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogle.text             = if (loading) "" else getString(R.string.btn_google_sign_in)
        binding.btnGoogle.isEnabled        = !loading
    }
}