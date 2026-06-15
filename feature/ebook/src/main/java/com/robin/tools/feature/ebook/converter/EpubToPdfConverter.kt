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

        // ~8-10 A4 pages worth of text at 14px font. Lowered from 50K to keep
        // Print Framework's per-segment page buffer under ~28MB on low-memory devices.
        // Each A4 page at 96 DPI ≈ 1.8MB; 10 pages ≈ 18MB, well within heap limits.
        private const val MAX_CHARS_PER_SEGMENT = 30_000

        // 96 DPI — per-page bitmap ~794×1123 px. At RGB_565 that's ~1.8MB per page.
        // Lowered from 120 DPI to reduce memory pressure on low-end devices.
        // Quality is still sufficient for reading text on screen and most print scenarios.
        private const val PDF_DPI = 96

        // Pause after WebView destroy during OOM recovery to allow native memory to be freed
        private const val OOM_RECOVERY_DELAY_MS = 300L
    }

    private val tempDir = File(context.cacheDir, "epub_temp")

    suspend fun convert(epubUri: Uri, outputFileName: String, callback: ProgressCallback) {
        var webView: WebView? = null
        try {
            withContext(Dispatchers.IO) {
                PDFBoxResourceLoader.init(context)
                if (tempDir.exists()) tempDir.deleteRecursively()
                tempDir.mkdirs()

                var book: Book? = context.contentResolver.openInputStream(epubUri)?.use { stream ->
                    EpubReader().readEpub(stream)
                } ?: throw IllegalArgumentException("Could not open input stream for URI: $epubUri")

                // Extract spine references BEFORE clearing resources, since clearing
                // the underlying resource data invalidates spine references
                val chapters = book!!.spine.spineReferences
                    .map { it.resource?.href ?: "" }
                    .filter { it.isNotEmpty() }
                    .toList()  // Materialize to a concrete list before releasing book data
                if (chapters.isEmpty()) throw IllegalStateException("No valid chapters found in EPUB")

                // Write resources to disk AND release each resource's byte[] immediately.
                // The Book object holds ALL resource byte[] across the entire conversion,
                // which is the #1 cause of OOM for large EPUBs. By releasing each resource
                // right after writing it to disk, we ensure only one resource's byte[]
                // is in memory at a time.
                extractAndReleaseResources(book!!, tempDir)

                // Release the Book object to allow GC of all metadata
                book = null

                // Create one WebView on the main thread — reused for all chapters to avoid OOM
                webView = withContext(Dispatchers.Main) { createWebView() }

                var mergedPdf: File? = null

                val runtime = Runtime.getRuntime()
                val maxMemoryMB = runtime.maxMemory() / (1024 * 1024)
                Log.d(TAG, "Max JVM heap: ${maxMemoryMB}MB, chapters (files): ${chapters.size}")

                // Process chapters one-at-a-time. Each chapter may be split into
                // sub-parts if it exceeds MAX_CHARS_PER_SEGMENT. Critically, we do
                // NOT pre-read all chapters — each chapter's HTML is read, split,
                // rendered, and then released before the next chapter is loaded.
                for (chapterIdx in chapters.indices) {
                    val chapterHref = chapters[chapterIdx]

                    val result = processChapter(
                        webView!!, chapterIdx, chapterHref, chapters.size,
                        mergedPdf, runtime, maxMemoryMB
                    )

                    webView = result.first
                    mergedPdf = result.second

                    withContext(Dispatchers.Main) {
                        callback.onProgress(((chapterIdx.toFloat() / chapters.size) * 85).toInt())
                    }
                }

                if (mergedPdf == null) throw IllegalStateException("All chapters failed to render")

                val outputDir = File(context.cacheDir, "pdf_output")
                outputDir.mkdirs()
                val outputFile = File(outputDir, outputFileName)
                mergedPdf.copyTo(outputFile, overwrite = true)

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

    /**
     * Processes a single chapter: reads its HTML from disk, splits if needed,
     * renders each part to PDF, and incrementally merges the result.
     * Extracted to a function so local variables (rawHtml, parts) can be GC'd
     * after this chapter completes, before the next one starts.
     */
    private suspend fun processChapter(
        webView: WebView,
        chapterIdx: Int,
        chapterHref: String,
        totalChapters: Int,
        currentMergedPdf: File?,
        runtime: Runtime,
        maxMemoryMB: Long
    ): Pair<WebView, File?> {
        var wv = webView
        val chapterFile = File(tempDir, chapterHref)
        val chapterLabel = "${chapterIdx + 1}"
        var mergedResult = currentMergedPdf

        if (!chapterFile.exists() || chapterFile.length() == 0L) {
            Log.w(TAG, "Skipping empty/missing chapter $chapterLabel: $chapterHref")
            return wv to mergedResult
        }

        // Read ONE chapter at a time using readText() which avoids the intermediate
        // ByteArray that readBytes().toString() would create.
        val rawHtml: String
        try {
            rawHtml = chapterFile.inputStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read chapter $chapterLabel, skipping: ${e.message}")
            return wv to mergedResult
        }
        if (rawHtml.isBlank()) return wv to mergedResult

        // Split into page-count-safe chunks. Most chapters won't split.
        val parts = splitLongHtml(rawHtml)
        // rawHtml is no longer referenced after this point; eligible for GC

        for (partIdx in parts.indices) {
            val htmlContent = parts[partIdx]
            val partLabel = if (parts.size > 1)
                "$chapterLabel.${partIdx + 1}" else chapterLabel

            // Memory check before each render
            val usedMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            if (usedMB > maxMemoryMB * 0.8) {
                Log.w(TAG, "Memory high: ${usedMB}/${maxMemoryMB}MB — GC before $partLabel")
                System.gc()
                System.runFinalization()
            }

            val segmentFile = File(tempDir, "segment_${chapterIdx}_${partIdx}.pdf")
            Log.d(TAG, "Rendering $partLabel (${htmlContent.length} chars, ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)}/${maxMemoryMB}MB)")

            var renderOk = false
            try {
                renderChapterToPdf(wv, htmlContent, tempDir.absolutePath, segmentFile)
                renderOk = true
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM at $partLabel, purging WebView and retrying once...")
                withContext(Dispatchers.Main) {
                    wv.destroy()
                }
                System.gc()
                System.runFinalization()
                // Delay to allow native WebView memory to be fully released before recreating
                delay(OOM_RECOVERY_DELAY_MS)
                System.gc()
                wv = withContext(Dispatchers.Main) { createWebView() }
                try {
                    renderChapterToPdf(wv, htmlContent, tempDir.absolutePath, segmentFile)
                    renderOk = true
                    Log.d(TAG, "OOM retry OK for $partLabel")
                } catch (e2: Exception) {
                    Log.e(TAG, "OOM retry failed for $partLabel, skipping")
                }
            } catch (e: CancellationException) {
                throw IllegalStateException("Cancelled at $partLabel: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed $partLabel: ${e.message}, skipping")
            }

            if (renderOk && segmentFile.exists() && segmentFile.length() > 0) {
                // Incremental merge: merge this segment into the running result immediately
                // rather than accumulating all segments. This keeps at most 2 PDF files
                // open at a time instead of N, substantially reducing memory for long books.
                mergedResult = incrementalMergePdf(mergedResult, segmentFile)
            }

            // WebView cleanup between parts
            withContext(Dispatchers.Main) {
                wv.clearHistory()
                wv.clearCache(true)
                wv.freeMemory()
            }
        }

        // After each chapter, force a stronger WebView reset
        val segmentsAfterChapter = parts.size // approximate count
        if (segmentsAfterChapter > 0 && parts.size % WEBVIEW_RECYCLE_INTERVAL == 0) {
            withContext(Dispatchers.Main) {
                wv.destroy()
                wv = createWebView()
            }
            System.gc()
            System.runFinalization()
        }

        return wv to mergedResult
    }

    /**
     * Extracts each resource to disk and immediately releases its byte[] to minimize
     * peak memory. Instead of holding ALL resource byte[] simultaneously, only one
     * resource's data is in memory at any point during extraction.
     * For images that need resizing, writes the original to disk first then resizes
     * in-place from file — this avoids holding both the original byte[] and the
     * decoded Bitmap simultaneously.
     */
    private fun extractAndReleaseResources(book: Book, resDir: File) {
        book.resources.all.forEach { res ->
            val relativePath = res.href.trimStart('/')
            val safePath = relativePath.replace("../", "").replace("..\\", "")
            val file = File(resDir, safePath)
            file.parentFile?.mkdirs()

            // Write original data to disk first
            file.writeBytes(res.data)

            // If it's a large image, resize in-place from file (avoids holding
            // byte[] + Bitmap + ByteArrayOutputStream simultaneously)
            if (isImageFile(safePath) && res.data.size > 50 * 1024) {
                resizeImageInPlace(file)
            }

            // Immediately release this resource's byte[] so it can be GC'd
            // before the next resource is processed
            res.data = ByteArray(0)
        }
    }

    private fun isImageFile(path: String): Boolean {
        val ext = path.lowercase().substringAfterLast('.', "")
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
    }

    /**
     * Resizes an image file in-place. Reads dimensions from file (not byte[]),
     * decodes with downsampling directly from file, and compresses back to the
     * same file. This avoids holding the original byte[] and the decoded Bitmap
     * simultaneously — only one Bitmap is in memory at a time.
     */
    private fun resizeImageInPlace(file: File): Boolean {
        return try {
            // Decode bounds only (no pixel allocation)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            val maxDim = maxOf(opts.outWidth, opts.outHeight)
            if (maxDim <= MAX_IMAGE_DIMENSION) return false // Already small enough

            // Calculate power-of-2 sample size (Android requires powers of 2)
            var sampleSize = 1
            while (maxDim / sampleSize > MAX_IMAGE_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                // RGB_565 uses 2 bytes/pixel instead of 4 (ARGB_8888).
                // Acceptable quality loss for print — halves bitmap memory.
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            // Decode directly from file — no intermediate byte[] in memory
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                ?: return false

            // Determine output format and quality
            val format = if (opts.outMimeType == "image/png" &&
                (opts.outWidth <= 200 || opts.outHeight <= 200))
                Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            val quality = if (format == Bitmap.CompressFormat.JPEG) 70 else 85

            // Write compressed result directly to file — no ByteArrayOutputStream buffer
            val tempFile = File(file.parent, file.name + ".tmp")
            try {
                tempFile.outputStream().use { fos ->
                    if (!bitmap.compress(format, quality, fos)) {
                        Log.w(TAG, "Bitmap.compress returned false for ${file.name}")
                        return false
                    }
                    fos.flush()
                    fos.fd.sync()
                }
                // Capture dimensions BEFORE recycle
                val newW = bitmap.width
                val newH = bitmap.height
                bitmap.recycle()

                // Replace original with resized version
                if (!file.delete() || !tempFile.renameTo(file)) {
                    Log.w(TAG, "Failed to replace image file, keeping original")
                    tempFile.delete()
                    return false
                }
                Log.d(TAG, "Resized image: ${opts.outWidth}x${opts.outHeight} → " +
                    "${newW}x${newH} ${file.length() / 1024}KB (sample=$sampleSize)")
                true
            } catch (e: Exception) {
                bitmap.recycle()
                tempFile.delete()
                Log.w(TAG, "Failed to resize image ${file.name}: ${e.message}")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resize image ${file.name}, using original", e)
            false
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
                // Stop any ongoing WebView loading to prevent callbacks after timeout/OOM
                try { webView.stopLoading() } catch (_: Exception) {}
            }
        }

        timeoutJob = launch {
            delay(CHAPTER_TIMEOUT_MS)
            if (!isCompleted) {
                isCompleted = true
                try { webView.stopLoading() } catch (_: Exception) {}
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
            wv.settings.allowFileAccess = true  // Needed for file:// loading of local images/resources
            wv.settings.allowContentAccess = false
            wv.settings.loadWithOverviewMode = true
            wv.settings.useWideViewPort = true
            wv.settings.builtInZoomControls = false
            wv.settings.displayZoomControls = false
            wv.settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
            wv.settings.blockNetworkLoads = true  // No external resources
            wv.settings.blockNetworkImage = false  // Allow LOCAL images (file://)
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
     * At 96 DPI: ~794px. Each page buffer ≈ 1.8MB at RGB_565.
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
     * Estimates length by removing <head> before deciding whether to split,
     * avoiding creation of a full string copy for the simple case.
     * Splits at natural boundaries: <h1>-<h6>, <hr>, <section>, <div class="chapter">, etc.
     */
    private fun splitLongHtml(html: String): List<String> {
        // Quick check: if HTML is well within the limit, skip the expensive regex
        if (html.length <= MAX_CHARS_PER_SEGMENT) return listOf(html)

        val headRegex = Regex("<head>.*?</head>", RegexOption.DOT_MATCHES_ALL)
        // Estimate stripped length without creating a full copy
        val estimatedLength = html.length - headRegex.findAll(html).sumOf { it.value.length }
        if (estimatedLength <= MAX_CHARS_PER_SEGMENT) return listOf(html)

        // Only now create the stripped version for actual splitting
        val stripped = html.replace(headRegex, "")
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
            val parts = stripped.split(pattern).filter { it.isNotBlank() }
            if (parts.size > 1) {
                val merged = mergeSmallParts(parts, MAX_CHARS_PER_SEGMENT)
                Log.d(TAG, "Split HTML into ${merged.size} parts using $pattern")
                return merged
            }
        }

        // Last resort: split by paragraph boundaries
        val paragraphs = stripped.split(Regex("(?=</?p\\b)", RegexOption.IGNORE_CASE)).filter { it.isNotBlank() }
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
     * Pre-allocates StringBuilder capacity to reduce array copies during appends.
     */
    private fun mergeSmallParts(parts: List<String>, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder(maxChars.coerceAtMost(MAX_CHARS_PER_SEGMENT))
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

        // Call onStart() to follow the full PrintDocumentAdapter lifecycle
        adapter.onStart()

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
                                adapter.onFinish()  // Complete the adapter lifecycle
                                onComplete()
                            }
                        })
                } catch (e: Exception) {
                    try { pfd.close() } catch (_: Exception) {}
                    adapter.onFinish()  // Ensure lifecycle completes even on error
                    throw e
                }
            }
        }, null)
    }

    /**
     * Incrementally merges a new segment into the existing merged PDF (if any).
     * Keeps at most 2 PDF files open at a time, instead of accumulating all
     * segments and merging at the end. This substantially reduces peak memory
     * for books with many chapters/segments.
     */
    private fun incrementalMergePdf(currentMerged: File?, newSegment: File): File {
        if (currentMerged == null) {
            // First segment — just use it directly
            return newSegment
        }

        val mergedOutput = File(tempDir, "merge_${System.nanoTime()}.pdf")
        val merger = PDFMergerUtility()
        merger.isIgnoreAcroFormErrors = true
        merger.addSource(currentMerged)
        merger.addSource(newSegment)
        java.io.BufferedOutputStream(FileOutputStream(mergedOutput), 64 * 1024).use { bos ->
            merger.destinationStream = bos
            merger.mergeDocuments(null)
        }

        // Clean up intermediate files
        currentMerged.delete()
        newSegment.delete()

        Log.d(TAG, "Incremental merge → ${mergedOutput.length() / 1024}KB")
        return mergedOutput
    }
}