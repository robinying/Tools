package com.robin.tools.feature.camera.segment

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object SegmentMerger {
    fun mergeSegments(segments: List<SegmentData>, outputPath: String): Boolean {
        if (segments.isEmpty()) return false
        if (segments.size == 1) {
            File(segments[0].filePath).copyTo(File(outputPath), overwrite = true)
            return true
        }

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var muxerStarted = false

        try {
            for (segment in segments) {
                val extractor = MediaExtractor()
                extractor.setDataSource(segment.filePath)

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                    if (mime.startsWith("video/")) {
                        if (videoTrackIndex < 0) videoTrackIndex = muxer.addTrack(format)
                        extractor.selectTrack(i)
                        if (!muxerStarted) { muxer.start(); muxerStarted = true }

                        val buffer = ByteBuffer.allocate(256 * 1024)
                        val bufferInfo = android.media.MediaCodec.BufferInfo()
                        while (extractor.readSampleData(buffer, 0) > 0) {
                            bufferInfo.apply {
                                offset = 0
                                size = buffer.position()
                                presentationTimeUs = extractor.sampleTime
                                flags = extractor.sampleFlags
                            }
                            buffer.position(0)
                            muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                            buffer.clear()
                            extractor.advance()
                        }
                    }
                }
                extractor.release()
            }
        } catch (e: Exception) {
            muxer.release()
            return false
        }

        muxer.stop()
        muxer.release()
        return true
    }
}
