package com.robin.tools.feature.camera.storage

import android.content.Context
import java.io.File

class CameraFileManager(context: Context) {
    private val cameraDir = File(context.filesDir, "camera").apply { mkdirs() }
    val segmentDir = File(cameraDir, "segments").apply { mkdirs() }
    val outputDir = File(cameraDir, "output").apply { mkdirs() }

    fun createOutputFile(prefix: String = "video"): File {
        return File(outputDir, "${prefix}_${System.currentTimeMillis()}.mp4")
    }

    fun clearSegments() {
        segmentDir.listFiles()?.forEach { it.delete() }
    }

    fun clearAll() {
        segmentDir.listFiles()?.forEach { it.delete() }
        outputDir.listFiles()?.forEach { it.delete() }
    }
}
