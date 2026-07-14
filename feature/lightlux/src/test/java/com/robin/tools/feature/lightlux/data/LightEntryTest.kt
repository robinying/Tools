package com.robin.tools.feature.lightlux.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LightEntryTest {

    @Test
    fun `LightEntry default id is zero and note empty`() {
        val entry = LightEntry(timestamp = 1000L, luxValue = 50.0f)
        assertEquals(0L, entry.id)
        assertEquals(1000L, entry.timestamp)
        assertEquals(50.0f, entry.luxValue, 0.01f)
        assertEquals("", entry.note)
    }

    @Test
    fun `LightEntry preserves values including note`() {
        val entry = LightEntry(
            id = 42L,
            timestamp = 1700000000000L,
            luxValue = 123.45f,
            note = "desk"
        )
        assertEquals(42L, entry.id)
        assertEquals(1700000000000L, entry.timestamp)
        assertEquals(123.45f, entry.luxValue, 0.01f)
        assertEquals("desk", entry.note)
    }

    @Test
    fun `LightEntry equality works`() {
        val entry1 = LightEntry(id = 1L, timestamp = 100L, luxValue = 10.0f, note = "a")
        val entry2 = LightEntry(id = 1L, timestamp = 100L, luxValue = 10.0f, note = "a")
        assertEquals(entry1, entry2)
    }
}
