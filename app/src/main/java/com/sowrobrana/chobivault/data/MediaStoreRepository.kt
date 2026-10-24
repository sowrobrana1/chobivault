package com.sowrobrana.chobivault.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One photo or video on the device, read from MediaStore.
 * The gallery shows these directly — no copy or import needed.
 */
data class MediaItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAdded: Long,
    val bucketName: String,
    val size: Long,
    val durationMs: Long = 0L,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video")
}

data class Album(
    val name: String,
    val count: Int,
    val thumbUri: Uri,
)

class MediaStoreRepository(private val context: Context) {

    /** All device photos + videos, newest first. */
    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        loadImages() + loadVideos()
            .sortedByDescending { it.dateAdded }
    }

    /** Albums grouped by folder, newest-photo-first inside each album. */
    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        loadAll()
            .groupBy { it.bucketName.ifBlank { "অন্যান্য" } }
            .map { (name, items) ->
                Album(name, items.size, items.first().uri)
            }
            .sortedByDescending { it.count }
    }

    private fun loadImages(): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                out += MediaItem(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id,
                    ),
                    displayName = cursor.getString(nameCol) ?: "ছবি",
                    mimeType = cursor.getString(mimeCol) ?: "image/*",
                    dateAdded = cursor.getLong(dateCol),
                    bucketName = cursor.getString(bucketCol) ?: "",
                    size = cursor.getLong(sizeCol),
                )
            }
        }
        return out
    }

    private fun loadVideos(): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION,
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                out += MediaItem(
                    uri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id,
                    ),
                    displayName = cursor.getString(nameCol) ?: "ভিডিও",
                    mimeType = cursor.getString(mimeCol) ?: "video/*",
                    dateAdded = cursor.getLong(dateCol),
                    bucketName = cursor.getString(bucketCol) ?: "",
                    size = cursor.getLong(sizeCol),
                    durationMs = cursor.getLong(durCol),
                )
            }
        }
        return out
    }
}
