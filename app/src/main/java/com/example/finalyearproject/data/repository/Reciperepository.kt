package com.example.finalyearproject.data.repository

import android.content.Context
import android.net.Uri
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.RecipeFilter
import com.example.finalyearproject.data.model.PaginatedResult
import com.example.finalyearproject.data.model.Review
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class RecipeRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance(),
    private val context: Context? = null
) {
    private val isOnline: Boolean
        get() = context?.let {
            val cm = it.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } ?: true

    private val authRepository = AuthRepository.getInstance()
    private val userRepository = UserRepository.getInstance()
    private val uid get() = authRepository.currentUserId ?: ""

    // ── Compatibility & Helper Methods (from old Reciperepository.kt) ──────────

    suspend fun uploadImage(uri: Uri): Resource<String> = safeCall(AppLogger.TAG_REPO, "uploadImage") {
        val storage = FirebaseStorage.getInstance()
        val ref = storage.reference.child("recipe_images/${UUID.randomUUID()}.jpg")
        ref.putFile(uri).await()
        Resource.Success(ref.downloadUrl.await().toString())
    }

    suspend fun createRecipe(
        title: String,
        description: String,
        ingredients: List<String>,
        steps: List<String>,
        category: String,
        cookTime: Int,
        imageUrl: String,
        videoUrl: String = ""
    ): Resource<String> = safeCall(AppLogger.TAG_REPO, "createRecipe") {
        val data = hashMapOf(
            "userId"       to uid,
            "authorName"   to (FirebaseAuth.getInstance().currentUser?.displayName ?: ""),
            "title"        to title,
            "searchTitle"  to title.lowercase().trim(),
            "description"  to description,
            "ingredients"  to ingredients,
            "instructions" to steps, // Map steps to instructions field in Recipe model
            "category"     to category,
            "cookTimeMinutes" to cookTime,
            "imageUrl"     to imageUrl,
            "videoUrl"     to videoUrl,
            "likeCount"    to 0,
            "viewCount"    to 0,
            "commentCount" to 0,
            "isPublished"  to true,
            "createdAt"    to Timestamp.now(),
            "updatedAt"    to Timestamp.now()
        )

        val db = FirebaseFirestore.getInstance()
        val ref = db.collection(Constants.COLLECTION_RECIPES).add(data).await()

        // Write recipeId back into the document
        db.collection(Constants.COLLECTION_RECIPES).document(ref.id).update("recipeId", ref.id).await()

        // Add to user's my_recipes subcollection
        db.collection(Constants.COLLECTION_USERS).document(uid).collection("my_recipes")
            .document(ref.id)
            .set(mapOf("recipeId" to ref.id, "createdAt" to Timestamp.now()))
            .await()

        // Increment user's recipeCount
        db.collection(Constants.COLLECTION_USERS).document(uid)
            .update("recipeCount", FieldValue.increment(1))
            .await()

        AppCache.invalidateRecipes()
        Resource.Success(ref.id)
    }

    suspend fun getMyRecipes(): Resource<List<Recipe>> = getRecipesByAuthor(uid)

    suspend fun getFavorites(): Resource<List<Recipe>> = safeCall(AppLogger.TAG_REPO, "getFavorites") {
        val favs = userRepository.getFavorites(uid)
        if (favs is Resource.Success) {
            val recipes = mutableListOf<Recipe>()
            for (f in favs.data) {
                val r = getRecipeById(f.recipeId)
                if (r is Resource.Success) recipes.add(r.data)
            }
            Resource.Success(recipes)
        } else Resource.Error((favs as Resource.Error).message)
    }

    suspend fun toggleLike(recipeId: String): Resource<Boolean> = safeCall(AppLogger.TAG_REPO, "toggleLike") {
        val db = FirebaseFirestore.getInstance()
        val likeRef = db.collection(Constants.COLLECTION_RECIPES).document(recipeId)
            .collection("likes").document(uid)
        val snap = likeRef.get().await()

        if (snap.exists()) {
            likeRef.delete().await()
            db.collection(Constants.COLLECTION_RECIPES).document(recipeId)
                .update("likeCount", FieldValue.increment(-1)).await()
            Resource.Success(false)
        } else {
            likeRef.set(mapOf("userId" to uid, "timestamp" to Timestamp.now())).await()
            db.collection(Constants.COLLECTION_RECIPES).document(recipeId)
                .update("likeCount", FieldValue.increment(1)).await()
            Resource.Success(true)
        }
    }

    suspend fun isLikedBy(recipeId: String): Boolean = try {
        FirebaseFirestore.getInstance().collection(Constants.COLLECTION_RECIPES).document(recipeId)
            .collection("likes").document(uid).get().await().exists()
    } catch (e: Exception) { false }

    suspend fun toggleSave(recipe: Recipe): Resource<Boolean> = safeCall(AppLogger.TAG_REPO, "toggleSave") {
        val isFav = userRepository.isFavorited(uid, recipe.recipeId)
        if (isFav) {
            userRepository.removeFavorite(uid, recipe.recipeId)
            Resource.Success(false)
        } else {
            val fav = Favorite(
                userId = uid,
                recipeId = recipe.recipeId,
                recipeTitle = recipe.title,
                recipeImageUrl = recipe.imageUrl
            )
            userRepository.addFavorite(fav)
            Resource.Success(true)
        }
    }

    suspend fun isSavedBy(recipeId: String): Boolean = try {
        userRepository.isFavorited(uid, recipeId)
    } catch (e: Exception) { false }

    suspend fun addComment(recipeId: String, text: String): Resource<Unit> = safeCall(AppLogger.TAG_REPO, "addComment") {
        val comment = hashMapOf(
            "userId"      to uid,
            "authorName"  to (FirebaseAuth.getInstance().currentUser?.displayName ?: "User"),
            "text"        to text,
            "timestamp"   to Timestamp.now()
        )
        FirebaseFirestore.getInstance().collection(Constants.COLLECTION_RECIPES)
            .document(recipeId).collection("comments").add(comment).await()
        FirebaseFirestore.getInstance().collection(Constants.COLLECTION_RECIPES)
            .document(recipeId).update("commentCount", FieldValue.increment(1)).await()
        Resource.Success(Unit)
    }

    suspend fun getComments(recipeId: String): Resource<List<Map<String, Any>>> = safeCall(AppLogger.TAG_REPO, "getComments") {
        val snap = FirebaseFirestore.getInstance().collection(Constants.COLLECTION_RECIPES)
            .document(recipeId).collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30).get().await()
        Resource.Success(snap.documents.map { it.data ?: emptyMap() })
    }

    // ── Original RecipeRepository Methods (from Repositories.kt) ───────────────

    suspend fun addRecipe(recipe: Recipe): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addRecipe") {
            validateRecipe(recipe)?.let { return@safeCall Resource.Error(it) }
            AppLogger.firestoreWrite(Constants.COLLECTION_RECIPES, "addRecipe")
            val result = firestoreService.addRecipe(recipe)
            if (result is Resource.Success) {
                AppCache.invalidateRecipes()
            }
            result
        }

    suspend fun getRecipes(
        limit: Long = Constants.PAGE_SIZE_RECIPES
    ): Resource<List<Recipe>> = networkAwareCall(
        isOnline = isOnline,
        cacheBlock = {
            AppCache.getRecipes()?.let {
                AppLogger.repoCacheHit("RecipeRepository", "getRecipes")
                Resource.Success(it)
            }
        },
        networkBlock = {
            AppLogger.repoCacheMiss("RecipeRepository", "getRecipes")
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipes(limit=$limit)")
            val result = firestoreService.getRecipes(limit)
            if (result is Resource.Success) {
                AppCache.setRecipes(result.data)
                OfflineManager.persistRecipes(result.data)
                OfflineManager.markOnline()
            }
            result
        }
    )

    fun observeRecipes(limit: Long = Constants.PAGE_SIZE_RECIPES): Flow<Resource<List<Recipe>>> {
        AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "observeRecipes (Flow)")
        return firestoreService.observeRecipes(limit)
    }

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> {
        if (recipeId.isBlank()) return Resource.Error("Invalid recipe ID.")

        return networkAwareCall(
            isOnline = isOnline,
            cacheBlock = {
                AppCache.getRecipeById(recipeId)?.let {
                    AppLogger.repoCacheHit("RecipeRepository", "getRecipeById:$recipeId")
                    Resource.Success(it)
                }
            },
            networkBlock = {
                AppLogger.repoCacheMiss("RecipeRepository", "getRecipeById:$recipeId")
                AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipeById($recipeId)")
                val result = firestoreService.getRecipeById(recipeId)
                if (result is Resource.Success) {
                    AppCache.setRecipeById(recipeId, result.data)
                }
                result
            }
        )
    }

    suspend fun getRecipesByAuthor(authorId: String): Resource<List<Recipe>> {
        if (authorId.isBlank()) return Resource.Error("Invalid author ID.")

        return networkAwareCall(
            isOnline = isOnline,
            cacheBlock = {
                AppCache.getRecipesByAuthor(authorId)?.let {
                    AppLogger.repoCacheHit("RecipeRepository", "getRecipesByAuthor:$authorId")
                    Resource.Success(it)
                }
            },
            networkBlock = {
                AppLogger.repoCacheMiss("RecipeRepository", "getRecipesByAuthor:$authorId")
                AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "getRecipesByAuthor($authorId)")
                val result = firestoreService.getRecipesByAuthor(authorId)
                if (result is Resource.Success) {
                    AppCache.setRecipesByAuthor(authorId, result.data)
                }
                result
            }
        )
    }

    suspend fun updateRecipe(recipeId: String, fields: Map<String, Any?>): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "updateRecipe") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreWrite(Constants.COLLECTION_RECIPES, "updateRecipe", recipeId)
            val result = firestoreService.updateRecipe(recipeId, fields)
            if (result is Resource.Success) AppCache.invalidateRecipeById(recipeId)
            result
        }

    suspend fun deleteRecipe(recipeId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteRecipe") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreDelete(Constants.COLLECTION_RECIPES, recipeId)
            val result = firestoreService.deleteRecipe(recipeId)
            if (result is Resource.Success) AppCache.invalidateRecipeById(recipeId)
            result
        }

    // ── Search & Filter ───────────────────────────────────────────────────────

    suspend fun searchRecipes(query: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "searchRecipes") {
            if (query.trim().length < 2)
                return@safeCall Resource.Error("Search term must be at least 2 characters.")
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "search(query=$query)")
            firestoreService.searchRecipesByTitle(query.trim())
        }

    suspend fun filterRecipes(filter: RecipeFilter): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterRecipes") {
            AppLogger.d(AppLogger.TAG_REPO, "filterRecipes: $filter")
            firestoreService.filterRecipes(filter)
        }

    suspend fun filterRecipesByRating(minRating: Double): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterRecipesByRating") {
            if (minRating < 1.0 || minRating > 5.0)
                return@safeCall Resource.Error("Rating must be between 1 and 5.")
            firestoreService.filterRecipesByRating(minRating)
        }

    suspend fun filterByCategory(category: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "filterByCategory") {
            if (category.isBlank()) return@safeCall Resource.Error("Category cannot be empty.")
            firestoreService.filterByCategory(category)
        }

    // ── Pagination ────────────────────────────────────────────────────────────

    suspend fun loadFirstPage(
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "loadFirstPage") {
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "loadFirstPage(size=$pageSize)")
            firestoreService.loadRecipesFirstPage(pageSize)
        }

    suspend fun loadNextPage(
        lastVisible: DocumentSnapshot,
        pageSize: Long = Constants.PAGINATION_PAGE_SIZE
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "loadNextPage") {
            AppLogger.firestoreRead(Constants.COLLECTION_RECIPES, "loadNextPage")
            firestoreService.loadNextRecipesPage(lastVisible, pageSize)
        }

    suspend fun searchPaginated(
        query: String,
        lastVisible: DocumentSnapshot? = null
    ): Resource<PaginatedResult<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "searchPaginated") {
            if (query.trim().length < 2)
                return@safeCall Resource.Error("Search term must be at least 2 characters.")
            firestoreService.searchRecipesPaginated(query, lastVisible)
        }

    // ── Recommendations ───────────────────────────────────────────────────────

    suspend fun getRecommendedRecipes(userId: String): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "getRecommendedRecipes") {
            AppLogger.d(AppLogger.TAG_REPO, "Building recommendations for userId=$userId")
            firestoreService.getRecommendedRecipes(userId)
        }

    suspend fun getTrendingRecipes(limit: Long = 10): Resource<List<Recipe>> =
        safeCall(AppLogger.TAG_REPO, "getTrendingRecipes") {
            firestoreService.getTrendingRecipes(limit)
        }

    // ── Reviews ───────────────────────────────────────────────────────────────

    suspend fun addReview(review: Review): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addReview") {
            if (review.comment.isBlank()) return@safeCall Resource.Error("Comment cannot be empty.")
            if (review.rating < Constants.MIN_RATING || review.rating > Constants.MAX_RATING)
                return@safeCall Resource.Error("Rating must be between 1 and 5.")
            AppLogger.firestoreWrite("recipes/${review.recipeId}/reviews", "addReview")
            firestoreService.addReview(review)
        }

    suspend fun getReviews(recipeId: String): Resource<List<Review>> =
        safeCall(AppLogger.TAG_REPO, "getReviews") {
            if (recipeId.isBlank()) return@safeCall Resource.Error("Invalid recipe ID.")
            AppLogger.firestoreRead("recipes/$recipeId/reviews", "getReviews")
            firestoreService.getReviews(recipeId)
        }

    suspend fun deleteReview(recipeId: String, reviewId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteReview") {
            firestoreService.deleteReview(recipeId, reviewId)
        }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateRecipe(recipe: Recipe): String? = when {
        recipe.title.isBlank()
            -> "Recipe title cannot be empty."
        recipe.title.length > Constants.MAX_RECIPE_TITLE_LENGTH
            -> "Title must be ${Constants.MAX_RECIPE_TITLE_LENGTH} characters or fewer."
        recipe.ingredients.isEmpty()
            -> "Add at least one ingredient."
        recipe.instructions.isEmpty()
            -> "Add at least one instruction step."
        recipe.ingredients.size > Constants.MAX_INGREDIENTS
            -> "Maximum ${Constants.MAX_INGREDIENTS} ingredients allowed."
        recipe.prepTimeMinutes < 0
            -> "Prep time cannot be negative."
        recipe.servings < 1
            -> "Servings must be at least 1."
        else -> null
    }

    companion object {
        @Volatile private var instance: RecipeRepository? = null
        fun getInstance(context: Context? = null): RecipeRepository =
            instance ?: synchronized(this) {
                instance ?: RecipeRepository(context = context).also { instance = it }
            }
    }
}
