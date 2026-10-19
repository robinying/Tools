package com.robin.tools.feature.media.scanner

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.robin.tools.core.media.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class DocumentScanResult(
    val bitmap: Bitmap,
    val pageDetected: Boolean
)

/**
 * Offline document scanning: finds the largest convex quadrilateral in the
 * image (the page), then applies a perspective warp to a flat rectangle.
 *
 * If no convincing quad is found, the full image is returned unchanged so the
 * user can still save or crop it with another tool.
 */
object DocumentScanProcessor {

    private const val LIB_NAME = "opencv_java4"
    private const val MAX_DIMENSION = 2600

    @Volatile
    private var loaded = false

    private fun ensureLoaded(): Boolean {
        if (loaded) return true
        synchronized(this) {
            if (loaded) return true
            return try {
                System.loadLibrary(LIB_NAME)
                loaded = true
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
    }

    suspend fun scan(context: Context, uri: Uri): Result<DocumentScanResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(ensureLoaded()) { "OpenCV native library failed to load" }
                val source = BitmapLoader.load(context, uri, MAX_DIMENSION)
                    ?: error("Cannot decode the selected image")
                try {
                    val rgba = Mat()
                    Utils.bitmapToMat(source, rgba)
                    try {
                        val quad = detectPageQuad(rgba)
                        if (quad == null) {
                            DocumentScanResult(
                                source.copy(Bitmap.Config.ARGB_8888, false),
                                pageDetected = false
                            )
                        } else {
                            DocumentScanResult(
                                warp(rgba, quad),
                                pageDetected = true
                            )
                        }
                    } finally {
                        rgba.release()
                    }
                } finally {
                    source.recycle()
                }
            }
        }

    private fun detectPageQuad(rgba: Mat): List<Point>? {
        val gray = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 60.0, 180.0)
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_RECT,
                Size(3.0, 3.0)
            )
            Imgproc.dilate(edges, edges, kernel)
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_LIST,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = rgba.width().toDouble() * rgba.height()
            val minimumArea = imageArea * 0.15
            for (contour in contours.sortedByDescending { Imgproc.contourArea(it) }) {
                val area = Imgproc.contourArea(contour)
                if (area < minimumArea) break

                val curve = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(curve, true)
                    Imgproc.approxPolyDP(curve, approx, 0.02 * perimeter, true)
                    if (approx.total() == 4L) {
                        val points = approx.toArray().toList()
                        if (points.isConvex()) {
                            return orderCorners(points)
                        }
                    }
                } finally {
                    curve.release()
                    approx.release()
                }
            }
            return null
        } finally {
            gray.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun List<Point>.isConvex(): Boolean {
        if (size != 4) return false
        var sign = 0
        for (i in indices) {
            val a = this[i]
            val b = this[(i + 1) % 4]
            val c = this[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val current = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (current != 0) {
                if (sign == 0) sign = current else if (current != sign) return false
            }
        }
        return true
    }

    private fun orderCorners(points: List<Point>): List<Point> {
        val topLeft = points.minByOrNull { it.x + it.y } ?: points[0]
        val bottomRight = points.maxByOrNull { it.x + it.y } ?: points[2]
        val topRight = points.minByOrNull { it.y - it.x } ?: points[1]
        val bottomLeft = points.maxByOrNull { it.y - it.x } ?: points[3]
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun warp(rgba: Mat, quad: List<Point>): Bitmap {
        val topLeft = quad[0]
        val topRight = quad[1]
        val bottomRight = quad[2]
        val bottomLeft = quad[3]
        val width = maxOf(distance(topLeft, topRight), distance(bottomLeft, bottomRight))
            .toInt().coerceAtLeast(1)
        val height = maxOf(distance(topLeft, bottomLeft), distance(topRight, bottomRight))
            .toInt().coerceAtLeast(1)

        val source = MatOfPoint2f(topLeft, topRight, bottomRight, bottomLeft)
        val destination = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width - 1.0, 0.0),
            Point(width - 1.0, height - 1.0),
            Point(0.0, height - 1.0)
        )
        val transform = Imgproc.getPerspectiveTransform(source, destination)
        val warped = Mat()
        try {
            Imgproc.warpPerspective(
                rgba,
                warped,
                transform,
                Size(width.toDouble(), height.toDouble())
            )
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warped, output)
            return output
        } finally {
            source.release()
            destination.release()
            transform.release()
            warped.release()
        }
    }

    private fun distance(a: Point, b: Point): Double =
        Math.hypot(a.x - b.x, a.y - b.y)
}
