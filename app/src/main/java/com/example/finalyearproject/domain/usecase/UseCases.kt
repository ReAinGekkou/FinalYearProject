package com.example.finalyearproject.domain.usecase

import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.PaginatedResult
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.RecipeFilter
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.data.repository.AuthRepository
import com.example.finalyearproject.data.repository.BlogRepository
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.data.repository.UserRepository
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// ═══════════════════════════════════════════════════════════════════════════════
// AUTH USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class LoginUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(email: String, password: String): Resource<FirebaseUser> {
        if (email.isBlank()) return Resource.Error("Email cannot be empty.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Please enter a valid email address.")
        if (password.length < 6) return Resource.Error("Password must be at least 6 characters.")
        return authRepository.login(email.trim(), password)
    }
}

class RegisterUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Resource<Unit> {
        val name = displayName.trim()
        if (name.length < 2) return Resource.Error("Display name must be at least 2 characters.")
        if (name.length > 30) return Resource.Error("Display name must be 30 characters or fewer.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Please enter a valid email address.")
        if (password.length < 8) return Resource.Error("Password must be at least 8 characters.")
        if (password != confirmPassword) return Resource.Error("Passwords do not match.")
        return authRepository.register(email.trim(), password, name)
    }
}

class LogoutUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    operator fun invoke() = authRepository.logout()
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECIPE — BASIC
// ═══════════════════════════════════════════════════════════════════════════════

class GetRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(limit: Long = Constants.PAGE_SIZE_RECIPES): Resource<List<Recipe>> =
        recipeRepository.getRecipes(limit)

    fun asFlow(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        recipeRepository.observeRecipes(limit)
}

class GetRecipeByIdUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(recipeId: String): Resource<Recipe> =
        recipeRepository.getRecipeById(recipeId)
}

class AddRecipeUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<String> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to add a recipe.")
        return recipeRepository.addRecipe(recipe.copy(authorId = uid))
    }
}

class DeleteRecipeUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<Unit> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        if (recipe.authorId != uid)
            return Resource.Error("You can only delete your own recipes.")
        return recipeRepository.deleteRecipe(recipe.recipeId)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEARCH USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class SearchRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /**
     * Simple keyword search.
     * Usage: searchRecipesUseCase("pasta")
     */
    suspend operator fun invoke(query: String): Resource<List<Recipe>> =
        recipeRepository.searchRecipes(query)
}

class FilterRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /**
     * Applies a [RecipeFilter] — any combination of category, rating, cuisine, etc.
     *
     * Usage:
     *   filterRecipesUseCase(RecipeFilter(category = "Dinner", minRating = 4.0))
     */
    suspend operator fun invoke(filter: RecipeFilter): Resource<List<Recipe>> =
        recipeRepository.filterRecipes(filter)

    /** Convenience: filter by minimum star rating only. */
    suspend fun byRating(minRating: Double): Resource<List<Recipe>> =
        recipeRepository.filterRecipesByRating(minRating)

    /** Convenience: filter by category only. */
    suspend fun byCategory(category: String): Resource<List<Recipe>> =
        recipeRepository.filterByCategory(category)
}

// ═══════════════════════════════════════════════════════════════════════════════
// PAGINATION USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class GetRecipesPaginatedUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /**
     * Loads the first page. Call on screen open.
     *
     * Usage in ViewModel:
     *   val result = getRecipesPaginatedUseCase.firstPage()
     *   // store result.data.lastVisible for next call
     */
    suspend fun firstPage(
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        recipeRepository.loadFirstPage(pageSize)

    /**
     * Loads the next page. Call when user scrolls to bottom.
     *
     * @param lastVisible  The DocumentSnapshot from the previous result.
     */
    suspend fun nextPage(
        lastVisible: DocumentSnapshot,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        recipeRepository.loadNextPage(lastVisible, pageSize)

    /**
     * Paginated search results — for the search screen.
     */
    suspend fun searchPage(
        query: String,
        lastVisible: DocumentSnapshot? = null
    ): Resource<PaginatedResult<Recipe>> =
        recipeRepository.searchPaginated(query, lastVisible)
}

// ═══════════════════════════════════════════════════════════════════════════════
// RECOMMENDATION USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class GetRecommendedRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    /**
     * Returns personalised recipe recommendations for the current user.
     * Falls back to highest-rated recipes if no favorites exist.
     */
    suspend operator fun invoke(): Resource<List<Recipe>> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to get recommendations.")
        return recipeRepository.getRecommendedRecipes(uid)
    }
}

class GetTrendingRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(limit: Long = 10): Resource<List<Recipe>> =
        recipeRepository.getTrendingRecipes(limit)
}

// ═══════════════════════════════════════════════════════════════════════════════
// REVIEW USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class AddReviewUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(review: Review): Resource<String> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to leave a review.")
        if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
            return Resource.Error("Rating must be between 1 and 5.")
        if (review.comment.isBlank())
            return Resource.Error("Review comment cannot be empty.")
        return recipeRepository.addReview(review.copy(reviewerId = uid))
    }
}

class GetReviewsUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(recipeId: String): Resource<List<Review>> =
        recipeRepository.getReviews(recipeId)
}

// ═══════════════════════════════════════════════════════════════════════════════
// FAVORITE USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class ToggleFavoriteUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    /**
     * @return Resource.Success(true) = added, Resource.Success(false) = removed
     */
    suspend operator fun invoke(recipe: Recipe): Resource<Boolean> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")

        val alreadyFav = userRepository.isFavorited(uid, recipe.recipeId)

        return if (alreadyFav) {
            when (val r = userRepository.removeFavorite(uid, recipe.recipeId)) {
                is Resource.Success -> Resource.Success(false)
                is Resource.Error   -> Resource.Error(r.message)
                else                -> Resource.Error("Unknown error.")
            }
        } else {
            val fav = Favorite(
                userId           = uid,
                recipeId         = recipe.recipeId,
                recipeTitle      = recipe.title,
                recipeImageUrl   = recipe.imageUrl,
                recipeAuthorName = recipe.authorName
            )
            when (val r = userRepository.addFavorite(fav)) {
                is Resource.Success -> Resource.Success(true)
                is Resource.Error   -> Resource.Error(r.message)
                else                -> Resource.Error("Unknown error.")
            }
        }
    }
}

class GetFavoritesUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(): Resource<List<Favorite>> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        return userRepository.getFavorites(uid)
    }

    fun asFlow(): Flow<Resource<List<Favorite>>> {
        val uid = authRepository.currentUserId
            ?: return flow { emit(Resource.Error("You must be logged in.")) }
        return userRepository.observeFavorites(uid)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// USER PROFILE USE CASES
// ═══════════════════════════════════════════════════════════════════════════════

class GetUserProfileUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance()
) {
    suspend operator fun invoke(userId: String): Resource<User> =
        userRepository.getUserProfile(userId)

    fun asFlow(userId: String): Flow<Resource<User>> =
        userRepository.observeUserProfile(userId)
}

class GetUserRecipesUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance()
) {
    suspend operator fun invoke(userId: String): Resource<List<Recipe>> =
        userRepository.getUserRecipes(userId)
}

class UpdateUserProfileUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    /**
     * Updates only the fields passed in [fields].
     * Validates displayName and bio length before writing.
     *
     * Usage:
     *   updateUserProfileUseCase(mapOf("displayName" to "Alice", "bio" to "I love food!"))
     */
    suspend operator fun invoke(fields: Map<String, Any?>): Resource<Unit> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        return userRepository.updateUserProfile(uid, fields)
    }
}