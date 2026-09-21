package com.robin.tools.feature.storage.data

import java.nio.ByteBuffer
import java.util.SplittableRandom

/**
 * 测试用缓冲区的创建与填充。
 *
 * 统一使用直接缓冲区：`FileChannel` 读写直接缓冲区时不需要额外的堆内拷贝，
 * 测量结果更贴近真实的 IO 路径开销。
 */
object BenchmarkBuffers {

    /** 填充伪随机数据时使用的种子，固定值以保证不同次测试的数据分布一致。 */
    private const val RANDOM_SEED = 0x5EED_1234_ABCD_EF01L

    /**
     * 创建指定大小的直接缓冲区。
     *
     * @param sizeBytes 缓冲区字节数，必须为正
     */
    fun directBuffer(sizeBytes: Int): ByteBuffer {
        require(sizeBytes > 0) { "buffer size must be positive, was $sizeBytes" }
        return ByteBuffer.allocateDirect(sizeBytes)
    }

    /**
     * 用伪随机字节填满缓冲区，并把 position 复位到 0。
     *
     * **不要**改成全零填充。部分文件系统（如 F2FS 压缩）和存储主控对全零块有特殊处理，
     * 会让写入速度虚高，测出来的数字不再代表真实介质性能。
     */
    fun fillWithPseudoRandom(buffer: ByteBuffer, seed: Long = RANDOM_SEED) {
        val random = SplittableRandom(seed)
        buffer.clear()
        while (buffer.remaining() >= Long.SIZE_BYTES) {
            buffer.putLong(random.nextLong())
        }
        while (buffer.hasRemaining()) {
            buffer.put(random.nextInt().toByte())
        }
        buffer.clear()
    }

    /**
     * 生成一段伪随机字节数组。
     *
     * 调用方应在计时区间**之外**生成并复用，避免生成开销混进被测延迟。
     * 填充伪随机而非全零的理由同 [fillWithPseudoRandom]。
     *
     * 这里刻意用 `nextLong()` 手工拆字节，而不用 `SplittableRandom#nextBytes`：
     * 后者继承自 Java 17 的 `RandomGenerator`，Android 平台上 API 33 才有，
     * 在 minSdk 28 的设备上会直接 `NoSuchMethodError`。
     */
    fun pseudoRandomBytes(sizeBytes: Int, seed: Long = RANDOM_SEED): ByteArray {
        require(sizeBytes > 0) { "payload size must be positive, was $sizeBytes" }
        val random = SplittableRandom(seed)
        val bytes = ByteArray(sizeBytes)

        var index = 0
        while (index + Long.SIZE_BYTES <= sizeBytes) {
            val value = random.nextLong()
            for (byteIndex in 0 until Long.SIZE_BYTES) {
                bytes[index + byteIndex] = (value ushr (byteIndex * Byte.SIZE_BITS)).toByte()
            }
            index += Long.SIZE_BYTES
        }
        while (index < sizeBytes) {
            bytes[index] = random.nextInt().toByte()
            index++
        }
        return bytes
    }
}
