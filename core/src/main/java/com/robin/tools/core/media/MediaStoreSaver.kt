package com.robin.tools.core.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** A file this toolkit produced, including the URI that can be opened or shared. */
data class SavedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)

/**
 * Writes generated images/PDFs to shared storage.
 *
 * Android 10+ goes through MediaStore with `IS_PENDING`, so no storage permission
 * is needed. Older releases fall back to a public directory scan and, if that is
 * denied, to app-private external storage exposed through FileProvider.
 */
object MediaStoreSaver {

    private const val TOOLKIT_DIR = "toolkit"

    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        fileName: String,
        quality: Int
    ): Result<SavedMedia> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToMediaStore(context, bitmap, format, mimeType, fileName, quality)
        } else {
            saveBitmapLegacy(context, bitmap, format, mimeType, fileName, quality)
        }
    }

    fun savePdf(context: Context, bytes: ByteArray, fileName: String): Result<SavedMedia> =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveBytesToMediaStore(
                    context = context,
                    collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Tools",
                    bytes = bytes,
                    mimeType = MIME_PDF,
                    fileName = fileName
                )
            } else {
                saveBytesLegacy(context, bytes, MIME_PDF, fileName, Environment.DIRECTORY_DOWNLOADS)
            }
        }

    /** Saves arbitrary bytes (CSV, text, JSON) under Downloads/Tools. */
    fun saveDocument(
        context: Context,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): Result<SavedMedia> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBytesToMediaStore(
                context = context,
                collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Tools",
                bytes = bytes,
                mimeType = mimeType,
                fileName = fileName
            )
        } else {
            saveBytesLegacy(
                context,
                bytes,
                mimeType,
                fileName,
                Environment.DIRECTORY_DOWNLOADS
            )
        }
    }

    private fun saveImageToMediaStore(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        fileName: String,
        quality: Int
    ): SavedMedia {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Tools"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create $fileName")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                check(bitmap.compress(format, quality, out)) { "Image encode failed" }
            } ?: error("Cannot open output stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedMedia(uri, fileName, mimeType)
    }

    private fun saveBytesToMediaStore(
        context: Context,
        collection: Uri,
        relativePath: String,
        bytes: ByteArray,
        mimeType: String,
        fileName: String
    ): SavedMedia {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore refused to create $fileName")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Cannot open output stream")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return SavedMedia(uri, fileName, mimeType)
    }

    private fun saveBitmapLegacy(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat,
        mimeType: String,
        fileName: String,
        quality: Int
    ): SavedMedia {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!dir.exists() && !dir.mkdirs()) {
                error("Cannot create ${dir.absolutePath}")
            }
            val file = File(dir, fileName)
            FileOutputStream(file).use { out ->
                check(bitmap.compress(format, quality, out)) { "Image encode failed" }
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null
            )
            SavedMedia(Uri.fromFile(file), fileName, mimeType)
        } catch (e: Exception) {
            saveToAppStorage(context, fileName, mimeType) { out ->
                check(bitmap.compress(format, quality, out)) { "Image encode failed" }
            }
        }
    }

    private fun saveBytesLegacy(
        context: Context,
        bytes: ByteArray,
        mimeType: String,
        fileName: String,
        publicDir: String
    ): SavedMedia {
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(publicDir)
            if (!dir.exists() && !dir.mkdirs()) {
                error("Cannot create ${dir.absolutePath}")
            }
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(bytes) }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null
            )
            SavedMedia(Uri.fromFile(file), fileName, mimeType)
        } catch (e: Exception) {
            saveToAppStorage(context, fileName, mimeType) { it.write(bytes) }
        }
    }

    private fun saveToAppStorage(
        context: Context,
        fileName: String,
        mimeType: String,
        writer: (FileOutputStream) -> Unit
    ): SavedMedia {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, TOOLKIT_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use(writer)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return SavedMedia(uri, fileName, mimeType)
    }

    const val MIME_PDF = "application/pdf"
}
