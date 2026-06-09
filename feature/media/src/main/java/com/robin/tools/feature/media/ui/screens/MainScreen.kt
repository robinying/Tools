package com.robin.tools.feature.media.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.utils.FileUtils

@Composable
fun MainScreen(
    onVideoCompressClick: () -> Unit,
    onImageCompressClick: () -> Unit,
    onGifConvertClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.media_compression_tool),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        CompressionCard(
            title = context.getString(R.string.video_compress),
            description = context.getString(R.string.video_compress_desc),
            icon = Icons.Default.VideoLibrary,
            onClick = onVideoCompressClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        CompressionCard(
            title = context.getString(R.string.image_compress),
            description = context.getString(R.string.image_compress_desc),
            icon = Icons.Default.Image,
            onClick = onImageCompressClick
        )

        Spacer(modifier = Modifier.height(16.dp))

        CompressionCard(
            title = context.getString(R.string.video_to_gif),
            description = context.getString(R.string.video_to_gif_desc),
            icon = Icons.Default.Slideshow,
            onClick = onGifConvertClick
        )

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = {
                FileUtils.clearCache(context)
                Toast.makeText(context, context.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
            }
        ) {
            Text(context.getString(R.string.clear_cache), color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun CompressionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
