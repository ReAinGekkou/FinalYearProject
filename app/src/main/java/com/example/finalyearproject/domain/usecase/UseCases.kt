package com.example.finalyearproject.domain.usecase

import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.repository.AuthRepository
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.data.repository.UserRepository
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// LoginUseCase
// ─────────────────────────────────────────────────────────────────────────────

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


// ─────────────────────────────────────────────────────────────────────────────
// RegisterUseCase
// ─────────────────────────────────────────────────────────────────────────────

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
        if (name.length < 2)
            return Resource.Error("Display name must be at least 2 characters.")
        if (name.length > 30)
            return Resource.Error("Display name must be 30 characters or fewer.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            return Resource.Error("Please enter a valid email address.")
        if (password.length < 8)
            return Resource.Error("Password must be at least 8 characters.")
        if (password != confirmPassword)
            return Resource.Error("Passwords do not match.")

        return authRepository.register(email.trim(), password, name)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// LogoutUseCase
// ─────────────────────────────────────────────────────────────────────────────

class LogoutUseCase(
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    operator fun invoke() = authRepository.logout()
}


// ─────────────────────────────────────────────────────────────────────────────
// GetRecipesUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetRecipesUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    /** One-shot fetch */
    suspend operator fun invoke(limit: Long = Constants.PAGE_SIZE_RECIPES): Resource<List<Recipe>> =
        recipeRepository.getRecipes(limit)

    /** Real-time stream */
    fun asFlow(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        recipeRepository.observeRecipes(limit)
}


// ─────────────────────────────────────────────────────────────────────────────
// GetRecipeByIdUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetRecipeByIdUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance()
) {
    suspend operator fun invoke(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return recipeRepository.getRecipeById(recipeId)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AddRecipeUseCase
// ─────────────────────────────────────────────────────────────────────────────

class AddRecipeUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<String> {
        // Must be logged in
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to add a recipe.")

        // Attach current user as author
        val recipeWithAuthor = recipe.copy(authorId = uid)

        // Delegate validation + write to repository
        return recipeRepository.addRecipe(recipeWithAuthor)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// DeleteRecipeUseCase
// ─────────────────────────────────────────────────────────────────────────────

class DeleteRecipeUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<Unit> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")

        // Only the author can delete
        if (recipe.authorId != uid)
            return Resource.Error("You can only delete your own recipes.")

        return recipeRepository.deleteRecipe(recipe.recipeId)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// AddReviewUseCase
// ─────────────────────────────────────────────────────────────────────────────

class AddReviewUseCase(
    private val recipeRepository: RecipeRepository = RecipeRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(review: Review): Resource<String> {
        // Must be logged in
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to leave a review.")

        // Validate rating range
        if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
            return Resource.Error("Rating must be between 1 and 5.")

        // Validate comment
        if (review.comment.isBlank())
            return Resource.Error("Review comment cannot be empty.")

        // Attach reviewer ID
        val reviewWithId = review.copy(reviewerId = uid)

        return recipeRepository.addReview(reviewWithId)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// ToggleFavoriteUseCase
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Adds the recipe to favorites if not already saved.
 * Removes it if already saved (toggle behaviour).
 * Returns Resource.Success(true) = added, Resource.Success(false) = removed.
 */
class ToggleFavoriteUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    suspend operator fun invoke(recipe: Recipe): Resource<Boolean> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")

        val alreadyFavorited = userRepository.isFavorited(uid, recipe.recipeId)

        return if (alreadyFavorited) {
            // Remove
            val result = userRepository.removeFavorite(uid, recipe.recipeId)
            when (result) {
                is Resource.Success -> Resource.Success(false)
                is Resource.Error   -> Resource.Error(result.message)
                else                -> Resource.Error("Unknown error.")
            }
        } else {
            // Add — no duplicate possible because Firestore doc ID = recipeId
            val favorite = Favorite(
                userId           = uid,
                recipeId         = recipe.recipeId,
                recipeTitle      = recipe.title,
                recipeImageUrl   = recipe.imageUrl,
                recipeAuthorName = recipe.authorName
            )
            val result = userRepository.addFavorite(favorite)
            when (result) {
                is Resource.Success -> Resource.Success(true)
                is Resource.Error   -> Resource.Error(result.message)
                else                -> Resource.Error("Unknown error.")
            }
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// GetFavoritesUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetFavoritesUseCase(
    private val userRepository: UserRepository = UserRepository.getInstance(),
    private val authRepository: AuthRepository = AuthRepository.getInstance()
) {
    /** One-shot fetch */
    suspend operator fun invoke(): Resource<List<Favorite>> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in.")
        return userRepository.getFavorites(uid)
    }

    /** Real-time stream */
    fun asFlow(): Flow<Resource<List<Favorite>>> {
        val uid = authRepository.currentUserId ?: return kotlinx.coroutines.flow.flow {
            emit(Resource.Error("You must be logged in."))
        }
        return userRepository.observeFavorites(uid)
    }
}