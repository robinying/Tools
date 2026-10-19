package com.robin.tools.feature.storage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkCsvTest {

    private fun entry(name: String = "Pixel \"Test\"") = BenchmarkHistoryEntry(
        id = 1,
        startedAtEpochMillis = 1_700_000_000_000,
        target = BenchmarkTarget.INTERNAL_FILES,
        preset = BenchmarkPreset.STANDARD,
        coldReadAttempted = true,
        sequentialWriteBytesPerSecond = 1234.5,
        sequentialReadBytesPerSecond = 2345.6,
        randomReadIops = 345.6,
        randomWriteIops = 456.7,
        smallFilesPerSecond = 567.8,
        sqliteRowsPerSecond = null,
        totalElapsedMillis = 12_345,
        deviceModel = name
    )

    @Test
    fun headerIsStable() {
        val csv = BenchmarkCsv.format(emptyList()).trimEnd()
        assertEquals(1, csv.lines().size)
        assertTrue(csv.startsWith("started_at,target,preset,cold_read"))
    }

    @Test
    fun rowsContainAllValues() {
        val csv = BenchmarkCsv.format(listOf(entry()))
        val row = csv.lines()[1]
        assertTrue(row.contains("INTERNAL_FILES"))
        assertTrue(row.contains("STANDARD"))
        assertTrue(row.contains("true"))
        assertTrue(row.contains("1234.50"))
        assertTrue(row.contains("12345"))
    }

    @Test
    fun quotesAreEscaped() {
        val csv = BenchmarkCsv.format(listOf(entry()))
        assertTrue(csv.contains("\"Pixel \"\"Test\"\"\""))
    }

    @Test
    fun nullSqliteValueIsEmpty() {
        val csv = BenchmarkCsv.format(listOf(entry()))
        val row = csv.lines()[1].split(",")
        assertEquals("", row[9])
    }
}
