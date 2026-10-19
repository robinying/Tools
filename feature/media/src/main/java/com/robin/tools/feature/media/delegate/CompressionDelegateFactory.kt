package com.robin.tools.feature.media.delegate

import com.robin.tools.feature.media.data.CompressionType

object CompressionDelegateFactory {
    fun create(type: CompressionType): CompressionDelegate {
        return when (type) {
            CompressionType.VIDEO -> VideoCompressionDelegate()
            CompressionType.GIF -> GifConversionDelegate()
            CompressionType.IMAGE -> ImageCompressionDelegate()
            CompressionType.EXTRACT_AUDIO -> AudioExtractDelegate()
            CompressionType.STRIP_AUDIO -> StripAudioDelegate()
            CompressionType.TRANSCODE -> TranscodeDelegate()
            CompressionType.SPEED -> SpeedChangeDelegate()
            CompressionType.REVERSE -> ReverseDelegate()
            CompressionType.CONCAT -> ConcatDelegate()
            CompressionType.CROP -> CropAspectDelegate()
            CompressionType.VOLUME_FADE -> VolumeFadeDelegate()
            CompressionType.AUDIO_TRIM -> AudioTrimDelegate()
            CompressionType.VIDEO_CUSTOM -> VideoCompressionDelegate()
            CompressionType.AUDIO_CONCAT -> AudioConcatDelegate()
            CompressionType.VIDEO_BGM -> VideoBgmDelegate(mixWithOriginal = true)
            CompressionType.VIDEO_PIP -> VideoPipDelegate(PipPosition.TOP_RIGHT)
            CompressionType.VIDEO_SPEED_CUSTOM -> CustomSpeedDelegate(speed = 0.5)
        }
    }
}
