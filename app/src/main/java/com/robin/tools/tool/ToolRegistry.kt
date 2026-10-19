package com.robin.tools.tool

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.robin.tools.R
import com.robin.tools.feature.media.data.CompressionType
import com.robin.tools.navigation.AppRoute

enum class ToolCategory(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    CREATE(
        R.string.tool_category_create,
        R.string.tool_category_create_desc,
        Icons.Default.Videocam
    ),
    IMAGE(
        R.string.tool_category_image,
        R.string.tool_category_image_desc,
        Icons.Default.Image
    ),
    MEDIA(
        R.string.tool_category_media,
        R.string.tool_category_media_desc,
        Icons.Default.VideoLibrary
    ),
    UTILITY(
        R.string.tool_category_utility,
        R.string.tool_category_utility_desc,
        Icons.Default.Storage
    )
}

/** One entry in the searchable home workbench. */
data class ToolEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val category: ToolCategory,
    val route: AppRoute,
    /** Extra search terms, including Chinese aliases. */
    val keywords: List<String> = emptyList()
)

/**
 * Single source of truth for navigable tools.
 *
 * Adding a feature means adding one entry here; search, favorites, and recents
 * pick it up automatically.
 */
object ToolRegistry {

    val tools: List<ToolEntry> = listOf(
        ToolEntry(
            "camera", R.string.tool_camera_title, R.string.tool_camera_desc,
            Icons.Default.Videocam, ToolCategory.CREATE, AppRoute.CameraMain,
            listOf("record", "video", "录像", "拍摄", "相机")
        ),
        ToolEntry(
            "record", R.string.tool_record_title, R.string.tool_record_desc,
            Icons.Default.Videocam, ToolCategory.CREATE, AppRoute.CameraRecord,
            listOf("record", "segment", "录制", "分段")
        ),
        ToolEntry(
            "photo", R.string.tool_photo_title, R.string.tool_photo_desc,
            Icons.Default.PhotoCamera, ToolCategory.CREATE, AppRoute.CameraPhoto,
            listOf("photo", "still", "拍照", "照片")
        ),
        ToolEntry(
            "media_editor", R.string.tool_media_title, R.string.tool_media_desc,
            Icons.Default.Image, ToolCategory.CREATE, AppRoute.MediaMain,
            listOf("media", "editor", "媒体", "编辑")
        ),
        ToolEntry(
            "camera_edit", R.string.tool_camera_edit_title, R.string.tool_camera_edit_desc,
            Icons.Default.Edit, ToolCategory.CREATE, AppRoute.CameraMain,
            listOf("watermark", "sticker", "subtitle", "水印", "贴纸", "字幕")
        ),
        ToolEntry(
            "camera_trim", R.string.tool_camera_trim_title, R.string.tool_camera_trim_desc,
            Icons.Default.ContentCut, ToolCategory.CREATE, AppRoute.CameraMain,
            listOf("trim", "cut", "剪辑", "裁剪")
        ),
        ToolEntry(
            "camera_cover", R.string.tool_camera_cover_title, R.string.tool_camera_cover_desc,
            Icons.Default.Collections, ToolCategory.CREATE, AppRoute.CameraMain,
            listOf("cover", "frame", "封面", "首帧")
        ),
        ToolEntry(
            "text_to_video", R.string.tool_text_video_title, R.string.tool_text_video_desc,
            Icons.Default.TextFields, ToolCategory.CREATE, AppRoute.TextToVideo,
            listOf("text", "card", "文字", "卡片", "生成视频")
        ),
        ToolEntry(
            "slideshow", R.string.tool_slideshow_title, R.string.tool_slideshow_desc,
            Icons.Default.Slideshow, ToolCategory.CREATE, AppRoute.Slideshow,
            listOf("slideshow", "photo", "相册", "幻灯片")
        ),
        ToolEntry(
            "image_studio", R.string.tool_image_studio_title, R.string.tool_image_studio_desc,
            Icons.Default.Tune, ToolCategory.IMAGE, AppRoute.ImageTool("CONVERT"),
            listOf("image", "toolkit", "图片", "工坊")
        ),
        ToolEntry(
            "image_convert", R.string.tool_image_convert_title, R.string.tool_image_convert_desc,
            Icons.Default.Image, ToolCategory.IMAGE, AppRoute.ImageTool("CONVERT"),
            listOf("convert", "jpeg", "png", "webp", "格式", "转换")
        ),
        ToolEntry(
            "image_resize", R.string.tool_image_resize_title, R.string.tool_image_resize_desc,
            Icons.Default.AspectRatio, ToolCategory.IMAGE, AppRoute.ImageTool("RESIZE"),
            listOf("resize", "scale", "缩放", "尺寸")
        ),
        ToolEntry(
            "image_rotate", R.string.tool_image_rotate_title, R.string.tool_image_rotate_desc,
            Icons.AutoMirrored.Filled.RotateRight, ToolCategory.IMAGE, AppRoute.ImageTool("ROTATE"),
            listOf("rotate", "旋转", "方向")
        ),
        ToolEntry(
            "image_exif", R.string.tool_image_exif_title, R.string.tool_image_exif_desc,
            Icons.Default.Info, ToolCategory.IMAGE, AppRoute.ImageTool("EXIF"),
            listOf("exif", "metadata", "privacy", "元数据", "隐私")
        ),
        ToolEntry(
            "image_pdf", R.string.tool_image_pdf_title, R.string.tool_image_pdf_desc,
            Icons.Default.PictureAsPdf, ToolCategory.IMAGE, AppRoute.ImageTool("TO_PDF"),
            listOf("pdf", "image", "图片", "转pdf")
        ),
        ToolEntry(
            "qr", R.string.tool_qr_title, R.string.tool_qr_desc,
            Icons.Default.QrCode, ToolCategory.IMAGE, AppRoute.QrTool(),
            listOf("qr", "barcode", "二维码", "扫码", "生成")
        ),
        ToolEntry(
            "document_scanner", R.string.tool_document_scanner_title,
            R.string.tool_document_scanner_desc,
            Icons.Default.DocumentScanner, ToolCategory.IMAGE, AppRoute.DocumentScanner(),
            listOf("scan", "document", "pdf", "扫描", "文档", "透视校正")
        ),
        ToolEntry(
            "ocr", R.string.tool_ocr_title, R.string.tool_ocr_desc,
            Icons.Default.DocumentScanner, ToolCategory.IMAGE, AppRoute.OcrTool(),
            listOf("ocr", "text", "识别", "文字", "提取")
        ),
        ToolEntry(
            "video_compress", R.string.tool_video_compress_title, R.string.tool_video_compress_desc,
            Icons.Default.VideoLibrary, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.VIDEO),
            listOf("compress", "video", "压缩", "视频")
        ),
        ToolEntry(
            "image_compress", R.string.tool_image_compress_title, R.string.tool_image_compress_desc,
            Icons.Default.Image, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.IMAGE),
            listOf("compress", "image", "压缩", "图片")
        ),
        ToolEntry(
            "gif", R.string.tool_gif_title, R.string.tool_gif_desc,
            Icons.Default.Slideshow, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.GIF),
            listOf("gif", "animation", "动图")
        ),
        ToolEntry(
            "transcode", R.string.tool_transcode_title, R.string.tool_transcode_desc,
            Icons.Default.Transform, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.TRANSCODE),
            listOf("mp4", "transcode", "转码", "格式")
        ),
        ToolEntry(
            "speed", R.string.tool_speed_title, R.string.tool_speed_desc,
            Icons.Default.Speed, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.SPEED),
            listOf("speed", "slow", "fast", "变速", "倍速")
        ),
        ToolEntry(
            "reverse", R.string.tool_reverse_title, R.string.tool_reverse_desc,
            Icons.Default.Replay, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.REVERSE),
            listOf("reverse", "倒放", "反向")
        ),
        ToolEntry(
            "concat", R.string.tool_concat_title, R.string.tool_concat_desc,
            Icons.AutoMirrored.Filled.CallMerge, ToolCategory.MEDIA,
            AppRoute.Compression(CompressionType.CONCAT),
            listOf("merge", "join", "合并", "拼接")
        ),
        ToolEntry(
            "crop", R.string.tool_crop_title, R.string.tool_crop_desc,
            Icons.Default.Crop, ToolCategory.MEDIA, AppRoute.Compression(CompressionType.CROP),
            listOf("crop", "aspect", "裁剪", "比例")
        ),
        ToolEntry(
            "volume", R.string.tool_volume_title, R.string.tool_volume_desc,
            Icons.AutoMirrored.Filled.VolumeUp, ToolCategory.MEDIA,
            AppRoute.Compression(CompressionType.VOLUME_FADE),
            listOf("volume", "fade", "音量", "淡入淡出")
        ),
        ToolEntry(
            "extract_audio", R.string.tool_extract_audio_title, R.string.tool_extract_audio_desc,
            Icons.Default.AudioFile, ToolCategory.MEDIA,
            AppRoute.Compression(CompressionType.EXTRACT_AUDIO),
            listOf("audio", "extract", "音频", "提取")
        ),
        ToolEntry(
            "strip_audio", R.string.tool_strip_audio_title, R.string.tool_strip_audio_desc,
            Icons.AutoMirrored.Filled.VolumeOff, ToolCategory.MEDIA,
            AppRoute.Compression(CompressionType.STRIP_AUDIO),
            listOf("mute", "remove audio", "静音", "去音频")
        ),
        ToolEntry(
            "video_custom", R.string.tool_video_custom_title, R.string.tool_video_custom_desc,
            Icons.Default.Tune, ToolCategory.MEDIA, AppRoute.VideoCustomCompress(),
            listOf("compress", "bitrate", "resolution", "压缩", "码率", "分辨率")
        ),
        ToolEntry(
            "audio_concat", R.string.tool_audio_concat_title, R.string.tool_audio_concat_desc,
            Icons.Default.AudioFile, ToolCategory.MEDIA, AppRoute.AudioConcat(),
            listOf("audio", "merge", "join", "音频", "拼接", "合并")
        ),
        ToolEntry(
            "audio_recorder", R.string.tool_audio_recorder_title, R.string.tool_audio_recorder_desc,
            Icons.Default.Mic, ToolCategory.MEDIA, AppRoute.AudioRecorder,
            listOf("record", "microphone", "录音", "波形")
        ),
        ToolEntry(
            "video_bgm", R.string.tool_video_bgm_title, R.string.tool_video_bgm_desc,
            Icons.Default.MusicNote, ToolCategory.MEDIA, AppRoute.VideoBgm(),
            listOf("music", "audio", "mix", "背景音乐", "混音")
        ),
        ToolEntry(
            "video_pip", R.string.tool_video_pip_title, R.string.tool_video_pip_desc,
            Icons.Default.PictureInPictureAlt, ToolCategory.MEDIA, AppRoute.VideoPip(),
            listOf("pip", "overlay", "画中画", "叠加")
        ),
        ToolEntry(
            "video_speed_custom", R.string.tool_video_speed_custom_title,
            R.string.tool_video_speed_custom_desc,
            Icons.Default.Speed, ToolCategory.MEDIA, AppRoute.VideoSpeedCustom(),
            listOf("slow motion", "timelapse", "慢动作", "延时", "变速")
        ),
        ToolEntry(
            "frame_extract", R.string.tool_frame_extract_title, R.string.tool_frame_extract_desc,
            Icons.Default.Collections, ToolCategory.MEDIA, AppRoute.FrameExtract(),
            listOf("frames", "cover", "抽帧", "封面", "静帧")
        ),
        ToolEntry(
            "audio_trim", R.string.tool_audio_trim_title, R.string.tool_audio_trim_desc,
            Icons.Default.AudioFile, ToolCategory.MEDIA, AppRoute.AudioTrim(),
            listOf("audio", "trim", "ringtone", "音频", "裁剪", "铃声")
        ),
        ToolEntry(
            "filter", R.string.tool_filter_title, R.string.tool_filter_desc,
            Icons.Default.FilterAlt, ToolCategory.MEDIA, AppRoute.Filter,
            listOf("filter", "opencv", "滤镜", "风格")
        ),
        ToolEntry(
            "ebook", R.string.tool_ebook_title, R.string.tool_ebook_desc,
            Icons.AutoMirrored.Filled.MenuBook, ToolCategory.UTILITY, AppRoute.Ebook,
            listOf("epub", "pdf", "电子书", "转换")
        ),
        ToolEntry(
            "light", R.string.tool_light_title, R.string.tool_light_desc,
            Icons.Default.LightMode, ToolCategory.UTILITY, AppRoute.LightLux,
            listOf("light", "lux", "sensor", "光照", "亮度")
        ),
        ToolEntry(
            "face", R.string.tool_face_title, R.string.tool_face_desc,
            Icons.Default.Face, ToolCategory.UTILITY, AppRoute.FaceCompare,
            listOf("face", "similarity", "人脸", "对比")
        ),
        ToolEntry(
            "face_blur", R.string.tool_face_blur_title, R.string.tool_face_blur_desc,
            Icons.Default.Face, ToolCategory.UTILITY, AppRoute.FaceBlur(),
            listOf("blur", "privacy", "mosaic", "打码", "马赛克", "隐私")
        ),
        ToolEntry(
            "storage", R.string.tool_storage_title, R.string.tool_storage_desc,
            Icons.Default.Storage, ToolCategory.UTILITY, AppRoute.StorageBench,
            listOf("storage", "benchmark", "speed", "存储", "测速")
        ),
        ToolEntry(
            "face_id_photo", R.string.tool_face_id_photo_title, R.string.tool_face_id_photo_desc,
            Icons.Default.Badge, ToolCategory.UTILITY, AppRoute.FaceIdPhoto(),
            listOf("id photo", "passport", "证件照", "换底色")
        ),
        ToolEntry(
            "face_group", R.string.tool_face_group_title, R.string.tool_face_group_desc,
            Icons.Default.Groups, ToolCategory.UTILITY, AppRoute.FaceGroup,
            listOf("group", "similar", "分组", "相似", "人脸")
        ),
        ToolEntry(
            "storage_cleanup", R.string.tool_storage_cleanup_title, R.string.tool_storage_cleanup_desc,
            Icons.Default.Storage, ToolCategory.UTILITY, AppRoute.StorageCleanup,
            listOf("cleanup", "large files", "duplicates", "清理", "大文件", "重复")
        ),
        ToolEntry(
            "history", R.string.tool_history_title, R.string.tool_history_desc,
            Icons.Default.History, ToolCategory.UTILITY, AppRoute.TaskHistory,
            listOf("history", "tasks", "历史", "任务")
        )
    )

    fun byId(id: String): ToolEntry? = tools.firstOrNull { it.id == id }
}
