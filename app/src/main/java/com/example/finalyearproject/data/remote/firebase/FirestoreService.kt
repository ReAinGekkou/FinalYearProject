package com.example.finalyearproject.data.remote.firebase

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.PaginatedResult
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.RecipeFilter
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * FirestoreService — Day 3 Advanced
 *
 * New capabilities:
 *  - Case-insensitive keyword search via searchTitle field
 *  - Multi-field filtering (category, cuisine, rating, difficulty)
 *  - Cursor-based pagination via startAfter()
 *  - Recommendation engine queries
 *  - Full user profile CRUD
 */
class FirestoreService private constructor() {

    private val db = FirebaseFirestore.getInstance()

    private val usersCol   get() = db.collection(Constants.COLLECTION_USERS)
    private val recipesCol get() = db.collection(Constants.COLLECTION_RECIPES)
    private val blogsCol   get() = db.collection(Constants.COLLECTION_BLOGS)

    companion object {
        @Volatile private var instance: FirestoreService? = null
        fun getInstance(): FirestoreService =
            instance ?: synchronized(this) {
                instance ?: FirestoreService().also { instance = it }
            }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH  — case-insensitive title search
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Firestore does not support native full-text search.
     * Strategy: store a lowercase "searchTitle" field alongside the real title.
     * Query uses range filter: searchTitle >= query && searchTitle <= query + '\uf8ff'
     *
     * When you call addRecipe(), always set:
     *   "searchTitle" to recipe.title.lowercase().trim()
     *
     * This gives prefix-matching search (e.g. "chick" matches "chicken curry").
     */
    suspend fun searchRecipesByTitle(query: String): Resource<List<Recipe>> = try {
        if (query.isBlank()) return Resource.Error("Search query cannot be empty.")

        val normalised = query.lowercase().trim()

        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .whereGreaterThanOrEqualTo("searchTitle", normalised)
            .whereLessThanOrEqualTo("searchTitle", normalised + "\uF8FF")
            .limit(Constants.PAGE_SIZE_RECIPES)
            .get()
            .await()

        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Search failed.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILTER  — combine multiple constraints
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Applies [RecipeFilter] constraints to a Firestore query.
     *
     * Firestore limitation: only ONE inequality filter (>, >=, <, <=) per query.
     * If both minRating and maxPrepTime are set, minRating takes priority on
     * Firestore; maxPrepTime is applied client-side after fetch.
     *
     * Equality filters (category, cuisineType, difficulty) stack freely.
     */
    suspend fun filterRecipes(filter: RecipeFilter): Resource<List<Recipe>> = try {
        var query: Query = recipesCol.whereEqualTo("isPublished", true)

        // ── Equality filters ───────────────────────────────────────────────────
        filter.category?.let    { query = query.whereEqualTo("category", it) }
        filter.cuisineType?.let { query = query.whereEqualTo("cuisineType", it) }
        filter.difficulty?.let  { query = query.whereEqualTo("difficulty", it) }

        // ── Inequality filter (only ONE allowed server-side) ───────────────────
        if (filter.minRating != null) {
            query = query
                .whereGreaterThanOrEqualTo("averageRating", filter.minRating)
                .orderBy("averageRating", Query.Direction.DESCENDING)
        } else {
            query = query.orderBy("createdAt", Query.Direction.DESCENDING)
        }

        var recipes = query
            .limit(Constants.PAGE_SIZE_RECIPES * 2)  // fetch extra for client-side trim
            .get()
            .await()
            .toObjects(Recipe::class.java)

        // ── Client-side post-filters ───────────────────────────────────────────
        filter.maxPrepTime?.let { max -> recipes = recipes.filter { it.prepTimeMinutes <= max } }
        filter.tags.takeIf { it.isNotEmpty() }?.let { tags ->
            recipes = recipes.filter { recipe -> recipe.tags.containsAll(tags) }
        }
        filter.query?.let { q ->
            val normalised = q.lowercase().trim()
            recipes = recipes.filter { it.title.lowercase().contains(normalised) }
        }

        Resource.Success(recipes)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Filter failed.")
    }

    /**
     * Convenience: filter by minimum rating only.
     */
    suspend fun filterRecipesByRating(minRating: Double): Resource<List<Recipe>> =
        filterRecipes(RecipeFilter(minRating = minRating))

    /**
     * Convenience: filter by category only.
     */
    suspend fun filterByCategory(category: String): Resource<List<Recipe>> =
        filterRecipes(RecipeFilter(category = category))

    // ═══════════════════════════════════════════════════════════════════════════
    // PAGINATION  — cursor-based via startAfter()
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Loads the first page of recipes.
     * Store the returned [PaginatedResult.lastVisible] and pass it to
     * [loadNextRecipesPage] for subsequent pages.
     */
    suspend fun loadRecipesFirstPage(
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)
            .get()
            .await()

        val recipes = snapshot.toObjects(Recipe::class.java)
        val lastDoc = snapshot.documents.lastOrNull()

        Resource.Success(
            PaginatedResult(
                items = recipes,
                lastVisible = lastDoc,
                hasMore = snapshot.size() >= pageSize
            )
        )
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load recipes.")
    }

    /**
     * Loads the next page after [lastVisible] document.
     * Returns empty list with hasMore=false when exhausted.
     */
    suspend fun loadNextRecipesPage(
        lastVisible: DocumentSnapshot,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(lastVisible)
            .limit(pageSize)
            .get()
            .await()

        val recipes = snapshot.toObjects(Recipe::class.java)
        val lastDoc = snapshot.documents.lastOrNull()

        Resource.Success(
            PaginatedResult(
                items = recipes,
                lastVisible = lastDoc,
                hasMore = snapshot.size() >= pageSize
            )
        )
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load more recipes.")
    }

    /**
     * Paginated filtered search — combines keyword + cursor pagination.
     */
    suspend fun searchRecipesPaginated(
        query: String,
        lastVisible: DocumentSnapshot? = null,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> = try {
        val normalised = query.lowercase().trim()

        var q = recipesCol
            .whereEqualTo("isPublished", true)
            .whereGreaterThanOrEqualTo("searchTitle", normalised)
            .whereLessThanOrEqualTo("searchTitle", normalised + "\uF8FF")
            .limit(pageSize)

        if (lastVisible != null) q = q.startAfter(lastVisible)

        val snapshot = q.get().await()
        val recipes = snapshot.toObjects(Recipe::class.java)

        Resource.Success(
            PaginatedResult(
                items = recipes,
                lastVisible = snapshot.documents.lastOrNull(),
                hasMore = snapshot.size() >= pageSize
            )
        )
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Paginated search failed.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECOMMENDATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Step 1 — Get the user's favorite recipe IDs.
     */
    suspend fun getFavoriteRecipeIds(userId: String): List<String> = try {
        usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .get()
            .await()
            .documents
            .mapNotNull { it.id }   // doc ID == recipeId
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Step 2 — Fetch the full Recipe objects for the given IDs.
     * Firestore whereIn supports max 30 IDs per query.
     */
    suspend fun getRecipesByIds(ids: List<String>): Resource<List<Recipe>> = try {
        if (ids.isEmpty()) return Resource.Success(emptyList())
        val chunks = ids.chunked(10) // whereIn max 10 per query
        val results = mutableListOf<Recipe>()
        for (chunk in chunks) {
            val snapshot = recipesCol
                .whereIn("recipeId", chunk)
                .get()
                .await()
            results.addAll(snapshot.toObjects(Recipe::class.java))
        }
        Resource.Success(results)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipes by IDs.")
    }

    /**
     * Step 3 — Fetch recipes that share categories with the user's favorites.
     * This is the "because you liked X" recommendation pattern.
     */
    suspend fun getRecommendedRecipes(
        userId: String,
        limit: Long = 10
    ): Resource<List<Recipe>> = try {
        // 1. Get favorite recipe details to extract categories
        val favIds = getFavoriteRecipeIds(userId)
        val favCategories = mutableSetOf<String>()

        if (favIds.isNotEmpty()) {
            val favRecipes = getRecipesByIds(favIds.take(10))
            if (favRecipes is Resource.Success) {
                favCategories.addAll(favRecipes.data.map { it.category }.filter { it.isNotBlank() })
            }
        }

        if (favCategories.isEmpty()) {
            // Fallback: return highest-rated recipes
            val fallback = recipesCol
                .whereEqualTo("isPublished", true)
                .orderBy("averageRating", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            return Resource.Success(fallback.toObjects(Recipe::class.java))
        }

        // 2. Query recipes in those categories, excluding already-favorited ones
        val category = favCategories.first() // Firestore whereIn for categories
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .whereEqualTo("category", category)
            .orderBy("averageRating", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        val recommended = snapshot.toObjects(Recipe::class.java)
            .filter { it.recipeId !in favIds }  // exclude already-favourited

        Resource.Success(recommended)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to get recommendations.")
    }

    /**
     * Trending recipes — highest like count in last 7 days.
     * (Simplified: ordered by likeCount overall since Firestore
     *  requires a Cloud Function for time-windowed trending.)
     */
    suspend fun getTrendingRecipes(limit: Long = 10): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("likeCount", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch trending recipes.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USER PROFILE
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addUser(user: User): Resource<Unit> = try {
        usersCol.document(user.uid).set(user.toMap()).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to create profile.")
    }

    suspend fun getUser(uid: String): Resource<User> = try {
        val snapshot = usersCol.document(uid).get().await()
        if (!snapshot.exists()) return Resource.Error("User profile not found.")
        Resource.Success(snapshot.toObject(User::class.java)!!)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch profile.")
    }

    suspend fun updateUser(uid: String, fields: Map<String, Any?>): Resource<Unit> = try {
        usersCol.document(uid).update(fields).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update profile.")
    }

    fun observeUser(uid: String): Flow<Resource<User>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol.document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Resource.Error(err.localizedMessage ?: "Profile error"))
                    return@addSnapshotListener
                }
                val user = snap?.toObject(User::class.java)
                if (user != null) trySend(Resource.Success(user))
                else trySend(Resource.Error("Profile not found."))
            }
        awaitClose { listener.remove() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPE CRUD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a recipe. Also writes the lowercase [searchTitle] field
     * needed for case-insensitive search queries.
     */
    suspend fun addRecipe(recipe: Recipe): Resource<String> = try {
        val map = recipe.toMap().toMutableMap()
        map["searchTitle"] = recipe.title.lowercase().trim()
        val docRef = recipesCol.add(map).await()
        Resource.Success(docRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add recipe.")
    }

    suspend fun getRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipes.")
    }

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> = try {
        val snapshot = recipesCol.document(recipeId).get().await()
        if (!snapshot.exists()) return Resource.Error("Recipe not found.")
        Resource.Success(snapshot.toObject(Recipe::class.java)!!)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipe.")
    }

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch user recipes.")
    }

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> = try {
        val mutable = fields.toMutableMap()
        // Keep searchTitle in sync if title is being updated
        (fields["title"] as? String)?.let {
            mutable["searchTitle"] = it.lowercase().trim()
        }
        recipesCol.document(recipeId).update(mutable).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update recipe.")
    }

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> = try {
        recipesCol.document(recipeId).delete().await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete recipe.")
    }

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        callbackFlow {
            trySend(Resource.Loading())
            val listener = recipesCol
                .whereEqualTo("isPublished", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(Resource.Error(err.localizedMessage ?: "Error"))
                        return@addSnapshotListener
                    }
                    trySend(Resource.Success(snap?.toObjects(Recipe::class.java) ?: emptyList()))
                }
            awaitClose { listener.remove() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOGS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addBlog(blog: Blog): Resource<String> = try {
        val docRef = blogsCol.add(blog.toMap()).await()
        Resource.Success(docRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add blog.")
    }

    suspend fun getBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Resource<List<Blog>> = try {
        val snapshot = blogsCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Blog::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch blogs.")
    }

    suspend fun getBlogById(blogId: String): Resource<Blog> = try {
        val snapshot = blogsCol.document(blogId).get().await()
        if (!snapshot.exists()) return Resource.Error("Blog not found.")
        Resource.Success(snapshot.toObject(Blog::class.java)!!)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch blog.")
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> = try {
        blogsCol.document(blogId).delete().await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete blog.")
    }

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> =
        callbackFlow {
            trySend(Resource.Loading())
            val listener = blogsCol
                .whereEqualTo("isPublished", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(Resource.Error(err.localizedMessage ?: "Error"))
                        return@addSnapshotListener
                    }
                    trySend(Resource.Success(snap?.toObjects(Blog::class.java) ?: emptyList()))
                }
            awaitClose { listener.remove() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // REVIEWS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addReview(review: Review): Resource<String> = try {
        val reviewRef = recipesCol
            .document(review.recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .add(review.toMap())
            .await()
        recipesCol.document(review.recipeId)
            .update("reviewCount", FieldValue.increment(1))
            .await()
        Resource.Success(reviewRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add review.")
    }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> = try {
        val snapshot = recipesCol
            .document(recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Review::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch reviews.")
    }

    suspend fun deleteReview(recipeId: String, reviewId: String): Resource<Unit> = try {
        recipesCol.document(recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .document(reviewId)
            .delete()
            .await()
        recipesCol.document(recipeId)
            .update("reviewCount", FieldValue.increment(-1))
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete review.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FAVORITES
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> = try {
        usersCol.document(favorite.userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(favorite.recipeId)
            .set(favorite.toMap())
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to save favorite.")
    }

    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> = try {
        usersCol.document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(recipeId)
            .delete()
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to remove favorite.")
    }

    suspend fun getFavorites(userId: String): Resource<List<Favorite>> = try {
        val snapshot = usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Favorite::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch favorites.")
    }

    suspend fun isFavorited(userId: String, recipeId: String): Boolean = try {
        usersCol.document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(recipeId)
            .get()
            .await()
            .exists()
    } catch (e: Exception) { false }

    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol.document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Resource.Error(err.localizedMessage ?: "Error"))
                    return@addSnapshotListener
                }
                trySend(Resource.Success(snap?.toObjects(Favorite::class.java) ?: emptyList()))
            }
        awaitClose { listener.remove() }
    }
}