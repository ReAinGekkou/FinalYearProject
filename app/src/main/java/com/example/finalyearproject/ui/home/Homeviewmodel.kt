package com.example.finalyearproject.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.remote.ai.AIRecommendationEngine
import com.example.finalyearproject.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import android.util.Log

/**
 * HomeViewModel
 *
 * Drives the Home screen with three data streams:
 *   1. AI personalised recommendations
 *   2. Trending recipes (by trending score)
 *   3. Full recipe feed (paginated)
 *
 * Also handles:
 *   - Like / unlike
 *   - View tracking (feeds AI engine)
 *   - Pull-to-refresh
 */
class HomeViewModel : ViewModel() {

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    // ── LiveData ──────────────────────────────────────────────────────────────

    private val _recommendations = MutableLiveData<Resource<List<Recipe>>>()
    val recommendations: LiveData<Resource<List<Recipe>>> = _recommendations

    private val _trending = MutableLiveData<Resource<List<Recipe>>>()
    val trending: LiveData<Resource<List<Recipe>>> = _trending

    private val _recipes = MutableLiveData<Resource<List<Recipe>>>()
    val recipes: LiveData<Resource<List<Recipe>>> = _recipes

    private val _likedIds = MutableLiveData<Set<String>>(emptySet())
    val likedIds: LiveData<Set<String>> = _likedIds

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        loadAll()
        loadLikedIds()
    }

    // ── Load all sections ─────────────────────────────────────────────────────

    fun loadAll() {
        viewModelScope.launch {
            _isRefreshing.value = true

            // Set loading states immediately so shimmer shows
            _recommendations.value = Resource.Loading()
            _trending.value        = Resource.Loading()
            _recipes.value         = Resource.Loading()

            try {
                // Fetch all published recipes once — then derive sections from it
                val allRecipes = fetchAllRecipes()

                if (allRecipes.isEmpty()) {
                    _recipes.value        = Resource.Success(emptyList())
                    _trending.value       = Resource.Success(emptyList())
                    _recommendations.value = Resource.Success(emptyList())
                    _isRefreshing.value   = false
                    return@launch
                }

                // All recipes feed (already sorted by createdAt DESC from Firestore)
                _recipes.value = Resource.Success(allRecipes)

                // Trending — compute in parallel
                val trendingDeferred = async {
                    AIRecommendationEngine.getTrending(allRecipes, limit = 10)
                }

                // AI recommendations — compute in parallel
                val recsDeferred = async {
                    val uid = currentUserId
                    if (uid != null) {
                        AIRecommendationEngine.getRecommendations(
                            userId     = uid,
                            allRecipes = allRecipes,
                            limit      = 10
                        )
                    } else {
                        AIRecommendationEngine.getTrending(allRecipes, 10)
                    }
                }

                _trending.value        = Resource.Success(trendingDeferred.await())
                _recommendations.value = Resource.Success(recsDeferred.await())

            } catch (e: Exception) {
                Log.e("HomeViewModel", "loadAll failed: ${e.message}", e)
                val errMsg = e.localizedMessage ?: "Failed to load recipes"
                _recipes.value         = Resource.Error(errMsg)
                _trending.value        = Resource.Error(errMsg)
                _recommendations.value = Resource.Error(errMsg)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refresh() = loadAll()

    // ── Firestore fetch ───────────────────────────────────────────────────────

    private suspend fun fetchAllRecipes(): List<Recipe> {
        val snapshot = db.collection("recipes")
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .await()
        return snapshot.toObjects(Recipe::class.java)
    }

    // ── Liked IDs ─────────────────────────────────────────────────────────────

    private fun loadLikedIds() {
        val uid = currentUserId ?: return
        db.collection("user_activity")
            .whereEqualTo("userId", uid)
            .whereEqualTo("actionType", "like")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val ids = snapshot?.documents
                    ?.mapNotNull { it.getString("recipeId") }
                    ?.toSet() ?: emptySet()
                _likedIds.value = ids
            }
    }

    // ── Engagement actions ────────────────────────────────────────────────────

    /**
     * Records a view — called when a recipe card becomes visible in the feed.
     * Debounced to once per recipe per session (tracked in-memory).
     */
    private val viewedThisSession = mutableSetOf<String>()

    fun onRecipeVisible(recipeId: String) {
        if (recipeId in viewedThisSession) return
        viewedThisSession.add(recipeId)
        val uid = currentUserId ?: return
        AIRecommendationEngine.recordActivity(uid, recipeId, "view")
        // Increment view count on recipe document
        db.collection("recipes").document(recipeId)
            .update("viewCount", com.google.firebase.firestore.FieldValue.increment(1))
    }

    /**
     * Toggles the like state for a recipe.
     * Optimistically updates the local liked set.
     */
    fun toggleLike(recipe: Recipe) {
        val uid = currentUserId ?: return
        val recipeId = recipe.recipeId
        val currentLiked = _likedIds.value ?: emptySet()
        val isLiked = recipeId in currentLiked

        // Optimistic UI update
        _likedIds.value = if (isLiked) {
            currentLiked - recipeId
        } else {
            currentLiked + recipeId
        }

        viewModelScope.launch {
            try {
                if (isLiked) {
                    // Unlike: remove activity doc
                    val existing = db.collection("user_activity")
                        .whereEqualTo("userId", uid)
                        .whereEqualTo("recipeId", recipeId)
                        .whereEqualTo("actionType", "like")
                        .get()
                        .await()
                    existing.documents.forEach { it.reference.delete() }
                    db.collection("recipes").document(recipeId)
                        .update("likeCount", com.google.firebase.firestore.FieldValue.increment(-1))
                } else {
                    // Like: add activity + increment
                    AIRecommendationEngine.recordActivity(uid, recipeId, "like")
                    db.collection("recipes").document(recipeId)
                        .update("likeCount", com.google.firebase.firestore.FieldValue.increment(1))
                }
            } catch (e: Exception) {
                // Revert optimistic update on failure
                _likedIds.value = currentLiked
                Log.e("HomeViewModel", "toggleLike failed: ${e.message}")
            }
        }
    }

    /**
     * Saves a recipe to bookmarks.
     */
    fun saveRecipe(recipe: Recipe) {
        val uid = currentUserId ?: return
        AIRecommendationEngine.recordActivity(uid, recipe.recipeId, "save")
        val favorite = hashMapOf(
            "userId"          to uid,
            "recipeId"        to recipe.recipeId,
            "recipeTitle"     to recipe.title,
            "recipeImageUrl"  to (recipe.imageUrl ?: ""),
            "savedAt"         to com.google.firebase.Timestamp.now()
        )
        db.collection("users").document(uid)
            .collection("favorites").document(recipe.recipeId)
            .set(favorite)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun search(query: String) {
        if (query.isBlank()) {
            loadAll()
            return
        }
        viewModelScope.launch {
            _recipes.value = Resource.Loading()
            try {
                val q = query.lowercase().trim()
                val snapshot = db.collection("recipes")
                    .whereEqualTo("isPublished", true)
                    .whereGreaterThanOrEqualTo("searchTitle", q)
                    .whereLessThanOrEqualTo("searchTitle", q + "\uF8FF")
                    .limit(30)
                    .get()
                    .await()
                _recipes.value = Resource.Success(snapshot.toObjects(Recipe::class.java))
            } catch (e: Exception) {
                _recipes.value = Resource.Error(e.localizedMessage ?: "Search failed")
            }
        }
    }

    // ── Category filter ───────────────────────────────────────────────────────

    fun filterByCategory(category: String?) {
        viewModelScope.launch {
            _recipes.value = Resource.Loading()
            try {
                val query = if (category == null) {
                    db.collection("recipes")
                        .whereEqualTo("isPublished", true)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(50)
                } else {
                    db.collection("recipes")
                        .whereEqualTo("isPublished", true)
                        .whereEqualTo("category", category)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .limit(30)
                }
                val snapshot = query.get().await()
                _recipes.value = Resource.Success(snapshot.toObjects(Recipe::class.java))
            } catch (e: Exception) {
                _recipes.value = Resource.Error(e.localizedMessage ?: "Filter failed")
            }
        }
    }
}