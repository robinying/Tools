package com.robin.tools.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One completed (or failed) task shown in the history list. */
@Entity(tableName = "task_history")
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val toolId: String,
    val title: String,
    val detail: String?,
    val outputUri: String?,
    val isSuccess: Boolean,
    val createdAt: Long
)
