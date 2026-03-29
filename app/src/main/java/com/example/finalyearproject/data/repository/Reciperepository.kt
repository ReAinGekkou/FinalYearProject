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

class RecipeRepository {

    private val db      = FirebaseFirestore.getInstance()
    private val auth    = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val recipesCol get() = db.collection("recipes")
    private val usersCol   get() = db.collection("users")
    private val uid        get() = auth.currentUser?.uid ?: ""

    // ── Upload image ──────────────────────────────────────────────────────────

    suspend fun uploadImage(uri: Uri): Resource<String> = try {
        val ref = storage.reference.child("recipe_images/${UUID.randomUUID()}.jpg")
        ref.putFile(uri).await()
        Resource.Success(ref.downloadUrl.await().toString())
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Image upload failed")
    }

    // ── Create recipe ─────────────────────────────────────────────────────────

    suspend fun createRecipe(
        title       : String,
        description : String,
        ingredients : List<String>,
        steps       : List<String>,
        category    : String,
        cookTime    : Int,
        imageUrl    : String,
        videoUrl    : String = ""
    ): Resource<String> = try {
        val data = hashMapOf(
            "userId"       to uid,
            "authorName"   to (auth.currentUser?.displayName ?: ""),
            "title"        to title,
            "searchTitle"  to title.lowercase().trim(),
            "description"  to description,
            "ingredients"  to ingredients,
            "steps"        to steps,
            "category"     to category,
            "cookTimeMinutes" to cookTime,
            "imageUrl"     to imageUrl,
            "videoUrl"     to videoUrl,
            "likesCount"   to 0,
            "likeCount"    to 0,
            "viewCount"    to 0,
            "commentCount" to 0,
            "isPublished"  to true,
            "isFeatured"   to false,
            "trendingScore" to 0.0,
            "averageRating" to 0.0,
            "reviewCount"  to 0,
            "tags"         to emptyList<String>(),
            "createdAt"    to Timestamp.now(),
            "updatedAt"    to Timestamp.now()
        )

        val ref = recipesCol.add(data).await()

        // Write recipeId back into the document
        recipesCol.document(ref.id).update("recipeId", ref.id).await()

        // Add to user's my_recipes subcollection
        usersCol.document(uid).collection("my_recipes")
            .document(ref.id)
            .set(mapOf("recipeId" to ref.id, "createdAt" to Timestamp.now()))
            .await()

        // Increment user's recipeCount
        usersCol.document(uid)
            .update("recipeCount", FieldValue.increment(1))
            .await()

        Resource.Success(ref.id)
    } catch (e: Exception) {
        Log.e("RecipeRepo", "createRecipe failed: ${e.message}", e)
        Resource.Error(e.localizedMessage ?: "Failed to create recipe")
    }

    // ── My recipes ────────────────────────────────────────────────────────────

    suspend fun getMyRecipes(): Resource<List<Recipe>> = try {
        val snap = recipesCol
            .whereEqualTo("userId", uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        Resource.Success(snap.toObjects(Recipe::class.java))
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load your recipes")
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    suspend fun getFavorites(): Resource<List<Recipe>> = try {
        val favSnap = usersCol.document(uid)
            .collection("favorites")
            .orderBy("savedAt", Query.Direction.DESCENDING)
            .limit(30)
            .get().await()

        val ids = favSnap.documents.mapNotNull { it.id }.takeIf { it.isNotEmpty() }
            ?: return Resource.Success(emptyList())

        val recipes = mutableListOf<Recipe>()
        ids.chunked(10).forEach { chunk ->
            val snap = recipesCol.whereIn("recipeId", chunk).get().await()
            recipes.addAll(snap.toObjects(Recipe::class.java))
        }
        Resource.Success(recipes)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load favorites")
    }

    // ── Recipe detail ─────────────────────────────────────────────────────────

    suspend fun getRecipeById(recipeId: String): Resource<Recipe> = try {
        val snap = recipesCol.document(recipeId).get().await()
        val r = snap.toObject(Recipe::class.java)
            ?: return Resource.Error("Recipe not found")
        Resource.Success(r)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load recipe")
    }

    // ── Like ──────────────────────────────────────────────────────────────────

    suspend fun toggleLike(recipeId: String): Resource<Boolean> = try {
        val likeRef = recipesCol.document(recipeId)
            .collection("likes").document(uid)
        val snap = likeRef.get().await()

        if (snap.exists()) {
            likeRef.delete().await()
            recipesCol.document(recipeId)
                .update("likeCount", FieldValue.increment(-1)).await()
            Resource.Success(false)
        } else {
            likeRef.set(mapOf("userId" to uid, "timestamp" to Timestamp.now())).await()
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
        val favRef = usersCol.document(uid).collection("favorites").document(recipe.recipeId)
        val exists = favRef.get().await().exists()
        if (exists) {
            favRef.delete().await()
            Resource.Success(false)
        } else {
            favRef.set(mapOf(
                "recipeId"  to recipe.recipeId,
                "title"     to recipe.title,
                "imageUrl"  to (recipe.imageUrl ?: ""),
                "savedAt"   to Timestamp.now()
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
        val comment = hashMapOf(
            "userId"      to uid,
            "authorName"  to (auth.currentUser?.displayName ?: "User"),
            "text"        to text,
            "timestamp"   to Timestamp.now()
        )
        recipesCol.document(recipeId).collection("comments").add(comment).await()
        recipesCol.document(recipeId)
            .update("commentCount", FieldValue.increment(1)).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Comment failed")
    }

    suspend fun getComments(recipeId: String): Resource<List<Map<String, Any>>> = try {
        val snap = recipesCol.document(recipeId).collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(30).get().await()
        Resource.Success(snap.documents.map { it.data ?: emptyMap() })
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to load comments")
    }

    companion object {
        @Volatile private var instance: RecipeRepository? = null
        fun getInstance() = instance ?: synchronized(this) {
            instance ?: RecipeRepository().also { instance = it }
        }
    }
}