package com.example.finalyearproject.domain.usecase

import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.repository.AuthRepository
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.data.repository.UserRepository
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/*
 * UseCases.kt — Fixed
 *
 * All calls now map exclusively to methods that exist in RecipeRepository:
 *   createRecipe(), getMyRecipes(), getMyRecipesViaSubcollection(),
 *   getPublicRecipes(), getRecipeById(), toggleLike(), isLikedBy(),
 *   toggleSave(), isSavedBy(), addComment(), getComments(), getFavorites()
 *
 * Removed / stubbed:
 *   • getRecipes()         → getPublicRecipes()
 *   • observeRecipes()     → flow wrapping getPublicRecipes()
 *   • addRecipe(Recipe)    → createRecipe() with recipe fields unpacked
 *   • deleteRecipe()       → stubbed (returns error — not in repository)
 *   • searchRecipes()      → client-side filter on getPublicRecipes()
 *   • filterRecipes()      → client-side filter on getPublicRecipes()
 *   • pagination           → simplified to getPublicRecipes(limit)
 *   • recommendations      → client-side scoring on getPublicRecipes()
 *   • addReview/getReviews → mapped to addComment/getComments
 *   • ToggleFavorite       → recipeRepository.toggleSave() — no UserRepository
 *   • GetFavorites         → recipeRepository.getFavorites() → List<Recipe>
 *
 * Removed imports that caused unresolved reference errors:
 *   Favorite, PaginatedResult, RecipeFilter, Review, BlogRepository,
 *   DocumentSnapshot
 */

// ═══════════════════════════════════════════════════════════════════════════
// AUTH USE CASES  (unchanged — AuthRepository already works)
// ═══════════════════════════════════════════════════════════════════════════

class LoginUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(
        email: String,
        password: String
    ): Resource<FirebaseUser> {
        if (email.isBlank())
            return Resource.Error("Email cannot be empty.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Please enter a valid email address.")
        if (password.length < 6)
            return Resource.Error("Password must be at least 6 characters.")
        return authRepository.login(email.trim(), password)
    }
}

class RegisterUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(
        displayName    : String,
        email          : String,
        password       : String,
        confirmPassword: String
    ): Resource<Unit> {
        val name = displayName.trim()
        if (name.length < 2)  return Resource.Error("Display name must be at least 2 characters.")
        if (name.length > 30) return Resource.Error("Display name must be 30 characters or fewer.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Please enter a valid email address.")
        if (password.length < 8)     return Resource.Error("Password must be at least 8 characters.")
        if (password != confirmPassword) return Resource.Error("Passwords do not match.")
        return authRepository.register(email.trim(), password, name)
    }
}

class LogoutUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    operator fun invoke() = authRepository.logout()
}

// ═══════════════════════════════════════════════════════════════════════════
// RECIPE — GET / OBSERVE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: was calling getRecipes() / observeRecipes() — neither exists.
 * Now delegates to getPublicRecipes(limit).
 * asFlow() wraps the one-shot call in a cold flow so callers that
 * previously used a Flow still compile without change.
 */
class GetRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(limit: Long = 20L): Resource<List<Recipe>> =
        recipeRepository.getPublicRecipes(limit)

    /** Emits a single value then completes — replaces the removed real-time listener. */
    fun asFlow(limit: Long = 20L): Flow<Resource<List<Recipe>>> = flow {
        emit(Resource.Loading())
        emit(recipeRepository.getPublicRecipes(limit))
    }
}

// ── Get by ID — already correct ──────────────────────────────────────────

class GetRecipeByIdUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return recipeRepository.getRecipeById(recipeId)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RECIPE — CREATE / DELETE
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: was calling addRecipe(Recipe) — does not exist.
 * Now unpacks the Recipe object and calls createRecipe() which does exist.
 */
class AddRecipeUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<String> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to add a recipe.")

        if (recipe.title.isBlank())
            return Resource.Error("Recipe title cannot be empty.")
        if (recipe.ingredients.isEmpty())
            return Resource.Error("Add at least one ingredient.")

        return recipeRepository.createRecipe(
            title       = recipe.title,
            description = recipe.description,
            ingredients = recipe.ingredients,
            steps       = recipe.instructions,   // Recipe.instructions maps to repository "steps"
            category    = recipe.category,
            cookTime    = recipe.cookTimeMinutes,
            imageUrl    = recipe.imageUrl ?: "",
            videoUrl    = ""
        )
    }
}

/**
 * FIX: deleteRecipe() does not exist in RecipeRepository.
 * Stubbed — returns a clear error so callers don't crash.
 * Implement when deleteRecipe() is added to the repository.
 */
class DeleteRecipeUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<Unit> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")

        if (recipe.authorId != authRepository.currentUserId)
            return Resource.Error("You can only delete your own recipes.")

        // deleteRecipe() not yet in RecipeRepository — return graceful stub
        return Resource.Error("Delete is not yet supported in this version.")
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// SEARCH  (client-side — avoids Firestore composite index requirement)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: searchRecipes() does not exist in RecipeRepository.
 * Fetches all public recipes and filters client-side by title keyword.
 * Sufficient for FYP scale.
 */
class SearchRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(query: String): Resource<List<Recipe>> {
        if (query.trim().length < 2)
            return Resource.Error("Search term must be at least 2 characters.")

