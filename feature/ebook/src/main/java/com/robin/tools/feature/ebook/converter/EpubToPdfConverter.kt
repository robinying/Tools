package com.robin.tools.feature.ebook.converter

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.*
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

class EpubToPdfConverter(private val context: Context) {
    interface ProgressCallback {
        fun onProgress(percent: Int)
        fun onSuccess(file: File)
        fun onError(e: Exception)
    }

    private val tempDir = File(context.cacheDir, "epub_temp")

    suspend fun convert(epubUri: Uri, outputFileName: String, callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context)
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            val inputStream = context.contentResolver.openInputStream(epubUri)
                ?: throw IllegalArgumentException("Could not open input stream for URI: $epubUri")
            val book = EpubReader().readEpub(inputStream)
            extractResources(book, tempDir)

            val chapters = book.spine.spineReferences
            val pdfSegments = mutableListOf<File>()

            for (i in chapters.indices) {
                val progress = ((i.toFloat() / chapters.size) * 90).toInt()
                withContext(Dispatchers.Main) { callback.onProgress(progress) }

                val segmentFile = File(tempDir, "segment_$i.pdf")
                val htmlContent = String(chapters[i].resource.data)
                renderHtmlToPdf(htmlContent, tempDir.absolutePath, segmentFile)
                pdfSegments.add(segmentFile)
            }

            val outputFile = File(context.cacheDir, outputFileName)
            mergePdfs(pdfSegments, outputFile)

            withContext(Dispatchers.Main) {
                callback.onProgress(100)
                callback.onSuccess(outputFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { callback.onError(e) }
        } finally {
            // tempDir.deleteRecursively() // Optional: clean up here or in a separate step
        }
    }

    private fun extractResources(book: Book, resDir: File) {
        book.resources.all.forEach { res ->
            val file = File(resDir, res.href)
            file.parentFile?.mkdirs()
            file.writeBytes(res.data)
        }
    }

    private suspend fun renderHtmlToPdf(html: String, baseUrl: String, outputFile: File) = withContext(Dispatchers.Main) {
        val webView = WebView(context)
        val deferred = CompletableDeferred<Unit>()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                saveWebViewToPdf(view!!, outputFile) { deferred.complete(Unit) }
            }
        }
        webView.loadDataWithBaseURL("file://$baseUrl/", html, "text/html", "UTF-8", null)
        deferred.await()
    }

    private fun saveWebViewToPdf(webView: WebView, outputFile: File, onComplete: () -> Unit) {
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("id", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        val adapter = webView.createPrintDocumentAdapter("Chapter")
        adapter.onLayout(null, attributes, null, object : android.print.PrintDocumentAdapterHelper.LayoutCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                adapter.onWrite(arrayOf(PageRange.ALL_PAGES), ParcelFileDescriptor.open(outputFile, ParcelFileDescriptor.MODE_READ_WRITE), null, object : android.print.PrintDocumentAdapterHelper.WriteCallback() {
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
