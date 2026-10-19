package com.robin.tools.feature.toolkit.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** QR generation (ZXing) and decoding (ML Kit) used by the QR tool. */
object QrCodeTools {

    fun generate(content: String, size: Int = 1024): Bitmap {
        require(content.isNotBlank()) { "QR content is empty" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    suspend fun decode(context: Context, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, uri)
            BarcodeScanning.getClient()
                .process(image)
                .await()
                .mapNotNull { it.rawValue }
                .filter { it.isNotBlank() }
        }
}
