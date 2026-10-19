package com.robin.tools.feature.storage.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Serializes benchmark history for export/sharing. */
object BenchmarkCsv {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    fun format(entries: List<BenchmarkHistoryEntry>): String {
        val builder = StringBuilder()
        builder.appendLine(
            "started_at,target,preset,cold_read,sequential_write_bps," +
                "sequential_read_bps,random_read_iops,random_write_iops," +
                "small_files_per_second,sqlite_rows_per_second,total_elapsed_ms,device"
        )
        entries.forEach { entry ->
            builder.appendLine(
                listOf(
                    escape(dateFormat.format(Date(entry.startedAtEpochMillis))),
                    escape(entry.target.name),
                    escape(entry.preset?.name.orEmpty()),
                    if (entry.coldReadAttempted) "true" else "false",
                    number(entry.sequentialWriteBytesPerSecond),
                    number(entry.sequentialReadBytesPerSecond),
                    number(entry.randomReadIops),
                    number(entry.randomWriteIops),
                    number(entry.smallFilesPerSecond),
                    entry.sqliteRowsPerSecond?.let(::number).orEmpty(),
                    entry.totalElapsedMillis.toString(),
                    escape(entry.deviceModel)
                ).joinToString(",")
            )
        }
        return builder.toString()
    }

    private fun number(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    private fun escape(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
