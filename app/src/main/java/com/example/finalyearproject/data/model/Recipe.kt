package com.example.finalyearproject.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

/**
 * Recipe — fixed @DocumentId conflict
 *
 * ROOT CAUSE:
 *   Firestore's @DocumentId annotation auto-populates the field from
 *   the document ID. At the same time, createRecipe() was writing
 *   "recipeId" as a regular field inside the document body with the
 *   same value. When Firestore deserialises the document it sees
 *   "recipeId" in BOTH places and throws:
 *
 *     "'@DocumentId' conflict with field 'recipeId'"
 *
 * FIX:
 *   Remove @DocumentId from recipeId. Instead, populate recipeId from
 *   the regular Firestore field (which RecipeRepository already writes
 *   after add()). This way the field has exactly one source of truth.
 *
 *   If you query a document that was seeded WITHOUT a "recipeId" body
 *   field (i.e. older seed data), the default value "" is used safely.
 */
@Parcelize
data class Recipe(
    // No @DocumentId here — recipeId is read from the document body field.
    // RecipeRepository writes: recipesCol.document(ref.id).update("recipeId", ref.id)
    val recipeId: String = "",

    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String = "",
    val searchTitle: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val category: String = "",
    val cuisineType: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val prepTimeMinutes: Int = 0,
    val cookTimeMinutes: Int = 0,
    val servings: Int = 1,
    val calories: Int? = null,
    val difficulty: String = "MEDIUM",
    val likeCount: Int = 0,
    val viewCount: Int = 0,
    val commentCount: Int = 0,
    val reviewCount: Int = 0,
    val averageRating: Double = 0.0,
    val trendingScore: Double = 0.0,
    val isFeatured: Boolean = false,
    val isPublished: Boolean = true,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) : Parcelable {

    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes

    fun toMap(): Map<String, Any?> = mapOf(
        "authorId"        to authorId,
        "authorName"      to authorName,
        "authorImageUrl"  to authorImageUrl,
        "title"           to title,
        "searchTitle"     to title.lowercase().trim(),
        "description"     to description,
        "imageUrl"        to imageUrl,
        "category"        to category,
        "cuisineType"     to cuisineType,
        "ingredients"     to ingredients,
        "instructions"    to instructions,
        "tags"            to tags,
        "prepTimeMinutes" to prepTimeMinutes,
        "cookTimeMinutes" to cookTimeMinutes,
        "servings"        to servings,
        "calories"        to calories,
        "difficulty"      to difficulty,
        "likeCount"       to likeCount,
        "viewCount"       to viewCount,
        "commentCount"    to commentCount,
        "reviewCount"     to reviewCount,
        "averageRating"   to averageRating,
        "trendingScore"   to trendingScore,
        "isFeatured"      to isFeatured,
        "isPublished"     to isPublished
    )
}