package com.port80.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for SRT URL query-param extraction.
 */
class SrtUrlParamsTest {

    @Test
    fun `full param set splits into base url and fields`() {
        val parsed = SrtUrlParams.parse(
            "srt://ingest.example.com:9741?streamid=anything&passphrase=3bb23120&latency=250&pbkeylen=32&mode=listener"
        )!!
        assertEquals("srt://ingest.example.com:9741", parsed.baseUrl)
        assertEquals("anything", parsed.streamId)
        assertEquals("3bb23120", parsed.passphrase)
        assertEquals(250, parsed.latencyMs)
        assertEquals(SrtKeyLength.AES_256, parsed.keyLength)
        assertEquals(SrtMode.LISTENER, parsed.mode)
    }

    @Test
    fun `access control stream id with hash prefix survives verbatim`() {
        val parsed = SrtUrlParams.parse("srt://host:9000?streamid=#!::m=publish,r=live/test")!!
        assertEquals("#!::m=publish,r=live/test", parsed.streamId)
        assertEquals("srt://host:9000", parsed.baseUrl)
    }

    @Test
    fun `passphrase only leaves other fields null`() {
        val parsed = SrtUrlParams.parse("srt://host:9000?passphrase=0123456789")!!
        assertEquals("srt://host:9000", parsed.baseUrl)
        assertEquals("0123456789", parsed.passphrase)
        assertNull(parsed.streamId)
        assertNull(parsed.latencyMs)
        assertNull(parsed.keyLength)
        assertNull(parsed.mode)
    }

    @Test
    fun `non-srt url returns null`() {
        assertNull(SrtUrlParams.parse("rtmp://host/live?streamid=x"))
        assertNull(SrtUrlParams.parse("rtmps://host/live"))
    }

    @Test
    fun `url without query returns null`() {
        assertNull(SrtUrlParams.parse("srt://host:9000"))
        assertNull(SrtUrlParams.parse("srt://host:9000?"))
    }

    @Test
    fun `unknown params only are left alone`() {
        // No recognized params: return null so the URL is not stripped.
        assertNull(SrtUrlParams.parse("srt://host:9000?foo=bar&baz=1"))
    }

    @Test
    fun `recognized and unknown params can coexist`() {
        val parsed = SrtUrlParams.parse("srt://host:9000?foo=bar&streamid=x")!!
        assertEquals("srt://host:9000", parsed.baseUrl)
        assertEquals("x", parsed.streamId)
    }

    @Test
    fun `pbkeylen accepts byte sizes and enum names`() {
        assertEquals(SrtKeyLength.AES_128, SrtUrlParams.parse("srt://h:1?pbkeylen=16")!!.keyLength)
        assertEquals(SrtKeyLength.AES_192, SrtUrlParams.parse("srt://h:1?pbkeylen=24")!!.keyLength)
        assertEquals(SrtKeyLength.AES_256, SrtUrlParams.parse("srt://h:1?pbkeylen=32")!!.keyLength)
        assertEquals(SrtKeyLength.AES_256, SrtUrlParams.parse("srt://h:1?pbkeylen=AES_256")!!.keyLength)
        assertEquals(SrtKeyLength.AES_256, SrtUrlParams.parse("srt://h:1?pbkeylen=AES-256")!!.keyLength)
        // Unsupported size is ignored (not mapped to a default).
        assertNull(SrtUrlParams.parse("srt://h:1?pbkeylen=17&streamid=x")!!.keyLength)
    }

    @Test
    fun `invalid latency and mode values are ignored`() {
        val parsed = SrtUrlParams.parse("srt://h:1?latency=abc&mode=nonsense&streamid=x")!!
        assertNull(parsed.latencyMs)
        assertNull(parsed.mode)
        assertEquals("x", parsed.streamId)
        // Non-positive latency is not a real setting; ignore it.
        assertNull(SrtUrlParams.parse("srt://h:1?latency=0&mode=caller")!!.latencyMs)
    }

    @Test
    fun `empty values are ignored`() {
        assertNull(SrtUrlParams.parse("srt://host:9000?streamid=&passphrase="))
    }

    @Test
    fun `url is trimmed before parsing`() {
        val parsed = SrtUrlParams.parse("  srt://host:9000?streamid=x  ")!!
        assertEquals("srt://host:9000", parsed.baseUrl)
        assertEquals("x", parsed.streamId)
    }
}
