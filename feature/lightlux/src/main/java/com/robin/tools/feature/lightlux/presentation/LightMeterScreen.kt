package com.robin.tools.feature.lightlux.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.EmptyState
import com.robin.tools.core.ui.TextOptionChip
import com.robin.tools.core.ui.ToolsTopAppBar
import com.robin.tools.feature.lightlux.R
import com.robin.tools.feature.lightlux.data.ChartDataPoint
import com.robin.tools.feature.lightlux.data.ChartWindow
import com.robin.tools.feature.lightlux.data.LuxScene
import com.robin.tools.feature.lightlux.data.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LightMeterScreen(
    viewModel: MainViewModel,
    onNavigateToSnapshots: () -> Unit,
    onBack: () -> Unit = {},
) {
    val currentLux by viewModel.currentLux.collectAsStateWithLifecycle()
    val chartData by viewModel.realtimeChartData.collectAsStateWithLifecycle()
    val chartStats by viewModel.chartStats.collectAsStateWithLifecycle()
    val chartWindow by viewModel.chartWindow.collectAsStateWithLifecycle()
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var note by remember { mutableStateOf("") }
    var hasLightSensor by remember {
        mutableStateOf(
            (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
                .getDefaultSensor(Sensor.TYPE_LIGHT) != null
        )
    }

    LaunchedEffect(saveStatus) {
        val msg = saveStatus ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearSaveStatus()
    }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        hasLightSensor = lightSensor != null
        if (lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    viewModel.updateLuxFromSensor(event.values[0])
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose { sensorManager.unregisterListener(listener) }
        } else {
            onDispose { }
        }
    }

    Scaffold(
        topBar = {
            ToolsTopAppBar(
                title = stringResource(R.string.light_meter_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.back),
                actions = {
                    IconButton(onClick = onNavigateToSnapshots) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = stringResource(R.string.history)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (!hasLightSensor) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.LightMode,
                    title = stringResource(R.string.sensor_missing_title),
                    description = stringResource(R.string.sensor_missing_desc)
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimension.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val luxLabel = stringResource(R.string.lux_value, currentLux)
            Text(
                String.format("%.1f", currentLux),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = luxLabel }
            )
            Text(stringResource(R.string.lux_unit), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Dimension.sm))
            Text(
                text = stringResource(LuxScene.fromLux(currentLux).labelRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            if (chartStats.samples > 0) {
                Spacer(Modifier.height(Dimension.md))
                Text(
                    text = stringResource(
                        R.string.stats_line,
                        chartStats.min,
                        chartStats.max,
                        chartStats.avg,
                        chartStats.samples
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Dimension.lg))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.snapshot_note_hint)) },
                singleLine = true
            )
            Spacer(Modifier.height(Dimension.md))
            Button(
                onClick = {
                    viewModel.saveSnapshot(note)
                    note = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(Dimension.sm))
                Text(stringResource(R.string.save_snapshot))
            }

            Spacer(Modifier.height(Dimension.xl))
            Text(
                stringResource(R.string.realtime_chart_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Dimension.sm))
            Text(
                stringResource(R.string.chart_window_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Dimension.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimension.sm)
            ) {
                ChartWindow.entries.forEach { window ->
                    TextOptionChip(
                        selected = chartWindow == window,
                        onClick = { viewModel.setChartWindow(window) },
                        label = stringResource(window.labelRes)
                    )
                }
            }
            Spacer(Modifier.height(Dimension.md))
            if (chartData.isNotEmpty()) {
                LuxChart(
                    data = chartData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            } else {
                Text(
                    stringResource(R.string.waiting_sensor_data),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LuxChart(data: List<ChartDataPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.padding(8.dp)) {
        if (data.size < 2) return@Canvas
        val maxLux = (data.maxOf { it.luxValue }.toInt() + 10).coerceAtLeast(100)
        val minTime = data.first().timestamp
        val maxTime = data.last().timestamp
        val timeRange = (maxTime - minTime).coerceAtLeast(1L).toFloat()
        val w = size.width
        val h = size.height
        for (i in 0..4) {
            val y = h * i / 4
            drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f)
        }
        drawLine(axisColor, Offset(0f, h), Offset(w, h), 2f)
        drawLine(axisColor, Offset(0f, 0f), Offset(0f, h), 2f)
        val path = Path()
        data.forEachIndexed { i, p ->
            val x = ((p.timestamp - minTime) / timeRange) * w
            val y = h - (p.luxValue / maxLux) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
        data.forEach { p ->
            val x = ((p.timestamp - minTime) / timeRange) * w
            val y = h - (p.luxValue / maxLux) * h
            drawCircle(lineColor, radius = 4f, center = Offset(x, y))
        }
    }
}
