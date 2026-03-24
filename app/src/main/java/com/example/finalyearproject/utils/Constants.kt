package com.example.finalyearproject.utils

import com.example.finalyearproject.BuildConfig

/**
 * Constants.kt — Day 3 Updated
 * Replace your existing Constants.kt with this file.
 */
object Constants {

    // ── Firestore Collections ─────────────────────────────────────────────────
    const val COLLECTION_USERS     = "users"
    const val COLLECTION_RECIPES   = "recipes"
    const val COLLECTION_BLOGS     = "blogs"
    const val COLLECTION_REVIEWS   = "reviews"
    const val COLLECTION_FAVORITES = "favorites"

    // ── Firebase Storage Folders ──────────────────────────────────────────────
    const val STORAGE_PROFILE_IMAGES = "profile_images"
    const val STORAGE_RECIPE_IMAGES  = "recipe_images"
    const val STORAGE_BLOG_IMAGES    = "blog_covers"
    const val STORAGE_REVIEW_IMAGES  = "review_images"

    // ── AI API ────────────────────────────────────────────────────────────────
    const val AI_BASE_URL       = "https://generativelanguage.googleapis.com/v1beta/"
    const val AI_ENDPOINT_QUERY = "models/gemini-2.0-flash:generateContent"
    const val AI_MODEL          = "gemini-2.0-flash"
    val AI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY

    // ── Pagination ────────────────────────────────────────────────────────────
    const val PAGE_SIZE_RECIPES      = 20L   // real-time feed page size
    const val PAGE_SIZE_BLOGS        = 15L
    const val PAGE_SIZE_REVIEWS      = 30L
    const val PAGINATION_PAGE_SIZE   = 10L   // paginated load-more page size

    // ── Validation ────────────────────────────────────────────────────────────
    const val MAX_RECIPE_TITLE_LENGTH       = 80
    const val MAX_RECIPE_DESCRIPTION_LENGTH = 500
    const val MAX_INGREDIENTS               = 50
    const val MAX_INSTRUCTIONS              = 30
    const val MIN_RATING                    = 1.0f
    const val MAX_RATING                    = 5.0f
    const val MAX_REVIEW_IMAGES             = 3
    const val MAX_BIO_LENGTH                = 200

    // ── Navigation Args ───────────────────────────────────────────────────────
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_BLOG_ID   = "blogId"
    const val ARG_USER_ID   = "userId"

    // ── Network Timeouts (seconds) ────────────────────────────────────────────
    const val NETWORK_CONNECT_TIMEOUT = 30L
    const val NETWORK_READ_TIMEOUT    = 60L
    const val NETWORK_WRITE_TIMEOUT   = 30L

    // ── SharedPreferences ─────────────────────────────────────────────────────
    const val PREFS_NAME               = "ai_food_prefs"
    const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"
    const val PREF_THEME_MODE          = "theme_mode"
}