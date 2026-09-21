package com.robin.tools.feature.storage.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BenchmarkResultEntity::class], version = 1, exportSchema = false)
abstract class StorageBenchDatabase : RoomDatabase() {

    abstract fun benchmarkResultDao(): BenchmarkResultDao

    companion object {
        private const val DATABASE_NAME = "storage_bench_database"

        @Volatile
        private var instance: StorageBenchDatabase? = null

        /** 双检锁单例，与 feature:lightlux 的 AppDatabase 保持一致。 */
        fun getInstance(context: Context): StorageBenchDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StorageBenchDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }

        /** 仅供测试使用：清空进程级单例。 */
        internal fun clearInstanceForTests() {
            instance = null
        }
    }
}
