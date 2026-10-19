package com.robin.tools

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.robin.tools.core.widget.SwipeBackContainer
import com.robin.tools.feature.camera.ui.CameraMainScreen
import com.robin.tools.feature.camera.ui.cover.CoverScreen
import com.robin.tools.feature.camera.ui.edit.VideoEditScreen
import com.robin.tools.feature.camera.ui.photo.PhotoCaptureScreen
import com.robin.tools.feature.camera.ui.record.RecordScreen
import com.robin.tools.feature.camera.ui.slideshow.SlideshowScreen
import com.robin.tools.feature.camera.ui.text.TextToVideoScreen
import com.robin.tools.feature.camera.ui.trim.TrimScreen
import com.robin.tools.feature.ebook.ui.ConversionState
import com.robin.tools.feature.ebook.ui.ConversionViewModel
import com.robin.tools.feature.ebook.ui.MainScreen as EbookScreen
import com.robin.tools.feature.face.ui.FaceBlurScreen
import com.robin.tools.feature.face.ui.FaceBlurViewModel
import com.robin.tools.feature.face.ui.FaceCompareScreen
import com.robin.tools.feature.face.ui.FaceCompareViewModel
import com.robin.tools.feature.lightlux.data.AppDatabase
import com.robin.tools.feature.lightlux.data.LightRepository
import com.robin.tools.feature.lightlux.data.MainViewModel
import com.robin.tools.feature.lightlux.data.SnapshotListViewModel
import com.robin.tools.feature.lightlux.presentation.LightLuxScreen
import com.robin.tools.feature.media.data.CompressionManager
import com.robin.tools.feature.media.data.CompressionTaskState
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.feature.media.ui.screens.AudioTrimScreen
import com.robin.tools.feature.media.ui.screens.VideoCustomCompressScreen
import com.robin.tools.feature.media.ui.screens.CompressionScreen
import com.robin.tools.feature.media.ui.screens.FilterScreen
import com.robin.tools.feature.media.ui.screens.MainScreen as MediaMainScreen
import com.robin.tools.feature.media.ui.viewmodel.CompressionViewModel
import com.robin.tools.feature.storage.data.StorageBenchViewModel
import com.robin.tools.feature.storage.data.StorageCleanupViewModel
import com.robin.tools.feature.storage.presentation.StorageBenchScreen
import com.robin.tools.feature.storage.presentation.StorageCleanupScreen
import com.robin.tools.feature.toolkit.image.ImageToolScreen
import com.robin.tools.feature.toolkit.image.ImageToolType
import com.robin.tools.feature.toolkit.image.ImageToolViewModel
import com.robin.tools.feature.toolkit.ocr.OcrToolScreen
import com.robin.tools.feature.toolkit.ocr.OcrToolViewModel
import com.robin.tools.feature.toolkit.qr.QrToolScreen
import com.robin.tools.feature.toolkit.qr.QrToolViewModel
import com.robin.tools.history.TaskHistoryDatabase
import com.robin.tools.history.TaskHistoryRecorder
import com.robin.tools.history.TaskHistoryRepository
import com.robin.tools.history.TaskHistoryViewModel
import com.robin.tools.navigation.AppRoute
import com.robin.tools.tool.ToolPreferences
import com.robin.tools.ui.screens.HomeScreen
import com.robin.tools.ui.screens.ShareEntryScreen
import com.robin.tools.ui.screens.TaskHistoryScreen
import com.robin.tools.ui.theme.ToolsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop

class MainActivity : ComponentActivity() {

    private val sharedContent = MutableStateFlow<SharedPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A share intent survives configuration changes; only consume it on first creation.
        if (savedInstanceState == null) {
            sharedContent.value = intent.toSharedPayload(this)
        }

