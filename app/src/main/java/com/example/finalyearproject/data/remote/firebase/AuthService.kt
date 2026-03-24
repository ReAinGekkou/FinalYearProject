package com.example.finalyearproject.data.remote.firebase

import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AuthService
 *
 * Wraps Firebase Authentication into suspend functions.
 * All operations return [Resource] to propagate success / error
 * to the repository layer without leaking Firebase types upward.
 *
 * Location: data/remote/firebase/AuthService.kt
 */
@Singleton
class AuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {

    // ── Current user helper ───────────────────────────────────────────────────

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    val isLoggedIn: Boolean
        get() = firebaseAuth.currentUser != null

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Creates a new Firebase Auth account with email + password.
     *
     * @param email    Valid email address
     * @param password Minimum 6 characters (enforced by Firebase)
     * @return [Resource.Success] with [AuthResult], or [Resource.Error] with
     *         a user-friendly message.
     */
    suspend fun register(
        email: String,
        password: String
    ): Resource<AuthResult> = try {

        val result = firebaseAuth
            .createUserWithEmailAndPassword(email, password)
            .await()

        Resource.Success(result)

    } catch (e: FirebaseAuthWeakPasswordException) {
        Resource.Error("Password is too weak. Use at least 6 characters.")
    } catch (e: FirebaseAuthUserCollisionException) {
        Resource.Error("An account with this email already exists.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Resource.Error("Invalid email format.")
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Registration failed. Please try again.")
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Signs in an existing user with email + password.
     *
     * @return [Resource.Success] with [AuthResult], or [Resource.Error].
     */
    suspend fun login(
        email: String,
        password: String
    ): Resource<AuthResult> = try {

        val result = firebaseAuth
            .signInWithEmailAndPassword(email, password)
            .await()

        Resource.Success(result)

    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Resource.Error("Invalid email or password.")
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Login failed. Please try again.")
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Signs out the current user synchronously.
     * Firebase sign-out is a local operation — no network call needed.
     */
    fun logout() {
        firebaseAuth.signOut()
    }

    // ── Password Reset ────────────────────────────────────────────────────────

    /**
     * Sends a password-reset email to the given address.
     *
     * @return [Resource.Success] with Unit on success.
     */
    suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to send reset email.")
    }

    // ── Update Profile ────────────────────────────────────────────────────────

    /**
     * Updates the display name on the Firebase Auth user object.
     * Firestore profile document should be updated separately via FirestoreService.
     */
    suspend fun updateDisplayName(displayName: String): Resource<Unit> = try {
        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()

        firebaseAuth.currentUser
            ?.updateProfile(profileUpdates)
            ?.await()

        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update display name.")
    }
}