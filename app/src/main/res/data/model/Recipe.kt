package data.model

import android.os.Parcelable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

/**
 * Recipe — Day 7 updated
 * Added viewCount field used by trending + AI scoring.
 */
@Parcelize
data class Recipe(
    @DocumentId
    val recipeId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorImageUrl: String? = null,
    val title: String = "",
    val searchTitle: String = "",          // lowercase title for Firestore range query
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
    val difficulty: RecipeDifficulty = RecipeDifficulty.MEDIUM,
    val likeCount: Int = 0,
    val viewCount: Int = 0,                // tracked via user_activity
    val commentCount: Int = 0,
    val reviewCount: Int = 0,
    val averageRating: Double = 0.0,
    val trendingScore: Double = 0.0,       // pre-computed: likes*0.6 + comments*0.2 + views*0.2
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
        "difficulty"      to difficulty.name,
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

/*
═══════════════════════════════════════════════════════════════════════════════
 FIRESTORE FULL SCHEMA (Day 7)
═══════════════════════════════════════════════════════════════════════════════

collection: recipes/{recipeId}
  id              : String
  recipeId        : String          (same as doc id — for easy Parcel)
  title           : String
  searchTitle     : String          (lowercase title for range queries)
  description     : String
  imageUrl        : String?
  category        : String          ("Soup"|"Rice"|"Noodle"|...)
  cuisineType     : String
  tags            : List<String>    (["vegan","quick","protein",...])
  ingredients     : List<String>
  instructions    : List<String>
  prepTimeMinutes : Int
  cookTimeMinutes : Int
  servings        : Int
  calories        : Int?
  difficulty      : String          ("EASY"|"MEDIUM"|"HARD")
  likeCount       : Int
  viewCount       : Int
  commentCount    : Int
  reviewCount     : Int
  averageRating   : Double
  trendingScore   : Double          (pre-computed: likes*0.6+comments*0.2+views*0.2)
  isFeatured      : Boolean
  isPublished     : Boolean
  authorId        : String
  authorName      : String
  authorImageUrl  : String?
  createdAt       : Timestamp
  updatedAt       : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: users/{uid}
  uid             : String
  displayName     : String
  email           : String
  profileImageUrl : String?
  bio             : String?
  followerCount   : Int
  followingCount  : Int
  recipeCount     : Int
  isAdmin         : Boolean
  createdAt       : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: user_activity/{activityId}
  userId          : String
  recipeId        : String
  actionType      : String     ("view" | "like" | "save" | "comment")
  timestamp       : Timestamp

  Indexes required:
    - userId + actionType (for recommendation engine)
    - userId + timestamp  (for history)

───────────────────────────────────────────────────────────────────────────────

collection: follows/{followId}
  followerId      : String
  followingId     : String
  timestamp       : Timestamp

  Composite index: followerId + timestamp (for following feed)

───────────────────────────────────────────────────────────────────────────────

collection: users/{uid}/favorites/{recipeId}
  userId          : String
  recipeId        : String
  recipeTitle     : String
  recipeImageUrl  : String?
  savedAt         : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: recipes/{recipeId}/reviews/{reviewId}
  reviewerId      : String
  reviewerName    : String
  reviewerImageUrl: String?
  rating          : Float    (1.0–5.0)
  comment         : String
  createdAt       : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: blogs/{blogId}
  authorId        : String
  authorName      : String
  title           : String
  content         : String
  coverImageUrl   : String?
  tags            : List<String>
  likeCount       : Int
  commentCount    : Int
  isPublished     : Boolean
  createdAt       : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: chats/{chatId}/messages/{messageId}
  text            : String
  senderId        : String
  senderName      : String
  senderAvatar    : String?
  timestamp       : Timestamp

───────────────────────────────────────────────────────────────────────────────

collection: collections/{collectionId}
  userId          : String
  name            : String
  createdAt       : Timestamp

  sub-collection: collections/{collectionId}/items/{itemId}
    recipeId      : String
    addedAt       : Timestamp

═══════════════════════════════════════════════════════════════════════════════
 FIRESTORE SECURITY RULES (paste into Firebase Console)
═══════════════════════════════════════════════════════════════════════════════

rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // Recipes: anyone logged in can read; author can write
    match /recipes/{recipeId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update: if request.auth.uid == resource.data.authorId
                    || get(/databases/$(database)/documents/users/$(request.auth.uid)).data.isAdmin == true;
      allow delete: if request.auth.uid == resource.data.authorId
                    || get(/databases/$(database)/documents/users/$(request.auth.uid)).data.isAdmin == true;

      match /reviews/{reviewId} {
        allow read: if request.auth != null;
        allow create: if request.auth != null;
        allow delete: if request.auth.uid == resource.data.reviewerId;
      }
    }

    // Users: anyone logged in can read; only self can write
    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == uid;

      match /favorites/{recipeId} {
        allow read, write: if request.auth.uid == uid;
      }
    }

    // Activity: users can write their own; read restricted to self
    match /user_activity/{activityId} {
      allow create: if request.auth != null && request.resource.data.userId == request.auth.uid;
      allow read: if request.auth.uid == resource.data.userId;
    }

    // Blogs
    match /blogs/{blogId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
      allow update, delete: if request.auth.uid == resource.data.authorId;
    }

    // Follows
    match /follows/{followId} {
      allow read: if request.auth != null;
      allow create: if request.auth.uid == request.resource.data.followerId;
      allow delete: if request.auth.uid == resource.data.followerId;
    }

    // Chat
    match /chats/{chatId}/messages/{messageId} {
      allow read, write: if request.auth != null;
    }
  }
}

═══════════════════════════════════════════════════════════════════════════════
 REQUIRED FIRESTORE INDEXES (create in Firebase Console → Indexes)
═══════════════════════════════════════════════════════════════════════════════

1. Collection: recipes
   Fields: isPublished (ASC), createdAt (DESC)

2. Collection: recipes
   Fields: isPublished (ASC), category (ASC), createdAt (DESC)

3. Collection: recipes
   Fields: isPublished (ASC), averageRating (DESC)

4. Collection: recipes
   Fields: isPublished (ASC), trendingScore (DESC)

5. Collection: recipes
   Fields: isPublished (ASC), searchTitle (ASC), searchTitle (DESC)

6. Collection: user_activity
   Fields: userId (ASC), actionType (ASC), timestamp (DESC)

7. Collection: user_activity
   Fields: recipeId (ASC), actionType (ASC)

8. Collection: follows
   Fields: followerId (ASC), timestamp (DESC)

*/