        setContent {
            ToolsTheme {
                MainNavHost(sharedContent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedContent.value = intent.toSharedPayload(this)
    }
}

@Composable
private fun MainNavHost(sharedContent: MutableStateFlow<SharedPayload?>) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val toolPreferences = remember { ToolPreferences(context) }
    val favorites by toolPreferences.favorites.collectAsState()
    val recents by toolPreferences.recents.collectAsState()

    val historyRepository = remember {
        TaskHistoryRepository(TaskHistoryDatabase.getInstance(context).historyDao())
    }
    val recorderScope = rememberCoroutineScope()
    val recorder = remember { TaskHistoryRecorder(historyRepository, recorderScope) }
    val historyViewModel: TaskHistoryViewModel = viewModel(
        factory = TaskHistoryViewModel.Factory(historyRepository)
    )

    // Media work is executed by a foreground service; record only the transition
    // into Finished so activity recreation does not duplicate entries.
    LaunchedEffect(Unit) {
        var previous: CompressionTaskState? = null
        CompressionManager.taskState.collect { state ->
            if (state is CompressionTaskState.Finished &&
                previous is CompressionTaskState.Processing
            ) {
                val type = CompressionManager.activeType
                recorder.record(
                    toolId = type?.let { "media_${it.name}" } ?: "media_task",
                    title = context.getString(R.string.tool_media_title),
                    detail = buildString {
                        type?.let { append(it.name.replace('_', ' ')) }
                        if (isNotEmpty()) append(" · ")
                        append(state.message)
                    },
                    outputUri = state.outputUri,
                    isSuccess = state.isSuccess
                )
            }
            previous = state
        }
    }

    val payload by sharedContent.collectAsState()
    LaunchedEffect(payload) {
        payload?.let {
            navController.navigate(AppRoute.ShareEntry(it.uri, it.mimeType, it.text))
            sharedContent.value = null
        }
    }

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
                    favorites = favorites,
                    recents = recents,
                    onToolClick = { tool ->
                        toolPreferences.recordRecent(tool.id)
                        navController.navigate(tool.route)
                    },
                    onToggleFavorite = { toolPreferences.toggleFavorite(it.id) }
                )
            }

            composable<AppRoute.MediaMain> {
                MediaMainWrapper(navController)
            }

            composable<AppRoute.Compression> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.Compression>()
                CompressionWrapper(navController, route.type, route.initialUri)
            }

            composable<AppRoute.Ebook> {
                EbookWrapper(navController, recorder)
            }

            composable<AppRoute.LightLux> {
                LightLuxWrapper(navController)
            }

            composable<AppRoute.FaceCompare> {
                FaceCompareWrapper(navController)
            }

            composable<AppRoute.FaceBlur> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.FaceBlur>()
                FaceBlurWrapper(navController, route.initialUri, recorder)
            }

            composable<AppRoute.StorageBench> {
                StorageBenchWrapper(navController)
            }

            composable<AppRoute.StorageCleanup> {
                StorageCleanupWrapper(navController)
            }

            composable<AppRoute.CameraMain> {
                CameraMainWrapper(navController)
            }

            composable<AppRoute.CameraRecord> {
                CameraRecordWrapper(navController, recorder)
            }

            composable<AppRoute.CameraPhoto> {
                CameraPhotoWrapper(navController)
            }

            composable<AppRoute.TextToVideo> {
                TextToVideoWrapper(navController, recorder)
            }

            composable<AppRoute.Slideshow> {
                SlideshowWrapper(navController, recorder)
            }

            composable<AppRoute.VideoEdit> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.VideoEdit>()
                VideoEditWrapper(navController, route.videoPath, recorder)
            }

            composable<AppRoute.VideoTrim> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.VideoTrim>()
                VideoTrimWrapper(navController, route.videoPath, recorder)
            }

            composable<AppRoute.CoverSelect> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.CoverSelect>()
                CoverSelectWrapper(navController, route.videoPath, recorder)
            }

            composable<AppRoute.Filter> {
                FilterWrapper(navController)
            }

            composable<AppRoute.AudioTrim> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.AudioTrim>()
                AudioTrimWrapper(navController, route.initialUri)
            }

            composable<AppRoute.VideoCustomCompress> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.VideoCustomCompress>()
                VideoCustomCompressWrapper(navController, route.initialUri)
            }

            composable<AppRoute.ImageTool> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.ImageTool>()
                ImageToolWrapper(navController, route.toolKey, route.initialUri, recorder)
            }

            composable<AppRoute.QrTool> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.QrTool>()
                QrToolWrapper(navController, route.initialText, route.initialUri, recorder)
            }

            composable<AppRoute.OcrTool> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.OcrTool>()
                OcrToolWrapper(navController, route.initialUri)
            }

            composable<AppRoute.TaskHistory> {
                SwipeBackContainer(onBack = { navController.popBackStack() }) {
                    TaskHistoryScreen(
                        viewModel = historyViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable<AppRoute.ShareEntry> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.ShareEntry>()
                ShareEntryWrapper(navController, route.uri, route.mimeType, route.text)
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
            onFilterClick = { navController.navigate(AppRoute.Filter) },
            onExtractAudioClick = {
                navController.navigate(AppRoute.Compression(CompressionType.EXTRACT_AUDIO))
            },
            onStripAudioClick = {
                navController.navigate(AppRoute.Compression(CompressionType.STRIP_AUDIO))
            },
            onTranscodeClick = {
                navController.navigate(AppRoute.Compression(CompressionType.TRANSCODE))
            },
            onSpeedClick = {
                navController.navigate(AppRoute.Compression(CompressionType.SPEED))
            },
            onReverseClick = {
                navController.navigate(AppRoute.Compression(CompressionType.REVERSE))
            },
            onConcatClick = {
                navController.navigate(AppRoute.Compression(CompressionType.CONCAT))
            },
            onCropClick = {
                navController.navigate(AppRoute.Compression(CompressionType.CROP))
            },
            onVolumeFadeClick = {
                navController.navigate(AppRoute.Compression(CompressionType.VOLUME_FADE))
            },
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun CompressionWrapper(
    navController: NavHostController,
    type: CompressionType,
    initialUri: String?
) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        CompressionScreen(
            type = type,
            onBack = { navController.popBackStack() },
            initialUri = initialUri
        )
    }
}

@Composable
private fun EbookWrapper(navController: NavHostController, recorder: TaskHistoryRecorder) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val context = LocalContext.current
        val title = context.getString(R.string.tool_ebook_title)
        val ebookViewModel: ConversionViewModel = viewModel(
            factory = ConversionViewModel.Factory(context.applicationContext)
        )
        LaunchedEffect(ebookViewModel) {
            ebookViewModel.uiState.drop(1).collect { state ->
                when (state) {
                    is ConversionState.Success -> recorder.record(
                        toolId = "ebook",
                        title = title,
                        detail = state.publicUri.toString(),
                        outputUri = state.publicUri.toString(),
                        isSuccess = true
                    )
                    is ConversionState.Error -> recorder.record(
                        toolId = "ebook",
                        title = title,
                        detail = state.message,
                        isSuccess = false
                    )
                    else -> Unit
                }
            }
        }
        EbookScreen(
            viewModel = ebookViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun LightLuxWrapper(navController: NavHostController) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context.applicationContext) }
    val repo = remember { LightRepository(db.lightEntryDao()) }
    val application = remember { context.applicationContext as Application }
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
        val faceViewModel: FaceCompareViewModel = viewModel(
            factory = FaceCompareViewModel.Factory(context.applicationContext)
        )
        FaceCompareScreen(
            viewModel = faceViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun FaceBlurWrapper(
    navController: NavHostController,
    initialUri: String?,
    recorder: TaskHistoryRecorder
) {
    val context = LocalContext.current
    val title = context.getString(R.string.tool_face_blur_title)
    val viewModel: FaceBlurViewModel = viewModel(
        factory = FaceBlurViewModel.Factory(context.applicationContext as Application)
    )
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        FaceBlurScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() },
            initialUri = initialUri,
            onSaved = { saved ->
                recorder.record(
                    toolId = "face_blur",
                    title = title,
                    detail = "${saved.displayName} · ${viewModel.uiState.value.faceCount} faces",
                    outputUri = saved.uri.toString(),
                    isSuccess = true
                )
            }
        )
    }
}

