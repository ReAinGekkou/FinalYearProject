package com.example.finalyearproject.data.repository

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// AuthRepository
// ─────────────────────────────────────────────────────────────────────────────

class AuthRepository(
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
    ): Resource<Unit> {
        val authResult = authService.register(email, password)
        if (authResult is Resource.Error) return Resource.Error(authResult.message)

        val uid = authService.currentUserId
            ?: return Resource.Error("Failed to retrieve user ID.")

        val user = User(uid = uid, email = email, displayName = displayName)
        firestoreService.addUser(user)

        return Resource.Success(Unit)
    }

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

    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// RecipeRepository
// ─────────────────────────────────────────────────────────────────────────────

class RecipeRepository(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {

    // ── Add ───────────────────────────────────────────────────────────────────

    /**
     * Validates before writing to Firestore.
     * Business rule: title, at least 1 ingredient, at least 1 instruction step.
     */
    suspend fun addRecipe(recipe: Recipe): Resource<String> {
        // Validation
        if (recipe.title.isBlank())
            return Resource.Error("Recipe title cannot be empty.")
        if (recipe.title.length > Constants.MAX_RECIPE_TITLE_LENGTH)
            return Resource.Error("Title must be ${Constants.MAX_RECIPE_TITLE_LENGTH} characters or fewer.")
        if (recipe.ingredients.isEmpty())
            return Resource.Error("Add at least one ingredient.")
        if (recipe.instructions.isEmpty())
            return Resource.Error("Add at least one instruction step.")
        if (recipe.ingredients.size > Constants.MAX_INGREDIENTS)
            return Resource.Error("Maximum ${Constants.MAX_INGREDIENTS} ingredients allowed.")

        return firestoreService.addRecipe(recipe)
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    suspend fun getRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Resource<List<Recipe>> =
        firestoreService.getRecipes(limit)

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        firestoreService.observeRecipes(limit)

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return firestoreService.getRecipeById(recipeId)
    }

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> {
        if (authorId.isBlank()) return Resource.Error("Invalid author ID.")
        return firestoreService.getRecipesByAuthor(authorId)
    }

    suspend fun getRecipesByCategory(category: String): Resource<List<Recipe>> {
        if (category.isBlank()) return Resource.Error("Category cannot be empty.")
        return firestoreService.getRecipesByCategory(category)
    }

    // ── Update ────────────────────────────────────────────────────────────────

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return firestoreService.updateRecipe(recipeId, fields)
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return firestoreService.deleteRecipe(recipeId)
    }

    // ── Reviews ───────────────────────────────────────────────────────────────

    /**
     * Validates rating range (1–5) before writing.
     */
    suspend fun addReview(review: Review): Resource<String> {
        if (review.recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        if (review.comment.isBlank()) return Resource.Error("Review comment cannot be empty.")
        if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
            return Resource.Error("Rating must be between 1 and 5.")

        return firestoreService.addReview(review)
    }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return firestoreService.getReviews(recipeId)
    }

    suspend fun deleteReview(recipeId: String, reviewId: String): Resource<Unit> =
        firestoreService.deleteReview(recipeId, reviewId)

    companion object {
        @Volatile private var instance: RecipeRepository? = null
        fun getInstance(): RecipeRepository =
            instance ?: synchronized(this) {
                instance ?: RecipeRepository().also { instance = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BlogRepository
// ─────────────────────────────────────────────────────────────────────────────

class BlogRepository(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {

    suspend fun addBlog(blog: Blog): Resource<String> {
        if (blog.title.isBlank()) return Resource.Error("Blog title cannot be empty.")
        if (blog.content.isBlank()) return Resource.Error("Blog content cannot be empty.")
        if (blog.title.length > 100)
            return Resource.Error("Blog title must be 100 characters or fewer.")
        return firestoreService.addBlog(blog)
    }

    suspend fun getBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Resource<List<Blog>> =
        firestoreService.getBlogs(limit)

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> =
        firestoreService.observeBlogs(limit)

    suspend fun getBlogById(blogId: String): Resource<Blog> {
        if (blogId.isBlank()) return Resource.Error("Invalid blog ID.")
        return firestoreService.getBlogById(blogId)
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> {
        if (blogId.isBlank()) return Resource.Error("Invalid blog ID.")
        return firestoreService.deleteBlog(blogId)
    }

    companion object {
        @Volatile private var instance: BlogRepository? = null
        fun getInstance(): BlogRepository =
            instance ?: synchronized(this) {
                instance ?: BlogRepository().also { instance = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// UserRepository
// ─────────────────────────────────────────────────────────────────────────────

class UserRepository(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val authService: AuthService = AuthService.getInstance()
) {

    fun getCurrentUserId(): String? = authService.currentUserId

    suspend fun getUser(uid: String): Resource<User> {
        if (uid.isBlank()) return Resource.Error("Invalid user ID.")
        return firestoreService.getUser(uid)
    }

    fun observeUser(uid: String): Flow<Resource<User>> =
        firestoreService.observeUser(uid)

    suspend fun updateUserFields(uid: String, fields: Map<String, Any?>): Resource<Unit> {
        if (uid.isBlank()) return Resource.Error("Invalid user ID.")
        if (fields.isEmpty()) return Resource.Error("No fields to update.")
        return firestoreService.updateUser(uid, fields)
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> {
        if (favorite.userId.isBlank()) return Resource.Error("User not logged in.")
        if (favorite.recipeId.isBlank()) return Resource.Error("Invalid recipe.")
        return firestoreService.addFavorite(favorite)
    }

    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> {
        if (userId.isBlank() || recipeId.isBlank()) return Resource.Error("Invalid IDs.")
        return firestoreService.removeFavorite(userId, recipeId)
    }

    suspend fun getFavorites(userId: String): Resource<List<Favorite>> {
        if (userId.isBlank()) return Resource.Error("Invalid user ID.")
        return firestoreService.getFavorites(userId)
    }

    suspend fun isFavorited(userId: String, recipeId: String): Boolean =
        firestoreService.isFavorited(userId, recipeId)

    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> =
        firestoreService.observeFavorites(userId)

    companion object {
        @Volatile private var instance: UserRepository? = null
        fun getInstance(): UserRepository =
            instance ?: synchronized(this) {
                instance ?: UserRepository().also { instance = it }
            }
    }
}