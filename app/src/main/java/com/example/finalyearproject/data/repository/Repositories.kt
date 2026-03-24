package com.example.finalyearproject.data.repository

import android.net.Uri
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.data.remote.firebase.StorageService
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton


// ─────────────────────────────────────────────────────────────────────────────
// AuthRepository.kt
//
// Orchestrates registration (Auth + Firestore user doc creation) and login.
// Domain layer only talks to this — never to Firebase directly.
// ─────────────────────────────────────────────────────────────────────────────
@Singleton
class AuthRepository @Inject constructor(
    private val authService: AuthService,
    private val firestoreService: FirestoreService
) {

    val currentUser: FirebaseUser? get() = authService.currentUser
    val currentUserId: String?    get() = authService.currentUserId
    val isLoggedIn: Boolean       get() = authService.isLoggedIn

    /**
     * Full registration flow:
     * 1. Create Firebase Auth account
     * 2. Write user document to Firestore
     *
     * If Firestore write fails we still return success (the user can update
     * their profile later). A Cloud Function can also handle this sync.
     */
    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): Resource<Unit> {
        // Step 1 — Auth
        val authResult = authService.register(email, password)
        if (authResult is Resource.Error) {
            return Resource.Error(authResult.message)
        }

        val uid = authService.currentUserId
            ?: return Resource.Error("Failed to retrieve user ID after registration.")

        // Step 2 — Firestore profile doc
        val user = User(
            uid = uid,
            email = email,
            displayName = displayName
        )
        firestoreService.addUser(user)  // best-effort; don't block registration on this

        return Resource.Success(Unit)
    }

    /**
     * Signs in and returns the authenticated [FirebaseUser] on success.
     */
    suspend fun login(email: String, password: String): Resource<FirebaseUser> {
        val result = authService.login(email, password)
        return when (result) {
            is Resource.Success -> {
                val user = result.data.user
                    ?: return Resource.Error("Login succeeded but no user returned.")
                Resource.Success(user)
            }
            is Resource.Error   -> Resource.Error(result.message)
            is Resource.Loading -> Resource.Loading()
        }
    }

    fun logout() = authService.logout()

    suspend fun sendPasswordReset(email: String): Resource<Unit> =
        authService.sendPasswordResetEmail(email)
}


// ─────────────────────────────────────────────────────────────────────────────
// RecipeRepository.kt
//
// Single repository for all recipe, review, and favorite operations.
// Exposes both suspend functions (one-shot) and Flow (real-time).
// ─────────────────────────────────────────────────────────────────────────────
@Singleton
class RecipeRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val storageService: StorageService
) {

    // ── Recipes ───────────────────────────────────────────────────────────────

    /**
     * Publishes a new recipe. If an image [Uri] is provided, it is uploaded
     * first and the download URL is embedded in the [Recipe] object.
     */
    suspend fun addRecipe(
        recipe: Recipe,
        imageUri: Uri? = null
    ): Resource<String> {
        val finalRecipe = if (imageUri != null) {
            // Temporary ID for storage path — will be replaced by Firestore auto-ID
            val tempId = System.currentTimeMillis().toString()
            when (val upload = storageService.uploadRecipeImage(tempId, imageUri)) {
                is Resource.Success -> recipe.copy(imageUrl = upload.data)
                is Resource.Error   -> return Resource.Error(upload.message)
                else -> recipe
            }
        } else recipe

        return firestoreService.addRecipe(finalRecipe)
    }

    suspend fun getRecipes(limit: Long = 20): Resource<List<Recipe>> =
        firestoreService.getRecipes(limit)

    fun observeRecipes(limit: Long = 20): Flow<Resource<List<Recipe>>> =
        firestoreService.observeRecipes(limit)

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> =
        firestoreService.getRecipeById(recipeId)

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> =
        firestoreService.getRecipesByAuthor(authorId)

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> =
        firestoreService.deleteRecipe(recipeId)

    // ── Reviews ───────────────────────────────────────────────────────────────

    suspend fun addReview(
        review: Review,
        imageUris: List<Uri> = emptyList()
    ): Resource<String> {
        val finalReview = if (imageUris.isNotEmpty()) {
            val tempId = System.currentTimeMillis().toString()
            when (val upload = storageService.uploadReviewImages(tempId, imageUris)) {
                is Resource.Success -> review.copy(imageUrls = upload.data)
                is Resource.Error   -> return Resource.Error(upload.message)
                else -> review
            }
        } else review

        return firestoreService.addReview(finalReview)
    }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> =
        firestoreService.getReviews(recipeId)

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> =
        firestoreService.addFavorite(favorite)

    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> =
        firestoreService.removeFavorite(userId, recipeId)

    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> =
        firestoreService.observeFavorites(userId)
}


// ─────────────────────────────────────────────────────────────────────────────
// UserRepository.kt
//
// Manages user profile reads, updates, and profile image uploads.
// ─────────────────────────────────────────────────────────────────────────────
@Singleton
class UserRepository @Inject constructor(
    private val firestoreService: FirestoreService,
    private val storageService: StorageService,
    private val authService: AuthService
) {

    /**
     * Fetches a user profile document from Firestore.
     */
    suspend fun getUser(uid: String): Resource<User> =
        firestoreService.getUser(uid)

    /**
     * Real-time user profile stream. Ideal for Profile screen.
     */
    fun observeUser(uid: String): Flow<Resource<User>> =
        firestoreService.observeUser(uid)

    /**
     * Updates specific fields of the user's Firestore document.
     * Accepts a map so callers can patch only what changed.
     *
     * Example:
     *   updateUserFields(uid, mapOf("bio" to "I love cooking!"))
     */
    suspend fun updateUserFields(
        uid: String,
        fields: Map<String, Any?>
    ): Resource<Unit> = firestoreService.updateUser(uid, fields)

    /**
     * Full profile update flow:
     * 1. Upload new profile image (if provided)
     * 2. Update Firestore document
     * 3. Update Firebase Auth display name
     */
    suspend fun updateProfile(
        uid: String,
        displayName: String,
        bio: String?,
        imageUri: Uri? = null
    ): Resource<Unit> {
        val fields = mutableMapOf<String, Any?>(
            "displayName" to displayName,
            "bio" to bio
        )

        // Upload profile image if a new one was selected
        if (imageUri != null) {
            when (val upload = storageService.uploadProfileImage(uid, imageUri)) {
                is Resource.Success -> fields["profileImageUrl"] = upload.data
                is Resource.Error   -> return Resource.Error(upload.message)
                else -> Unit
            }
        }

        // Firestore update
        val firestoreResult = firestoreService.updateUser(uid, fields)
        if (firestoreResult is Resource.Error) return firestoreResult

        // Sync Auth display name
        authService.updateDisplayName(displayName)

        return Resource.Success(Unit)
    }

    /**
     * Convenience helper — returns the current user's UID or null.
     */
    fun getCurrentUserId(): String? = authService.currentUserId
}