@Composable
private fun StorageBenchWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val context = LocalContext.current
        val storageViewModel: StorageBenchViewModel = viewModel(
            factory = StorageBenchViewModel.Factory(context.applicationContext as Application)
        )
        StorageBenchScreen(
            viewModel = storageViewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun StorageCleanupWrapper(navController: NavHostController) {
    val context = LocalContext.current
    val viewModel: StorageCleanupViewModel = viewModel(
        factory = StorageCleanupViewModel.Factory(context.applicationContext as Application)
    )
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        StorageCleanupScreen(
            viewModel = viewModel,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun CameraMainWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        var pendingAction by remember { mutableStateOf<String?>(null) }

        val videoPickerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                when (pendingAction) {
                    "edit" -> navController.navigate(AppRoute.VideoEdit(it.toString()))
                    "trim" -> navController.navigate(AppRoute.VideoTrim(it.toString()))
                    "cover" -> navController.navigate(AppRoute.CoverSelect(it.toString()))
                }
                pendingAction = null
            }
        }

        CameraMainScreen(
            onBack = { navController.popBackStack() },
            onRecord = { navController.navigate(AppRoute.CameraRecord) },
            onPhoto = { navController.navigate(AppRoute.CameraPhoto) },
            onEditVideo = {
                pendingAction = "edit"
                videoPickerLauncher.launch("video/*")
            },
            onTrimVideo = {
                pendingAction = "trim"
                videoPickerLauncher.launch("video/*")
            },
            onCoverSelect = {
                pendingAction = "cover"
                videoPickerLauncher.launch("video/*")
            },
            onTextToVideo = { navController.navigate(AppRoute.TextToVideo) },
            onSlideshow = { navController.navigate(AppRoute.Slideshow) }
        )
    }
}

