package com.robin.tools.feature.lightlux.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "light_entries")
data class LightEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val luxValue: Float
)
