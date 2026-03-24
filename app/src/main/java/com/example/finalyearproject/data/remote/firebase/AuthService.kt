package com.example.finalyearproject.data.remote.firebase

import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * AuthService — No Hilt version.
 * Accessed via companion object singleton.
 */
class AuthService {

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser
    val currentUserId: String?     get() = firebaseAuth.currentUser?.uid
    val isLoggedIn: Boolean        get() = firebaseAuth.currentUser != null

    suspend fun register(email: String, password: String): Resource<AuthResult> = try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        Resource.Success(result)
    } catch (e: FirebaseAuthWeakPasswordException) {
        Resource.Error("Password is too weak. Use at least 6 characters.")
    } catch (e: FirebaseAuthUserCollisionException) {
        Resource.Error("An account with this email already exists.")
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Resource.Error("Invalid email format.")
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Registration failed.")
    }

    suspend fun login(email: String, password: String): Resource<AuthResult> = try {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        Resource.Success(result)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Resource.Error("Invalid email or password.")
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Login failed.")
    }

    fun logout() = firebaseAuth.signOut()

    suspend fun sendPasswordResetEmail(email: String): Resource<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to send reset email.")
    }

    suspend fun updateDisplayName(displayName: String): Resource<Unit> = try {
        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
            .setDisplayName(displayName)
            .build()
        firebaseAuth.currentUser?.updateProfile(profileUpdates)?.await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update display name.")
    }

    companion object {
        @Volatile private var instance: AuthService? = null
        fun getInstance(): AuthService =
            instance ?: synchronized(this) {
                instance ?: AuthService().also { instance = it }
            }
    }
}