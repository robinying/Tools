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

        var muxer: MediaMuxer? = null
        var videoTrackIndex = -1
        var muxerStarted = false

        return try {
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            for (segment in segments) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segment.filePath)

                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                        if (mime.startsWith("video/")) {
                            if (videoTrackIndex < 0) {
                                videoTrackIndex = muxer.addTrack(format)
                            }
                            extractor.selectTrack(i)
                            if (!muxerStarted) {
                                muxer.start()
                                muxerStarted = true
                            }

                            val buffer = ByteBuffer.allocate(256 * 1024)
                            val bufferInfo = android.media.MediaCodec.BufferInfo()
                            while (true) {
                                val sampleSize = extractor.readSampleData(buffer, 0)
                                if (sampleSize < 0) break
                                bufferInfo.set(
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    extractor.sampleFlags
                                )
                                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                                extractor.advance()
                            }
                        }
                    }
                } finally {
                    try {
                        extractor.release()
                    } catch (_: Exception) {
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }
}
