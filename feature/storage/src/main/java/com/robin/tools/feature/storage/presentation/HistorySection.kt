package com.robin.tools.feature.storage.presentation

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioActionStyle
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.feature.storage.R
import com.robin.tools.feature.storage.data.BenchmarkHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录列表。
 *
 * 清空历史是不可撤销的破坏性操作，因此必须二次确认——这个页面没有回收站，
 * 误触一次就再也拿不回来了。
 */
@Composable
fun HistorySection(
    entries: List<BenchmarkHistoryEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }

    StudioSectionHeader(
        modifier = modifier,
        eyebrow = stringResource(R.string.storage_history_eyebrow),
        title = stringResource(R.string.storage_history_title),
        description = stringResource(R.string.storage_history_desc),
        accent = MaterialTheme.colorScheme.secondary
    )

    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = stringResource(R.string.storage_history_empty_title),
            description = stringResource(R.string.storage_history_empty_desc)
        )
        return
    }

    entries.forEach { entry -> HistoryCard(entry) }

    StudioActionButton(
        label = stringResource(R.string.storage_history_clear),
        onClick = { confirmClear = true },
        style = StudioActionStyle.DESTRUCTIVE,
        icon = Icons.Default.DeleteSweep,
        // 图标不单独给描述：按钮本身已有文字标签，再给一次会让读屏把
        // "清空历史" 念两遍。
        iconContentDescription = null
    )

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.storage_history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.storage_history_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    }
                ) {
                    Text(stringResource(R.string.storage_history_clear_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.storage_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryCard(entry: BenchmarkHistoryEntry) {
    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        Text(
            text = stringResource(
                R.string.storage_history_entry_title,
                formatTimestamp(entry.startedAtEpochMillis),
                entry.preset?.let { stringResource(it.labelRes) }
                    ?: stringResource(R.string.storage_history_custom_workload),
                stringResource(entry.target.labelRes)
            ),
            style = MaterialTheme.typography.titleSmall
        )

        Spacer(Modifier.height(Dimension.xs))
        Text(
            text = stringResource(
                R.string.storage_history_entry_throughput,
                byteRateText(entry.sequentialWriteBytesPerSecond),
                byteRateText(entry.sequentialReadBytesPerSecond)
            ),
            style = MaterialTheme.typography.bodyMedium
        )

        val randomRead = MetricFormat.scaledRate(entry.randomReadIops)
        val randomWrite = MetricFormat.scaledRate(entry.randomWriteIops)
        val smallFiles = MetricFormat.scaledRate(entry.smallFilesPerSecond)
        val sqliteRows = entry.sqliteRowsPerSecond
        Text(
            text = if (sqliteRows != null) {
                stringResource(
                    R.string.storage_history_entry_details_with_sqlite,
                    randomRead,
                    randomWrite,
                    smallFiles,
                    MetricFormat.scaledRate(sqliteRows)
                )
            } else {
                stringResource(
                    R.string.storage_history_entry_details,
                    randomRead,
                    randomWrite,
                    smallFiles
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 按区域格式化历史时间戳。
 *
 * 用系统给的**骨架串**而不是写死的 `MM-dd HH:mm`：字段顺序与分隔符在不同区域差异很大
 * （en-US 习惯 `MM/dd HH:mm`，不少欧洲区域是 `dd.MM HH:mm`），
 * 交给 `getBestDateTimePattern` 才能得到当地写法。
 */
@Composable
private fun formatTimestamp(epochMillis: Long): String {
    val locale = Locale.getDefault()
    return remember(epochMillis, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, TIMESTAMP_SKELETON)
        SimpleDateFormat(pattern, locale).format(Date(epochMillis))
    }
}

/** 只需要到分钟：历史列表里跨年与否不关键，月份与时刻才是有用的定位信息。 */
private const val TIMESTAMP_SKELETON = "MMddHHmm"
