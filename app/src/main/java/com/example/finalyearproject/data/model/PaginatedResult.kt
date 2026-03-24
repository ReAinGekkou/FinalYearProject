package com.example.finalyearproject.data.model

import com.google.firebase.firestore.DocumentSnapshot

/**
 * Wraps a paginated Firestore result.
 * [lastVisible] is passed back into the next loadPage() call.
 * [hasMore] tells the UI whether to show a "Load More" button.
 */
data class PaginatedResult<T>(
    val items: List<T>,
    val lastVisible: DocumentSnapshot?,
    val hasMore: Boolean
)

/**
 * Encapsulates all recipe filter/search parameters in one object.
 * Pass this into RecipeRepository.searchRecipes() — null fields are ignored.
 *
 * Example:
 *   RecipeFilter(query = "pasta", category = "Dinner", minRating = 4.0)
 */
data class RecipeFilter(
    val query: String? = null,           // title keyword search
    val category: String? = null,        // e.g. "Breakfast", "Dinner"
    val cuisineType: String? = null,     // e.g. "Italian", "Asian"
    val minRating: Double? = null,       // minimum averageRating
    val maxPrepTime: Int? = null,        // max prepTimeMinutes
    val difficulty: String? = null,      // "EASY", "MEDIUM", "HARD"
    val tags: List<String> = emptyList() // must contain all listed tags
)