@Composable
private fun CameraRecordWrapper(navController: NavHostController, recorder: TaskHistoryRecorder) {
    val title = LocalContext.current.getString(R.string.tool_record_title)
    RecordScreen(
        onBack = { navController.popBackStack() },
        onRecordingComplete = { videoPath ->
            recorder.record("camera_record", title, videoPath, videoPath, true)
            navController.navigate(AppRoute.VideoEdit(videoPath)) {
                popUpTo(AppRoute.CameraMain)
            }
        }
    )
}

@Composable
private fun CameraPhotoWrapper(navController: NavHostController) {
    PhotoCaptureScreen(
        onBack = { navController.popBackStack() }
    )
}

@Composable
private fun TextToVideoWrapper(navController: NavHostController, recorder: TaskHistoryRecorder) {
    val title = LocalContext.current.getString(R.string.tool_text_video_title)
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        TextToVideoScreen(
            onBack = { navController.popBackStack() },
            onGenerated = { path ->
                recorder.record("text_to_video", title, path, path, true)
                navController.navigate(AppRoute.VideoEdit(path)) {
                    popUpTo(AppRoute.CameraMain)
                }
            }
        )
    }
}

@Composable
private fun SlideshowWrapper(navController: NavHostController, recorder: TaskHistoryRecorder) {
    val title = LocalContext.current.getString(R.string.tool_slideshow_title)
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        SlideshowScreen(
            onBack = { navController.popBackStack() },
            onGenerated = { path ->
                recorder.record("slideshow", title, path, path, true)
                navController.navigate(AppRoute.VideoEdit(path)) {
                    popUpTo(AppRoute.CameraMain)
                }
            }
        )
    }
}

@Composable
private fun VideoEditWrapper(
    navController: NavHostController,
    videoPath: String,
    recorder: TaskHistoryRecorder
) {
    val title = LocalContext.current.getString(R.string.tool_camera_edit_title)
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        VideoEditScreen(
            videoPath = videoPath,
            onBack = { navController.popBackStack() },
            onComplete = { outputPath ->
                recorder.record("camera_edit", title, outputPath, outputPath, true)
                navController.popBackStack()
            }
        )
    }
}

@Composable
private fun VideoTrimWrapper(
    navController: NavHostController,
    videoPath: String,
    recorder: TaskHistoryRecorder
) {
    val title = LocalContext.current.getString(R.string.tool_camera_trim_title)
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        TrimScreen(
            videoPath = videoPath,
            onBack = { navController.popBackStack() },
            onComplete = { outputPath ->
                recorder.record("camera_trim", title, outputPath, outputPath, true)
                navController.popBackStack()
            }
        )
    }
}

@Composable
private fun CoverSelectWrapper(
    navController: NavHostController,
    videoPath: String,
    recorder: TaskHistoryRecorder
) {
    val title = LocalContext.current.getString(R.string.tool_camera_cover_title)
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        CoverScreen(
            videoPath = videoPath,
            onBack = { navController.popBackStack() },
            onComplete = { outputPath ->
                recorder.record("camera_cover", title, outputPath, outputPath, true)
                navController.popBackStack()
            }
        )
    }
}

@Composable
private fun FilterWrapper(navController: NavHostController) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        FilterScreen(
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun AudioTrimWrapper(navController: NavHostController, initialUri: String?) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val viewModel: CompressionViewModel = viewModel()
        AudioTrimScreen(
            onBack = { navController.popBackStack() },
            viewModel = viewModel,
            initialUri = initialUri
        )
    }
}

