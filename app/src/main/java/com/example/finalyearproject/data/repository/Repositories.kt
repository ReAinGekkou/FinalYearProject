package com.example.finalyearproject.data.repository

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.PaginatedResult
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.RecipeFilter
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.safeCall
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// AuthRepository
// ─────────────────────────────────────────────────────────────────────────────

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
    ): Resource<Unit> = safeCall {
        val authResult = authService.register(email, password)
        if (authResult is Resource.Error) return@safeCall Resource.Error(authResult.message)

        val uid = authService.currentUserId
            ?: return@safeCall Resource.Error("UID missing after registration.")

        firestoreService.addUser(User(uid = uid, email = email, displayName = displayName))
        Resource.Success(Unit)
    }

    suspend fun login(email: String, password: String): Resource<FirebaseUser> = safeCall {
        when (val result = authService.login(email, password)) {
            is Resource.Success -> Resource.Success(
                result.data.user ?: return@safeCall Resource.Error("No user returned.")
            )
            is Resource.Error   -> Resource.Error(result.message)
            else                -> Resource.Loading()
        }
    }

    fun logout() = authService.logout()

    suspend fun sendPasswordReset(email: String): Resource<Unit> =
        safeCall { authService.sendPasswordResetEmail(email) }

    companion object {
        @Volatile private var instance: AuthRepository? = null
        fun getInstance(): AuthRepository =
            instance ?: synchronized(this) {
                instance ?: AuthRepository().also { instance = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// RecipeRepository  — search + filter + pagination + recommendations
// ─────────────────────────────────────────────────────────────────────────────

class RecipeRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun addRecipe(recipe: Recipe): Resource<String> = safeCall {
        validateRecipe(recipe)?.let { return@safeCall Resource.Error(it) }
        firestoreService.addRecipe(recipe)
    }

    suspend fun getRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Resource<List<Recipe>> =
        safeCall { firestoreService.getRecipes(limit) }

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        firestoreService.observeRecipes(limit)

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> = safeCall {
        if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
        firestoreService.getRecipeById(recipeId)
    }

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> = safeCall {
        if (authorId.isBlank()) return@safeCall Resource.Error("Invalid author ID.")
        firestoreService.getRecipesByAuthor(authorId)
    }

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> = safeCall {
        if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
        firestoreService.updateRecipe(recipeId, fields)
    }

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> = safeCall {
        if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
        firestoreService.deleteRecipe(recipeId)
    }

    // ── SEARCH ────────────────────────────────────────────────────────────────

    /**
     * Case-insensitive prefix search on recipe titles.
     * Minimum 2 characters required to avoid overly broad results.
     */
    suspend fun searchRecipes(query: String): Resource<List<Recipe>> = safeCall {
        if (query.trim().length < 2)
            return@safeCall Resource.Error("Search term must be at least 2 characters.")
        firestoreService.searchRecipesByTitle(query.trim())
    }

    // ── FILTER ────────────────────────────────────────────────────────────────

    suspend fun filterRecipes(filter: RecipeFilter): Resource<List<Recipe>> = safeCall {
        firestoreService.filterRecipes(filter)
    }

    suspend fun filterRecipesByRating(minRating: Double): Resource<List<Recipe>> = safeCall {
        if (minRating < 1.0 || minRating > 5.0)
            return@safeCall Resource.Error("Rating must be between 1 and 5.")
        firestoreService.filterRecipesByRating(minRating)
    }

    suspend fun filterByCategory(category: String): Resource<List<Recipe>> = safeCall {
        if (category.isBlank()) return@safeCall Resource.Error("Category cannot be empty.")
        firestoreService.filterByCategory(category)
    }

    // ── PAGINATION ────────────────────────────────────────────────────────────

    /**
     * Load the first page. Call this when the screen first opens.
     */
    suspend fun loadFirstPage(
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> = safeCall {
        firestoreService.loadRecipesFirstPage(pageSize)
    }

    /**
     * Load the next page. Pass [lastVisible] from the previous result.
     */
    suspend fun loadNextPage(
        lastVisible: DocumentSnapshot,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> = safeCall {
        firestoreService.loadNextRecipesPage(lastVisible, pageSize)
    }

    /**
     * Paginated search — for the search results screen.
     */
    suspend fun searchPaginated(
        query: String,
        lastVisible: DocumentSnapshot? = null
    ): Resource<PaginatedResult<Recipe>> = safeCall {
        if (query.trim().length < 2)
            return@safeCall Resource.Error("Search term must be at least 2 characters.")
        firestoreService.searchRecipesPaginated(query, lastVisible)
    }

    // ── RECOMMENDATIONS ───────────────────────────────────────────────────────

    suspend fun getRecommendedRecipes(userId: String): Resource<List<Recipe>> = safeCall {
        if (userId.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        firestoreService.getRecommendedRecipes(userId)
    }

    suspend fun getTrendingRecipes(limit: Long = 10): Resource<List<Recipe>> = safeCall {
        firestoreService.getTrendingRecipes(limit)
    }

    // ── REVIEWS ───────────────────────────────────────────────────────────────

    suspend fun addReview(review: Review): Resource<String> = safeCall {
        if (review.comment.isBlank()) return@safeCall Resource.Error("Comment cannot be empty.")
        if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
            return@safeCall Resource.Error("Rating must be between 1 and 5.")
        firestoreService.addReview(review)
    }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> = safeCall {
        if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
        firestoreService.getReviews(recipeId)
    }

    suspend fun deleteReview(recipeId: String, reviewId: String): Resource<Unit> = safeCall {
        firestoreService.deleteReview(recipeId, reviewId)
    }

    // ── PRIVATE VALIDATION ────────────────────────────────────────────────────

    /**
     * Returns an error message string if invalid, null if valid.
     */
    private fun validateRecipe(recipe: Recipe): String? = when {
        recipe.title.isBlank()              -> "Recipe title cannot be empty."
        recipe.title.length > Constants.MAX_RECIPE_TITLE_LENGTH ->
            "Title must be ${Constants.MAX_RECIPE_TITLE_LENGTH} characters or fewer."
        recipe.ingredients.isEmpty()        -> "Add at least one ingredient."
        recipe.instructions.isEmpty()       -> "Add at least one instruction step."
        recipe.ingredients.size > Constants.MAX_INGREDIENTS ->
            "Maximum ${Constants.MAX_INGREDIENTS} ingredients allowed."
        recipe.prepTimeMinutes < 0          -> "Prep time cannot be negative."
        recipe.servings < 1                 -> "Servings must be at least 1."
        else -> null
    }

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

class BlogRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {
    suspend fun addBlog(blog: Blog): Resource<String> = safeCall {
        if (blog.title.isBlank()) return@safeCall Resource.Error("Blog title cannot be empty.")
        if (blog.content.isBlank()) return@safeCall Resource.Error("Blog content cannot be empty.")
        firestoreService.addBlog(blog)
    }

    suspend fun getBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Resource<List<Blog>> =
        safeCall { firestoreService.getBlogs(limit) }

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> =
        firestoreService.observeBlogs(limit)

    suspend fun getBlogById(blogId: String): Resource<Blog> = safeCall {
        if (blogId.isBlank()) return@safeCall Resource.Error("Invalid blog ID.")
        firestoreService.getBlogById(blogId)
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> = safeCall {
        if (blogId.isBlank()) return@safeCall Resource.Error("Invalid blog ID.")
        firestoreService.deleteBlog(blogId)
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
// UserRepository  — full profile system
// ─────────────────────────────────────────────────────────────────────────────

class UserRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val authService: AuthService = AuthService.getInstance()
) {
    fun getCurrentUserId(): String? = authService.currentUserId

    // ── Profile ───────────────────────────────────────────────────────────────

    suspend fun getUserProfile(userId: String): Resource<User> = safeCall {
        if (userId.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        firestoreService.getUser(userId)
    }

    fun observeUserProfile(userId: String): Flow<Resource<User>> =
        firestoreService.observeUser(userId)

    /**
     * Updates specific profile fields. Validates display name length.
     */
    suspend fun updateUserProfile(
        uid: String,
        fields: Map<String, Any?>
    ): Resource<Unit> = safeCall {
        if (uid.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        if (fields.isEmpty()) return@safeCall Resource.Error("No fields to update.")

        // Validate displayName if being updated
        (fields["displayName"] as? String)?.let { name ->
            if (name.trim().length < 2)
                return@safeCall Resource.Error("Display name must be at least 2 characters.")
            if (name.trim().length > 30)
                return@safeCall Resource.Error("Display name must be 30 characters or fewer.")
        }

        // Validate bio if being updated
        (fields["bio"] as? String)?.let { bio ->
            if (bio.length > 200)
                return@safeCall Resource.Error("Bio must be 200 characters or fewer.")
        }

        firestoreService.updateUser(uid, fields)
    }

    /**
     * Fetches all recipes authored by [userId].
     */
    suspend fun getUserRecipes(userId: String): Resource<List<Recipe>> = safeCall {
        if (userId.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        firestoreService.getRecipesByAuthor(userId)
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> = safeCall {
        if (favorite.userId.isBlank()) return@safeCall Resource.Error("User not logged in.")
        if (favorite.recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe.")
        firestoreService.addFavorite(favorite)
    }

    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> = safeCall {
        firestoreService.removeFavorite(userId, recipeId)
    }

    suspend fun getFavorites(userId: String): Resource<List<Favorite>> = safeCall {
        if (userId.isBlank()) return@safeCall Resource.Error("Invalid user ID.")
        firestoreService.getFavorites(userId)
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