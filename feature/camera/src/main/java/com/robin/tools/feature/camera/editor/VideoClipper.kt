package com.robin.tools.feature.camera.editor

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

/**
 * Remuxes a time range from [inputPath] into [outputPath], preserving both
 * video and audio tracks when present. No re-encode — keyframe-accurate seek.
 */
class VideoClipper {
    fun clip(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        return try {
            extractor.setDataSource(inputPath)
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = HashMap<Int, Int>() // extractor index -> muxer index
            var rotation = 0

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    val muxerIndex = muxer.addTrack(format)
                    trackMap[i] = muxerIndex
                    if (mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_ROTATION)) {
                        rotation = format.getInteger(MediaFormat.KEY_ROTATION)
                    }
                }
            }

            if (trackMap.isEmpty()) return false

            if (rotation != 0) {
                muxer.setOrientationHint(rotation)
            }
            muxer.start()
            muxerStarted = true

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            for ((extractorIndex, muxerIndex) in trackMap) {
                extractor.selectTrack(extractorIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

                // Shared time base (startUs) keeps A/V in sync after clip.
                while (true) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break

                    val sampleTime = extractor.sampleTime
                    if (sampleTime > endUs) break

                    if (sampleTime >= startUs) {
                        bufferInfo.offset = 0
                        bufferInfo.size = size
                        bufferInfo.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                        bufferInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxerIndex, buffer, bufferInfo)
                    }
                    extractor.advance()
                }

                extractor.unselectTrack(extractorIndex)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
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