@Composable
private fun VideoCustomCompressWrapper(
    navController: NavHostController,
    initialUri: String?
) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        val viewModel: CompressionViewModel = viewModel()
        VideoCustomCompressScreen(
            onBack = { navController.popBackStack() },
            viewModel = viewModel,
            initialUri = initialUri
        )
    }
}

@Composable
private fun ImageToolWrapper(
    navController: NavHostController,
    toolKey: String,
    initialUri: String?,
    recorder: TaskHistoryRecorder
) {
    val context = LocalContext.current
    val tool = ImageToolType.fromKey(toolKey)
    val title = context.getString(tool.titleRes)
    val imageToolViewModel: ImageToolViewModel = viewModel(
        factory = ImageToolViewModel.Factory(context.applicationContext as Application)
    )
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        ImageToolScreen(
            viewModel = imageToolViewModel,
            tool = tool,
            onBack = { navController.popBackStack() },
            initialUri = initialUri,
            onSaved = { saved ->
                recorder.record(
                    toolId = "image_${tool.name.lowercase()}",
                    title = title,
                    detail = saved.displayName,
                    outputUri = saved.uri.toString(),
                    isSuccess = true
                )
            }
        )
    }
}

@Composable
private fun QrToolWrapper(
    navController: NavHostController,
    initialText: String?,
    initialUri: String?,
    recorder: TaskHistoryRecorder
) {
    val context = LocalContext.current
    val title = context.getString(R.string.tool_qr_title)
    val qrToolViewModel: QrToolViewModel = viewModel(
        factory = QrToolViewModel.Factory(context.applicationContext as Application)
    )
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        QrToolScreen(
            viewModel = qrToolViewModel,
            onBack = { navController.popBackStack() },
            initialText = initialText,
            initialUri = initialUri,
            onSaved = { saved ->
                recorder.record(
                    toolId = "qr",
                    title = title,
                    detail = saved.displayName,
                    outputUri = saved.uri.toString(),
                    isSuccess = true
                )
            }
        )
    }
}

@Composable
private fun OcrToolWrapper(navController: NavHostController, initialUri: String?) {
    val context = LocalContext.current
    val ocrToolViewModel: OcrToolViewModel = viewModel(
        factory = OcrToolViewModel.Factory(context.applicationContext as Application)
    )
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        OcrToolScreen(
            viewModel = ocrToolViewModel,
            onBack = { navController.popBackStack() },
            initialUri = initialUri
        )
    }
}

@Composable
private fun ShareEntryWrapper(
    navController: NavHostController,
    uri: String?,
    mimeType: String?,
    text: String?
) {
    SwipeBackContainer(onBack = { navController.popBackStack() }) {
        ShareEntryScreen(
            uri = uri,
            mimeType = mimeType,
            text = text,
            onBack = { navController.popBackStack() },
            onCompression = { type, initialUri ->
                navController.navigate(AppRoute.Compression(type, initialUri))
            },
            onImageTool = { toolKey, initialUri ->
                navController.navigate(AppRoute.ImageTool(toolKey, initialUri))
            },
            onQr = { initialText, initialUri ->
                navController.navigate(AppRoute.QrTool(initialText, initialUri))
            },
            onOcr = { initialUri ->
                navController.navigate(AppRoute.OcrTool(initialUri))
            },
            onAudioTrim = { initialUri ->
                navController.navigate(AppRoute.AudioTrim(initialUri))
            }
        )
    }
}

/** Content received through ACTION_SEND / ACTION_VIEW. */
private data class SharedPayload(
    val uri: String?,
    val mimeType: String?,
    val text: String?
)

private fun Intent?.toSharedPayload(context: android.content.Context): SharedPayload? {
    if (this == null) return null
    return when (action) {
        Intent.ACTION_SEND -> {
            val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(Intent.EXTRA_STREAM)
            }
            val sharedText = getStringExtra(Intent.EXTRA_TEXT)
            if (stream == null && sharedText.isNullOrBlank()) {
                null
            } else {
                SharedPayload(stream?.toString(), type, sharedText)
            }
        }

        Intent.ACTION_VIEW -> data?.let { uri ->
            SharedPayload(uri.toString(), type ?: context.contentResolver.getType(uri), null)
        }

        else -> null
    }
}
