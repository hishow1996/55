package com.example.player

import com.example.model.VideoMediaInfo
import com.example.model.VideoSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSourceResolverTest {

    @Test
    fun pageUrlIsNeverTreatedAsNativeMedia() {
        val page = "https://example.com/watch/video"
        val video = VideoMediaInfo(
            url = page,
            pageUrl = page
        )

        assertFalse(VideoSourceResolver.canUseNativePlayer(video))
        assertTrue(VideoSourceResolver.resolve(video) is VideoSource.WebFallback)
    }

    @Test
    fun blobUrlFallsBackToWebPlayback() {
        val video = VideoMediaInfo(
            url = "blob:https://example.com/1234",
            pageUrl = "https://example.com/watch"
        )

        assertFalse(VideoSourceResolver.canUseNativePlayer(video))
        assertTrue(VideoSourceResolver.resolve(video) is VideoSource.WebFallback)
    }

    @Test
    fun hlsAndDashSourcesUseNativePlayback() {
        val hls = VideoMediaInfo(
            url = "https://cdn.example.com/live/master.m3u8",
            pageUrl = "https://example.com/watch"
        )
        val dash = VideoMediaInfo(
            url = "https://cdn.example.com/live/manifest.mpd",
            pageUrl = "https://example.com/watch"
        )

        assertTrue(VideoSourceResolver.canUseNativePlayer(hls))
        assertTrue(VideoSourceResolver.canUseNativePlayer(dash))
        assertTrue(VideoSourceResolver.resolve(hls) is VideoSource.Hls)
        assertTrue(VideoSourceResolver.resolve(dash) is VideoSource.Dash)
    }

    @Test
    fun directVideoUrlUsesNativePlayback() {
        val video = VideoMediaInfo(
            url = "https://cdn.example.com/video.mp4?token=abc",
            pageUrl = "https://example.com/watch"
        )

        assertTrue(VideoSourceResolver.canUseNativePlayer(video))
        assertTrue(VideoSourceResolver.resolve(video) is VideoSource.Direct)
    }

    @Test
    fun signedVideoQueryCanBeDetectedWithoutExtension() {
        val video = VideoMediaInfo(
            url = "https://cdn.example.com/stream?id=1&mime=video%2Fmp4",
            pageUrl = "https://example.com/watch"
        )

        assertTrue(VideoSourceResolver.canUseNativePlayer(video))
    }
}
