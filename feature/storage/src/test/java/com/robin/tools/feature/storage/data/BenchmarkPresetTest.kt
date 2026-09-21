package com.robin.tools.feature.storage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkPresetTest {

    @Test
    fun `payload grows with the preset tier`() {
        val sizes = BenchmarkPreset.entries.map { it.sequentialBytes }

        assertEquals(sizes.sorted(), sizes)
        assertEquals(sizes.distinct().size, sizes.size)
        assertEquals(BenchmarkPreset.QUICK, BenchmarkPreset.entries.first())
        assertEquals(BenchmarkPreset.THOROUGH, BenchmarkPreset.entries.last())
    }

    @Test
    fun `every workload dimension grows with the preset tier`() {
        val presets = BenchmarkPreset.entries

        assertEquals(presets.map { it.randomRegionBytes }.sorted(), presets.map { it.randomRegionBytes })
        assertEquals(presets.map { it.randomOps }.sorted(), presets.map { it.randomOps })
        assertEquals(presets.map { it.smallFileCount }.sorted(), presets.map { it.smallFileCount })
        assertEquals(presets.map { it.cacheFlushBytes }.sorted(), presets.map { it.cacheFlushBytes })
    }

    @Test
    fun `every preset exposes its own label and detail text`() {
        val presets = BenchmarkPreset.entries

        assertEquals(presets.size, presets.map { it.labelRes }.distinct().size)
        assertEquals(presets.size, presets.map { it.detailRes }.distinct().size)
    }

    @Test
    fun `toSpec carries the whole workload and the originating preset`() {
        val spec = BenchmarkPreset.STANDARD.toSpec(BenchmarkTarget.EXTERNAL_APP)

        assertEquals(BenchmarkTarget.EXTERNAL_APP, spec.target)
        assertEquals(BenchmarkPreset.STANDARD, spec.preset)
        assertEquals(BenchmarkPreset.STANDARD.sequentialBytes, spec.sequentialBytes)
        assertEquals(BenchmarkPreset.STANDARD.randomRegionBytes, spec.randomRegionBytes)
        assertEquals(BenchmarkPreset.STANDARD.randomOps, spec.randomOps)
        assertEquals(BenchmarkPreset.STANDARD.smallFileCount, spec.smallFileCount)
        assertEquals(BenchmarkPreset.STANDARD.smallFileBytes, spec.smallFileBytes)
        assertEquals(BenchmarkPreset.STANDARD.sqliteRows, spec.sqliteRows)
        assertEquals(DEFAULT_RANDOM_BLOCK_BYTES, spec.randomBlockBytes)
        assertFalse(spec.coldRead)
    }

    @Test
    fun `toSpec forwards the cold read choice`() {
        assertTrue(BenchmarkPreset.QUICK.toSpec(BenchmarkTarget.INTERNAL_FILES, coldRead = true).coldRead)
    }

    @Test
    fun `an explicit spec may carry no preset`() {
        val spec = testSpec()

        assertEquals(null, spec.preset)
    }

    @Test
    fun `the phase plan skips the optional phases when they are not requested`() {
        val spec = testSpec(coldRead = false, sqliteRows = 0)

        assertEquals(
            listOf(
                BenchmarkPhase.SEQUENTIAL_WRITE,
                BenchmarkPhase.SEQUENTIAL_READ,
                BenchmarkPhase.RANDOM_READ,
                BenchmarkPhase.RANDOM_WRITE,
                BenchmarkPhase.SMALL_FILE
            ),
            spec.phasePlan
        )
    }

    @Test
    fun `the phase plan includes the optional phases when they are requested`() {
        val spec = testSpec(coldRead = true, sqliteRows = 100)

        assertEquals(
            listOf(
                BenchmarkPhase.SEQUENTIAL_WRITE,
                BenchmarkPhase.CACHE_FLUSH,
                BenchmarkPhase.SEQUENTIAL_READ,
                BenchmarkPhase.RANDOM_READ,
                BenchmarkPhase.RANDOM_WRITE,
                BenchmarkPhase.SMALL_FILE,
                BenchmarkPhase.SQLITE_INSERT
            ),
            spec.phasePlan
        )
    }

    @Test
    fun `cold read without a flush payload does not add a phase`() {
        // 没有丢弃数据就没有可执行的驱逐动作，进度分母里不该出现空转的阶段。
        val spec = testSpec(coldRead = true, cacheFlushBytes = 0L)

        assertFalse(spec.phasePlan.contains(BenchmarkPhase.CACHE_FLUSH))
    }

    @Test
    fun `the quick preset skips the sqlite phase`() {
        val spec = BenchmarkPreset.QUICK.toSpec(BenchmarkTarget.INTERNAL_FILES)

        assertEquals(0, spec.sqliteRows)
        assertFalse(spec.phasePlan.contains(BenchmarkPhase.SQLITE_INSERT))
    }

    @Test
    fun `cache flush runs before the sequential read it is meant to affect`() {
        val plan = testSpec(coldRead = true).phasePlan

        assertTrue(plan.indexOf(BenchmarkPhase.CACHE_FLUSH) < plan.indexOf(BenchmarkPhase.SEQUENTIAL_READ))
        assertTrue(plan.indexOf(BenchmarkPhase.SEQUENTIAL_WRITE) < plan.indexOf(BenchmarkPhase.CACHE_FLUSH))
    }

    @Test
    fun `a full phase plan has no duplicate phases`() {
        val plan = testSpec(coldRead = true, sqliteRows = 100).phasePlan

        assertEquals(plan.distinct().size, plan.size)
        assertEquals(BenchmarkPhase.entries.size, plan.size)
    }
}
