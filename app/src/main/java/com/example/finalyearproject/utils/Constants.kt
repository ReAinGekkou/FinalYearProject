package com.example.finalyearproject.utils
import com.example.finalyearproject.BuildConfig
object Constants {

    // ── Firestore Collection Names ────────────────────────────────────────────
    const val COLLECTION_USERS     = "users"
    const val COLLECTION_RECIPES   = "recipes"
    const val COLLECTION_BLOGS     = "blogs"
    const val COLLECTION_REVIEWS   = "reviews"
    const val COLLECTION_FAVORITES = "favorites"
    const val COLLECTION_COMMENTS  = "comments"

    // ── Firebase Storage Folders ──────────────────────────────────────────────
    const val STORAGE_PROFILE_IMAGES = "profile_images"
    const val STORAGE_RECIPE_IMAGES  = "recipe_images"
    const val STORAGE_BLOG_IMAGES    = "blog_covers"
    const val STORAGE_REVIEW_IMAGES  = "review_images"

    // ── AI API Configuration ──────────────────────────────────────────────────
    // Replace with your provider's base URL:
    //   OpenAI:      "https://api.openai.com/v1/"
    //   Groq:        "https://api.groq.com/openai/v1/"
    //   Together.ai: "https://api.together.xyz/v1/"
    //   Mistral:     "https://api.mistral.ai/v1/"
    const val AI_BASE_URL       = "https://generativelanguage.googleapis.com/v1beta/"
    const val AI_ENDPOINT_QUERY = "models/gemini-2.0-flash:generateContent"
    const val AI_MODEL          = "gemini-2.0-flash"
    // ⚠️ In production: read from BuildConfig.AI_API_KEY (set in local.properties)
    val AI_API_KEY              = BuildConfig.GEMINI_API_KEY  // ← clean and simple

    // ── Pagination ────────────────────────────────────────────────────────────
    const val PAGE_SIZE_RECIPES   = 20L
    const val PAGE_SIZE_BLOGS     = 15L
    const val PAGE_SIZE_REVIEWS   = 30L

    // ── SharedPreferences Keys ────────────────────────────────────────────────
    const val PREFS_NAME                 = "ai_food_prefs"
    const val PREF_ONBOARDING_COMPLETE   = "onboarding_complete"
    const val PREF_THEME_MODE            = "theme_mode"

    // ── Navigation Argument Keys ──────────────────────────────────────────────
    const val ARG_RECIPE_ID   = "recipeId"
    const val ARG_BLOG_ID     = "blogId"
    const val ARG_USER_ID     = "userId"

    // ── Image Compression ─────────────────────────────────────────────────────
    const val IMAGE_QUALITY         = 85   // JPEG quality (0-100)
    const val MAX_IMAGE_DIMENSION   = 1080 // px — resize before upload

    // ── Recipe Constraints ────────────────────────────────────────────────────
    const val MAX_RECIPE_TITLE_LENGTH       = 80
    const val MAX_RECIPE_DESCRIPTION_LENGTH = 500
    const val MAX_INGREDIENTS               = 50
    const val MAX_INSTRUCTIONS              = 30

    // ── Review Constraints ────────────────────────────────────────────────────
    const val MIN_RATING = 1.0f
    const val MAX_RATING = 5.0f
    const val MAX_REVIEW_IMAGES = 3

    // ── Timeout (seconds) ─────────────────────────────────────────────────────
    const val NETWORK_CONNECT_TIMEOUT = 30L
    const val NETWORK_READ_TIMEOUT    = 60L
    const val NETWORK_WRITE_TIMEOUT   = 30L

}