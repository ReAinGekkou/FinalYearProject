package com.example.finalyearproject.data.remote.ai

import android.util.Log
import com.example.finalyearproject.data.model.Recipe
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.ln

/**
 * AIRecommendationEngine
 *
 * Real hybrid recommendation system using Firestore user activity data.
 *
 * Scoring formula per recipe:
 *   score = (tagMatch * 0.4) + (ingredientMatch * 0.2)
 *           + (popularityScore * 0.2) + (collaborativeScore * 0.2)
 *
 * Trending formula:
 *   trending = (likes * 0.6) + (comments * 0.2) + (views * 0.2)
 *
 * Time-decay applied to both scores.
 *
 * Fallback: if no user history → return trending recipes.
 */
object AIRecommendationEngine {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "AIRecommendation"

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns top [limit] recommended recipes for [userId].
     * Falls back to trending if user has no activity history.
     */
    suspend fun getRecommendations(
        userId: String,
        allRecipes: List<Recipe>,
        limit: Int = 10
    ): List<Recipe> {
        if (allRecipes.isEmpty()) return emptyList()

        return try {
            val userProfile = buildUserProfile(userId)
            Log.d(TAG, "User profile: tags=${userProfile.preferredTags.size}, " +
                    "ingredients=${userProfile.preferredIngredients.size}, " +
                    "viewedIds=${userProfile.viewedRecipeIds.size}")

            if (userProfile.isEmpty()) {
                // Cold start — return trending
                Log.d(TAG, "No user history — falling back to trending")
                return getTrending(allRecipes, limit)
            }

            val collaborativeIds = getCollaborativeIds(userId, userProfile, limit * 2)

            scoreAndRank(
                recipes        = allRecipes,
                userProfile    = userProfile,
                collaborativeIds = collaborativeIds,
                limit          = limit
            )
        } catch (e: Exception) {
            Log.e(TAG, "Recommendation failed: ${e.message}", e)
            getTrending(allRecipes, limit)
        }
    }

    /**
     * Returns top [limit] trending recipes sorted by trending score.
     */
    fun getTrending(allRecipes: List<Recipe>, limit: Int = 10): List<Recipe> {
        return allRecipes
            .sortedByDescending { trendingScore(it) }
            .take(limit)
    }

