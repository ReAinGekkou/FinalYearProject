package com.example.finalyearproject.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.finalyearproject.R
import com.example.finalyearproject.databinding.ActivityRegisterBinding
import com.example.finalyearproject.ui.BaseActivity
import com.example.finalyearproject.ui.home.HomeFragment
import com.example.finalyearproject.utils.LanguageManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            handleGoogleSignInResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Crash fix: set content view BEFORE accessing binding ──────────────
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db   = FirebaseFirestore.getInstance()

        // If already logged in, skip to home
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
        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.btnGoogle.setOnClickListener   { launchGoogleSignIn() }
        binding.btnLogin.setOnClickListener    { finish() }  // back to LoginActivity
        binding.btnLanguage.setOnClickListener { toggleLanguage() }
    }

    // ── Language ──────────────────────────────────────────────────────────────

    private fun toggleLanguage() {
        val current = LanguageManager.getSavedLanguage(this)
        LanguageManager.setLanguage(this, LanguageManager.toggle(current))
    }

    // ── Email / Password Register ─────────────────────────────────────────────

    private fun attemptRegister() {
        // Read and trim all fields safely — Elvis operator prevents NPE crash
        val displayName     = binding.etDisplayName.text?.toString()?.trim() ?: ""
        val email           = binding.etEmail.text?.toString()?.trim() ?: ""
        val password        = binding.etPassword.text?.toString() ?: ""
        val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""

        // Clear previous errors
        binding.tilDisplayName.error     = null
        binding.tilEmail.error           = null
        binding.tilPassword.error        = null
        binding.tilConfirmPassword.error = null

        // ── Validation ────────────────────────────────────────────────────────
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
                    updateProfileAndProceed(displayName)
                } else {
                    setRegisterLoading(false)
                    val message = when (task.exception) {
                        is FirebaseAuthUserCollisionException ->
                            getString(R.string.error_email_already_used)
                        else -> task.exception?.localizedMessage
                            ?: getString(R.string.error_generic)
                    }
                    showSnackbar(binding.root, message)
                }
            }
    }

    /**
     * After Firebase Auth account is created:
     * 1. Set the display name on the Auth user object
     * 2. Write a Firestore profile document
     * 3. Navigate to HomeActivity
     *
     * All steps are best-effort — a failure in step 2 or 3 does NOT
     * block the user from reaching the app.
     */
    private fun updateProfileAndProceed(displayName: String) {
        val user = auth.currentUser
        if (user == null) {
            // Extremely rare — auth state was lost between create and update
            setRegisterLoading(false)
            showSnackbar(binding.root, getString(R.string.error_generic))
            return
        }

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        user.updateProfile(profileUpdates)
            .addOnCompleteListener { profileTask ->
                if (!profileTask.isSuccessful) {
                    Log.w("RegisterActivity",
                        "updateProfile failed (non-fatal): ${profileTask.exception?.message}")
                }
                // Write Firestore doc — best effort, don't block navigation
                createFirestoreUserDoc(
                    uid         = user.uid,
                    displayName = displayName,
                    email       = user.email ?: ""
                )
                setRegisterLoading(false)
                goHome()
            }
    }

    private fun createFirestoreUserDoc(uid: String, displayName: String, email: String) {
        val userDoc = hashMapOf(
            "uid"         to uid,
            "displayName" to displayName,
            "email"       to email,
            "createdAt"   to com.google.firebase.Timestamp.now(),
            "isAdmin"     to false
        )
        // merge: true → safe to call even if doc partially exists
        db.collection("users").document(uid)
            .set(userDoc, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.w("RegisterActivity",
                    "Firestore user doc failed (non-fatal): ${e.message}")
            }
    }

    // ── Google Sign-In ────────────────────────────────────────────────────────

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
            Log.e("RegisterActivity", "Google sign-in ApiException code=${e.statusCode}", e)
            when (e.statusCode) {
                12501 -> { /* User cancelled */ }
                12500 -> showSnackbar(binding.root, getString(R.string.error_google_sha1))
                else  -> showSnackbar(
                    binding.root,
                    getString(R.string.error_google_sign_in) + " (code: ${e.statusCode})"
                )
            }
        }
    }

    /**
     * Google sign-in on the Register screen:
     * - If the Google account has no Firebase record  → creates one automatically
     * - If it already has a Firebase record (existing user) → just signs them in
     * Both flows land on HomeActivity — Firebase handles the distinction.
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                setGoogleLoading(false)
                if (task.isSuccessful) {
                    val user   = auth.currentUser
                    val isNew  = task.result?.additionalUserInfo?.isNewUser == true
                    if (isNew && user != null) {
                        createFirestoreUserDoc(
                            uid         = user.uid,
                            displayName = user.displayName ?: "",
                            email       = user.email ?: ""
                        )
                    }
                    if (!isNew) {
                        // Existing account — let them know they were signed in, not registered
                        showSnackbar(binding.root, getString(R.string.msg_google_existing_account))
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
        startActivity(Intent(this, HomeFragment::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private fun setRegisterLoading(loading: Boolean) {
        binding.progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.text  = if (loading) "" else getString(R.string.btn_create_account)
        binding.btnRegister.isEnabled = !loading
        binding.btnGoogle.isEnabled   = !loading
    }

    private fun setGoogleLoading(loading: Boolean) {
        binding.progressGoogle.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnGoogle.text  = if (loading) "" else getString(R.string.btn_google_sign_in)
        binding.btnGoogle.isEnabled   = !loading
        binding.btnRegister.isEnabled = !loading
    }
}