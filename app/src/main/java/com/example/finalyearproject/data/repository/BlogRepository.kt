package com.example.finalyearproject.data.repository

import com.example.finalyearproject.data.model.Blog
import com.example.finalyearproject.data.remote.firebase.FirestoreService
import com.example.finalyearproject.utils.AppCache
import com.example.finalyearproject.utils.AppLogger
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.example.finalyearproject.utils.safeCall
import kotlinx.coroutines.flow.Flow

class BlogRepository private constructor(
    private val firestoreService: FirestoreService = FirestoreService.getInstance()
) {
    suspend fun addBlog(blog: Blog): Resource<String> =
        safeCall(AppLogger.TAG_REPO, "addBlog") {
            if (blog.title.isBlank()) return@safeCall Resource.Error("Blog title cannot be empty.")
            if (blog.content.isBlank()) return@safeCall Resource.Error("Content cannot be empty.")
            AppLogger.firestoreWrite(Constants.COLLECTION_BLOGS, "addBlog")
            val result = firestoreService.addBlog(blog)
            if (result is Resource.Success) AppCache.invalidateBlogs()
            result
        }

    suspend fun getBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Resource<List<Blog>> {
        AppCache.getBlogs()?.let {
            AppLogger.repoCacheHit("BlogRepository", "getBlogs")
            return Resource.Success(it)
        }
        AppLogger.repoCacheMiss("BlogRepository", "getBlogs")
        AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "getBlogs")
        return safeCall(AppLogger.TAG_REPO, "getBlogs") {
            val result = firestoreService.getBlogs(limit)
            if (result is Resource.Success) AppCache.setBlogs(result.data)
            result
        }
    }

    fun observeBlogs(limit: Long = Constants.PAGE_SIZE_BLOGS): Flow<Resource<List<Blog>>> {
        AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "observeBlogs (Flow)")
        return firestoreService.observeBlogs(limit)
    }

    suspend fun getBlogById(blogId: String): Resource<Blog> {
        if (blogId.isBlank()) return Resource.Error("Invalid blog ID.")
        AppCache.getBlogById(blogId)?.let {
            AppLogger.repoCacheHit("BlogRepository", "getBlogById:$blogId")
            return Resource.Success(it)
        }
        return safeCall(AppLogger.TAG_REPO, "getBlogById") {
            AppLogger.firestoreRead(Constants.COLLECTION_BLOGS, "getBlogById($blogId)")
            val result = firestoreService.getBlogById(blogId)
            if (result is Resource.Success) AppCache.setBlogById(blogId, result.data)
            result
        }
    }

    suspend fun deleteBlog(blogId: String): Resource<Unit> =
        safeCall(AppLogger.TAG_REPO, "deleteBlog") {
            if (blogId.isBlank()) return@safeCall Resource.Error("Invalid blog ID.")
            AppLogger.firestoreDelete(Constants.COLLECTION_BLOGS, blogId)
            val result = firestoreService.deleteBlog(blogId)
            if (result is Resource.Success) AppCache.invalidateBlogs()
            result
        }

    companion object {
        @Volatile private var instance: BlogRepository? = null
        fun getInstance(): BlogRepository =
            instance ?: synchronized(this) {
                instance ?: BlogRepository().also { instance = it }
            }
    }
}
