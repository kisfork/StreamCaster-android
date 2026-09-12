package com.port80.app.data.model

/**
 * Extracts SRT connection parameters embedded as query params in an SRT URL
 * (e.g. `srt://host:port?streamid=x&passphrase=y&latency=120&pbkeylen=32`).
 *
 * Users routinely paste such URLs wholesale. The streaming stack never reads
 * these params from the URL — Stream ID, Passphrase, Latency, Mode, and Key
 * Length come from their dedicated profile fields — so anything left in the
 * URL query is silently dropped at connect time (observed as e.g. an
 * unencrypted connection rejected with SRT_REJ_UNSECURE). This parser splits
 * the URL into the clean `srt://host:port` base plus typed fields.
 *
 * Pure Kotlin (no android.net/java.net.URI) so it is JVM-testable, and
 * deliberately mirrors RootEncoder's naive query handling: values are taken
 * verbatim between `=` and the next `&`, which keeps SRT Access Control IDs
 * like `#!::m=publish,r=live/x` intact.
 */
object SrtUrlParams {

    /** Result of splitting an SRT URL into a clean base URL + typed params. */
    data class Parsed(
        /** Clean URL without the query string: `srt://host:port`. */
        val baseUrl: String,
        val streamId: String? = null,
        val passphrase: String? = null,
        val latencyMs: Int? = null,
        val keyLength: SrtKeyLength? = null,
        val mode: SrtMode? = null
    )

    /**
     * Parse an SRT URL. Returns null when there is nothing to extract:
     * non-SRT scheme, no query string, or no recognized parameters
     * (unknown params are left alone rather than stripped).
     */
    fun parse(url: String): Parsed? {
        val trimmed = url.trim()
        if (!trimmed.lowercase().startsWith("srt://")) return null
        val queryStart = trimmed.indexOf('?')
        if (queryStart < 0 || queryStart == trimmed.length - 1) return null
        val baseUrl = trimmed.substring(0, queryStart)
        val rawQuery = trimmed.substring(queryStart + 1)

        var streamId: String? = null
        var passphrase: String? = null
        var latencyMs: Int? = null
        var keyLength: SrtKeyLength? = null
        var mode: SrtMode? = null

        for (entry in rawQuery.split('&')) {
            val separator = entry.indexOf('=')
            if (separator <= 0) continue
            val key = entry.substring(0, separator).trim().lowercase()
            val value = entry.substring(separator + 1).trim()
            if (value.isEmpty()) continue
            when (key) {
                "streamid" -> streamId = value
                "passphrase" -> passphrase = value
                "latency" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { latencyMs = it }
                "pbkeylen" -> keyLength = parseKeyLength(value) ?: keyLength
                "mode" -> SrtMode.fromString(value)
                    .takeIf { it.name.equals(value, ignoreCase = true) }
                    ?.let { mode = it }
            }
        }

        return if (streamId == null && passphrase == null && latencyMs == null &&
            keyLength == null && mode == null
        ) {
            null
        } else {
            Parsed(baseUrl, streamId, passphrase, latencyMs, keyLength, mode)
        }
    }

    /**
     * Map a pbkeylen value to a key length: byte sizes (16/24/32) per the SRT
     * URI convention, or enum-name forms like AES_256 / AES-256.
     */
    private fun parseKeyLength(value: String): SrtKeyLength? {
        SrtKeyLength.entries.firstOrNull { it.bytes.toString() == value }?.let { return it }
        return SrtKeyLength.entries.firstOrNull {
            it.name.equals(value.replace("-", "_"), ignoreCase = true)
        }
    }
}
