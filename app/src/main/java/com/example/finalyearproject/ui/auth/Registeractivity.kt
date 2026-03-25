package com.example.finalyearproject.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityRegisterBinding
import com.example.finalyearproject.ui.BaseActivity
import com.example.finalyearproject.ui.home.HomeActivity
import com.example.finalyearproject.utils.LanguageManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            handleGoogleSignInResult(result)
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

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
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.btnGoogle.setOnClickListener { launchGoogleSignIn() }
        binding.btnLogin.setOnClickListener { finish() }   // back to Login
        binding.btnLanguage.setOnClickListener { toggleLanguage() }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    private fun toggleLanguage() {
        val current = LanguageManager.getSavedLanguage(this)
        LanguageManager.setLanguage(this, LanguageManager.toggle(current))
    }

    // ── Email / Password Register ─────────────────────────────────────────────

    private fun attemptRegister() {
        val displayName     = binding.etDisplayName.text?.toString()?.trim() ?: ""
        val email           = binding.etEmail.text?.toString()?.trim() ?: ""
        val password        = binding.etPassword.text?.toString() ?: ""
        val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""

        // Clear errors
        binding.tilDisplayName.error     = null
        binding.tilEmail.error           = null
        binding.tilPassword.error        = null
        binding.tilConfirmPassword.error = null

        // Validate
        var valid = true
        if (displayName.length < 2) {
            binding.tilDisplayName.error = getString(R.string.error_display_name_short)
            valid = false
        }
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_empty)
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            valid = false
        }
        if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.error_password_short_register)
            valid = false
        }
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_mismatch)
            valid = false
        }
        if (!valid) return

        setRegisterLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Save display name to Firebase Auth profile
                    updateDisplayNameAndProceed(displayName)
                } else {
                    setRegisterLoading(false)
                    val msg = task.exception?.localizedMessage
                        ?: getString(R.string.error_generic)
                    showSnackbar(binding.root, msg)
                }
            }
    }

    /**
     * Sets the display name on Firebase Auth user and writes a Firestore user doc.
     */
    private fun updateDisplayNameAndProceed(displayName: String) {
        val user = auth.currentUser ?: run {
            setRegisterLoading(false)
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener {
                // Write Firestore user document (best-effort — don't block navigation)
                createFirestoreUserDoc(user.uid, displayName, user.email ?: "")
                setRegisterLoading(false)
                goHome()
            }
    }

    private fun createFirestoreUserDoc(uid: String, displayName: String, email: String) {
        val userDoc = hashMapOf(
            "uid"         to uid,
            "displayName" to displayName,
            "email"       to email,
            "createdAt"   to com.google.firebase.Timestamp.now()
        )
        db.collection("users").document(uid)
            .set(userDoc)
            .addOnFailureListener { e ->
                // Non-fatal — user can still use the app
                android.util.Log.w("RegisterActivity",
                    "Firestore user doc failed (non-fatal): ${e.message}")
            }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

    private fun launchGoogleSignIn() {
        setGoogleLoading(true)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun handleGoogleSignInResult(result: ActivityResult) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            setGoogleLoading(false)
            if (e.statusCode != 12501) {
                showSnackbar(binding.root, getString(R.string.error_google_sign_in))
            }
        }
    }

    /**
     * Google auth works for both new and existing accounts:
     * - New account → Firebase creates it automatically
     * - Existing account → signs in without error
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setGoogleLoading(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    // Write Firestore doc for new Google users (isNewUser flag)
                    val isNew = task.result?.additionalUserInfo?.isNewUser == true
                    if (isNew && user != null) {
                        createFirestoreUserDoc(
                            uid         = user.uid,
                            displayName = user.displayName ?: "",
                            email       = user.email ?: ""
                        )
                    }
                    goHome()
                } else {
                    val msg = task.exception?.localizedMessage
                        ?: getString(R.string.error_generic)
                    showSnackbar(binding.root, msg)
                }
            }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java).apply {
            // Clear the back-stack so the user can't press back to auth
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ── Loading states ────────────────────────────────────────────────────────

    private fun setRegisterLoading(loading: Boolean) {
        binding.progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.text = if (loading) "" else getString(R.string.btn_create_account)
        binding.btnRegister.isEnabled = !loading
        binding.btnGoogle.isEnabled   = !loading
    }

    private fun setGoogleLoading(loading: Boolean) {
        binding.progressGoogle.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogle.text = if (loading) "" else getString(R.string.btn_google_sign_in)
        binding.btnGoogle.isEnabled   = !loading
        binding.btnRegister.isEnabled = !loading
    }
}