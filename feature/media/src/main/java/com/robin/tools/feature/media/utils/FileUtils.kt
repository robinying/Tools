package com.robin.tools.feature.media.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileUtils {
    private const val TAG = "FileUtils"
    /** Scoped input cache — only this dir is wiped by [clearCache]. */
    private const val MEDIA_TMP_DIR = "media_tmp"
    /** Scoped output dir under external files / cache. */
    private const val MEDIA_OUT_DIR = "media_out"

    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val rawName = getFileName(context, uri)
            val safeName = sanitizeFileName(rawName)
            val tmpDir = File(context.cacheDir, MEDIA_TMP_DIR).apply { mkdirs() }
            val tempFile = File(tmpDir, "in_${System.currentTimeMillis()}_$safeName")

            val cacheCanonical = context.cacheDir.canonicalPath
            if (!tempFile.canonicalPath.startsWith(cacheCanonical + File.separator)) {
                Log.w(TAG, "Rejected path outside cacheDir: ${tempFile.path}")
                return null
            }

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "getFileFromUri failed", e)
            null
        }
    }

    /**
     * Strips path separators and non-safe characters so DISPLAY_NAME cannot
     * escape [cacheDir] via path traversal.
     */
    internal fun sanitizeFileName(name: String?): String {
        val base = name
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            .orEmpty()
        val cleaned = base.replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
        return cleaned.ifBlank { "temp.bin" }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    fun createOutputFile(context: Context, extension: String): File {
        val parent = context.getExternalFilesDir(null) ?: context.cacheDir
        val dir = File(parent, MEDIA_OUT_DIR).apply { mkdirs() }
        val fileName = "compressed_${System.currentTimeMillis()}.$extension"
        return File(dir, fileName)
    }

    fun saveVideoToGallery(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VideoEditor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        return saveToGallery(context, file, values, collection)
    }

    fun saveImageToGallery(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VideoEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        return saveToGallery(context, file, values, collection)
    }

    fun saveGifToGallery(context: Context, file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VideoEditor")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        return saveToGallery(context, file, values, collection)
    }

    /**
     * Clears only media-scoped temp/output directories so concurrent ebook/camera
     * work under other cache subdirs is not deleted.
     */
    fun clearCache(context: Context) {
        try {
            File(context.cacheDir, MEDIA_TMP_DIR).deleteRecursively()
            val externalRoot = context.getExternalFilesDir(null)
            if (externalRoot != null) {
                File(externalRoot, MEDIA_OUT_DIR).deleteRecursively()
            }
            File(context.cacheDir, MEDIA_OUT_DIR).deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache", e)
        }
    }

    private fun saveToGallery(context: Context, file: File, values: ContentValues, collection: Uri): Uri? {
        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values) ?: return null

        return try {
            val outputStream = resolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("openOutputStream returned null for $itemUri")
            outputStream.use { out ->
                FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            }
            itemUri
        } catch (e: Exception) {
            Log.e(TAG, "saveToGallery failed, deleting pending row", e)
            try {
                resolver.delete(itemUri, null, null)
            } catch (deleteError: Exception) {
                Log.e(TAG, "Failed to delete pending MediaStore row", deleteError)
            }
            null
        }
    }
}
