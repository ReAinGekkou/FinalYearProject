package com.example.finalyearproject.data.remote.firebase

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.User
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FirestoreService
 *
 * Single source of truth for all Firestore read/write operations.
 * Returns [Resource] wrappers and exposes real-time listeners as [Flow].
 *
 * Location: data/remote/firebase/FirestoreService.kt
 *
 * Collection layout:
 *   users/{uid}
 *   recipes/{recipeId}
 *   recipes/{recipeId}/reviews/{reviewId}
 *   blogs/{blogId}
 *   users/{uid}/favorites/{favoriteId}
 */
@Singleton
class FirestoreService @Inject constructor(
    private val db: FirebaseFirestore
) {

    // ── Collection references ─────────────────────────────────────────────────

    private val usersCol    get() = db.collection(Constants.COLLECTION_USERS)
    private val recipesCol  get() = db.collection(Constants.COLLECTION_RECIPES)
    private val blogsCol    get() = db.collection(Constants.COLLECTION_BLOGS)

    // ═══════════════════════════════════════════════════════════════════════════
    // USER OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates or overwrites a user document in Firestore.
     * Called immediately after Firebase Auth registration.
     */
    suspend fun addUser(user: User): Resource<Unit> = try {
        usersCol.document(user.uid).set(user.toMap()).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to create user profile.")
    }

    /**
     * Fetches a single user document by UID.
     */
    suspend fun getUser(uid: String): Resource<User> = try {
        val snapshot = usersCol.document(uid).get().await()
        val user = snapshot.toObject(User::class.java)
            ?: return Resource.Error("User not found.")
        Resource.Success(user)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch user.")
    }

    /**
     * Real-time listener for a user's profile document.
     * Emits [Resource.Loading] on subscription, then [Resource.Success]/[Resource.Error]
     * on every Firestore update.
     */
    fun observeUser(uid: String): Flow<Resource<User>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Snapshot error"))
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(User::class.java)
                if (user != null) trySend(Resource.Success(user))
                else trySend(Resource.Error("User document missing."))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Partially updates user fields (does not overwrite entire document).
     */
    suspend fun updateUser(uid: String, fields: Map<String, Any?>): Resource<Unit> = try {
        usersCol.document(uid).update(fields).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update user.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a new recipe document. Firestore auto-generates the document ID.
     * @return [Resource.Success] with the new document ID.
     */
    suspend fun addRecipe(recipe: Recipe): Resource<String> = try {
        val docRef = recipesCol.add(recipe.toMap()).await()
        Resource.Success(docRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add recipe.")
    }

    /**
     * Fetches paginated list of published recipes, ordered by creation date.
     *
     * @param limit  Page size (default 20)
     */
    suspend fun getRecipes(limit: Long = 20): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        val recipes = snapshot.toObjects(Recipe::class.java)
        Resource.Success(recipes)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipes.")
    }

    /**
     * Real-time feed of recipes — emits on every change.
     */
    fun observeRecipes(limit: Long = 20): Flow<Resource<List<Recipe>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = recipesCol
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Snapshot error"))
                    return@addSnapshotListener
                }
                val recipes = snapshot?.toObjects(Recipe::class.java) ?: emptyList()
                trySend(Resource.Success(recipes))
            }
        awaitClose { listener.remove() }
    }

    /**
     * Fetches recipes for a specific author (My Recipes screen).
     */
    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch author's recipes.")
    }

    /**
     * Fetches a single recipe by ID.
     */
    suspend fun getRecipeById(recipeId: String): Resource<Recipe> = try {
        val snapshot = recipesCol.document(recipeId).get().await()
        val recipe = snapshot.toObject(Recipe::class.java)
            ?: return Resource.Error("Recipe not found.")
        Resource.Success(recipe)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipe.")
    }

    /**
     * Deletes a recipe document.
     */
    suspend fun deleteRecipe(recipeId: String): Resource<Unit> = try {
        recipesCol.document(recipeId).delete().await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete recipe.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REVIEW OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adds a review to the recipe's sub-collection.
     * Also atomically increments reviewCount and recalculates averageRating.
     */
    suspend fun addReview(review: Review): Resource<String> = try {
        // 1. Write the review document
        val reviewRef = recipesCol
            .document(review.recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .add(review.toMap())
            .await()

        // 2. Atomically update the recipe's aggregate counters
        recipesCol.document(review.recipeId).update(
            mapOf(
                "reviewCount" to FieldValue.increment(1),
                // averageRating re-computation is best done in a Cloud Function
                // for production; client-side increment is a placeholder.
                "averageRating" to review.rating.toDouble()
            )
        ).await()

        Resource.Success(reviewRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add review.")
    }

    /**
     * Fetches all reviews for a given recipe.
     */
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

    // ═══════════════════════════════════════════════════════════════════════════
    // FAVORITE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Saves a recipe to the user's favorites sub-collection.
     * Uses the recipeId as the document ID to allow idempotent upserts.
     */
    suspend fun addFavorite(favorite: Favorite): Resource<Unit> = try {
        usersCol
            .document(favorite.userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(favorite.recipeId)       // deterministic doc ID
            .set(favorite.toMap())
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to save favorite.")
    }

    /**
     * Removes a recipe from favorites.
     */
    suspend fun removeFavorite(userId: String, recipeId: String): Resource<Unit> = try {
        usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(recipeId)
            .delete()
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to remove favorite.")
    }

    /**
     * Fetches all favorites for a user — real-time [Flow].
     */
    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.localizedMessage ?: "Snapshot error"))
                    return@addSnapshotListener
                }
                val favs = snapshot?.toObjects(Favorite::class.java) ?: emptyList()
                trySend(Resource.Success(favs))
            }
        awaitClose { listener.remove() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOG OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addBlog(blog: Blog): Resource<String> = try {
        val docRef = blogsCol.add(blog.toMap()).await()
        Resource.Success(docRef.id)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add blog post.")
    }

    suspend fun getBlogs(limit: Long = 20): Resource<List<Blog>> = try {
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
}