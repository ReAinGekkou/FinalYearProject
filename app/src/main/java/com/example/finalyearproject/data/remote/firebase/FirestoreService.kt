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

/**
 * FirestoreService — Day 2 Full Implementation
 * All Firestore CRUD operations for every collection.
 * No Hilt — instantiated directly via companion object factory.
 */
class FirestoreService {

    private val db = FirebaseFirestore.getInstance()

    // ── Collection references ─────────────────────────────────────────────────
    private val usersCol    get() = db.collection(Constants.COLLECTION_USERS)
    private val recipesCol  get() = db.collection(Constants.COLLECTION_RECIPES)
    private val blogsCol    get() = db.collection(Constants.COLLECTION_BLOGS)

    companion object {
        @Volatile private var instance: FirestoreService? = null
        fun getInstance(): FirestoreService =
            instance ?: synchronized(this) {
                instance ?: FirestoreService().also { instance = it }
            }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USER
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addUser(user: User): Resource<Unit> = try {
        usersCol.document(user.uid).set(user.toMap()).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to create user.")
    }

    suspend fun getUser(uid: String): Resource<User> = try {
        val snapshot = usersCol.document(uid).get().await()
        val user = snapshot.toObject(User::class.java)
            ?: return Resource.Error("User not found.")
        Resource.Success(user)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch user.")
    }

    suspend fun updateUser(uid: String, fields: Map<String, Any?>): Resource<Unit> = try {
        usersCol.document(uid).update(fields).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to update user.")
    }

    fun observeUser(uid: String): Flow<Resource<User>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol.document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Resource.Error(err.localizedMessage ?: "Error"))
                    return@addSnapshotListener
                }
                val user = snap?.toObject(User::class.java)
                if (user != null) trySend(Resource.Success(user))
                else trySend(Resource.Error("User not found."))
            }
        awaitClose { listener.remove() }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPES
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addRecipe(recipe: Recipe): Resource<String> = try {
        val docRef = recipesCol.add(recipe.toMap()).await()
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
        val recipe = snapshot.toObject(Recipe::class.java)
            ?: return Resource.Error("Recipe not found.")
        Resource.Success(recipe)
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
        Resource.Error(e.localizedMessage ?: "Failed to fetch author's recipes.")
    }

    suspend fun getRecipesByCategory(category: String): Resource<List<Recipe>> = try {
        val snapshot = recipesCol
            .whereEqualTo("category", category)
            .whereEqualTo("isPublished", true)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        Resource.Success(snapshot.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch recipes by category.")
    }

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> = try {
        recipesCol.document(recipeId).update(fields).await()
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

    // ── Real-time recipe feed ─────────────────────────────────────────────────

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> =
        callbackFlow {
            trySend(Resource.Loading())
            val listener = recipesCol
                .whereEqualTo("isPublished", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(Resource.Error(err.localizedMessage ?: "Snapshot error"))
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
        val blog = snapshot.toObject(Blog::class.java)
            ?: return Resource.Error("Blog not found.")
        Resource.Success(blog)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to fetch blog.")
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> = try {
        blogsCol.document(blogId).delete().await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete blog.")
    }

    // ── Real-time blog feed ───────────────────────────────────────────────────

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> =
        callbackFlow {
            trySend(Resource.Loading())
            val listener = blogsCol
                .whereEqualTo("isPublished", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(Resource.Error(err.localizedMessage ?: "Snapshot error"))
                        return@addSnapshotListener
                    }
                    trySend(Resource.Success(snap?.toObjects(Blog::class.java) ?: emptyList()))
                }
            awaitClose { listener.remove() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // REVIEWS  — sub-collection: recipes/{recipeId}/reviews
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addReview(review: Review): Resource<String> = try {
        val reviewRef = recipesCol
            .document(review.recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .add(review.toMap())
            .await()

        // Atomically increment reviewCount on the parent recipe
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
        recipesCol
            .document(recipeId)
            .collection(Constants.COLLECTION_REVIEWS)
            .document(reviewId)
            .delete()
            .await()

        // Decrement reviewCount
        recipesCol.document(recipeId)
            .update("reviewCount", FieldValue.increment(-1))
            .await()

        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete review.")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FAVORITES  — sub-collection: users/{uid}/favorites
    // ═══════════════════════════════════════════════════════════════════════════

    suspend fun addFavorite(favorite: Favorite): Resource<Unit> = try {
        usersCol
            .document(favorite.userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(favorite.recipeId)   // deterministic ID — prevents duplicates
            .set(favorite.toMap())
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to add favorite.")
    }

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

    /**
     * Checks if a specific recipe is already in the user's favorites.
     * Used for duplicate prevention in ToggleFavoriteUseCase.
     */
    suspend fun isFavorited(userId: String, recipeId: String): Boolean = try {
        val snapshot = usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .document(recipeId)
            .get()
            .await()
        snapshot.exists()
    } catch (e: Exception) {
        false
    }

    fun observeFavorites(userId: String): Flow<Resource<List<Favorite>>> = callbackFlow {
        trySend(Resource.Loading())
        val listener = usersCol
            .document(userId)
            .collection(Constants.COLLECTION_FAVORITES)
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(Resource.Error(err.localizedMessage ?: "Snapshot error"))
                    return@addSnapshotListener
                }
                trySend(Resource.Success(snap?.toObjects(Favorite::class.java) ?: emptyList()))
            }
        awaitClose { listener.remove() }
    }
}