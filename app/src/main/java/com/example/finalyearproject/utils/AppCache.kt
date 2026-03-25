package com.example.finalyearproject.utils

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.model.Favorite
import com.example.finalyearproject.data.model.Recipe
import com.example.finalyearproject.data.model.User

/**
 * AppCache — Thread-safe in-memory cache manager.
 *
 * Stores the most recently fetched data per key.
 * Each entry has a TTL (time-to-live); stale entries are
 * ignored and trigger a fresh Firestore fetch.
 *
 * Location: utils/AppCache.kt
 *
 * Cache keys are defined as constants at the bottom of this file.
 * Keyed stores (e.g. recipes by author) use "prefix:id" keys.
 */
object AppCache {

    // ── TTL constants ─────────────────────────────────────────────────────────
    private const val TTL_RECIPES_MS   = 5  * 60 * 1000L   // 5 minutes
    private const val TTL_BLOGS_MS     = 5  * 60 * 1000L
    private const val TTL_USER_MS      = 10 * 60 * 1000L   // 10 minutes
    private const val TTL_FAVORITES_MS = 2  * 60 * 1000L   // 2 minutes (changes often)
    private const val TTL_SINGLE_MS    = 10 * 60 * 1000L   // single-item detail

    // ── Internal entry wrapper ────────────────────────────────────────────────

    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long = System.currentTimeMillis(),
        val ttlMs: Long
    ) {
        val isExpired: Boolean
            get() = System.currentTimeMillis() - timestamp > ttlMs
    }

    // ── Cache stores ──────────────────────────────────────────────────────────

    @Volatile private var recipeListCache: CacheEntry<List<Recipe>>? = null
    @Volatile private var blogListCache: CacheEntry<List<Blog>>? = null

    // Keyed by userId
    private val favoritesCache = mutableMapOf<String, CacheEntry<List<Favorite>>>()
    private val userCache       = mutableMapOf<String, CacheEntry<User>>()

    // Keyed by recipeId / blogId
    private val recipeDetailCache = mutableMapOf<String, CacheEntry<Recipe>>()
    private val blogDetailCache   = mutableMapOf<String, CacheEntry<Blog>>()

    // Keyed by authorId
    private val authorRecipesCache = mutableMapOf<String, CacheEntry<List<Recipe>>>()

    // ═══════════════════════════════════════════════════════════════════════════
    // RECIPES
    // ═══════════════════════════════════════════════════════════════════════════

    fun getRecipes(): List<Recipe>? =
        recipeListCache?.takeIf { !it.isExpired }?.data

    fun setRecipes(recipes: List<Recipe>) {
        recipeListCache = CacheEntry(recipes, ttlMs = TTL_RECIPES_MS)
    }

    fun getRecipeById(id: String): Recipe? =
        recipeDetailCache[id]?.takeIf { !it.isExpired }?.data

    fun setRecipeById(id: String, recipe: Recipe) {
        recipeDetailCache[id] = CacheEntry(recipe, ttlMs = TTL_SINGLE_MS)
    }

    fun getRecipesByAuthor(authorId: String): List<Recipe>? =
        authorRecipesCache[authorId]?.takeIf { !it.isExpired }?.data

    fun setRecipesByAuthor(authorId: String, recipes: List<Recipe>) {
        authorRecipesCache[authorId] = CacheEntry(recipes, ttlMs = TTL_RECIPES_MS)
    }

    /** Invalidate recipe caches after add/update/delete. */
    fun invalidateRecipes() {
        recipeListCache = null
        recipeDetailCache.clear()
        authorRecipesCache.clear()
        AppLogger.d(AppLogger.TAG_CACHE, "Recipe cache invalidated")
    }

    fun invalidateRecipeById(id: String) {
        recipeDetailCache.remove(id)
        recipeListCache = null
        AppLogger.d(AppLogger.TAG_CACHE, "Recipe cache invalidated for id=$id")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLOGS
    // ═══════════════════════════════════════════════════════════════════════════

    fun getBlogs(): List<Blog>? =
        blogListCache?.takeIf { !it.isExpired }?.data

    fun setBlogs(blogs: List<Blog>) {
        blogListCache = CacheEntry(blogs, ttlMs = TTL_BLOGS_MS)
    }

    fun getBlogById(id: String): Blog? =
        blogDetailCache[id]?.takeIf { !it.isExpired }?.data

    fun setBlogById(id: String, blog: Blog) {
        blogDetailCache[id] = CacheEntry(blog, ttlMs = TTL_SINGLE_MS)
    }

    fun invalidateBlogs() {
        blogListCache = null
        blogDetailCache.clear()
        AppLogger.d(AppLogger.TAG_CACHE, "Blog cache invalidated")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FAVORITES
    // ═══════════════════════════════════════════════════════════════════════════

    fun getFavorites(userId: String): List<Favorite>? =
        favoritesCache[userId]?.takeIf { !it.isExpired }?.data

    fun setFavorites(userId: String, favorites: List<Favorite>) {
        favoritesCache[userId] = CacheEntry(favorites, ttlMs = TTL_FAVORITES_MS)
    }

    fun invalidateFavorites(userId: String) {
        favoritesCache.remove(userId)
        AppLogger.d(AppLogger.TAG_CACHE, "Favorites cache invalidated for userId=$userId")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // USER
    // ═══════════════════════════════════════════════════════════════════════════

    fun getUser(uid: String): User? =
        userCache[uid]?.takeIf { !it.isExpired }?.data

    fun setUser(uid: String, user: User) {
        userCache[uid] = CacheEntry(user, ttlMs = TTL_USER_MS)
    }

    fun invalidateUser(uid: String) {
        userCache.remove(uid)
        AppLogger.d(AppLogger.TAG_CACHE, "User cache invalidated for uid=$uid")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GLOBAL
    // ═══════════════════════════════════════════════════════════════════════════

    /** Call on logout — clears all personal data from cache. */
    fun clearUserData(userId: String) {
        favoritesCache.remove(userId)
        userCache.remove(userId)
        AppLogger.d(AppLogger.TAG_CACHE, "User data cleared from cache for uid=$userId")
    }

    /** Full wipe — call sparingly (e.g. on app background/foreground). */
    fun clearAll() {
        recipeListCache = null
        blogListCache = null
        favoritesCache.clear()
        userCache.clear()
        recipeDetailCache.clear()
        blogDetailCache.clear()
        authorRecipesCache.clear()
        AppLogger.d(AppLogger.TAG_CACHE, "All caches cleared")
    }

    fun getCacheStatus(): String = buildString {
        appendLine("=== Cache Status ===")
        appendLine("Recipes list:   ${if (recipeListCache?.isExpired == false) "VALID" else "EMPTY/EXPIRED"}")
        appendLine("Blogs list:     ${if (blogListCache?.isExpired == false) "VALID" else "EMPTY/EXPIRED"}")
        appendLine("Recipe detail:  ${recipeDetailCache.size} entries")
        appendLine("Author recipes: ${authorRecipesCache.size} entries")
        appendLine("Users:          ${userCache.size} entries")
        appendLine("Favorites:      ${favoritesCache.size} user entries")
    }
}