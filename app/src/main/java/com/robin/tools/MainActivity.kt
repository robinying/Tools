package com.robin.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.robin.tools.feature.lightlux.data.*
import com.robin.tools.feature.lightlux.presentation.LightLuxScreen
import com.robin.tools.feature.ebook.ui.ConversionViewModel
import com.robin.tools.feature.ebook.ui.MainScreen as EbookScreen
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.feature.media.ui.screens.CompressionScreen
import com.robin.tools.feature.media.ui.screens.MainScreen as MediaMainScreen
import com.robin.tools.ui.theme.ToolsTheme
import com.robin.tools.util.SwipeBackContainer

sealed class AppScreen {
    object Home : AppScreen()
    data class Media(val type: CompressionType? = null) : AppScreen()
    object Ebook : AppScreen()
    object LightLux : AppScreen()
}

class MainActivity : ComponentActivity() {

    private lateinit var lightMainViewModel: MainViewModel
    private lateinit var lightSnapshotViewModel: SnapshotListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repo = LightRepository(db.lightEntryDao())
        lightMainViewModel = MainViewModel(application, repo)
        lightSnapshotViewModel = SnapshotListViewModel(repo)

        setContent {
            ToolsTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val screen = currentScreen) {
                        is AppScreen.Home -> HomeScreen(
                            onMediaClick = { currentScreen = AppScreen.Media() },
                            onEbookClick = { currentScreen = AppScreen.Ebook },
                            onLightLuxClick = { currentScreen = AppScreen.LightLux }
                        )
                        is AppScreen.Media -> {
                            SwipeBackContainer(onBack = { currentScreen = AppScreen.Home }) {
                                val type = screen.type
                                if (type != null) {
                                    CompressionScreen(
                                        type = type,
                                        onBack = { currentScreen = AppScreen.Home }
                                    )
                                } else {
                                    MediaMainScreen(
                                        onVideoCompressClick = { currentScreen = AppScreen.Media(CompressionType.VIDEO) },
                                        onImageCompressClick = { currentScreen = AppScreen.Media(CompressionType.IMAGE) },
                                        onGifConvertClick = { currentScreen = AppScreen.Media(CompressionType.GIF) },
                                        onBack = { currentScreen = AppScreen.Home }
                                    )
                                }
                            }
                        }
                        is AppScreen.Ebook -> {
                            SwipeBackContainer(onBack = { currentScreen = AppScreen.Home }) {
                                EbookScreen(viewModel = ConversionViewModel(applicationContext), onBack = { currentScreen = AppScreen.Home })
                            }
                        }
                        is AppScreen.LightLux -> {
                            SwipeBackContainer(onBack = { currentScreen = AppScreen.Home }) {
                                LightLuxScreen(
                                    mainViewModel = lightMainViewModel,
                                    snapshotViewModel = lightSnapshotViewModel,
                                    onBack = { currentScreen = AppScreen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    onMediaClick: () -> Unit,
    onEbookClick: () -> Unit,
    onLightLuxClick: () -> Unit
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
            icon = Icons.Default.MenuBook,
            onClick = onEbookClick
        )
        Spacer(Modifier.height(16.dp))
        FeatureCard(
            title = stringResource(R.string.light_meter_title),
            description = stringResource(R.string.light_meter_desc),
            icon = Icons.Default.LightMode,
            onClick = onLightLuxClick
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