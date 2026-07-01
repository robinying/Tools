package com.robin.tools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robin.tools.R

@Composable
fun HomeScreen(
    onMediaClick: () -> Unit,
    onEbookClick: () -> Unit,
    onLightLuxClick: () -> Unit,
    onFaceCompareClick: () -> Unit,
    onCameraClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.home_title),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp, top = 32.dp)
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        FeatureCard(
            title = stringResource(R.string.media_editor_title),
            description = stringResource(R.string.media_editor_desc),
            icon = Icons.Default.Image,
            onClick = onMediaClick
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = stringResource(R.string.ebook_converter_title),
            description = stringResource(R.string.ebook_converter_desc),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onEbookClick
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = stringResource(R.string.light_meter_title),
            description = stringResource(R.string.light_meter_desc),
            icon = Icons.Default.LightMode,
            onClick = onLightLuxClick
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = stringResource(R.string.face_compare_title),
            description = stringResource(R.string.face_compare_desc),
            icon = Icons.Default.Face,
            onClick = onFaceCompareClick
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = stringResource(R.string.camera_title),
            description = stringResource(R.string.camera_desc),
            icon = Icons.Default.Videocam,
            onClick = onCameraClick
        )
    }
}

@Composable
fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
