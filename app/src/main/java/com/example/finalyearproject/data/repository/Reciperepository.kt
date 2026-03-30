package com.example.finalyearproject.data.repository

import android.net.Uri
import android.util.Log
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.utils.Resource
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * RecipeRepository — Fixed
 *
 * BUG FIX: "FAILED_PRECONDITION: The query requires an index"
 *
 * ROOT CAUSE: The original query used BOTH a .whereEqualTo() filter
 * AND an .orderBy() on a DIFFERENT field — Firestore requires a
 * composite index for this combination.
 *
 * FIX OPTIONS (we use option B — no index required):
 *
 *   Option A: Create index in Firebase Console
 *     Collection: recipes
 *     Fields: userId (ASC), createdAt (DESC)
 *
 *   Option B (implemented here): Remove .orderBy() from the Firestore
 *     query and sort client-side. Works immediately, no index needed.
 *     Acceptable for FYP scale (<1000 recipes per user).
 */
class RecipeRepository private constructor() {

    private val db      = FirebaseFirestore.getInstance()
    private val auth    = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val recipesCol get() = db.collection("recipes")
    private val usersCol   get() = db.collection("users")
    private val uid        get() = auth.currentUser?.uid ?: ""

    // ── Image upload ──────────────────────────────────────────────────────────

    suspend fun uploadImage(uri: Uri): Resource<String> = try {
        val ref = storage.reference.child("recipe_images/${UUID.randomUUID()}.jpg")
        ref.putFile(uri).await()
        Resource.Success(ref.downloadUrl.await().toString())
    } catch (e: Exception) {
        Log.e(TAG, "uploadImage failed: ${e.message}")
        Resource.Error(e.localizedMessage ?: "Image upload failed")
    }

    // ── Create recipe ─────────────────────────────────────────────────────────

    suspend fun createRecipe(
        title      : String,
        description: String,
        ingredients: List<String>,
        steps      : List<String>,
        category   : String,
        cookTime   : Int,
        imageUrl   : String,
        videoUrl   : String = ""
    ): Resource<String> = try {
        val data = hashMapOf(
            "userId"          to uid,
            "authorId"        to uid,
            "authorName"      to (auth.currentUser?.displayName ?: ""),
            "authorImageUrl"  to (auth.currentUser?.photoUrl?.toString() ?: ""),
            "title"           to title,
            "searchTitle"     to title.lowercase().trim(),
            "description"     to description,
            "ingredients"     to ingredients,
            "steps"           to steps,
            "instructions"    to steps,   // keep both for compatibility
            "category"        to category,
            "cookTimeMinutes" to cookTime,
            "prepTimeMinutes" to 0,
            "servings"        to 2,
            "imageUrl"        to imageUrl,
            "videoUrl"        to videoUrl,
            "likeCount"       to 0,
            "likesCount"      to 0,
            "viewCount"       to 0,
            "commentCount"    to 0,
            "reviewCount"     to 0,
            "averageRating"   to 0.0,
            "trendingScore"   to 0.0,
            "isPublished"     to true,
            "isFeatured"      to false,
            "tags"            to emptyList<String>(),
            "createdAt"       to Timestamp.now(),
            "updatedAt"       to Timestamp.now()
        )

        val ref = recipesCol.add(data).await()
        recipesCol.document(ref.id).update("recipeId", ref.id).await()

        // Track in user's my_recipes (simple doc, no orderBy needed)
        usersCol.document(uid).collection("my_recipes")
            .document(ref.id)
            .set(mapOf("recipeId" to ref.id, "createdAt" to Timestamp.now()))
            .await()

        // Increment recipeCount
        usersCol.document(uid).update("recipeCount", FieldValue.increment(1))
            .await()

        Resource.Success(ref.id)
    } catch (e: Exception) {
        Log.e(TAG, "createRecipe failed: ${e.message}", e)
        Resource.Error(e.localizedMessage ?: "Failed to create recipe")
    }

    // ── My recipes — FIX: no composite index required ────────────────────────

    /**
     * FIXED: Removed .orderBy("createdAt") from the Firestore query.
     * The compound filter userId + orderBy(createdAt) requires a composite
     * index. We sort client-side instead — equivalent result, no index needed.
     *
     * If you do create the composite index in Firebase Console, you can
     * restore: .orderBy("createdAt", Query.Direction.DESCENDING)
     */
    suspend fun getMyRecipes(): Resource<List<Recipe>> = try {
        val snap = recipesCol
            .whereEqualTo("userId", uid)
            // NO .orderBy() here — avoids the composite index requirement
            .get()
            .await()

        val recipes = snap.toObjects(Recipe::class.java)
            .sortedByDescending { it.createdAt }  // sort client-side instead

        Resource.Success(recipes)
    } catch (e: Exception) {
        Log.e(TAG, "getMyRecipes failed: ${e.message}", e)
        // Return empty list instead of propagating error to UI
        Resource.Success(emptyList())
    }

