package com.example.finalyearproject.data.remote.firebase

import android.net.Uri
import com.example.finalyearproject.utils.Constants
import com.example.finalyearproject.utils.Resource
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StorageService
 *
 * Handles all Firebase Storage operations — image uploads, URL retrieval,
 * and deletion.
 *
 * Storage bucket layout:
 *   profile_images/{uid}/profile.jpg
 *   recipe_images/{recipeId}/{uuid}.jpg
 *   review_images/{reviewId}/{uuid}.jpg
 *   blog_covers/{blogId}/{uuid}.jpg
 *
 * Location: data/remote/firebase/StorageService.kt
 */
@Singleton
class StorageService @Inject constructor(
    private val storage: FirebaseStorage
) {

    // ── Generic upload ────────────────────────────────────────────────────────

    /**
     * Uploads an image from a local [Uri] to a specified Storage path.
     *
     * @param fileUri       Content URI from image picker / camera
     * @param storagePath   Full path in Storage bucket (e.g. "recipe_images/abc/uuid.jpg")
     * @return [Resource.Success] with the public download URL, or [Resource.Error].
     */
    suspend fun uploadImage(
        fileUri: Uri,
        storagePath: String
    ): Resource<String> = try {

        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        val ref = storage.reference.child(storagePath)

        // Upload and wait
        val uploadTask: UploadTask.TaskSnapshot = ref
            .putFile(fileUri, metadata)
            .await()

        // Retrieve the download URL
        val downloadUrl: String = ref.downloadUrl.await().toString()

        Resource.Success(downloadUrl)

    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Image upload failed.")
    }

    // ── Profile image ─────────────────────────────────────────────────────────

    /**
     * Uploads a profile picture for the given user.
     * Always stored at the same path so it auto-replaces the previous image.
     */
    suspend fun uploadProfileImage(
        uid: String,
        fileUri: Uri
    ): Resource<String> {
        val path = "${Constants.STORAGE_PROFILE_IMAGES}/$uid/profile.jpg"
        return uploadImage(fileUri, path)
    }

    // ── Recipe image ──────────────────────────────────────────────────────────

    /**
     * Uploads a recipe cover image. Generates a UUID filename to avoid
     * collisions when a recipe has multiple images.
     *
     * @return [Resource.Success] with download URL.
     */
    suspend fun uploadRecipeImage(
        recipeId: String,
        fileUri: Uri
    ): Resource<String> {
        val filename = UUID.randomUUID().toString()
        val path = "${Constants.STORAGE_RECIPE_IMAGES}/$recipeId/$filename.jpg"
        return uploadImage(fileUri, path)
    }

    // ── Blog cover image ──────────────────────────────────────────────────────

    suspend fun uploadBlogCoverImage(
        blogId: String,
        fileUri: Uri
    ): Resource<String> {
        val filename = UUID.randomUUID().toString()
        val path = "${Constants.STORAGE_BLOG_IMAGES}/$blogId/$filename.jpg"
        return uploadImage(fileUri, path)
    }

    // ── Review image ──────────────────────────────────────────────────────────

    /**
     * Uploads multiple review images and returns a list of download URLs.
     * If any upload fails, the operation short-circuits and returns [Resource.Error].
     */
    suspend fun uploadReviewImages(
        reviewId: String,
        fileUris: List<Uri>
    ): Resource<List<String>> {
        val urls = mutableListOf<String>()
        for (uri in fileUris) {
            val filename = UUID.randomUUID().toString()
            val path = "${Constants.STORAGE_REVIEW_IMAGES}/$reviewId/$filename.jpg"
            when (val result = uploadImage(uri, path)) {
                is Resource.Success -> urls.add(result.data)
                is Resource.Error   -> return Resource.Error(result.message)
                else -> Unit
            }
        }
        return Resource.Success(urls)
    }

    // ── Delete file ───────────────────────────────────────────────────────────

    /**
     * Deletes a file at the given Storage path.
     * Useful for cleanup when a recipe/blog/user is deleted.
     */
    suspend fun deleteFile(storagePath: String): Resource<Unit> = try {
        storage.reference.child(storagePath).delete().await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Failed to delete file.")
    }

    // ── Get download URL ──────────────────────────────────────────────────────

    /**
     * Returns a fresh download URL for an existing file.
     * URLs from [uploadImage] are long-lived but this is useful for refreshing.
     */
    suspend fun getDownloadUrl(storagePath: String): Resource<String> = try {
        val url = storage.reference.child(storagePath).downloadUrl.await().toString()
        Resource.Success(url)
    } catch (e: Exception) {
        Resource.Error(e.localizedMessage ?: "Could not retrieve file URL.")
    }
}