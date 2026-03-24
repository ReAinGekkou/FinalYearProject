package com.example.finalyearproject.data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

// ─────────────────────────────────────────────────────────────────────────────
// User.kt
// Firestore collection: "users"
// ─────────────────────────────────────────────────────────────────────────────
@Parcelize
data class User(
    @DocumentId
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val recipeCount: Int = 0,
    val isVerified: Boolean = false,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) : Parcelable {

    /**
     * Firestore requires a no-arg constructor for deserialization.
     * Kotlin data class default values satisfy this requirement.
     *
     * Convert to Map for Firestore set/update operations.
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "displayName" to displayName,
        "profileImageUrl" to profileImageUrl,
        "bio" to bio,
        "followerCount" to followerCount,
        "followingCount" to followingCount,
        "recipeCount" to recipeCount,
        "isVerified" to isVerified
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Recipe.kt
// Firestore collection: "recipes"
// ─────────────────────────────────────────────────────────────────────────────
@Parcelize
data class Recipe(
    @DocumentId
    val recipeId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val category: String = "",           // e.g. "Breakfast", "Dinner", "Dessert"
    val cuisineType: String = "",        // e.g. "Italian", "Asian"
    val ingredients: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val prepTimeMinutes: Int = 0,
    val cookTimeMinutes: Int = 0,
    val servings: Int = 1,
    val calories: Int? = null,
    val difficulty: RecipeDifficulty = RecipeDifficulty.MEDIUM,
    val tags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val reviewCount: Int = 0,
    val averageRating: Double = 0.0,
    val isFeatured: Boolean = false,
    val isPublished: Boolean = true,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) : Parcelable {

    fun toMap(): Map<String, Any?> = mapOf(
        "authorId" to authorId,
        "authorName" to authorName,
        "authorImageUrl" to authorImageUrl,
        "title" to title,
        "description" to description,
        "imageUrl" to imageUrl,
        "category" to category,
        "cuisineType" to cuisineType,
        "ingredients" to ingredients,
        "instructions" to instructions,
        "prepTimeMinutes" to prepTimeMinutes,
        "cookTimeMinutes" to cookTimeMinutes,
        "servings" to servings,
        "calories" to calories,
        "difficulty" to difficulty.name,
        "tags" to tags,
        "likeCount" to likeCount,
        "reviewCount" to reviewCount,
        "averageRating" to averageRating,
        "isFeatured" to isFeatured,
        "isPublished" to isPublished
    )

    /** Total time helper */
    val totalTimeMinutes: Int get() = prepTimeMinutes + cookTimeMinutes
}

enum class RecipeDifficulty { EASY, MEDIUM, HARD }


// ─────────────────────────────────────────────────────────────────────────────
// Blog.kt
// Firestore collection: "blogs"
// ─────────────────────────────────────────────────────────────────────────────
@Parcelize
data class Blog(
    @DocumentId
    val blogId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String = "",
    val content: String = "",
    val coverImageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val category: String = "",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val readTimeMinutes: Int = 0,
    val isPublished: Boolean = true,
    val isFeatured: Boolean = false,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) : Parcelable {

    fun toMap(): Map<String, Any?> = mapOf(
        "authorId" to authorId,
        "authorName" to authorName,
        "authorImageUrl" to authorImageUrl,
        "title" to title,
        "content" to content,
        "coverImageUrl" to coverImageUrl,
        "tags" to tags,
        "category" to category,
        "likeCount" to likeCount,
        "commentCount" to commentCount,
        "readTimeMinutes" to readTimeMinutes,
        "isPublished" to isPublished,
        "isFeatured" to isFeatured
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Review.kt
// Firestore sub-collection: "recipes/{recipeId}/reviews"
// ─────────────────────────────────────────────────────────────────────────────
@Parcelize
data class Review(
    @DocumentId
    val reviewId: String = "",
    val recipeId: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val reviewerImageUrl: String? = null,
    val rating: Float = 0f,             // 1.0 – 5.0
    val comment: String = "",
    val imageUrls: List<String> = emptyList(),
    val likeCount: Int = 0,
    val isEdited: Boolean = false,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) : Parcelable {

    fun toMap(): Map<String, Any?> = mapOf(
        "recipeId" to recipeId,
        "reviewerId" to reviewerId,
        "reviewerName" to reviewerName,
        "reviewerImageUrl" to reviewerImageUrl,
        "rating" to rating,
        "comment" to comment,
        "imageUrls" to imageUrls,
        "likeCount" to likeCount,
        "isEdited" to isEdited
    )
}


// ─────────────────────────────────────────────────────────────────────────────
// Favorite.kt
// Firestore collection: "users/{uid}/favorites"
// ─────────────────────────────────────────────────────────────────────────────
@Parcelize
data class Favorite(
    @DocumentId
    val favoriteId: String = "",
    val userId: String = "",
    val recipeId: String = "",
    val recipeTitle: String = "",
    val recipeImageUrl: String? = null,
    val recipeAuthorName: String = "",
    @ServerTimestamp
    val savedAt: Timestamp? = null
) : Parcelable {

    fun toMap(): Map<String, Any?> = mapOf(
        "userId" to userId,
        "recipeId" to recipeId,
        "recipeTitle" to recipeTitle,
        "recipeImageUrl" to recipeImageUrl,
        "recipeAuthorName" to recipeAuthorName
    )
}