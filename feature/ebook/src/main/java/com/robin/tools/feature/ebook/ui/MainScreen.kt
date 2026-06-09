package com.robin.tools.feature.ebook.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider

@Composable
fun MainScreen(viewModel: ConversionViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.convertFile(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Ebook to PDF", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(32.dp))

        when (val state = uiState) {
            is ConversionState.Idle -> {
                Button(onClick = { launcher.launch(arrayOf("application/epub+zip")) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select EPUB")
                }
            }
            is ConversionState.Converting -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LivelyLogoAnimation()
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.width(200.dp).graphicsLayer(clip = true, shape = RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("魔法转换中... ${state.progress}%", style = MaterialTheme.typography.bodyLarge)
                }
            }
            is ConversionState.Success -> {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = Color(0xFF00BFA5),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text("转换成功！", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { openPdf(context, state.cacheFile) }) {
                    Text("打开 PDF")
                }
                TextButton(onClick = { viewModel.reset() }) {
                    Text("继续转换其他文件")
                }
            }
            is ConversionState.Error -> {
                Text("哎呀，出错了: ${state.message}", color = Color.Red)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reset() }) {
                    Text("再试一次")
                }
            }
        }
    }
}

@Composable
fun LivelyLogoAnimation(modifier: Modifier = Modifier) {
    val ebookScale = remember { Animatable(0f) }
    val arrowProgress = remember { Animatable(0f) }
    val pdfScale = remember { Animatable(0f) }
    val starAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        ebookScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        arrowProgress.animateTo(1f, animationSpec = tween(600, easing = LinearOutSlowInEasing))
        pdfScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        starAlpha.animateTo(1f, animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ))
    }

    Canvas(modifier = modifier.size(160.dp)) {
        val w = size.width
        val h = size.height

        drawCircle(
            color = Color(0xFFF0F4C3),
            radius = w * 0.45f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        if (ebookScale.value > 0f) {
            scale(ebookScale.value) {
                drawRoundRect(
                    color = Color(0xFF00BFA5),
                    topLeft = Offset(w * 0.2f, h * 0.35f),
                    size = Size(w * 0.22f, h * 0.32f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawLine(Color.White, Offset(w * 0.25f, h * 0.42f), Offset(w * 0.35f, h * 0.42f), 4f)
                drawLine(Color.White, Offset(w * 0.25f, h * 0.48f), Offset(w * 0.35f, h * 0.48f), 4f)
            }
        }

        if (arrowProgress.value > 0f) {
            val path = Path().apply {
                moveTo(w * 0.45f, h * 0.5f)
                quadraticBezierTo(w * 0.52f, h * 0.35f, w * 0.6f, h * 0.5f)
            }
            val pathMeasure = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val partialPath = Path()
            pathMeasure.getSegment(0f, pathMeasure.length * arrowProgress.value, partialPath.asAndroidPath(), true)
            
            drawPath(
                path = partialPath,
                color = Color(0xFFFF9100),
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }

        if (pdfScale.value > 0f) {
            scale(pdfScale.value) {
                drawRoundRect(
                    color = Color(0xFFFF5252),
                    topLeft = Offset(w * 0.6f, h * 0.38f),
                    size = Size(w * 0.24f, h * 0.3f),
                    cornerRadius = CornerRadius(16f, 16f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "P", w * 0.66f, h * 0.6f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 64f
                        isFakeBoldText = true
                    }
                )
            }
        }

        if (starAlpha.value > 0f) {
            val starPositions = listOf(Offset(w * 0.52f, h * 0.25f), Offset(w * 0.82f, h * 0.35f))
            starPositions.forEach { pos ->
                withTransform({
                    scale(starAlpha.value, pos)
                }) {
                    drawStar(pos, 12f, Color(0xFFFFD600))
                }
            }
        }
    }
}

fun DrawScope.drawStar(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.4f, center.y - size * 0.4f)
        lineTo(center.x + size, center.y)
        lineTo(center.x + size * 0.4f, center.y + size * 0.4f)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.4f, center.y + size * 0.4f)
        lineTo(center.x - size, center.y)
        lineTo(center.x - size * 0.4f, center.y - size * 0.4f)
        close()
    }
    drawPath(path, color)
}

private fun openPdf(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Open PDF"))
}
