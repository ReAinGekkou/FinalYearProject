package data.repository

import android.content.Context
import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.PaginatedResult
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.RecipeFilter
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.remote.firebase.AuthService
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.AppCache
import com.example.finalyearproject.utils.AppLogger
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.OfflineManager
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.networkAwareCall
import com.example.finalyearproject.utils.retryCall
import com.example.finalyearproject.utils.safeCall
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// AuthRepository — Final
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
    ): Resource<Unit> = safeCall(AppLogger.TAG_AUTH, "register") {
        AppLogger.authEvent("register", email)
        val authResult = authService.register(email, password)
        if (authResult is Resource.Error) return@safeCall Resource.Error(authResult.message)

        val uid = authService.currentUserId
            ?: return@safeCall Resource.Error("UID missing after registration.")

        // Persist user ID for offline awareness
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


// ─────────────────────────────────────────────────────────────────────────────
// RecipeRepository — Final (cache + offline + logging)
// ─────────────────────────────────────────────────────────────────────────────

class RecipeRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val context: Context? = null
) {
    private val isOnline: Boolean
        get() = context?.let {
            val cm = it.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } ?: true  // assume online if no context

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun addRecipe(recipe: Recipe): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addRecipe") {
            validateRecipe(recipe)?.let { return@safeCall Resource.Error(it) }
            AppLogger.firestoreWrite(Constants.COLLECTION_RECIPES, "addRecipe")
            val result = firestoreService.addRecipe(recipe)
            if (result is Resource.Success) {
                AppCache.invalidateRecipes() // new recipe → invalidate feed cache
            }
            result
        }

    suspend fun getRecipes(
        limit: Long = Constants.PAGE_SIZE_RECIPES
    ): Resource<List<Recipe>> = networkAwareCall(
        isOnline = isOnline,
        cacheBlock = {
            AppCache.getRecipes()?.let {
                AppLogger.repoCacheHit("RecipeRepository", "getRecipes")
                Resource.Success(it)
            }
        },
        networkBlock = {
            AppLogger.repoCacheMiss("RecipeRepository", "getRecipes")
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipes(limit=$limit)")
            val result = firestoreService.getRecipes(limit)
            if (result is Resource.Success) {
                AppCache.setRecipes(result.data)
                OfflineManager.persistRecipes(result.data)
                OfflineManager.markOnline()
            }
            result
        }
    )

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> {
        AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "observeRecipes (Flow)")
        return firestoreService.observeRecipes(limit)
    }

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")

        return networkAwareCall(
            isOnline = isOnline,
            cacheBlock = {
                AppCache.getRecipeById(recipeId)?.let {
                    AppLogger.repoCacheHit("RecipeRepository", "getRecipeById:$recipeId")
                    Resource.Success(it)
                }
            },
            networkBlock = {
                AppLogger.repoCacheMiss("RecipeRepository", "getRecipeById:$recipeId")
                AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipeById($recipeId)")
                val result = firestoreService.getRecipeById(recipeId)
                if (result is Resource.Success) {
                    AppCache.setRecipeById(recipeId, result.data)
                }
                result
            }
        )
    }

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> {
        if (authorId.isBlank()) return Resource.Error("Invalid author ID.")

        return networkAwareCall(
            isOnline = isOnline,
            cacheBlock = {
                AppCache.getRecipesByAuthor(authorId)?.let {
                    AppLogger.repoCacheHit("RecipeRepository", "getRecipesByAuthor:$authorId")
                    Resource.Success(it)
                }
            },
            networkBlock = {
                AppLogger.repoCacheMiss("RecipeRepository", "getRecipesByAuthor:$authorId")
                AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipesByAuthor($authorId)")
                val result = firestoreService.getRecipesByAuthor(authorId)
                if (result is Resource.Success) {
                    AppCache.setRecipesByAuthor(authorId, result.data)
                }
                result
            }
        )
    }

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "updateRecipe") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreWrite(Constants.COLLECTION_RECIPES, "updateRecipe", recipeId)
            val result = firestoreService.updateRecipe(recipeId, fields)
            if (result is Resource.Success) AppCache.invalidateRecipeById(recipeId)
            result
        }

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteRecipe") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreDelete(Constants.COLLECTION_RECIPES, recipeId)
            val result = firestoreService.deleteRecipe(recipeId)
            if (result is Resource.Success) AppCache.invalidateRecipeById(recipeId)
            result
        }

    // ── Search & Filter ───────────────────────────────────────────────────────

    suspend fun searchRecipes(query: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "searchRecipes") {
            if (query.trim().length < 2)
                return@safeCall Resource.Error("Search term must be at least 2 characters.")
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "search(query=$query)")
            firestoreService.searchRecipesByTitle(query.trim())
        }

    suspend fun filterRecipes(filter: RecipeFilter): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterRecipes") {
            AppLogger.d(AppLogger.TAG_REPO, "filterRecipes: $filter")
            firestoreService.filterRecipes(filter)
        }

    suspend fun filterRecipesByRating(minRating: Double): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterRecipesByRating") {
            if (minRating < 1.0 || minRating > 5.0)
                return@safeCall Resource.Error("Rating must be between 1 and 5.")
            firestoreService.filterRecipesByRating(minRating)
        }

    suspend fun filterByCategory(category: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterByCategory") {
            if (category.isBlank()) return@safeCall Resource.Error("Category cannot be empty.")
            firestoreService.filterByCategory(category)
        }

    // ── Pagination ────────────────────────────────────────────────────────────

    suspend fun loadFirstPage(
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "loadFirstPage") {
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "loadFirstPage(size=$pageSize)")
            firestoreService.loadRecipesFirstPage(pageSize)
        }

    suspend fun loadNextPage(
        lastVisible: DocumentSnapshot,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "loadNextPage") {
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "loadNextPage")
            firestoreService.loadNextRecipesPage(lastVisible, pageSize)
        }

    suspend fun searchPaginated(
        query: String,
        lastVisible: DocumentSnapshot? = null
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "searchPaginated") {
            if (query.trim().length < 2)
                return@safeCall Resource.Error("Search term must be at least 2 characters.")
            firestoreService.searchRecipesPaginated(query, lastVisible)
        }

    // ── Recommendations ───────────────────────────────────────────────────────

    suspend fun getRecommendedRecipes(userId: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "getRecommendedRecipes") {
            AppLogger.d(AppLogger.TAG_REPO, "Building recommendations for userId=$userId")
            firestoreService.getRecommendedRecipes(userId)
        }

    suspend fun getTrendingRecipes(limit: Long = 10): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "getTrendingRecipes") {
            firestoreService.getTrendingRecipes(limit)
        }

    // ── Reviews ───────────────────────────────────────────────────────────────

    suspend fun addReview(review: Review): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addReview") {
            if (review.comment.isBlank()) return@safeCall Resource.Error("Comment cannot be empty.")
            if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
                return@safeCall Resource.Error("Rating must be between 1 and 5.")
            AppLogger.firestoreWrite("recipes/${review.recipeId}/reviews", "addReview")
            firestoreService.addReview(review)
        }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> =
        safeCall(AppLogger.TAG_REPO, "getReviews") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreRead("recipes/$recipeId/reviews", "getReviews")
            firestoreService.getReviews(recipeId)
        }

    suspend fun deleteReview(recipeId: String, reviewId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteReview") {
            firestoreService.deleteReview(recipeId, reviewId)
        }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateRecipe(recipe: Recipe): String? = when {
        recipe.title.isBlank()
            -> "Recipe title cannot be empty."
        recipe.title.length > Constants.MAX_RECIPE_TITLE_LENGTH
            -> "Title must be ${Constants.MAX_RECIPE_TITLE_LENGTH} characters or fewer."
        recipe.ingredients.isEmpty()
            -> "Add at least one ingredient."
        recipe.instructions.isEmpty()
            -> "Add at least one instruction step."
        recipe.ingredients.size > Constants.MAX_INGREDIENTS
            -> "Maximum ${Constants.MAX_INGREDIENTS} ingredients allowed."
        recipe.prepTimeMinutes < 0
            -> "Prep time cannot be negative."
        recipe.servings < 1
            -> "Servings must be at least 1."
        else -> null
    }

    companion object {
        @Volatile private var instance: RecipeRepository? = null
        fun getInstance(context: Context? = null): RecipeRepository =
            instance ?: synchronized(this) {
                instance ?: RecipeRepository(context = context).also { instance = it }
            }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// BlogRepository — Final
// ─────────────────────────────────────────────────────────────────────────────

class BlogRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {
    suspend fun addBlog(blog: Blog): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addBlog") {
            if (blog.title.isBlank()) return@safeCall Resource.Error("Blog title cannot be empty.")
            if (blog.content.isBlank()) return@safeCall Resource.Error("Content cannot be empty.")
            AppLogger.firestoreWrite(Constants.COLLECTION_BLOGS, "addBlog")
            val result = firestoreService.addBlog(blog)
            if (result is Resource.Success) AppCache.invalidateBlogs()
            result
        }

    suspend fun getBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Resource<List<Blog>> {
        AppCache.getBlogs()?.let {
            AppLogger.repoCacheHit("BlogRepository", "getBlogs")
            return Resource.Success(it)
        }
        AppLogger.repoCacheMiss("BlogRepository", "getBlogs")
        AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "getBlogs")
        return safeCall(AppLogger.TAG_REPO, "getBlogs") {
            val result = firestoreService.getBlogs(limit)
            if (result is Resource.Success) AppCache.setBlogs(result.data)
            result
        }
    }

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> {
        AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "observeBlogs (Flow)")
        return firestoreService.observeBlogs(limit)
    }

    suspend fun getBlogById(blogId: String): Resource<Blog> {
        if (blogId.isBlank()) return Resource.Error("Invalid blog ID.")
        AppCache.getBlogById(blogId)?.let {
            AppLogger.repoCacheHit("BlogRepository", "getBlogById:$blogId")
            return Resource.Success(it)
        }
        return safeCall(AppLogger.TAG_REPO, "getBlogById") {
            AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "getBlogById($blogId)")
            val result = firestoreService.getBlogById(blogId)
            if (result is Resource.Success) AppCache.setBlogById(blogId, result.data)
            result
        }
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteBlog") {
            if (blogId.isBlank()) return@safeCall Resource.Error("Invalid blog ID.")
            AppLogger.firestoreDelete(Constants.COLLECTION_BLOGS, blogId)
            val result = firestoreService.deleteBlog(blogId)
            if (result is Resource.Success) AppCache.invalidateBlogs()
            result
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
// UserRepository — Final
// ─────────────────────────────────────────────────────────────────────────────

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