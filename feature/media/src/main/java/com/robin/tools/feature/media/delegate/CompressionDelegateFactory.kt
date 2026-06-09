package com.robin.tools.feature.media.delegate

import com.robin.tools.feature.media.data.CompressionType

object CompressionDelegateFactory {
    fun create(type: CompressionType): CompressionDelegate {
        return when (type) {
            CompressionType.VIDEO -> VideoCompressionDelegate()
            CompressionType.GIF -> GifConversionDelegate()
            CompressionType.IMAGE -> ImageCompressionDelegate()
        }
    }
}
