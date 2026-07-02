package com.robin.tools.feature.camera.storage

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Resolves a video path/uri string into a real filesystem path that MediaExtractor/MediaMuxer
 * can use directly. Content URIs from the gallery picker are copied to a cache file.
 */
object VideoPathResolver {
    fun resolve(context: Context, pathOrUri: String): String {
        // Already a filesystem path
        if (pathOrUri.startsWith("/")) return pathOrUri

        // file:// URI
        if (pathOrUri.startsWith("file://")) {
            return Uri.parse(pathOrUri).path ?: pathOrUri
        }

        // content:// URI — copy to a temp file so MediaExtractor can read it
        if (pathOrUri.startsWith("content://")) {
            return copyContentToCache(context, Uri.parse(pathOrUri))
        }

        return pathOrUri
    }

    private fun copyContentToCache(context: Context, uri: Uri): String {
        val cacheDir = File(context.cacheDir, "camera_input").apply { mkdirs() }
        val dest = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw RuntimeException("Cannot open input stream for $uri")
        return dest.absolutePath
    }
}