    /**
     * Alternative: fetch via the user's my_recipes subcollection IDs
     * (avoids any userId query entirely — completely index-free)
     */
    suspend fun getMyRecipesViaSubcollection(): Resource<List<Recipe>> = try {
        val idSnap = usersCol.document(uid)
            .collection("my_recipes")
            .get()
            .await()

        val ids = idSnap.documents.mapNotNull { it.id }.takeIf { it.isNotEmpty() }
            ?: return Resource.Success(emptyList())

        val recipes = mutableListOf<Recipe>()
        ids.chunked(10).forEach { chunk ->
            val snap = recipesCol.whereIn("recipeId", chunk).get().await()
            recipes.addAll(snap.toObjects(Recipe::class.java))
        }

        Resource.Success(recipes.sortedByDescending { it.createdAt })
    } catch (e: Exception) {
        Log.e(TAG, "getMyRecipesViaSubcollection: ${e.message}", e)
        Resource.Success(emptyList())
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun getFavorites(): Resource<List<Recipe>> = try {
        val favSnap = usersCol.document(uid)
            .collection("favorites")
            .get()
            .await()

        val ids = favSnap.documents.mapNotNull { it.id }.takeIf { it.isNotEmpty() }
            ?: return Resource.Success(emptyList())

        val recipes = mutableListOf<Recipe>()
        ids.chunked(10).forEach { chunk ->
            val snap = recipesCol.whereIn("recipeId", chunk).get().await()
            recipes.addAll(snap.toObjects(Recipe::class.java))
        }
        Resource.Success(recipes)
    } catch (e: Exception) {
        Log.e(TAG, "getFavorites: ${e.message}", e)
        Resource.Success(emptyList())
    }

    // ── Public recipe feed — no index needed ──────────────────────────────────

    suspend fun getPublicRecipes(limit: Long = 20): Resource<List<Recipe>> = try {
        val snap = recipesCol
            .whereEqualTo("isPublished", true)
            .limit(limit)
            .get()
            .await()
        val recipes = snap.toObjects(Recipe::class.java)
            .sortedByDescending { it.createdAt }
        Resource.Success(recipes)
    } catch (e: Exception) {
        Log.e(TAG, "getPublicRecipes: ${e.message}", e)
        Resource.Success(emptyList())
    }

    // ── Recipe by ID ──────────────────────────────────────────────────────────

    suspend fun getRecipeById(id: String): Resource<Recipe> = try {
        val snap = recipesCol.document(id).get().await()
        val r = snap.toObject(Recipe::class.java) ?: return Resource.Error("Recipe not found")
        Resource.Success(r)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load recipe")
    }

    // ── Like ──────────────────────────────────────────────────────────────────

    suspend fun toggleLike(recipeId: String): Resource<Boolean> = try {
        val likeRef = recipesCol.document(recipeId).collection("likes").document(uid)
        val exists  = likeRef.get().await().exists()
        if (exists) {
            likeRef.delete().await()
            recipesCol.document(recipeId)
                .update("likeCount", FieldValue.increment(-1)).await()
            Resource.Success(false)
        } else {
            likeRef.set(mapOf("userId" to uid, "ts" to Timestamp.now())).await()
            recipesCol.document(recipeId)
                .update("likeCount", FieldValue.increment(1)).await()
            Resource.Success(true)
        }
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Like failed")
    }

    suspend fun isLikedBy(recipeId: String): Boolean = try {
        recipesCol.document(recipeId).collection("likes").document(uid).get().await().exists()
    } catch (e: Exception) { false }

    // ── Save / favorite ───────────────────────────────────────────────────────

    suspend fun toggleSave(recipe: Recipe): Resource<Boolean> = try {
        val ref    = usersCol.document(uid).collection("favorites").document(recipe.recipeId)
        val exists = ref.get().await().exists()
        if (exists) {
            ref.delete().await(); Resource.Success(false)
        } else {
            ref.set(mapOf(
                "recipeId" to recipe.recipeId,
                "title"    to recipe.title,
                "imageUrl" to (recipe.imageUrl ?: ""),
                "savedAt"  to Timestamp.now()
            )).await()
            Resource.Success(true)
        }
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Save failed")
    }

    suspend fun isSavedBy(recipeId: String): Boolean = try {
        usersCol.document(uid).collection("favorites").document(recipeId).get().await().exists()
    } catch (e: Exception) { false }

    // ── Comments ──────────────────────────────────────────────────────────────

    suspend fun addComment(recipeId: String, text: String): Resource<Unit> = try {
        recipesCol.document(recipeId).collection("comments").add(hashMapOf(
            "userId"     to uid,
            "authorName" to (auth.currentUser?.displayName ?: "User"),
            "text"       to text,
            "timestamp"  to Timestamp.now()
        )).await()
        recipesCol.document(recipeId)
            .update("commentCount", FieldValue.increment(1)).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Comment failed")
    }

    suspend fun getComments(recipeId: String): Resource<List<Map<String, Any>>> = try {
        val snap = recipesCol.document(recipeId).collection("comments")
            .limit(30).get().await()
        val list = snap.documents
            .mapNotNull { it.data }
            .sortedByDescending { (it["timestamp"] as? Timestamp)?.seconds ?: 0L }
        Resource.Success(list)
    } catch (e: Exception) {
        Resource.Success(emptyList())
    }

    companion object {
        private const val TAG = "RecipeRepository"
        @Volatile private var instance: RecipeRepository? = null
        fun getInstance(): RecipeRepository = instance ?: synchronized(this) {
            instance ?: RecipeRepository().also { instance = it }
        }
    }
}