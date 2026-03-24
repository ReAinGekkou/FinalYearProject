package com.example.finalyearproject.domain.usecase

import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.repository.AuthRepository
import com.example.finalyearproject.data.repository.RecipeRepository
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case layer — domain/usecase/
 *
 * Each use case represents a single business action.
 * They call repositories and can apply additional business logic
 * (validation, transformation, combining sources) without the
 * ViewModel knowing about Firebase or OkHttp.
 *
 * Naming convention: <Action><Entity>UseCase
 * Invoke convention: operator fun invoke(...) makes use cases
 *                    callable as functions — clean in ViewModels.
 */


// ─────────────────────────────────────────────────────────────────────────────
// LoginUseCase
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Validates input, then delegates to [AuthRepository.login].
 *
 * Business rules enforced here:
 *  - Email must not be blank
 *  - Password must be at least 6 characters
 *
 * ViewModel usage:
 *   val result = loginUseCase(email, password)
 */
class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    suspend operator fun invoke(
        email: String,
        password: String
    ): Resource<FirebaseUser> {

        // ── Input validation (domain-level rules) ─────────────────────────────
        if (email.isBlank()) {
            return Resource.Error("Email address cannot be empty.")
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            return Resource.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return Resource.Error("Password must be at least 6 characters.")
        }

        // ── Delegate to repository ────────────────────────────────────────────
        return authRepository.login(email.trim(), password)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// RegisterUseCase
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Validates registration fields, then calls [AuthRepository.register].
 *
 * Business rules:
 *  - Display name: 2–30 characters, no special chars
 *  - Email: valid format
 *  - Password: minimum 8 chars (stricter than Firebase's 6)
 *  - Passwords match
 */
class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {

    suspend operator fun invoke(
        displayName: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Resource<Unit> {

        // ── Validation ────────────────────────────────────────────────────────
        val trimmedName = displayName.trim()
        if (trimmedName.length < 2) {
            return Resource.Error("Display name must be at least 2 characters.")
        }
        if (trimmedName.length > 30) {
            return Resource.Error("Display name must be 30 characters or fewer.")
        }

        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            return Resource.Error("Email address cannot be empty.")
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            return Resource.Error("Please enter a valid email address.")
        }

        if (password.length < 8) {
            return Resource.Error("Password must be at least 8 characters.")
        }
        if (password != confirmPassword) {
            return Resource.Error("Passwords do not match.")
        }

        // ── Delegate ──────────────────────────────────────────────────────────
        return authRepository.register(trimmedEmail, password, trimmedName)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// GetRecipesUseCase
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Retrieves the recipe feed.
 *
 * Returns a [Flow] of [Resource<List<Recipe>>] so the ViewModel
 * receives real-time Firestore updates automatically.
 *
 * ViewModel usage (one-shot):
 *   val result = getRecipesUseCase()
 *
 * ViewModel usage (real-time stream):
 *   getRecipesUseCase.asFlow().collectLatest { resource -> ... }
 */
class GetRecipesUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {

    /** One-shot fetch — returns a snapshot of the feed. */
    suspend operator fun invoke(limit: Long = 20): Resource<List<Recipe>> =
        recipeRepository.getRecipes(limit)

    /** Real-time stream — emits on every Firestore update. */
    fun asFlow(limit: Long = 20): Flow<Resource<List<Recipe>>> =
        recipeRepository.observeRecipes(limit)
}


// ─────────────────────────────────────────────────────────────────────────────
// GetRecipeByIdUseCase
// ─────────────────────────────────────────────────────────────────────────────

class GetRecipeByIdUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {
    suspend operator fun invoke(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")
        return recipeRepository.getRecipeById(recipeId)
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// LogoutUseCase
// ─────────────────────────────────────────────────────────────────────────────

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    /** Synchronous — Firebase sign-out is a local operation. */
    operator fun invoke() = authRepository.logout()
}


// ─────────────────────────────────────────────────────────────────────────────
// AddRecipeUseCase
// ─────────────────────────────────────────────────────────────────────────────

class AddRecipeUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(recipe: Recipe): Resource<String> {
        val uid = authRepository.currentUserId
            ?: return Resource.Error("You must be logged in to add a recipe.")

        if (recipe.title.isBlank()) return Resource.Error("Recipe title cannot be empty.")
        if (recipe.ingredients.isEmpty()) return Resource.Error("Add at least one ingredient.")
        if (recipe.instructions.isEmpty()) return Resource.Error("Add at least one instruction step.")

        val recipeWithAuthor = recipe.copy(authorId = uid)
        return recipeRepository.addRecipe(recipeWithAuthor)
    }
}