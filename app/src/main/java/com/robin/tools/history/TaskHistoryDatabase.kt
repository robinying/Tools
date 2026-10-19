package com.robin.tools.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TaskHistoryEntity::class], version = 1, exportSchema = false)
abstract class TaskHistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): TaskHistoryDao

    companion object {
        @Volatile
        private var instance: TaskHistoryDatabase? = null

        fun getInstance(context: Context): TaskHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskHistoryDatabase::class.java,
                    "task_history.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
