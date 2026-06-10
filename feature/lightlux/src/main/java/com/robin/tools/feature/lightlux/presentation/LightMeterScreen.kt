package com.robin.tools.feature.lightlux.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robin.tools.feature.lightlux.R
import com.robin.tools.feature.lightlux.data.ChartDataPoint
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
    val saveStatus by viewModel.saveStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    viewModel.updateLuxFromSensor(event.values[0])
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        } else {
            onDispose { }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.light_meter_title)) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }, actions = {
                IconButton(onClick = onNavigateToSnapshots) { Icon(Icons.Default.History, stringResource(R.string.history)) }
            })
        }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(String.format("%.1f", currentLux), fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lux_unit), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.saveSnapshot() }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.save_snapshot))
            }
            saveStatus?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.realtime_chart_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (chartData.isNotEmpty()) {
                LuxChart(data = chartData, modifier = Modifier.fillMaxWidth().height(200.dp))
            } else {
                Text(stringResource(R.string.waiting_sensor_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LuxChart(data: List<ChartDataPoint>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.padding(8.dp)) {
        if (data.size < 2) return@Canvas
        val maxLux = (data.maxOf { it.luxValue }.toInt() + 10).coerceAtLeast(100)
        val minTime = data.first().timestamp; val maxTime = data.last().timestamp
        val timeRange = (maxTime - minTime).coerceAtLeast(1L).toFloat()
        val w = size.width; val h = size.height
        for (i in 0..4) { val y = h * i / 4; drawLine(gridColor, Offset(0f, y), Offset(w, y), 1f) }
        drawLine(Color.Gray, Offset(0f, h), Offset(w, h), 2f); drawLine(Color.Gray, Offset(0f, 0f), Offset(0f, h), 2f)
        val path = Path()
        data.forEachIndexed { i, p ->
            val x = ((p.timestamp - minTime) / timeRange) * w; val y = h - (p.luxValue / maxLux) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
        data.forEach { p ->
            val x = ((p.timestamp - minTime) / timeRange) * w; val y = h - (p.luxValue / maxLux) * h
            drawCircle(lineColor, radius = 4f, center = Offset(x, y))
        }
    }
}
