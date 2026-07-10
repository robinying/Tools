package com.robin.tools.feature.media.delegate

import android.graphics.Bitmap
import com.robin.tools.feature.media.data.FilterType
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Implements all [FilterType] filters using the OpenCV Android SDK (Java API).
 *
 * Native libraries are bundled with the `:opencv` Gradle module
 * (OpenCV 4.12.0 SDK, `libopencv_java4.so`). Loaded once on first use.
 */
class OpenCVFilterDelegate : FilterDelegate {

    companion object {
        private const val LIB_NAME = "opencv_java4"

        @Volatile
        private var loaded = false

        private fun ensureLoaded(): Boolean {
            if (loaded) return true
            synchronized(this) {
                if (loaded) return true
                try {
                    System.loadLibrary(LIB_NAME)
                    loaded = true
                } catch (_: UnsatisfiedLinkError) {
                    return false
                }
            }
            return true
        }
    }

    override suspend fun applyFilter(
        input: Bitmap,
        type: FilterType,
        onProgress: (Float, String) -> Unit
    ): Result<Bitmap> {
        if (!ensureLoaded()) {
            return Result.failure(IllegalStateException("OpenCV native library failed to load"))
        }

        if (input.isRecycled) {
            return Result.failure(IllegalStateException("Input bitmap is recycled"))
        }

        return try {
            val src = rgbFromBitmap(input)
            onProgress(0.1f, "Applying filter…")

            val dst = when (type) {
                FilterType.GRAYSCALE -> applyGrayscale(src)
                FilterType.BLUR -> applyBlur(src)
                FilterType.EDGE_DETECTION -> applyEdgeDetection(src)
                FilterType.CARTOON -> applyCartoon(src)
                FilterType.SHARPEN -> applySharpen(src)
                FilterType.SKETCH -> applySketch(src)
            }

            onProgress(0.8f, "Converting result…")
            val result = bitmapFromRgb(dst)
            onProgress(1.0f, "Done")

            src.release()
            dst.release()

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Filter implementations -----------------------------------------------

    private fun applyGrayscale(src: Mat): Mat {
        val gray = Mat()
        val dst = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        Imgproc.cvtColor(gray, dst, Imgproc.COLOR_GRAY2RGB)
        gray.release()
        return dst
    }

    private fun applyBlur(src: Mat): Mat {
        val dst = Mat()
        Imgproc.GaussianBlur(src, dst, Size(31.0, 31.0), 0.0)
        return dst
    }

    private fun applyEdgeDetection(src: Mat): Mat {
        val gray = Mat()
        val edges = Mat()
        val dst = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)
        Imgproc.Canny(gray, edges, 80.0, 200.0)
        // Invert so edges become dark lines on white background (sketch feel)
        Core.bitwise_not(edges, edges)
        Imgproc.cvtColor(edges, dst, Imgproc.COLOR_GRAY2RGB)
        gray.release()
        edges.release()
        return dst
    }

    private fun applyCartoon(src: Mat): Mat {
        val smoothed = Mat()
        // Bilateral filter preserves edges while smoothing flat regions
        Imgproc.bilateralFilter(src, smoothed, 9, 75.0, 75.0)

        val gray = Mat()
        Imgproc.cvtColor(smoothed, gray, Imgproc.COLOR_RGB2GRAY)

        val edges = Mat()
        Imgproc.adaptiveThreshold(
            gray,
            edges,
            255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY,
            9,
            2.0
        )

        val edgesRgb = Mat()
        Imgproc.cvtColor(edges, edgesRgb, Imgproc.COLOR_GRAY2RGB)

        val dst = Mat()
        // Overlay edges onto smoothed image via bitwise AND
        Core.bitwise_and(smoothed, edgesRgb, dst)

        smoothed.release()
        gray.release()
        edges.release()
        edgesRgb.release()
        return dst
    }

    private fun applySharpen(src: Mat): Mat {
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0, floatArrayOf(
             0f, -1f,  0f,
            -1f,  5f, -1f,
             0f, -1f,  0f
        ))
        val dst = Mat()
        Imgproc.filter2D(src, dst, -1, kernel)
        kernel.release()
        return dst
    }

    private fun applySketch(src: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGB2GRAY)

        val inverted = Mat()
        Core.bitwise_not(gray, inverted)

        val blurred = Mat()
        Imgproc.GaussianBlur(inverted, blurred, Size(21.0, 21.0), 0.0)

        val sketch = Mat()
        // Divide gray by blurred-inverted (dodge blend for pencil-sketch effect)
        Core.divide(gray, blurred, sketch, 255.0)

        val dst = Mat()
        Imgproc.cvtColor(sketch, dst, Imgproc.COLOR_GRAY2RGB)

        gray.release()
        inverted.release()
        blurred.release()
        sketch.release()
        return dst
    }

    // --- Bitmap ⇔ Mat helpers ------------------------------------------------

    private fun rgbFromBitmap(bitmap: Bitmap): Mat {
        val mat = Mat()
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(copy, mat)
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB)
        copy.recycle()
        return mat
    }

    private fun bitmapFromRgb(mat: Mat): Bitmap {
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_RGB2RGBA)
        val bitmap = Bitmap.createBitmap(rgba.cols(), rgba.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bitmap)
        rgba.release()
        return bitmap
    }
}
