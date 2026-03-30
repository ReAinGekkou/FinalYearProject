package com.example.finalyearproject.data.repository

import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.AppCache
import com.example.finalyearproject.utils.AppLogger
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.safeCall
import kotlinx.coroutines.flow.Flow

class UserRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val authService: AuthService = AuthService.getInstance()
) {
    fun getCurrentUserId(): String? = authService.currentUserId

    // ── Profile ───────────────────────────────────────────────────────────────

    suspend fun getUserProfile(userId: String): Resource<User> {
        if (userId.isBlank()) return Resource.Error("Invalid user ID.")
        AppCache.getUser(userId)?.let {
            AppLogger.repoCacheHit("UserRepository", "getUserProfile:$userId")
            return Resource.Success(it)
        }
        AppLogger.repoCacheMiss("UserRepository", "getUserProfile:$userId")
        return safeCall(AppLogger.TAG_REPO, "getUserProfile") {
            AppLogger.firestoreRead(Constants.COLLECTION_USERS, "getUser($userId)")
            val result = firestoreService.getUser(userId)
            if (result is Resource.Success) AppCache.setUser(userId, result.data)
            result
        }
    }

    fun observeUserProfile(userId: String): Flow<Resource<User>> {
        AppLogger.firestoreRead(Constants.COLLECTION_USERS, "observeUser (Flow)")
        return firestoreService.observeUser(userId)
    }

    suspend fun updateUserProfile(
        uid: String,
        fields: Map<String, Any?>
    ): Resource<Unit> = safeCall(AppLogger.TAG_REPO, "updateUserProfile") {
        if (uid.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        if (fields.isEmpty()) return@safeCall Resource.Error("No fields to update.")

        (fields["displayName"] as? String)?.let { name ->
            if (name.trim().length < 2)
                return@safeCall Resource.Error("Display name must be at least 2 characters.")
            if (name.trim().length > 30)
                return@safeCall Resource.Error("Display name must be 30 characters or fewer.")
        }
        (fields["bio"] as? String)?.let { bio ->
            if (bio.length > Constants.MAX_BIO_LENGTH)
                return@safeCall Resource.Error("Bio must be ${Constants.MAX_BIO_LENGTH} characters or fewer.")
        }

        AppLogger.firestoreWrite(Constants.COLLECTION_USERS, "updateUser", uid)
        val result = firestoreService.updateUser(uid, fields)
        if (result is Resource.Success) AppCache.invalidateUser(uid)
        result
    }

    suspend fun getUserRecipes(userId: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "getUserRecipes") {
            if (userId.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
            firestoreService.getRecipesByAuthor(userId)
        }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "addFavorite") {
            if (favorite.userId.isBlank()) return@safeCall Resource.Error("User not logged in.")
            if (favorite.recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe.")
            AppLogger.firestoreWrite(
                "users/${favorite.userId}/favorites", "addFavorite", favorite.recipeId
            )
            val result = firestoreService.addFavorite(favorite)
            if (result is Resource.Success) AppCache.invalidateFavorites(favorite.userId)
            result
        }

    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "removeFavorite") {
            AppLogger.firestoreDelete("users/$userId/favorites", recipeId)
            val result = firestoreService.removeFavorite(userId, recipeId)
            if (result is Resource.Success) AppCache.invalidateFavorites(userId)
            result
        }

    suspend fun getFavorites(userId: String): Resource<List<Favorite>> {
        if (userId.isBlank()) return Resource.Error("Invalid user ID.")
        AppCache.getFavorites(userId)?.let {
            AppLogger.repoCacheHit("UserRepository", "getFavorites:$userId")
            return Resource.Success(it)
        }
        AppLogger.repoCacheMiss("UserRepository", "getFavorites:$userId")
        return safeCall(AppLogger.TAG_REPO, "getFavorites") {
            AppLogger.firestoreRead("users/$userId/favorites", "getFavorites")
            val result = firestoreService.getFavorites(userId)
            if (result is Resource.Success) AppCache.setFavorites(userId, result.data)
            result
        }
    }

    suspend fun isFavorited(userId: String, recipeId: String): Boolean =
        firestoreService.isFavorited(userId, recipeId)

    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> {
        AppLogger.firestoreRead("users/$userId/favorites", "observeFavorites (Flow)")
        return firestoreService.observeFavorites(userId)
    }

    companion object {
        @Volatile private var instance: UserRepository? = null
        fun getInstance(): UserRepository =
            instance ?: synchronized(this) {
                instance ?: UserRepository().also { instance = it }
            }
    }
}