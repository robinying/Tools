package com.robin.tools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.robin.tools.core.widget.SwipeBackContainer
import com.robin.tools.feature.ebook.ui.ConversionViewModel
import com.robin.tools.feature.ebook.ui.MainScreen as EbookScreen
import com.robin.tools.feature.face.ui.FaceCompareScreen
import com.robin.tools.feature.face.ui.FaceCompareViewModel
import com.robin.tools.feature.lightlux.data.AppDatabase
import com.robin.tools.feature.lightlux.data.LightRepository
import com.robin.tools.feature.lightlux.data.MainViewModel
import com.robin.tools.feature.lightlux.data.SnapshotListViewModel
import com.robin.tools.feature.lightlux.presentation.LightLuxScreen
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.feature.media.ui.screens.CompressionScreen
import com.robin.tools.feature.media.ui.screens.MainScreen as MediaMainScreen
import com.robin.tools.navigation.AppRoute
import com.robin.tools.ui.screens.HomeScreen
import com.robin.tools.ui.theme.ToolsTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ToolsTheme {
                val navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.Home
                    ) {
                        composable<AppRoute.Home> {
                            HomeScreen(
                                onMediaClick = { navController.navigate(AppRoute.MediaMain) },
                                onEbookClick = { navController.navigate(AppRoute.Ebook) },
                                onLightLuxClick = { navController.navigate(AppRoute.LightLux) },
                                onFaceCompareClick = { navController.navigate(AppRoute.FaceCompare) }
                            )
                        }

                        composable<AppRoute.MediaMain> {
                            MediaMainWrapper(navController)
                        }

                        composable<AppRoute.Compression> { backStackEntry ->
                            val route = backStackEntry.toRoute<AppRoute.Compression>()
                            CompressionWrapper(navController, route.type)
                        }

                        composable<AppRoute.Ebook> {
                            EbookWrapper(navController)
                        }

                        composable<AppRoute.LightLux> {
                            LightLuxWrapper(navController)
                        }

                        composable<AppRoute.FaceCompare> {
                            FaceCompareWrapper(navController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaMainWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        MediaMainScreen(
            onVideoCompressClick = { navController.navigate(AppRoute.Compression(CompressionType.VIDEO)) },
            onImageCompressClick = { navController.navigate(AppRoute.Compression(CompressionType.IMAGE)) },
            onGifConvertClick = { navController.navigate(AppRoute.Compression(CompressionType.GIF)) },
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun CompressionWrapper(navController: NavHostController, type: CompressionType) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        CompressionScreen(
            type = type,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun EbookWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val context = LocalContext.current
        val ebookViewModel = remember { ConversionViewModel(context.applicationContext) }
        EbookScreen(
            viewModel = ebookViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun LightLuxWrapper(navController: NavHostController) {
    // 滑动返回由 LightLuxScreen 内部自治（Meter 回 Home、SnapshotList 回 Meter），
    // 因此此处不再套外层 SwipeBackContainer，避免两层边缘手势条相互拦截。
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context.applicationContext) }
    val repo = remember { LightRepository(db.lightEntryDao()) }
    val application = remember { context.applicationContext as android.app.Application }
    val lightMainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(application, repo)
    )
    val lightSnapshotViewModel: SnapshotListViewModel = viewModel(
        factory = SnapshotListViewModel.Factory(repo)
    )
    LightLuxScreen(
        mainViewModel = lightMainViewModel,
        snapshotViewModel = lightSnapshotViewModel,
        onBack = { navController.popBackStack() }
    )
}

@Composable
private fun FaceCompareWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val context = LocalContext.current
        val faceViewModel = remember {
            FaceCompareViewModel(context.applicationContext)
        }
        FaceCompareScreen(
            viewModel = faceViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}
