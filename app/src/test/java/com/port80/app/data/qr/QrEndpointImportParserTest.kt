package com.port80.app.data.qr

import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.model.SrtKeyLength
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.StreamProtocol
import com.port80.app.data.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for QR endpoint payload parsing and duplicate-key normalization. */
class QrEndpointImportParserTest {

    @Test
    fun `json rtmps payload splits embedded stream key and preserves default request`() {
        val rawJson = """
            {
              "v": 1,
              "name": "Main Channel",
              "url": "rtmps://LIVE.Example.com/app/live_key_123",
              "videoCodec": "H265",
              "isDefault": true
            }
        """.trimIndent()

        val result = QrEndpointImportParser.parse(rawJson)

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("Main Channel", candidate.name)
        assertEquals("rtmps://live.example.com/app", candidate.url)
        assertEquals("live_key_123", candidate.streamKey)
        assertEquals(VideoCodec.H265, candidate.videoCodec)
        assertTrue(candidate.requestedDefault)
    }

    @Test
    fun `plain rtmp URL is accepted as fallback payload`() {
        val result = QrEndpointImportParser.parse("rtmp://host.example/live/key_from_path")

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("rtmp://host.example/live", candidate.url)
        assertEquals("key_from_path", candidate.streamKey)
        assertEquals(VideoCodec.H264, candidate.videoCodec)
        assertFalse(candidate.requestedDefault)
    }

    @Test
    fun `srt json payload maps SRT fields safely`() {
        val rawJson = """
            {
              "v": 1,
              "name": "SRT ingest",
              "url": "srt://srt.example.com:9000",
              "videoCodec": "AV1",
              "srtPassphrase": "verysecret10",
              "srtKeyLength": "AES_256",
              "srtLatencyMs": 250,
              "srtMode": "CALLER",
              "srtStreamId": "#!::m=publish,r=live/test"
            }
        """.trimIndent()

        val result = QrEndpointImportParser.parse(rawJson)

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("srt://srt.example.com:9000", candidate.url)
        assertEquals("", candidate.streamKey)
        // AV1 is not supported for SRT, so the parser safely falls back to H.264.
        assertEquals(VideoCodec.H264, candidate.videoCodec)
        assertEquals("verysecret10", candidate.srtPassphrase)
        assertEquals(SrtKeyLength.AES_256, candidate.srtKeyLength)
        assertEquals(250, candidate.srtLatencyMs)
        assertEquals(SrtMode.CALLER, candidate.srtMode)
        assertEquals("#!::m=publish,r=live/test", candidate.srtStreamId)
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val result = QrEndpointImportParser.parse("{\"v\":2,\"url\":\"rtmp://host/live\",\"streamKey\":\"key\"}")

        assertTrue(result is QrEndpointParseResult.Invalid)
    }

    @Test
    fun `unknown json fields are rejected`() {
        val result = QrEndpointImportParser.parse(
            "{\"v\":1,\"url\":\"rtmp://host/live\",\"streamKey\":\"key\",\"surprise\":true}"
        )

        assertTrue(result is QrEndpointParseResult.Invalid)
    }

    @Test
    fun `plain srt url with query params splits into fields`() {
        val result = QrEndpointImportParser.parse(
            "srt://srt.example.com:9741?streamid=anything&passphrase=3bb23120f436eeec6e4a55f5e1f6b684b52b9fec7924c75616b11cbde489272c"
        )

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("srt://srt.example.com:9741", candidate.url)
        assertEquals("anything", candidate.srtStreamId)
        assertEquals(
            "3bb23120f436eeec6e4a55f5e1f6b684b52b9fec7924c75616b11cbde489272c",
            candidate.srtPassphrase
        )
    }

    @Test
    fun `plain srt url params map to typed candidate fields`() {
        val result = QrEndpointImportParser.parse(
            "srt://srt.example.com:9000?streamid=#!::m=publish,r=live/test&latency=250&pbkeylen=32"
        )

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("srt://srt.example.com:9000", candidate.url)
        assertEquals("#!::m=publish,r=live/test", candidate.srtStreamId)
        assertEquals(250, candidate.srtLatencyMs)
        assertEquals(SrtKeyLength.AES_256, candidate.srtKeyLength)
    }

    @Test
    fun `json srt url params are used but explicit json fields win`() {
        val rawJson = """
            {
              "v": 1,
              "name": "SRT ingest",
              "url": "srt://srt.example.com:9000?streamid=urlid&passphrase=urlpass123&latency=300",
              "srtStreamId": "jsonid",
              "srtPassphrase": "jsonpass123"
            }
        """.trimIndent()

        val result = QrEndpointImportParser.parse(rawJson)

        assertTrue(result is QrEndpointParseResult.Success)
        val candidate = (result as QrEndpointParseResult.Success).candidate
        assertEquals("srt://srt.example.com:9000", candidate.url)
        assertEquals("jsonid", candidate.srtStreamId)
        assertEquals("jsonpass123", candidate.srtPassphrase)
        // No explicit JSON latency — the URL-embedded one applies.
        assertEquals(300, candidate.srtLatencyMs)
    }

    @Test
    fun `duplicate key uses normalized URL and stream identity`() {
        val saved = EndpointProfile(
            id = "saved",
            name = "Saved",
            url = "RTMPS://LIVE.EXAMPLE.COM/app/",
            streamKey = "same-key"
        )
        val imported = QrEndpointImportCandidate(
            name = "Imported",
            url = "rtmps://live.example.com/app",
            streamKey = "same-key"
        )

        assertEquals(
            EndpointDuplicateKey(StreamProtocol.RTMPS, "rtmps://live.example.com/app", "same-key"),
            QrEndpointImportParser.duplicateKey(saved)
        )
        assertEquals(QrEndpointImportParser.duplicateKey(saved), QrEndpointImportParser.duplicateKey(imported))
    }
}