package com.robin.tools.feature.camera.ui.common

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.robin.tools.core.ui.TextOptionChip
import com.robin.tools.feature.camera.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Rule-of-thirds framing guide. */
@Composable
fun CameraGridOverlay(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.32f)
) {
    Canvas(modifier = modifier) {
        val thirdX = size.width / 3f
        val thirdY = size.height / 3f
        for (i in 1..2) {
            drawLine(
                color = color,
                start = Offset(thirdX * i, 0f),
                end = Offset(thirdX * i, size.height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = color,
                start = Offset(0f, thirdY * i),
                end = Offset(size.width, thirdY * i),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Horizon level indicator driven by the accelerometer.
 *
 * The line turns green once the device is within ±1.5° of level.
 */
@Composable
fun CameraLevelOverlay(
    rollDegrees: Float,
    modifier: Modifier = Modifier
) {
    val leveled = abs(rollDegrees) <= 1.5f
    val color = if (leveled) Color(0xFF4CD964) else Color.White.copy(alpha = 0.85f)
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width * 0.22f
        val radians = Math.toRadians((-rollDegrees).toDouble())
        val dx = (cos(radians) * radius).toFloat()
        val dy = (sin(radians) * radius).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x - dx, center.y - dy),
            end = Offset(center.x + dx, center.y + dy),
            strokeWidth = 2.dp.toPx()
        )
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/** Current device roll in degrees (0 = level in portrait). */
@Composable
fun rememberDeviceRollDegrees(): Float {
    val context = LocalContext.current
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    // Gravity components: roll around the camera axis.
                    roll = Math.toDegrees(
                        atan2(event.values[0].toDouble(), event.values[1].toDouble())
                    ).toFloat()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }
    return roll
}

/** Grid and level toggles shared by the photo and record screens. */
@Composable
fun CameraGuideChips(
    showGrid: Boolean,
    showLevel: Boolean,
    onToggleGrid: () -> Unit,
    onToggleLevel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        TextOptionChip(
            selected = showGrid,
            onClick = onToggleGrid,
            label = stringResource(R.string.camera_guide_grid)
        )
        Spacer(Modifier.width(8.dp))
        TextOptionChip(
            selected = showLevel,
            onClick = onToggleLevel,
            label = stringResource(R.string.camera_guide_level)
        )
    }
}