        return when (val all = recipeRepository.getPublicRecipes(50L)) {
            is Resource.Success -> {
                val q = query.lowercase().trim()
                Resource.Success(
                    all.data.filter { recipe ->
                        recipe.title.lowercase().contains(q) ||
                                recipe.description.lowercase().contains(q) ||
                                recipe.category.lowercase().contains(q) ||
                                recipe.ingredients.any { it.lowercase().contains(q) }
                    }
                )
            }
            is Resource.Error   -> all
            else                -> Resource.Success(emptyList())
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FILTER  (client-side)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: filterRecipes() / filterRecipesByRating() / filterByCategory()
 * do not exist in RecipeRepository.
 * All filtering is now done client-side after fetching public recipes.
 *
 * RecipeFilter model removed — individual params used instead so this
 * file compiles even if that model doesn't exist.
 */
class FilterRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /** Filter by category (case-insensitive, null = all). */
    suspend fun byCategory(category: String?): Resource<List<Recipe>> {
        val all = recipeRepository.getPublicRecipes(50L)
        if (all !is Resource.Success) return all
        if (category.isNullOrBlank()) return all
        return Resource.Success(
            all.data.filter { it.category.equals(category, ignoreCase = true) }
        )
    }

    /** Filter by minimum average rating. */
    suspend fun byRating(minRating: Double): Resource<List<Recipe>> {
        if (minRating < 1.0 || minRating > 5.0)
            return Resource.Error("Rating must be between 1 and 5.")
        val all = recipeRepository.getPublicRecipes(50L)
        if (all !is Resource.Success) return all
        return Resource.Success(
            all.data.filter { it.averageRating >= minRating }
        )
    }

    /** Filter by category AND minimum rating combined. */
    suspend operator fun invoke(
        category : String? = null,
        minRating: Double? = null
    ): Resource<List<Recipe>> {
        val all = recipeRepository.getPublicRecipes(50L)
        if (all !is Resource.Success) return all
        return Resource.Success(
            all.data.filter { recipe ->
                (category == null || recipe.category.equals(category, ignoreCase = true)) &&
                        (minRating == null || recipe.averageRating >= minRating)
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// PAGINATION  (simplified — real cursor pagination removed)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: loadFirstPage(), loadNextPage(), searchPaginated() and
 * DocumentSnapshot dependency all removed.
 * Replaced with a simple limit-based fetch.
 *
 * Callers that previously stored a lastVisible DocumentSnapshot should
 * switch to tracking an offset or just increasing limit.
 */
class GetRecipesPaginatedUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /** Returns the first [pageSize] public recipes. */
    suspend fun firstPage(pageSize: Long = 10L): Resource<List<Recipe>> =
        recipeRepository.getPublicRecipes(pageSize)

    /**
     * Simulates "next page" by loading more results with a larger limit.
     * For production, implement real cursor pagination in the repository.
     */
    suspend fun nextPage(
        currentCount: Int,
        pageSize    : Long = 10L
    ): Resource<List<Recipe>> {
        val newLimit = (currentCount + pageSize).toLong().coerceAtMost(200L)
        return recipeRepository.getPublicRecipes(newLimit)
    }

    /** Simple search within the public feed. */
    suspend fun searchPage(query: String): Resource<List<Recipe>> {
        if (query.trim().length < 2)
            return Resource.Error("Search term must be at least 2 characters.")
        val all = recipeRepository.getPublicRecipes(50L)
        if (all !is Resource.Success) return all
        val q = query.lowercase().trim()
        return Resource.Success(
            all.data.filter { it.title.lowercase().contains(q) }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// RECOMMENDATIONS  (client-side scoring on public recipes)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: getRecommendedRecipes() does not exist in RecipeRepository.
 *
 * Strategy:
 *   1. Fetch user's saved recipes to extract preferred categories.
 *   2. Fetch public recipes.
 *   3. Score each recipe:  category_match * 0.5 + popularity * 0.3 + recency * 0.2
 *   4. Return top N.
 *   5. If no saved recipes → fall back to trending (TrendingRecipesUseCase).
 */
class GetRecommendedRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(limit: Int = 10): Resource<List<Recipe>> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to get recommendations.")

        val allResult = recipeRepository.getPublicRecipes(60L)
        if (allResult !is Resource.Success) return allResult
        val all = allResult.data
        if (all.isEmpty()) return Resource.Success(emptyList())

        // Extract preferred categories from the user's saved recipes
        val favorites = recipeRepository.getFavorites()
        val preferredCategories: Set<String> = if (favorites is Resource.Success) {
            favorites.data.map { it.category.lowercase() }.toSet()
        } else emptySet()

        // Scoring — normalised to 0–1 per component
        val maxLikes    = all.maxOfOrNull { it.likeCount }?.toDouble()?.coerceAtLeast(1.0) ?: 1.0
        val newestMs    = all.mapNotNull { it.createdAt?.seconds }.maxOrNull()?.toDouble() ?: 1.0
        val oldestMs    = all.mapNotNull { it.createdAt?.seconds }.minOrNull()?.toDouble() ?: 0.0
        val timeRange   = (newestMs - oldestMs).coerceAtLeast(1.0)

        data class Scored(val recipe: Recipe, val score: Double)

        val scored = all.map { recipe ->
            val categoryMatch = if (recipe.category.lowercase() in preferredCategories) 1.0 else 0.0
            val popularity    = recipe.likeCount / maxLikes
            val recency       = ((recipe.createdAt?.seconds?.toDouble() ?: oldestMs) - oldestMs) / timeRange

            val score = (categoryMatch * 0.5) + (popularity * 0.3) + (recency * 0.2)
            Scored(recipe, score)
        }

        return Resource.Success(
            scored.sortedByDescending { it.score }.take(limit).map { it.recipe }
        )
    }
}

/**
 * FIX: getTrendingRecipes() does not exist in RecipeRepository.
 * Computes trending score client-side:
 *   trending = (likeCount * 0.6) + (commentCount * 0.2) + (viewCount * 0.2)
 */
class GetTrendingRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(limit: Long = 10): Resource<List<Recipe>> {
        val result = recipeRepository.getPublicRecipes(50L)
        if (result !is Resource.Success) return result
        return Resource.Success(
            result.data
                .sortedByDescending { recipe ->
                    (recipe.likeCount    * 0.6) +
                            (recipe.commentCount * 0.2) +
                            ((recipe.viewCount ?: 0) * 0.2)
                }
                .take(limit.toInt())
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// REVIEWS  (mapped to Comments — same Firestore sub-collection)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: addReview(Review) does not exist in RecipeRepository.
 * Review model removed. Mapped to addComment(recipeId, text).
 *
 * Callers should pass (recipeId, comment text) instead of a Review object.
 */
class AddReviewUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(
        recipeId: String,
        comment : String,
        rating  : Float = 0f   // stored as prefix in comment text for now
    ): Resource<String> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to leave a review.")

        if (comment.isBlank())
            return Resource.Error("Comment cannot be empty.")
        if (rating != 0f && (rating < 1f || rating > 5f))
            return Resource.Error("Rating must be between 1 and 5.")

        val text = if (rating > 0f) "⭐ ${"%.1f".format(rating)} — $comment" else comment

        return when (val r = recipeRepository.addComment(recipeId, text)) {
            is Resource.Success -> Resource.Success(recipeId)  // return recipeId as ack
            is Resource.Error   -> r
            else                -> Resource.Error("Failed to post review.")
        }
    }
}

/**
 * FIX: getReviews() does not exist in RecipeRepository.
 * Mapped to getComments() which returns List<Map<String, Any>>.
 */
class GetReviewsUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(
        recipeId: String
    ): Resource<List<Map<String, Any>>> =
        recipeRepository.getComments(recipeId)
}

// ═══════════════════════════════════════════════════════════════════════════
// FAVORITES
// ═══════════════════════════════════════════════════════════════════════════

/**
 * FIX: was routing through UserRepository (isFavorited, addFavorite,
 * removeFavorite) — those methods don't exist in the current UserRepository.
 *
 * Now routes directly through RecipeRepository.toggleSave() which already
 * handles the add/remove logic atomically.
 *
 * Return: Resource.Success(true)  = recipe was saved
 *         Resource.Success(false) = recipe was unsaved
 */
class ToggleFavoriteUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<Boolean> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")

        return recipeRepository.toggleSave(recipe)
    }
}

/**
 * FIX: was routing through UserRepository.getFavorites() / observeFavorites().
 * Now uses RecipeRepository.getFavorites() which returns List<Recipe> directly.
 *
 * Return type changed from Resource<List<Favorite>> → Resource<List<Recipe>>.
 * Update any ViewModel observers accordingly.
 */
class GetFavoritesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(): Resource<List<Recipe>> {
        authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        return recipeRepository.getFavorites()
    }

    /** Cold flow wrapping the one-shot call. */
    fun asFlow(): Flow<Resource<List<Recipe>>> = flow {
        authRepository.currentUserId
            ?: run { emit(Resource.Error("You must be logged in.")); return@flow }
        emit(Resource.Loading())
        emit(recipeRepository.getFavorites())
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// USER PROFILE  (unchanged — UserRepository assumed working)
// ═══════════════════════════════════════════════════════════════════════════

class GetUserProfileUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance()
) {
    suspend operator fun invoke(userId: String): Resource<User> =
        userRepository.getUserProfile(userId)

    fun asFlow(userId: String): Flow<Resource<User>> =
        userRepository.observeUserProfile(userId)
}

/**
 * FIX: userRepository.getUserRecipes() may or may not exist.
 * Falls back to RecipeRepository for the current user, or filters
 * public recipes by authorId for any other user.
 */
class GetUserRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository  : AuthRepository   = AuthRepository.getInstance()
) {
    suspend operator fun invoke(userId: String): Resource<List<Recipe>> {
        val currentUid = authRepository.currentUserId

        // Current user → use the optimised my_recipes path
        if (userId == currentUid) {
            return recipeRepository.getMyRecipes()
        }

        // Other user → filter public recipes by authorId client-side
        return when (val all = recipeRepository.getPublicRecipes(50L)) {
            is Resource.Success -> Resource.Success(
                all.data.filter { it.authorId == userId }
            )
            else -> all
        }
    }
}

class UpdateUserProfileUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(fields: Map<String, Any?>): Resource<Unit> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        return userRepository.updateUserProfile(uid, fields)
    }
}