package com.robin.tools.feature.face.data

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * MobileFaceNet TFLite embedding extractor.
 *
 * Expected default model (`mobile_face_net.tflite`):
 * - Input: NHWC float32, typically [1, 112, 112, 3], normalized to [-1, 1]
 * - Output: L2-normalized embedding (128 or 192 dims depending on model)
 *
 * Dimensions are read from the loaded interpreter so alternate MobileFaceNet
 * exports keep working without code changes.
 */
class FaceEmbeddingExtractor(context: Context) {

    private var interpreter: Interpreter? = null
    private var inputSize: Int = DEFAULT_INPUT_SIZE
    private var embeddingDim: Int = DEFAULT_EMBEDDING_DIM
    private var channels: Int = 3

    val isModelLoaded: Boolean
    val modelInputSize: Int get() = inputSize
    val modelEmbeddingDim: Int get() = embeddingDim

    init {
        isModelLoaded = runCatching {
            val model = loadModelFile(context.applicationContext, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            val tflite = Interpreter(model, options)
            configureFromInterpreter(tflite)
            interpreter = tflite
            Log.i(TAG, "Loaded $MODEL_FILE input=${inputSize}x${inputSize}x$channels embedding=$embeddingDim")
            true
        }.onFailure { e ->
            Log.w(TAG, "Face model not loaded: ${e.message}")
        }.getOrDefault(false)
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

        val resized = if (alignedFace.width != inputSize || alignedFace.height != inputSize) {
            Bitmap.createScaledBitmap(alignedFace, inputSize, inputSize, true)
        } else {
            alignedFace
        }

        try {
            val input = preprocess(resized)
            val output = Array(1) { FloatArray(embeddingDim) }
            tflite.run(input, output)
            l2Normalize(output[0])
            return output[0]
        } finally {
            if (resized !== alignedFace && !resized.isRecycled) {
                resized.recycle()
            }
        }
    }

    private fun configureFromInterpreter(tflite: Interpreter) {
        val inTensor = tflite.getInputTensor(0)
        val outTensor = tflite.getOutputTensor(0)
        val inShape = inTensor.shape() // e.g. [1, 112, 112, 3]
        val outShape = outTensor.shape() // e.g. [1, 192]

        // Support NHWC [1,H,W,C] (common) and NCHW [1,C,H,W]
        when {
            inShape.size == 4 && inShape[3] in 1..4 -> {
                inputSize = inShape[1]
                channels = inShape[3]
            }
            inShape.size == 4 && inShape[1] in 1..4 -> {
                channels = inShape[1]
                inputSize = inShape[2]
            }
            else -> {
                inputSize = DEFAULT_INPUT_SIZE
                channels = 3
            }
        }

        embeddingDim = when {
            outShape.isEmpty() -> DEFAULT_EMBEDDING_DIM
            outShape.size == 1 -> outShape[0]
            else -> outShape.last()
        }.coerceAtLeast(1)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val buffer = ByteBuffer.allocateDirect(
            1 * inputSize * inputSize * channels * java.lang.Float.SIZE / java.lang.Byte.SIZE
        )
        buffer.order(ByteOrder.nativeOrder())

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // MobileFaceNet: (x - 127.5) / 128 ≈ [-1, 1]
            buffer.putFloat((r - 127.5f) / 128f)
            buffer.putFloat((g - 127.5f) / 128f)
            buffer.putFloat((b - 127.5f) / 128f)
        }
        buffer.rewind()
        return buffer
    }

    private fun loadModelFile(context: Context, path: String): MappedByteBuffer {
        // Keep the AssetFileDescriptor mapping alive for the process lifetime;
        // closing the fd can invalidate the MappedByteBuffer on some devices.
        val descriptor = context.assets.openFd(path)
        val inputStream = java.io.FileInputStream(descriptor.fileDescriptor)
        val channel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            descriptor.startOffset,
            descriptor.declaredLength
        )
    }

    private fun l2Normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) norm += v * v
        norm = sqrt(norm)
        if (norm > 1e-6f) {
            for (i in vector.indices) vector[i] /= norm
        }
    }

    companion object {
        private const val TAG = "FaceEmbedding"
        const val MODEL_FILE = "mobile_face_net.tflite"
        private const val DEFAULT_INPUT_SIZE = 112
        private const val DEFAULT_EMBEDDING_DIM = 192
    }
}
