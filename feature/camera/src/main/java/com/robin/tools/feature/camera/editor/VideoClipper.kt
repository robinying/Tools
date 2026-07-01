package com.robin.tools.feature.camera.editor

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class VideoClipper {
    fun clip(inputPath: String, outputPath: String, startMs: Long, endMs: Long): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)
            val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIdx = -1
            var started = false

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    extractor.selectTrack(i)
                    videoTrackIdx = muxer.addTrack(format)
                    if (videoTrackIdx >= 0) { muxer.start(); started = true }
                }
            }

            if (!started) { muxer.release(); extractor.release(); return false }

            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(512 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                buffer.clear()
                bufferInfo.set(0, 0, 0, 0)
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (extractor.sampleTime > endMs * 1000) break

                bufferInfo.offset = 0
                bufferInfo.size = size
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(videoTrackIdx, buffer, bufferInfo)
                extractor.advance()
            }

            extractor.release()
            muxer.stop()
            muxer.release()
            true
        } catch (e: Exception) {
            false
        }
    }
}