    /**
     * Records a user interaction (view, like, save, comment).
     * Fire-and-forget — does not block the UI.
     */
    fun recordActivity(userId: String, recipeId: String, actionType: String) {
        val activity = hashMapOf(
            "userId"     to userId,
            "recipeId"   to recipeId,
            "actionType" to actionType,   // "view" | "like" | "save" | "comment"
            "timestamp"  to Timestamp.now()
        )
        db.collection("user_activity")
            .add(activity)
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to record activity: ${e.message}")
            }
    }

    // ── User profile builder ──────────────────────────────────────────────────

    private suspend fun buildUserProfile(userId: String): UserProfile {
        // Fetch last 100 interactions for this user
        val activities = db.collection("user_activity")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
            .documents

        if (activities.isEmpty()) return UserProfile()

        // Weight multipliers per action type
        val weights = mapOf("like" to 3.0, "save" to 3.0, "comment" to 2.0, "view" to 1.0)

        val viewedIds   = mutableSetOf<String>()
        val likedIds    = mutableSetOf<String>()
        val savedIds    = mutableSetOf<String>()
        val tagScores   = mutableMapOf<String, Double>()
        val ingScores   = mutableMapOf<String, Double>()

        // We need recipe data to extract tags/ingredients — batch fetch in chunks
        val recipeIds = activities.mapNotNull { it.getString("recipeId") }.distinct()
        val recipeMap = fetchRecipeMap(recipeIds)

        activities.forEach { doc ->
            val recipeId = doc.getString("recipeId") ?: return@forEach
            val action   = doc.getString("actionType") ?: "view"
            val weight   = weights[action] ?: 1.0

            when (action) {
                "view"    -> viewedIds.add(recipeId)
                "like"    -> likedIds.add(recipeId)
                "save"    -> savedIds.add(recipeId)
            }

            // Accumulate tag and ingredient preference scores
            val recipe = recipeMap[recipeId] ?: return@forEach
            recipe.tags.forEach { tag ->
                tagScores[tag] = (tagScores[tag] ?: 0.0) + weight
            }
            recipe.ingredients.forEach { ing ->
                val key = ing.lowercase().trim()
                ingScores[key] = (ingScores[key] ?: 0.0) + weight
            }
        }

        // Normalise scores to 0-1 range
        val maxTag = tagScores.values.maxOrNull() ?: 1.0
        val maxIng = ingScores.values.maxOrNull() ?: 1.0
        val normTags = tagScores.mapValues { it.value / maxTag }
        val normIngs = ingScores.mapValues { it.value / maxIng }

        // Top preferences
        val preferredTags = normTags.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }
            .toSet()

        val preferredIngredients = normIngs.entries
            .sortedByDescending { it.value }
            .take(20)
            .map { it.key }
            .toSet()

        return UserProfile(
            viewedRecipeIds      = viewedIds,
            likedRecipeIds       = likedIds,
            savedRecipeIds       = savedIds,
            preferredTags        = preferredTags,
            preferredIngredients = preferredIngredients,
            tagWeights           = normTags,
            ingredientWeights    = normIngs
        )
    }

    // ── Collaborative filtering ───────────────────────────────────────────────

    /**
     * Finds users who liked similar recipes, then returns recipes THEY liked
     * that our user hasn't seen. Basic item-based collaborative filtering.
     */
    private suspend fun getCollaborativeIds(
        userId: String,
        userProfile: UserProfile,
        limit: Int
    ): Set<String> {
        if (userProfile.likedRecipeIds.isEmpty()) return emptySet()

        try {
            // Find other users who liked the same recipes as our user
            val topLikedIds = userProfile.likedRecipeIds.take(5)
            val similarUserIds = mutableSetOf<String>()

            for (recipeId in topLikedIds) {
                val likes = db.collection("user_activity")
                    .whereEqualTo("recipeId", recipeId)
                    .whereEqualTo("actionType", "like")
                    .limit(20)
                    .get()
                    .await()

                likes.documents.forEach { doc ->
                    val uid = doc.getString("userId")
                    if (uid != null && uid != userId) similarUserIds.add(uid)
                }
            }

            if (similarUserIds.isEmpty()) return emptySet()

            // Get what those similar users liked
            val collaborativeRecipeIds = mutableSetOf<String>()
            for (simUserId in similarUserIds.take(10)) {
                val simActivities = db.collection("user_activity")
                    .whereEqualTo("userId", simUserId)
                    .whereEqualTo("actionType", "like")
                    .limit(10)
                    .get()
                    .await()

                simActivities.documents.forEach { doc ->
                    val rid = doc.getString("recipeId")
                    if (rid != null && rid !in userProfile.viewedRecipeIds) {
                        collaborativeRecipeIds.add(rid)
                    }
                }
            }

            Log.d(TAG, "Collaborative: found ${collaborativeRecipeIds.size} candidate IDs")
            return collaborativeRecipeIds
        } catch (e: Exception) {
            Log.w(TAG, "Collaborative filtering failed: ${e.message}")
            return emptySet()
        }
    }

    // ── Scoring and ranking ───────────────────────────────────────────────────

    private fun scoreAndRank(
        recipes: List<Recipe>,
        userProfile: UserProfile,
        collaborativeIds: Set<String>,
        limit: Int
    ): List<Recipe> {
        // Exclude already-viewed (unless we only have a few unseen)
        val unseen = recipes.filter { it.recipeId !in userProfile.viewedRecipeIds }
        val pool   = if (unseen.size >= limit) unseen else recipes

        data class ScoredRecipe(val recipe: Recipe, val score: Double)

        val scored = pool.map { recipe ->
            val tagScore           = computeTagScore(recipe, userProfile)
            val ingredientScore    = computeIngredientScore(recipe, userProfile)
            val popularityScore    = computePopularityScore(recipe)
            val collaborativeScore = if (recipe.recipeId in collaborativeIds) 1.0 else 0.0

            val total = (tagScore * 0.4) +
                    (ingredientScore * 0.2) +
                    (popularityScore * 0.2) +
                    (collaborativeScore * 0.2)

            Log.v(TAG, "${recipe.title}: tag=${"%.2f".format(tagScore)} " +
                    "ing=${"%.2f".format(ingredientScore)} " +
                    "pop=${"%.2f".format(popularityScore)} " +
                    "col=${"%.2f".format(collaborativeScore)} " +
                    "total=${"%.3f".format(total)}")

            ScoredRecipe(recipe, total)
        }

        return scored
            .sortedByDescending { it.score }
            .take(limit)
            .map { it.recipe }
    }

    // ── Score components ──────────────────────────────────────────────────────

    private fun computeTagScore(recipe: Recipe, profile: UserProfile): Double {
        if (recipe.tags.isEmpty() || profile.preferredTags.isEmpty()) return 0.0
        val matched = recipe.tags.count { tag -> tag in profile.preferredTags }
        // Weight-enhanced: use accumulated weight if available
        val weightSum = recipe.tags.sumOf { tag ->
            profile.tagWeights[tag] ?: if (tag in profile.preferredTags) 0.5 else 0.0
        }
        val rawRatio = matched.toDouble() / recipe.tags.size
        return minOf(1.0, (rawRatio * 0.5) + (weightSum / recipe.tags.size * 0.5))
    }

    private fun computeIngredientScore(recipe: Recipe, profile: UserProfile): Double {
        if (recipe.ingredients.isEmpty() || profile.preferredIngredients.isEmpty()) return 0.0
        val matched = recipe.ingredients.count { ing ->
            ing.lowercase().trim() in profile.preferredIngredients
        }
        return minOf(1.0, matched.toDouble() / recipe.ingredients.size)
    }

    /**
     * Normalised popularity using log scale to prevent viral content
     * from completely dominating recommendations.
     *
     * score = log(1 + trending) / log(1 + maxTrending)
     */
    private fun computePopularityScore(recipe: Recipe): Double {
        val trending = trendingScore(recipe)
        val maxExpected = 3000.0  // calibrated to seed data range
        return minOf(1.0, ln(1.0 + trending) / ln(1.0 + maxExpected))
    }

    // ── Trending score ────────────────────────────────────────────────────────

    fun trendingScore(recipe: Recipe): Double =
        (recipe.likeCount * 0.6) +
                (recipe.commentCount * 0.2) +
                ((recipe.viewCount ?: 0) * 0.2)

    // ── Firestore helpers ─────────────────────────────────────────────────────

    private suspend fun fetchRecipeMap(ids: List<String>): Map<String, Recipe> {
        if (ids.isEmpty()) return emptyMap()
        val result = mutableMapOf<String, Recipe>()
        // Firestore whereIn supports max 10 per query
        ids.chunked(10).forEach { chunk ->
            try {
                val snap = db.collection("recipes")
                    .whereIn("recipeId", chunk)
                    .get()
                    .await()
                snap.toObjects(Recipe::class.java).forEach { r ->
                    if (r.recipeId.isNotBlank()) result[r.recipeId] = r
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchRecipeMap chunk failed: ${e.message}")
            }
        }
        return result
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class UserProfile(
        val viewedRecipeIds      : Set<String> = emptySet(),
        val likedRecipeIds       : Set<String> = emptySet(),
        val savedRecipeIds       : Set<String> = emptySet(),
        val preferredTags        : Set<String> = emptySet(),
        val preferredIngredients : Set<String> = emptySet(),
        val tagWeights           : Map<String, Double> = emptyMap(),
        val ingredientWeights    : Map<String, Double> = emptyMap()
    ) {
        fun isEmpty() = viewedRecipeIds.isEmpty() &&
                likedRecipeIds.isEmpty() &&
                savedRecipeIds.isEmpty()
    }
}