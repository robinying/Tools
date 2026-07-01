package com.robin.tools.feature.face.data

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.sqrt

class FaceEmbeddingExtractor(context: Context) {

    private var interpreter: org.tensorflow.lite.Interpreter? = null
    val isModelLoaded: Boolean

    init {
        isModelLoaded = runCatching {
            val model = loadModelFile(context, MODEL_FILE)
            interpreter = org.tensorflow.lite.Interpreter(model)
        }.isSuccess
    }

    fun dispose() {
        interpreter?.close()
        interpreter = null
    }

    fun extract(alignedFace: Bitmap): FloatArray {
        val tflite = interpreter
            ?: throw IllegalStateException(
                "Face recognition model not loaded. Place $MODEL_FILE in assets/."
            )
        val input = preprocess(alignedFace)
        val output = Array(1) { FloatArray(EMBEDDING_DIM) }
        tflite.run(input, output)
        l2Normalize(output[0])
        return output[0]
    }

    private fun preprocess(bitmap: Bitmap): java.nio.ByteBuffer {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val buffer = java.nio.ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * CHANNELS * java.lang.Float.SIZE / java.lang.Byte.SIZE
        )
        buffer.order(java.nio.ByteOrder.nativeOrder())

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r / 127.5f) - 1f)
            buffer.putFloat((g / 127.5f) - 1f)
            buffer.putFloat((b / 127.5f) - 1f)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(context: Context, path: String): java.nio.MappedByteBuffer {
        val descriptor = context.assets.openFd(path)
        val inputStream = java.io.FileInputStream(descriptor.fileDescriptor)
        return inputStream.channel.map(
            java.nio.channels.FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength
        )
    }

    private fun l2Normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
    }

    companion object {
        private const val MODEL_FILE = "mobile_face_net.tflite"
        private const val INPUT_SIZE = 112
        private const val CHANNELS = 3
        private const val EMBEDDING_DIM = 128
    }
}
