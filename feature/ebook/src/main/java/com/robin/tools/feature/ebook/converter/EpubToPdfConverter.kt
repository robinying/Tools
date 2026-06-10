package com.robin.tools.feature.ebook.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.*
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class EpubToPdfConverter(private val context: Context) {
    interface ProgressCallback {
        fun onProgress(percent: Int)
        fun onSuccess(file: File)
        fun onError(e: Exception)
    }

    companion object {
        private const val TAG = "EpubToPdfConverter"
        private const val CHAPTER_TIMEOUT_MS = 60_000L
        private const val MAX_IMAGE_DIMENSION = 900 // Limit image size for 150 DPI A4 print
        private const val WEBVIEW_RECYCLE_INTERVAL = 3 // Destroy & recreate WebView every N chapters
    }

    private val tempDir = File(context.cacheDir, "epub_temp")

    suspend fun convert(epubUri: Uri, outputFileName: String, callback: ProgressCallback) {
        var webView: WebView? = null
        try {
            withContext(Dispatchers.IO) {
                PDFBoxResourceLoader.init(context)
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                val book = context.contentResolver.openInputStream(epubUri)?.use { stream ->
                    EpubReader().readEpub(stream)
                } ?: throw IllegalArgumentException("Could not open input stream for URI: $epubUri")

                extractResources(book, tempDir)

                val chapters = book.spine.spineReferences
                    .filter { it.resource?.data?.isNotEmpty() == true }
                if (chapters.isEmpty()) throw IllegalStateException("No valid chapters found in EPUB")

                // Create one WebView on the main thread — reused for all chapters to avoid OOM
                webView = withContext(Dispatchers.Main) {
                    WebView(context.applicationContext ?: context).also { wv ->
                        wv.settings.javaScriptEnabled = false
                        wv.settings.domStorageEnabled = false
                        wv.settings.loadWithOverviewMode = true
                        wv.settings.useWideViewPort = true
                        wv.settings.builtInZoomControls = false
                        wv.settings.displayZoomControls = false
                        wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                        // Reduce memory: disable path rendering cache
                        @Suppress("DEPRECATION")
                        wv.settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                        wv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                    }
                }

                val pdfSegments = mutableListOf<File>()
                val total = chapters.size

                for (i in chapters.indices) {
                    val progress = ((i.toFloat() / total) * 90).toInt()
                    withContext(Dispatchers.Main) { callback.onProgress(progress) }

                    val segmentFile = File(tempDir, "segment_$i.pdf")
                    val htmlContent = String(chapters[i].resource.data)
                    Log.d(TAG, "Rendering chapter ${i + 1}/$total (${htmlContent.length} chars)")

                    try {
                        renderChapterToPdf(webView!!, htmlContent, tempDir.absolutePath, segmentFile)
                    } catch (e: CancellationException) {
                        throw IllegalStateException(
                            "Chapter ${i + 1}/$total cancelled: ${e.message}"
                        )
                    }

                    pdfSegments.add(segmentFile)

                    // Destroy & recreate WebView every N chapters to prevent memory bloat
                    if ((i + 1) % WEBVIEW_RECYCLE_INTERVAL == 0 && i < total - 1) {
                        withContext(Dispatchers.Main) {
                            webView?.destroy()
                            webView = WebView(context.applicationContext ?: context).also { wv ->
                                wv.settings.javaScriptEnabled = false
                                wv.settings.domStorageEnabled = false
                                wv.settings.loadWithOverviewMode = true
                                wv.settings.useWideViewPort = true
                                wv.settings.builtInZoomControls = false
                                wv.settings.displayZoomControls = false
                                wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                @Suppress("DEPRECATION")
                                wv.settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
                                wv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            }
                        }
                        withContext(Dispatchers.IO) {
                            System.gc()
                            System.runFinalization()
                        }
                        Log.d(TAG, "Recreated WebView after chapter ${i + 1}")
                    } else {
                        // Free memory between chapters
                        withContext(Dispatchers.Main) {
                            webView?.clearHistory()
                            webView?.clearCache(true)
                            webView?.clearFormData()
                            webView?.freeMemory()
                        }
                        withContext(Dispatchers.IO) {
                            System.gc()
                            System.runFinalization()
                        }
                    }
                }

                val outputFile = File(context.cacheDir, outputFileName)
                mergePdfs(pdfSegments, outputFile)

                withContext(Dispatchers.Main) {
                    callback.onProgress(100)
                    callback.onSuccess(outputFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed", e)
            withContext(Dispatchers.Main) { callback.onError(e) }
        } finally {
            withContext(Dispatchers.Main) {
                webView?.destroy()
            }
            tempDir.deleteRecursively()
        }
    }

    private fun extractResources(book: Book, resDir: File) {
        book.resources.all.forEach { res ->
            val relativePath = res.href.trimStart('/')
            val safePath = relativePath.replace("../", "").replace("..\\", "")
            val file = File(resDir, safePath)
            file.parentFile?.mkdirs()
            if (isImageFile(safePath) && res.data.size > 50 * 1024) {
                // Downscale large images to print resolution to avoid OOM
                file.writeBytes(resizeImage(res.data))
            } else {
                file.writeBytes(res.data)
            }
        }
    }

    private fun isImageFile(path: String): Boolean {
        val ext = path.lowercase().substringAfterLast('.', "")
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    private fun resizeImage(data: ByteArray): ByteArray {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            val scale = maxOf(
                (opts.outWidth.toFloat() / MAX_IMAGE_DIMENSION),
                (opts.outHeight.toFloat() / MAX_IMAGE_DIMENSION),
                1f
            )
            if (scale <= 1f) return data // Already small enough

            val sampleSize = scale.toInt().coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts)
                ?: return data

            val output = ByteArrayOutputStream()
            val format = if (opts.outMimeType == "image/png" || opts.outMimeType == "image/webp")
                Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            bitmap.compress(format, 85, output)
            bitmap.recycle()
            Log.d(TAG, "Resized image: ${opts.outWidth}x${opts.outHeight} → ~${output.size() / 1024}KB (${data.size / 1024}KB original)")
            output.toByteArray()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resize image, using original", e)
            data
        }
    }

    /**
     * Renders a single chapter to PDF using the shared [webView].
     * Called from IO context; switches to Main for WebView operations.
     */
    private suspend fun renderChapterToPdf(
        webView: WebView,
        html: String,
        baseUrl: String,
        outputFile: File
    ) = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Unit>()
        var isCompleted = false
        var timeoutJob: Job? = null

        fun cleanup() {
            if (!isCompleted) {
                isCompleted = true
                timeoutJob?.cancel()
            }
        }

        timeoutJob = launch {
            delay(CHAPTER_TIMEOUT_MS)
            if (!isCompleted) {
                isCompleted = true
                deferred.completeExceptionally(
                    CancellationException("Chapter PDF rendering timed out after ${CHAPTER_TIMEOUT_MS}ms")
                )
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (isCompleted) return
                if (view != null) {
                    saveWebViewToPdf(view, outputFile) {
                        cleanup()
                        deferred.complete(Unit)
                    }
                } else {
                    cleanup()
                    deferred.completeExceptionally(
                        IllegalStateException("WebView is null in onPageFinished")
                    )
                }
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                cleanup()
                deferred.completeExceptionally(
                    RuntimeException("WebView error $errorCode: $description")
                )
            }
        }

        try {
            val fullHtml = wrapHtml(html)
            webView.loadDataWithBaseURL("file://$baseUrl/", fullHtml, "text/html", "UTF-8", null)
            deferred.await()
        } finally {
            cleanup()
        }
    }

    private fun wrapHtml(html: String): String {
        return buildString {
            append("<!DOCTYPE html><html><head>")
            append("<meta charset=\"UTF-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<style>")
            append("body { font-family: serif; font-size: 16px; line-height: 1.6; max-width: 100%; overflow-wrap: break-word; }")
            append("img { max-width: 100%; height: auto; }")
            append("</style>")
            append("</head><body>")
            append(html)
            append("</body></html>")
        }
    }

    private fun saveWebViewToPdf(webView: WebView, outputFile: File, onComplete: () -> Unit) {
        outputFile.parentFile?.mkdirs()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("ebook_pdf", "pdf", 150, 150))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val adapter = webView.createPrintDocumentAdapter("Chapter")
        adapter.onLayout(null, attributes, null, object : android.print.PrintDocumentAdapterHelper.LayoutCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                adapter.onWrite(
                    arrayOf(PageRange.ALL_PAGES),
                    ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE),
                    null,
                    object : android.print.PrintDocumentAdapterHelper.WriteCallback() {
                        override fun onWriteFinished(pages: Array<out PageRange>?) { onComplete() }
                    })
            }
        }, null)
    }

    private fun mergePdfs(segments: List<File>, outputFile: File) {
        val merger = PDFMergerUtility()
        segments.forEach { merger.addSource(it) }
        FileOutputStream(outputFile).use { merger.destinationStream = it; merger.mergeDocuments(null) }
    }
}
