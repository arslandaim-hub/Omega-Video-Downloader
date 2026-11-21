package com.arslandaim.omegavideodownloader

import com.arslandaim.omegavideodownloader.resolver.TwitterResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitterSupportTest {

    @Test
    fun urlUtils_handlesAllTwitterDomainsAndFormats() {
        assertTrue(UrlUtils.isTwitterOrX("https://x.com/user/status/123456789?s=20"))
        assertTrue(UrlUtils.isTwitterOrX("https://fxtwitter.com/user/status/123456789"))
        assertTrue(UrlUtils.isTwitterOrX("https://vxtwitter.com/user/status/123456789"))
        assertTrue(UrlUtils.isTwitterOrX("https://fixupx.com/user/status/123456789"))
        assertTrue(UrlUtils.isTwitterOrX("https://t.co/abcXYZ"))
        assertFalse(UrlUtils.isTwitterOrX("https://dropbox.com/s/video.mp4"))
        assertFalse(UrlUtils.isTwitterOrX("https://youtube.com/watch?v=123"))

        assertEquals(
            "https://twitter.com/elonmusk/status/987654321?s=20",
            UrlUtils.normalize("https://fxtwitter.com/elonmusk/status/987654321?s=20")
        )
        assertEquals(
            "https://x.com/elonmusk/status/987654321",
            UrlUtils.toXUrl("https://twitter.com/elonmusk/status/987654321")
        )

        assertEquals("elonmusk" to "987654321", UrlUtils.extractTweetTarget("https://x.com/elonmusk/status/987654321?t=abc"))
        assertEquals("i" to "11223344", UrlUtils.extractTweetTarget("https://twitter.com/i/web/status/11223344"))
        assertNull(UrlUtils.extractTweetTarget("https://x.com/i/spaces/1vOxwabc"))

        assertTrue(UrlUtils.isTwitterSpaceOrBroadcast("https://x.com/i/spaces/1vOxwabc"))
        assertTrue(UrlUtils.isTwitterSpaceOrBroadcast("https://twitter.com/i/broadcasts/1OyKAabc"))
        assertTrue(UrlUtils.isTwitterSpaceOrBroadcast("https://www.pscp.tv/w/123"))
        assertFalse(UrlUtils.isTwitterSpaceOrBroadcast("https://x.com/user/status/123"))
    }

    @Test
    fun parseFxTwitterJson_parsesSingleVideoWithVariantsAndHls() {
        val json = """
            {
              "code": 200,
              "tweet": {
                "text": "Rocket launch clip https://t.co/abc123",
                "author": { "screen_name": "spacex" },
                "media": {
                  "videos": [
                    {
                      "type": "video",
                      "url": "https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/hi.mp4",
                      "thumbnail_url": "https://pbs.twimg.com/thumb.jpg",
                      "duration": 42.5,
                      "width": 1280,
                      "height": 720,
                      "variants": [
                        { "content_type": "application/x-mpegURL", "url": "https://video.twimg.com/ext_tw_video/1/pu/pl/master.m3u8" },
                        { "content_type": "video/mp4", "bitrate": 832000, "url": "https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/640x360/mid.mp4" },
                        { "content_type": "video/mp4", "bitrate": 2176000, "url": "https://video.twimg.com/ext_tw_video/1/pu/vid/avc1/1280x720/hi.mp4" }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val meta = TwitterResolver.parseFxTwitterJson(json, "https://twitter.com/spacex/status/1")
        assertNotNull(meta)
        meta!!
        assertEquals("@spacex: Rocket launch clip", meta.title)
        assertEquals(42L, meta.durationSec)
        assertFalse(meta.isGif)
        assertEquals(2, meta.qualities.size)
        assertEquals("720p (MP4)", meta.qualities.first().label)
        assertTrue(meta.qualities.first().useDirectUrl)
        assertEquals(StreamKind.HLS, meta.streams.first().kind)
        assertTrue(meta.mediaItems.isEmpty())
    }

    @Test
    fun parseFxTwitterJson_parsesGifAndMultiVideoCarousel() {
        val json = """
            {
              "tweet": {
                "text": "Two clips in one post",
                "author": { "screen_name": "creator" },
                "media": {
                  "all": [
                    { "type": "photo", "url": "https://pbs.twimg.com/img.jpg" },
                    {
                      "type": "video",
                      "url": "https://video.twimg.com/ext_tw_video/10/vid/720x1280/clip1.mp4",
                      "thumbnail_url": "https://pbs.twimg.com/v1.jpg",
                      "duration": 10.0,
                      "width": 720,
                      "height": 1280
                    },
                    {
                      "type": "gif",
                      "url": "https://video.twimg.com/tweet_video/loop.mp4",
                      "thumbnail_url": "https://pbs.twimg.com/gif.jpg",
                      "duration": 3.0,
                      "width": 480,
                      "height": 480
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val meta = TwitterResolver.parseFxTwitterJson(json, "https://twitter.com/creator/status/2")
        assertNotNull(meta)
        meta!!
        assertEquals(2, meta.mediaItems.size)
        assertFalse(meta.mediaItems[0].isGif)
        assertTrue(meta.mediaItems[1].isGif)
        assertTrue(meta.mediaItems[1].qualities.first().label.startsWith("GIF"))
        assertTrue(meta.mediaItems[1].qualities.first().useDirectUrl)
    }

    @Test
    fun parseFxTwitterJson_fallsBackToQuotedTweetVideo() {
        val json = """
            {
              "tweet": {
                "text": "Look at this!",
                "author": { "screen_name": "quoter" },
                "quote": {
                  "media": {
                    "videos": [
                      {
                        "type": "video",
                        "url": "https://video.twimg.com/ext_tw_video/99/vid/1920x1080/q.mp4",
                        "thumbnail_url": "https://pbs.twimg.com/q.jpg",
                        "duration": 15.0,
                        "width": 1920,
                        "height": 1080
                      }
                    ]
                  }
                }
              }
            }
        """.trimIndent()

        val meta = TwitterResolver.parseFxTwitterJson(json, "https://twitter.com/quoter/status/3")
        assertNotNull(meta)
        assertEquals("1080p (MP4)", meta!!.qualities.first().label)
    }

    @Test
    fun parseVxTwitterJson_handlesQuoteFallback() {
        val json = """
            {
              "user_screen_name": "alice",
              "text": "RT comment",
              "media_extended": [],
              "qrt": {
                "media_extended": [
                  {
                    "type": "gif",
                    "url": "https://video.twimg.com/tweet_video/abc.mp4",
                    "thumbnail_url": "https://pbs.twimg.com/abc.jpg",
                    "duration_millis": 4200,
                    "size": { "width": 498, "height": 360 }
                  }
                ]
              }
            }
        """.trimIndent()

        val meta = TwitterResolver.parseVxTwitterJson(json, "https://twitter.com/alice/status/4")
        assertNotNull(meta)
        meta!!
        assertTrue(meta.isGif)
        assertEquals(4L, meta.durationSec)
        assertEquals("GIF • 360p (MP4)", meta.qualities.first().label)
    }
}
