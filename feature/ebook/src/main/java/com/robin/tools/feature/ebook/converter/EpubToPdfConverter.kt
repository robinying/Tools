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
        private const val MAX_IMAGE_DIMENSION = 480 // Sufficient for 120 DPI A4, keeps decoded images small
        private const val WEBVIEW_RECYCLE_INTERVAL = 2 // Destroy & recreate WebView every N segments
        private const val LARGE_HTML_THRESHOLD = 256 * 1024 // 256KB — write large HTML to file

        // ~15 A4 pages worth of text at 14px font. Splitting more aggressively
        // keeps the Print Framework's per-segment page buffer small.
        private const val MAX_CHARS_PER_SEGMENT = 50_000

        // 120 DPI — per-page bitmap ~992×1403 px. At RGB_565 that's ~2.8MB per page.
        // A 15-page segment thus needs ~42MB for page buffers, well within typical
        // Android app heap limits (128-512MB depending on device).
        private const val PDF_DPI = 120
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

                // Extract spine references BEFORE clearing resources, since clearing
                // the underlying resource data invalidates spine references
                val chapters = book.spine.spineReferences
                    .map { it.resource?.href ?: "" }
                    .filter { it.isNotEmpty() }
                if (chapters.isEmpty()) throw IllegalStateException("No valid chapters found in EPUB")

                // Write resources to disk first, then CLEAR the book's in-memory data.
                // The Book object holds ALL resource byte[] across the entire conversion,
                // which is the #1 cause of OOM for large EPUBs.
                extractResources(book, tempDir)

                // Free all in-memory resource data now that everything is on disk.
                // After this point, all chapter HTML/images must be read from disk.
                book.resources.all.forEach { res ->
                    res.data = ByteArray(0)  // Release the byte[]
                }
                Log.d(TAG, "Released in-memory book resources (${chapters.size} chapters)")

                // Create one WebView on the main thread — reused for all chapters to avoid OOM
                webView = withContext(Dispatchers.Main) { createWebView() }

                val pdfSegments = mutableListOf<File>()

                val runtime = Runtime.getRuntime()
                val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
                Log.d(TAG, "Max JVM heap: ${maxMemoryMB}MB, chapters (files): ${chapters.size}")

                // Process chapters one-at-a-time. Each chapter may be split into
                // sub-parts if it exceeds MAX_CHARS_PER_SEGMENT. Critically, we do
                // NOT pre-read all chapters — each chapter's HTML is read, split,
                // rendered, and then released before the next chapter is loaded.
                var taskSeq = 0

                for (chapterIdx in chapters.indices) {
                    val chapterHref = chapters[chapterIdx]
                    val chapterFile = File(tempDir, chapterHref)

                    if (!chapterFile.exists() || chapterFile.length() == 0L) {
                        Log.w(TAG, "Skipping empty/missing chapter ${chapterIdx + 1}: $chapterHref")
                        continue
                    }

                    // Read ONE chapter at a time, then release before the next
                    val rawHtml: String
                    try {
                        rawHtml = chapterFile.inputStream().use { it.readBytes().toString(Charsets.UTF_8) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read chapter ${chapterIdx + 1}, skipping: ${e.message}")
                        continue
                    }
                    if (rawHtml.isBlank()) continue

                    // Split into page-count-safe chunks.  Most chapters won't split.
                    val parts = splitLongHtml(rawHtml)

                    for (partIdx in parts.indices) {
                        val htmlContent = parts[partIdx]
                        val chapterLabel = if (parts.size > 1)
                            "${chapterIdx + 1}.${partIdx + 1}" else "${chapterIdx + 1}"

                        // Memory check before each render
                        val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        if (usedMB > maxMemoryMB * 0.8) {
                            Log.w(TAG, "Memory high: ${usedMB}/${maxMemoryMB}MB — GC before $chapterLabel")
                            System.gc()
                            System.runFinalization()
                        }

                        taskSeq++
                        // Progress is approximate since we discover splits on-the-fly
                        val progress = ((chapterIdx.toFloat() / chapters.size) * 85).toInt()
                        withContext(Dispatchers.Main) { callback.onProgress(progress) }

                        val segmentFile = File(tempDir, "segment_${chapterIdx}_${partIdx}.pdf")
                        Log.d(TAG, "Rendering $chapterLabel (${htmlContent.length} chars, ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)}/${maxMemoryMB}MB)")

                        var renderOk = false
                        try {
                            renderChapterToPdf(webView!!, htmlContent, tempDir.absolutePath, segmentFile)
                            renderOk = true
                        } catch (e: OutOfMemoryError) {
                            Log.e(TAG, "OOM at $chapterLabel, purging WebView and retrying once...")
                            withContext(Dispatchers.Main) {
                                webView?.destroy()
                            }
                            System.gc()
                            System.runFinalization()
                            webView = withContext(Dispatchers.Main) { createWebView() }
                            try {
                                renderChapterToPdf(webView!!, htmlContent, tempDir.absolutePath, segmentFile)
                                renderOk = true
                                Log.d(TAG, "OOM retry OK for $chapterLabel")
                            } catch (e2: Exception) {
                                Log.e(TAG, "OOM retry failed for $chapterLabel, skipping")
                            }
                        } catch (e: CancellationException) {
                            throw IllegalStateException("Cancelled at $chapterLabel: ${e.message}", e)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed $chapterLabel: ${e.message}, skipping")
                        }

                        if (renderOk && segmentFile.exists() && segmentFile.length() > 0) {
                            pdfSegments.add(segmentFile)
                        }

                        // WebView cleanup between parts
                        withContext(Dispatchers.Main) {
                            webView?.clearHistory()
                            webView?.clearCache(true)
                            webView?.freeMemory()
                        }
                    }

                    // After each chapter, force a stronger WebView reset
                    val segmentsAfterChapter = pdfSegments.size
                    if (segmentsAfterChapter % WEBVIEW_RECYCLE_INTERVAL == 0) {
                        withContext(Dispatchers.Main) {
                            webView?.destroy()
                            webView = createWebView()
                        }
                        System.gc()
                        System.runFinalization()
                    }
                }

                if (pdfSegments.isEmpty()) throw IllegalStateException("All chapters failed to render")

                val outputDir = File(context.cacheDir, "pdf_output")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)
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
            withContext(NonCancellable + Dispatchers.Main) {
                webView?.destroy()
            }
            withContext(NonCancellable) {
                tempDir.deleteRecursively()
            }
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

    /**
     * Resize image to fit within [MAX_IMAGE_DIMENSION] using power-of-2 sample size
     * and RGB_565 config to cut memory in half. Necessary because WebView loads
     * every image into its render tree simultaneously.
     */
    private fun resizeImage(data: ByteArray): ByteArray {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            if (maxDim <= MAX_IMAGE_DIMENSION) return data // Already small enough

            // Calculate power-of-2 sample size (Android requires powers of 2)
            var sampleSize = 1
            while (maxDim / sampleSize > MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                // RGB_565 uses 2 bytes/pixel instead of 4 (ARGB_8888).
                // Acceptable quality loss for 150 DPI print — halves bitmap memory.
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts)
                ?: return data

            val output = ByteArrayOutputStream()
            // Always use JPEG for photos/illustrations in a print context.
            // PNG only for images where transparency matters (rare in EPUBs).
            val format = if (opts.outMimeType == "image/png" &&
                (opts.outWidth <= 200 || opts.outHeight <= 200))
                Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (format == Bitmap.CompressFormat.JPEG) 70 else 85
            bitmap.compress(format, quality, output)
            // Capture dimensions BEFORE recycle() — accessing bitmap after recycle() crashes
            val newW = bitmap.width
            val newH = bitmap.height
            bitmap.recycle()
            Log.d(TAG, "Resized image: ${opts.outWidth}x${opts.outHeight} → " +
                "${newW}x${newH} ${output.size() / 1024}KB (was ${data.size / 1024}KB, sample=$sampleSize)")
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
                    try {
                        saveWebViewToPdf(view, outputFile) {
                            cleanup()
                            deferred.complete(Unit)
                        }
                    } catch (e: OutOfMemoryError) {
                        // OOM during PrintAdapter layout/write — propagate to caller
                        cleanup()
                        deferred.completeExceptionally(e)
                    } catch (e: Exception) {
                        cleanup()
                        deferred.completeExceptionally(e)
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
            val (content, baseUrlResolved) = prepareHtmlForWebView(html, baseUrl)
            if (content.startsWith("file://")) {
                webView.loadUrl(content)
            } else {
                webView.loadDataWithBaseURL(baseUrlResolved, content, "text/html", "UTF-8", null)
            }
            deferred.await()
        } catch (e: OutOfMemoryError) {
            // Re-throw so outer loop can attempt OOM recovery
            cleanup()
            throw e
        } finally {
            cleanup()
        }
    }

    /**
     * Creates a WebView configured for minimal memory footprint.
     * The layout width is set to match our CSS viewport so the Print adapter
     * can measure pages correctly without allocating an oversized buffer.
     */
    private fun createWebView(): WebView {
        return WebView(context.applicationContext ?: context).also { wv ->
            wv.settings.javaScriptEnabled = false
            wv.settings.domStorageEnabled = false
            wv.settings.allowFileAccess = false
            wv.settings.allowContentAccess = false
            wv.settings.loadWithOverviewMode = true
            wv.settings.useWideViewPort = true
            wv.settings.builtInZoomControls = false
            wv.settings.displayZoomControls = false
            wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            wv.settings.blockNetworkLoads = true  // No external resources
            wv.settings.blockNetworkImage = true  // No external images
            @Suppress("DEPRECATION")
            wv.settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            wv.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            // Match viewport width so the Print adapter's page measurement is correct.
            // Height is tall enough for page measurement without being screen-sized.
            val vw = viewportWidth
            wv.layout(0, 0, vw, vw * 2)
        }
    }

    /**
     * A4 width in CSS pixels at [PDF_DPI] DPI.
     * A4 = 210mm wide → 210/25.4 = 8.2677 inches → 8.2677 × DPI pixels.
     * At 120 DPI: ~992px. This gives a 1:1 CSS-px-to-output-dot mapping,
     * which is the most memory-efficient configuration.
     */
    private val viewportWidth: Int get() = (210f / 25.4f * PDF_DPI).toInt()

    private fun wrapHtml(html: String): String {
        val vw = viewportWidth
        return buildString {
            append("<!DOCTYPE html><html><head>")
            append("<meta charset=\"UTF-8\">")
            append("<meta name=\"viewport\" content=\"width=$vw, initial-scale=1\">")
            append("<style>")
            append("html, body { width: ${vw}px; margin: 0; padding: 12px; }")
            append("body { font-family: serif; font-size: 14px; line-height: 1.5; max-width: 100%; overflow-wrap: break-word; }")
            // Cap images at viewport width × 1.4, matching our MAX_IMAGE_DIMENSION philosophy
            append("img, svg, video, canvas { max-width: 100%; max-height: ${(vw * 1.4).toInt()}px; height: auto; object-fit: contain; }")
            // For WebView: decoding hint to conserve memory
            append("img { image-rendering: auto; }")
            // Avoid fragmenting images/tables across pages (reduces repeat bitmaps)
            append("img, svg, figure, table { page-break-inside: avoid; }")
            // Remove large embedded base64 images that bloat the render tree
            append("img[src^=\"data:\"] { max-width: 200px; max-height: 200px; }")
            append("</style>")
            append("</head><body>")
            append(html)
            append("</body></html>")
        }
    }

    /**
     * Splits HTML content that exceeds [MAX_CHARS_PER_SEGMENT] into smaller chunks.
     * Splits at natural boundaries: <h1>-<h6>, <hr>, <section>, <div class="chapter">, etc.
     * If no natural boundaries are found, splits after paragraph boundaries.
     * Returns a list with the original HTML if no splitting is needed.
     */
    private fun splitLongHtml(html: String): List<String> {
        val stripped = html.replace(Regex("<head>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
        if (stripped.length <= MAX_CHARS_PER_SEGMENT) return listOf(html)

        Log.d(TAG, "Splitting long HTML (${html.length} chars) into segments")

        // Split points in priority order: headings, horizontal rules, section breaks
        val splitPatterns = listOf(
            Regex("(?=<h[1-6]\\b)", RegexOption.IGNORE_CASE),
            Regex("(?=<hr\\b)", RegexOption.IGNORE_CASE),
            Regex("(?=<section\\b)", RegexOption.IGNORE_CASE),
            Regex("(?=<div\\s+class=\"chapter\")", RegexOption.IGNORE_CASE),
            Regex("(?=<br\\s*/>\\s*<br\\s*/>)", RegexOption.IGNORE_CASE),
        )

        for (pattern in splitPatterns) {
            val parts = html.split(pattern).filter { it.isNotBlank() }
            if (parts.size > 1) {
                val merged = mergeSmallParts(parts, MAX_CHARS_PER_SEGMENT)
                Log.d(TAG, "Split HTML into ${merged.size} parts using $pattern")
                return merged
            }
        }

        // Last resort: split by paragraph boundaries
        val paragraphs = html.split(Regex("(?=</?p\\b)", RegexOption.IGNORE_CASE)).filter { it.isNotBlank() }
        if (paragraphs.size > 1) {
            val merged = mergeSmallParts(paragraphs, MAX_CHARS_PER_SEGMENT)
            if (merged.size > 1) {
                Log.d(TAG, "Split HTML into ${merged.size} parts by paragraphs")
                return merged
            }
        }

        // Can't split meaningfully — return as-is (will be a single large segment)
        Log.w(TAG, "Cannot split HTML naturally (${html.length} chars), rendering as single chunk")
        return listOf(html)
    }

    /**
     * Merges small adjacent parts so each chunk approaches but doesn't exceed [maxChars].
     */
    private fun mergeSmallParts(parts: List<String>, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (part in parts) {
            if (current.length + part.length > maxChars && current.isNotEmpty()) {
                result.add(current.toString())
                current.clear()
            }
            current.append(part)
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result.ifEmpty { parts }
    }

    /**
     * For very large chapter HTML (> [LARGE_HTML_THRESHOLD]), writes to a temp file
     * and loads via file:// URL to avoid keeping a massive String in memory
     * inside the WebView's data-URL internals.
     */
    private fun prepareHtmlForWebView(html: String, baseDir: String): Pair<String, String> {
        if (html.length <= LARGE_HTML_THRESHOLD) {
            return wrapHtml(html) to "file://$baseDir/"
        }
        // Write to temp file, then load via file:// URL (WebView streams it)
        val htmlFile = File(baseDir, "_chapter_${System.nanoTime()}.html")
        htmlFile.writeText(wrapHtml(html), Charsets.UTF_8)
        Log.d(TAG, "Large chapter HTML written to file: ${htmlFile.length() / 1024}KB")
        return htmlFile.toURI().toString() to "file://$baseDir/"
    }

    private fun saveWebViewToPdf(webView: WebView, outputFile: File, onComplete: () -> Unit) {
        outputFile.parentFile?.mkdirs()
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("ebook_pdf", "pdf", PDF_DPI, PDF_DPI))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val adapter = webView.createPrintDocumentAdapter("Chapter")
        adapter.onLayout(null, attributes, null, object : android.print.PrintDocumentAdapterHelper.LayoutCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                val pfd = ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
                try {
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        pfd,
                        null,
                        object : android.print.PrintDocumentAdapterHelper.WriteCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                try { pfd.close() } catch (_: Exception) {}
                                onComplete()
                            }
                        })
                } catch (e: Exception) {
                    try { pfd.close() } catch (_: Exception) {}
                    throw e
                }
            }
        }, null)
    }

    private fun mergePdfs(segments: List<File>, outputFile: File) {
        if (segments.size == 1) {
            // No merge needed — just copy to avoid PDFBox overhead
            segments[0].copyTo(outputFile, overwrite = true)
            Log.d(TAG, "Single segment, copied directly (${outputFile.length() / 1024}KB)")
            return
        }
        val merger = PDFMergerUtility()
        merger.isIgnoreAcroFormErrors = true
        segments.forEach { merger.addSource(it) }
        // Use buffered stream to reduce memory pressure during merge
        java.io.BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { bos ->
            merger.destinationStream = bos
            merger.mergeDocuments(null)
        }
        Log.d(TAG, "Merged ${segments.size} segments → ${outputFile.length() / 1024}KB")
    }
}
