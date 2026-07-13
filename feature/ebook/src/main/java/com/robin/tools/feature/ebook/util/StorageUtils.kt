package com.robin.tools.feature.ebook.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream

object StorageUtils {
    private const val TAG = "StorageUtils"

    fun savePdfToDownloads(context: Context, cacheFile: File, fileName: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri = resolver.insert(collection, contentValues) ?: return null

        return try {
            val outputStream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("openOutputStream returned null for $uri")
            outputStream.use { out ->
                FileInputStream(cacheFile).use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.e(TAG, "savePdfToDownloads failed, deleting pending row", e)
            try {
                resolver.delete(uri, null, null)
            } catch (deleteError: Exception) {
                Log.e(TAG, "Failed to delete pending MediaStore row", deleteError)
            }
            null
        }
    }
}
