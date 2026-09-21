package com.robin.tools.feature.storage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.ProgressBlock
import com.robin.tools.core.ui.StudioActionButton
import com.robin.tools.core.ui.StudioSectionHeader
import com.robin.tools.core.ui.StudioSelectionOption
import com.robin.tools.core.ui.StudioSelectionRow
import com.robin.tools.core.ui.StudioSurface
import com.robin.tools.core.ui.StudioSurfaceTone
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.storage.R
import com.robin.tools.feature.storage.data.BenchmarkPhase
import com.robin.tools.feature.storage.data.BenchmarkPreset
import com.robin.tools.feature.storage.data.BenchmarkProgress
import com.robin.tools.feature.storage.data.BenchmarkResult
import com.robin.tools.feature.storage.data.BenchmarkTarget
import com.robin.tools.feature.storage.data.MB
import com.robin.tools.feature.storage.data.RandomIoMetric
import com.robin.tools.feature.storage.data.RatingThresholds
import com.robin.tools.feature.storage.data.StorageBenchViewModel

/**
 * 存储速度测试主界面。
 *
 * 测试运行期间保持屏幕常亮：进程进入后台或屏幕熄灭会让 IO 调度失真，
 * `View#keepScreenOn` 不需要任何权限，也不修改全局系统设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageBenchScreen(
    viewModel: StorageBenchViewModel,
    onBack: () -> Unit = {}
) {
    val selectedTarget by viewModel.selectedTarget.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val targetInfo by viewModel.targetInfo.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val coldRead by viewModel.coldRead.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val isRunning = progress is BenchmarkProgress.Running
    val view = LocalView.current
    DisposableEffect(isRunning) {
        view.keepScreenOn = isRunning
        onDispose { view.keepScreenOn = false }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.storage_bench_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.back)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimension.pageHorizontal),
            verticalArrangement = Arrangement.spacedBy(Dimension.lg)
        ) {
            Spacer(Modifier.height(Dimension.sm))
            Text(
                text = stringResource(R.string.storage_bench_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TargetSection(
                selected = selectedTarget,
                capacityLine = targetInfo?.let {
                    stringResource(
                        R.string.storage_capacity_line,
                        bytesText(it.availableBytes),
                        bytesText(it.totalBytes)
                    )
                },
                enabled = !isRunning,
                onSelect = viewModel::selectTarget
            )

            PresetSection(
                selected = selectedPreset,
                coldRead = coldRead,
                enabled = !isRunning,
                onSelect = viewModel::selectPreset,
                onColdReadChange = viewModel::setColdRead
            )

            StudioActionButton(
                label = stringResource(R.string.storage_start),
                onClick = viewModel::start,
                enabled = !isRunning,
                loading = isRunning
            )

            (progress as? BenchmarkProgress.Running)?.let { running ->
                ProgressBlock(
                    progress = running.overallFraction,
                    message = stringResource(
                        R.string.storage_running_message,
                        stringResource(running.phase.labelRes),
                        liveRateText(running.phase, running.liveRatePerSecond),
                        durationText(running.elapsedMillis)
                    ),
                    onCancel = viewModel::cancel,
                    cancelLabel = stringResource(R.string.storage_cancel)
                )
            }

            when (val state = progress) {
                is BenchmarkProgress.Finished -> ResultSection(state.result)
                is BenchmarkProgress.Failed -> FailureBlock(state)
                BenchmarkProgress.Cancelled -> EmptyState(
                    icon = Icons.Default.Warning,
                    title = stringResource(R.string.storage_cancelled_title),
                    description = stringResource(R.string.storage_cancelled_desc)
                )

                BenchmarkProgress.Idle, is BenchmarkProgress.Running -> Unit
            }

            HistorySection(
                entries = history,
                onClear = viewModel::clearHistory
            )

            Spacer(Modifier.height(Dimension.xl))
        }
    }
}

@Composable
private fun TargetSection(
    selected: BenchmarkTarget,
    capacityLine: String?,
    enabled: Boolean,
    onSelect: (BenchmarkTarget) -> Unit
) {
    StudioSectionHeader(
        eyebrow = stringResource(R.string.storage_target_eyebrow),
        title = stringResource(R.string.storage_target_title),
        description = stringResource(R.string.storage_target_desc),
        accent = MaterialTheme.colorScheme.secondary
    )
    StudioSelectionRow(
        options = BenchmarkTarget.entries.map { StudioSelectionOption(it.name, stringResource(it.labelRes)) },
        selectedKey = selected.name,
        onOptionSelected = { key ->
            BenchmarkTarget.entries.firstOrNull { it.name == key }?.let(onSelect)
        },
        enabled = enabled
    )
    Text(
        text = capacityLine ?: stringResource(R.string.storage_target_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PresetSection(
    selected: BenchmarkPreset,
    coldRead: Boolean,
    enabled: Boolean,
    onSelect: (BenchmarkPreset) -> Unit,
    onColdReadChange: (Boolean) -> Unit
) {
    StudioSectionHeader(
        eyebrow = stringResource(R.string.storage_preset_eyebrow),
        title = stringResource(R.string.storage_preset_title),
        description = stringResource(R.string.storage_preset_desc),
        accent = MaterialTheme.colorScheme.tertiary
    )
    StudioSelectionRow(
        options = BenchmarkPreset.entries.map { StudioSelectionOption(it.name, stringResource(it.labelRes)) },
        selectedKey = selected.name,
        onOptionSelected = { key ->
            BenchmarkPreset.entries.firstOrNull { it.name == key }?.let(onSelect)
        },
        enabled = enabled
    )
    Text(
        text = stringResource(selected.detailRes, selected.sequentialBytes / MB),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 整行合并成一个可切换节点，读屏用户只会遇到一个复选框而不是两个。
    Row(
        modifier = Modifier
            .toggleable(
                value = coldRead,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onColdReadChange
            )
            .padding(vertical = Dimension.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = coldRead, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(Dimension.sm))
        Text(
            text = stringResource(R.string.storage_cold_read_label),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    Text(
        text = stringResource(R.string.storage_cold_read_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun FailureBlock(failed: BenchmarkProgress.Failed) {
    val requiredBytes = failed.requiredBytes
    val availableBytes = failed.availableBytes
    StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(Dimension.sm))
            Text(
                text = stringResource(failed.reason.messageRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        if (requiredBytes != null && availableBytes != null) {
            Spacer(Modifier.height(Dimension.xs))
            Text(
                text = stringResource(
                    R.string.storage_failure_space_detail,
                    bytesText(requiredBytes),
                    bytesText(availableBytes)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultSection(result: BenchmarkResult) {
    StudioSectionHeader(
        eyebrow = stringResource(R.string.storage_result_eyebrow),
        title = stringResource(R.string.storage_result_title),
        accent = MaterialTheme.colorScheme.primary
    )

    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        CardTitle(stringResource(R.string.storage_result_write_title))
        MetricRow(
            label = stringResource(R.string.storage_label_write_average),
            value = byteRateText(result.sequentialWrite.averageBytesPerSecond),
            rating = RatingThresholds.rate(
                BenchmarkPhase.SEQUENTIAL_WRITE,
                result.sequentialWrite.averageBytesPerSecond
            )
        )
        // 下面两项都取自 fsync 之前的写入循环，必须紧挨着平均值呈现，
        // 否则"稳态高于平均"看起来自相矛盾。
        result.sequentialWrite.bufferedBytesPerSecond?.let { buffered ->
            MetricRow(
                label = stringResource(R.string.storage_label_write_buffered),
                value = byteRateText(buffered)
            )
        }
        result.sequentialWrite.steadyStateBytesPerSecond?.let { steady ->
            MetricRow(
                label = stringResource(R.string.storage_label_write_steady_state),
                value = byteRateText(steady)
            )
        }
        if (result.sequentialWrite.timeline.size >= MIN_CHART_SAMPLES) {
            Spacer(Modifier.height(Dimension.sm))
            ThroughputChart(
                timeline = result.sequentialWrite.timeline,
                accent = MaterialTheme.colorScheme.primary
            )
        }
    }

    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        CardTitle(stringResource(R.string.storage_result_read_title))
        MetricRow(
            label = stringResource(R.string.storage_label_read_first_pass),
            value = byteRateText(result.sequentialReadFirstPass.averageBytesPerSecond),
            // 首轮口径更保守，因此按首轮评级。
            rating = RatingThresholds.rate(
                BenchmarkPhase.SEQUENTIAL_READ,
                result.sequentialReadFirstPass.averageBytesPerSecond
            )
        )
        MetricRow(
            label = stringResource(R.string.storage_label_read_repeat),
            value = byteRateText(result.sequentialReadRepeat.averageBytesPerSecond)
        )
        Spacer(Modifier.height(Dimension.sm))
        Text(
            text = stringResource(
                if (result.coldReadAttempted) {
                    R.string.storage_read_caveat_cold
                } else {
                    R.string.storage_read_caveat
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        CardTitle(stringResource(R.string.storage_result_random_title))
        RandomIoRow(
            label = stringResource(R.string.storage_label_random_read),
            metric = result.randomRead
        )
        RandomIoRow(
            label = stringResource(R.string.storage_label_random_write),
            metric = result.randomWrite
        )
        Spacer(Modifier.height(Dimension.sm))
        // 实测同一台设备上 4K 结果的 run-to-run 波动可达 30%，远超顺序指标。
        // 不给个说明的话，用户重跑一次看到差三成会以为工具坏了。
        Text(
            text = stringResource(R.string.storage_random_variance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    StudioSurface(tone = StudioSurfaceTone.STANDARD) {
        CardTitle(stringResource(R.string.storage_result_small_file_title))
        RandomIoRow(
            label = stringResource(R.string.storage_label_small_file),
            metric = result.smallFile
        )
    }

    result.sqliteInsert?.let { sqlite ->
        StudioSurface(tone = StudioSurfaceTone.STANDARD) {
            CardTitle(stringResource(R.string.storage_result_sqlite_title))
            MetricRow(
                label = stringResource(R.string.storage_label_sqlite_insert),
                value = stringResource(
                    R.string.storage_rate_rows,
                    MetricFormat.scaledRate(sqlite.operationsPerSecond)
                ),
                rating = RatingThresholds.rate(
                    BenchmarkPhase.SQLITE_INSERT,
                    sqlite.operationsPerSecond
                )
            )
        }
    }

    StudioSurface(tone = StudioSurfaceTone.OUTLINED) {
        Text(
            text = stringResource(
                R.string.storage_total_elapsed,
                durationText(result.totalElapsedMillis)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(
                R.string.storage_device_line,
                result.device.model,
                result.device.apiLevel,
                result.device.abi
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RandomIoRow(label: String, metric: RandomIoMetric) {
    MetricRow(
        label = label,
        value = operationRateText(metric.phase, metric.operationsPerSecond),
        rating = RatingThresholds.rate(metric.phase, metric.operationsPerSecond),
        caption = stringResource(
            R.string.storage_caption_latency,
            latencyText(metric.latencyP50Micros),
            latencyText(metric.latencyP95Micros),
            latencyText(metric.latencyP99Micros)
        )
    )
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
}
