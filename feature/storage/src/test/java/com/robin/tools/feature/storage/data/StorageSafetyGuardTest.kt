package com.robin.tools.feature.storage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageSafetyGuardTest {

    @Test
    fun `verifyCapacity accepts a payload that fits both budgets`() {
        // 64 MB payload on a 64 GB volume: well inside the 10% ratio and leaves the floor intact.
        StorageSafetyGuard.verifyCapacity(payloadBytes = 64L * MB, availableBytes = 64L * KB * MB)
    }

    @Test
    fun `verifyCapacity ignores a non positive payload`() {
        StorageSafetyGuard.verifyCapacity(payloadBytes = 0L, availableBytes = 0L)
        StorageSafetyGuard.verifyCapacity(payloadBytes = -1L, availableBytes = 0L)
    }

    @Test
    fun `verifyCapacity rejects a payload above the allocation ratio`() {
        val available = KB * MB // 1 GB
        val payload = 200L * MB // 20% of the volume

        val error = assertThrows(InsufficientStorageException::class.java) {
            StorageSafetyGuard.verifyCapacity(payloadBytes = payload, availableBytes = available)
        }

        // 报告的是"让该档位变得安全所需的可用空间"，约等于 payload / 10%。
        // 比例是浮点常量，ceil 之后可能有 1 字节的偏差，因此按区间断言。
        val expected = payload * 10L
        assertTrue(
            "required=${error.requiredAvailableBytes} should be about $expected",
            error.requiredAvailableBytes in expected..(expected + 1L)
        )
        assertEquals(available, error.availableBytes)
    }

    @Test
    fun `verifyCapacity rejects a payload that would consume the safety floor`() {
        // 200 MB free, 10 MB payload: passes the ratio test (budget 20 MB) but
        // would leave less than the 200 MB floor behind.
        val available = 200L * MB
        val payload = 10L * MB

        val error = assertThrows(InsufficientStorageException::class.java) {
            StorageSafetyGuard.verifyCapacity(payloadBytes = payload, availableBytes = available)
        }

        assertEquals(payload + StorageSafetyGuard.MIN_FREE_BYTES_AFTER_RUN, error.requiredAvailableBytes)
        assertEquals(available, error.availableBytes)
    }

    @Test
    fun `verifyCapacity rejects on an empty volume`() {
        assertThrows(InsufficientStorageException::class.java) {
            StorageSafetyGuard.verifyCapacity(payloadBytes = 64L * MB, availableBytes = 0L)
        }
    }

    @Test
    fun `verifyRuntimeHeadroom accepts at the floor and rejects below it`() {
        StorageSafetyGuard.verifyRuntimeHeadroom(StorageSafetyGuard.MIN_FREE_BYTES_AFTER_RUN)

        val error = assertThrows(InsufficientStorageException::class.java) {
            StorageSafetyGuard.verifyRuntimeHeadroom(StorageSafetyGuard.MIN_FREE_BYTES_AFTER_RUN - 1L)
        }
        assertEquals(StorageSafetyGuard.MIN_FREE_BYTES_AFTER_RUN, error.requiredAvailableBytes)
    }

    @Test
    fun `purgeTestArtifacts removes nested artifacts but keeps the workspace`() {
        val workspace = createWorkspace()
        try {
            File(workspace, "seq.dat").writeBytes(ByteArray(16))
            val nested = File(workspace, "small").apply { mkdirs() }
            File(nested, "chunk.dat").writeBytes(ByteArray(8))

            assertTrue(StorageSafetyGuard.purgeTestArtifacts(workspace))

            assertTrue("workspace itself must survive", workspace.isDirectory)
            assertEquals(0, workspace.listFiles()?.size ?: -1)
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun `purgeTestArtifacts is a no op for an absent directory`() {
        val missing = File(Files.createTempDirectory("storage-bench-test").toFile(), "not-created")

        assertTrue(StorageSafetyGuard.purgeTestArtifacts(missing))
        assertFalse(missing.exists())
    }

    @Test
    fun `peakPayloadBytes adds the sequential file to the largest phase artifact`() {
        val spec = testSpec(
            coldRead = true,
            sequentialBytes = 100L * MB,
            cacheFlushBytes = 40L * MB,
            randomRegionBytes = 10L * MB,
            smallFileCount = 100,
            smallFileBytes = 4 * 1024
        )

        // 峰值是顺序文件 + 最大的阶段性产物，不是全部相加：
        // 丢弃文件用完即删，随机区域在小文件阶段之前也已删除，它们不会同时存在。
        assertEquals(140L * MB, StorageSafetyGuard.peakPayloadBytes(spec))
    }

    @Test
    fun `peakPayloadBytes accounts for the small file burst`() {
        val smallFileCount = 1_000
        val smallFileBytes = 4 * 1024
        val spec = testSpec(
            coldRead = true,
            sequentialBytes = 10L * MB,
            cacheFlushBytes = 1L * MB,
            randomRegionBytes = 1L * MB,
            smallFileCount = smallFileCount,
            smallFileBytes = smallFileBytes
        )

        // 1000 个 4 KiB 文件共 4,096,000 字节（注意不是 4 MiB），超过这里的丢弃文件与随机区域。
        assertEquals(
            10L * MB + smallFileCount.toLong() * smallFileBytes,
            StorageSafetyGuard.peakPayloadBytes(spec)
        )
    }

    @Test
    fun `peakPayloadBytes grows with the sqlite row count`() {
        val small = StorageSafetyGuard.peakPayloadBytes(testSpec(sqliteRows = 10))
        val large = StorageSafetyGuard.peakPayloadBytes(testSpec(sqliteRows = 100_000))

        assertTrue("sqlite rows must be accounted for", large > small)
    }

    @Test
    fun `peakPayloadBytes ignores phases that will not run`() {
        val sequentialBytes = 10L * MB
        val randomRegionBytes = 1L * MB
        val flushBytes = 500L * MB

        val withoutColdRead = StorageSafetyGuard.peakPayloadBytes(
            testSpec(
                coldRead = false,
                sequentialBytes = sequentialBytes,
                cacheFlushBytes = flushBytes,
                randomRegionBytes = randomRegionBytes
            )
        )
        val withColdRead = StorageSafetyGuard.peakPayloadBytes(
            testSpec(
                coldRead = true,
                sequentialBytes = sequentialBytes,
                cacheFlushBytes = flushBytes,
                randomRegionBytes = randomRegionBytes
            )
        )

        // 冷读关闭时不会写丢弃文件，容量校验不该为它预留空间。
        assertEquals(sequentialBytes + randomRegionBytes, withoutColdRead)
        assertEquals(sequentialBytes + flushBytes, withColdRead)
    }

    private fun createWorkspace(): File =
        File(Files.createTempDirectory("storage-bench-test").toFile(), "workspace").apply { mkdirs() }
}
