package com.robin.tools.feature.storage.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** A media file that is large enough to be worth reviewing. */
data class LargeMediaFile(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val isVideo: Boolean
)

/** A set of files whose sampled content hash is identical. */
data class DuplicateGroup(
    val sizeBytes: Long,
    val files: List<LargeMediaFile>
)

data class CleanupReport(
    val largeFiles: List<LargeMediaFile>,
    val duplicateGroups: List<DuplicateGroup>
) {
    val duplicateBytes: Long
        get() = duplicateGroups.sumOf { group -> group.sizeBytes * (group.files.size - 1) }
}

/**
 * Read-only scanner over the user's images and videos.
 *
 * Only media collections are queried so no extra storage permission is needed.
 * Duplicate detection samples the first and last 64 KiB of each candidate rather
 * than reading whole files, keeping the scan fast on large libraries.
 */
class StorageCleanupRepository(private val context: Context) {

    suspend fun scan(
        largeFileThresholdBytes: Long,
        onProgress: (String) -> Unit = {}
    ): CleanupReport = withContext(Dispatchers.IO) {
        onProgress("large")
        val allMedia = queryMedia(largeFileThresholdBytes)
        val largeFiles = allMedia.sortedByDescending { it.sizeBytes }

        onProgress("duplicates")
        val candidates = allMedia
            .filter { it.sizeBytes >= MIN_HASH_SIZE_BYTES }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }
            .values
            .flatten()
        val hashed = mutableMapOf<String, MutableList<LargeMediaFile>>()
        for (file in candidates) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val hash = sampleHash(file.uri) ?: continue
            hashed.getOrPut("${file.sizeBytes}:$hash") { mutableListOf() }.add(file)
        }
        val duplicateGroups = hashed.values
            .filter { it.size > 1 }
            .map { files ->
                DuplicateGroup(
                    sizeBytes = files.first().sizeBytes,
                    files = files.sortedBy { it.displayName }
                )
            }
            .sortedByDescending { it.sizeBytes * (it.files.size - 1) }

        CleanupReport(largeFiles, duplicateGroups)
    }

    private fun queryMedia(minSizeBytes: Long): List<LargeMediaFile> {
        val result = mutableListOf<LargeMediaFile>()
        result += queryCollection(
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            isVideo = false,
            minSizeBytes = minSizeBytes
        )
        result += queryCollection(
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            isVideo = true,
            minSizeBytes = minSizeBytes
        )
        return result
    }

    private fun queryCollection(
        collection: Uri,
        isVideo: Boolean,
        minSizeBytes: Long
    ): List<LargeMediaFile> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val files = mutableListOf<LargeMediaFile>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.SIZE} >= ?",
                arrayOf(minSizeBytes.toString()),
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (cursor.moveToNext() && files.size < MAX_RESULTS) {
                    val id = cursor.getLong(idIndex)
                    files += LargeMediaFile(
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameIndex) ?: "media_$id",
                        sizeBytes = cursor.getLong(sizeIndex),
                        mimeType = cursor.getString(mimeIndex),
                        isVideo = isVideo
                    )
                }
            }
        }
        return files
    }

    /** Hashes the first and last 64 KiB; enough to reject same-size collisions cheaply. */
    private fun sampleHash(uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(SAMPLE_BYTES)
            var read = input.read(buffer)
            val firstRead = read.coerceAtLeast(0)
            if (read > 0) digest.update(buffer, 0, read)
            val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
            if (size != null && size > SAMPLE_BYTES * 2) {
                // Position the stream at the start of the final sample chunk.
                val skip = size - firstRead - SAMPLE_BYTES
                var remaining = skip.coerceAtLeast(0)
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped <= 0) break
                    remaining -= skipped
                }
                read = input.read(buffer)
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    companion object {
        private const val SAMPLE_BYTES = 64 * 1024
        private const val MIN_HASH_SIZE_BYTES = 512L * 1024
        private const val MAX_RESULTS = 300
        val THRESHOLD_OPTIONS = listOf(50L * MB, 100L * MB, 500L * MB)
    }
}
