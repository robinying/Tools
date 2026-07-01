package com.robin.tools.feature.camera.segment

import android.util.Log
import java.io.File

class SegmentRecorder(private val outputDir: File) {
    private val segments = mutableListOf<SegmentData>()
    private var currentSegmentIndex: Int = 0

    val segmentCount: Int get() = segments.size
    val totalDurationMs: Long get() = segments.sumOf { it.durationMs }

    fun startNewSegment(): String {
        currentSegmentIndex++
        return File(outputDir, "segment_${currentSegmentIndex}.mp4").absolutePath
    }

    fun onSegmentComplete(durationMs: Long) {
        val path = File(outputDir, "segment_${currentSegmentIndex}.mp4").absolutePath
        segments.add(SegmentData(path, durationMs, currentSegmentIndex))
    }

    fun deleteLastSegment(): Boolean {
        if (segments.isEmpty()) return false
        val last = segments.removeAt(segments.size - 1)
        currentSegmentIndex--
        File(last.filePath).delete()
        return true
    }

    fun getSegments(): List<SegmentData> = segments.toList()

    fun reset() {
        segments.forEach { File(it.filePath).delete() }
        segments.clear()
        currentSegmentIndex = 0
    }
}
