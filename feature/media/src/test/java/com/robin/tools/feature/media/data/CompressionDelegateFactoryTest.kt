package com.robin.tools.feature.media.data

import com.robin.tools.feature.media.delegate.AudioExtractDelegate
import com.robin.tools.feature.media.delegate.CompressionDelegateFactory
import com.robin.tools.feature.media.delegate.GifConversionDelegate
import com.robin.tools.feature.media.delegate.ImageCompressionDelegate
import com.robin.tools.feature.media.delegate.StripAudioDelegate
import com.robin.tools.feature.media.delegate.TranscodeDelegate
import com.robin.tools.feature.media.delegate.VideoCompressionDelegate
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionDelegateFactoryTest {

    @Test
    fun `create returns VideoCompressionDelegate for VIDEO type`() {
        val delegate = CompressionDelegateFactory.create(CompressionType.VIDEO)
        assertTrue("Should be VideoCompressionDelegate", delegate is VideoCompressionDelegate)
    }

    @Test
    fun `create returns ImageCompressionDelegate for IMAGE type`() {
        val delegate = CompressionDelegateFactory.create(CompressionType.IMAGE)
        assertTrue("Should be ImageCompressionDelegate", delegate is ImageCompressionDelegate)
    }

    @Test
    fun `create returns GifConversionDelegate for GIF type`() {
        val delegate = CompressionDelegateFactory.create(CompressionType.GIF)
        assertTrue("Should be GifConversionDelegate", delegate is GifConversionDelegate)
    }

    @Test
    fun `create returns AudioExtractDelegate for EXTRACT_AUDIO`() {
        assertTrue(
            CompressionDelegateFactory.create(CompressionType.EXTRACT_AUDIO) is AudioExtractDelegate
        )
    }

    @Test
    fun `create returns StripAudioDelegate for STRIP_AUDIO`() {
        assertTrue(
            CompressionDelegateFactory.create(CompressionType.STRIP_AUDIO) is StripAudioDelegate
        )
    }

    @Test
    fun `create returns TranscodeDelegate for TRANSCODE`() {
        assertTrue(
            CompressionDelegateFactory.create(CompressionType.TRANSCODE) is TranscodeDelegate
        )
    }
}