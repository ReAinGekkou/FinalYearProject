package com.example.finalyearproject.data.repository

import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.AppCache
import com.example.finalyearproject.utils.AppLogger
import com.example.finalyearproject.utils.OfflineManager
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.safeCall
import com.google.firebase.auth.FirebaseUser

class AuthRepository private constructor(
    private val authService: AuthService = AuthService.getInstance(),
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {
    val currentUser: FirebaseUser? get() = authService.currentUser
    val currentUserId: String?    get() = authService.currentUserId
    val isLoggedIn: Boolean       get() = authService.isLoggedIn

    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Resource<Unit> = safeCall(AppLogger.TAG_AUTH, "register") {
        AppLogger.authEvent("register", email)
        val authResult = authService.register(email, password)
        if (authResult is Resource.Error) return@safeCall Resource.Error(authResult.message)

        val uid = authService.currentUserId
            ?: return@safeCall Resource.Error("UID missing after registration.")

        OfflineManager.saveLastUserId(uid)

        firestoreService.addUser(User(uid = uid, email = email, displayName = displayName))
        AppLogger.authEvent("register_complete", uid)
        Resource.Success(Unit)
    }

    suspend fun login(email: String, password: String): Resource<FirebaseUser> =
        safeCall(AppLogger.TAG_AUTH, "login") {
            AppLogger.authEvent("login_attempt")
            when (val result = authService.login(email, password)) {
                is Resource.Success -> {
                    val user = result.data.user
                        ?: return@safeCall Resource.Error("No user returned.")
                    OfflineManager.saveLastUserId(user.uid)
                    AppLogger.authEvent("login_success", user.uid)
                    Resource.Success(user)
                }
                is Resource.Error   -> Resource.Error(result.message)
                else                -> Resource.Loading()
            }
        }

    fun logout() {
        val uid = authService.currentUserId ?: ""
        authService.logout()
        AppCache.clearUserData(uid)
        OfflineManager.clearUserData()
        AppLogger.authEvent("logout", uid)
    }

    suspend fun sendPasswordReset(email: String): Resource<Unit> =
        safeCall(AppLogger.TAG_AUTH, "sendPasswordReset") {
            authService.sendPasswordResetEmail(email)
        }

    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }
}
