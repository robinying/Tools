package com.robin.tools.feature.media.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.robin.tools.feature.media.R
import com.robin.tools.feature.media.utils.FileUtils
import com.robin.tools.core.ui.Dimension
import com.robin.tools.core.ui.FeatureCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onBack: () -> Unit = {},
    onVideoCompressClick: () -> Unit,
    onImageCompressClick: () -> Unit,
    onGifConvertClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.media_compression_tool)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimension.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = context.getString(R.string.media_compression_tool),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Dimension.xxl)
            )

            FeatureCard(
                title = context.getString(R.string.video_compress),
                description = context.getString(R.string.video_compress_desc),
                icon = Icons.Default.VideoLibrary,
                onClick = onVideoCompressClick
            )
            Spacer(Modifier.height(Dimension.lg))

            FeatureCard(
                title = context.getString(R.string.image_compress),
                description = context.getString(R.string.image_compress_desc),
                icon = Icons.Default.Image,
                onClick = onImageCompressClick
            )
            Spacer(Modifier.height(Dimension.lg))

            FeatureCard(
                title = context.getString(R.string.video_to_gif),
                description = context.getString(R.string.video_to_gif_desc),
                icon = Icons.Default.Slideshow,
                onClick = onGifConvertClick
            )
            Spacer(Modifier.height(Dimension.lg))

            FeatureCard(
                title = context.getString(R.string.image_filter),
                description = context.getString(R.string.image_filter_desc),
                icon = Icons.Default.FilterAlt,
                onClick = onFilterClick
            )

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = {
                    FileUtils.clearCache(context)
                    android.widget.Toast.makeText(context, context.getString(R.string.cache_cleared), android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(context.getString(R.string.clear_cache), color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
