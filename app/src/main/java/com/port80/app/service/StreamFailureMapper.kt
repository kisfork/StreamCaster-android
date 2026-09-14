package com.port80.app.service

import com.port80.app.crash.CredentialSanitizer
import com.port80.app.data.model.StopReason

/**
 * Utility to map raw error strings to user-facing messages and StopReasons.
 */
object StreamFailureMapper {

    fun mapFailureReason(reason: String): StopReason {
        val normalized = reason.uppercase()
        return when {
            "AUTH" in normalized -> StopReason.ERROR_AUTH
            "AUDIO" in normalized -> StopReason.ERROR_AUDIO
            "CAMERA" in normalized || "PREVIEW" in normalized -> StopReason.ERROR_CAMERA
            // Network/transport failures from the streaming engine — a
            // lost connection is not an encoder problem, and reporting
            // it as one hides the real cause from the user.
            isNetworkFailure(normalized) -> StopReason.ERROR_NETWORK
            else -> StopReason.ERROR_ENCODER
        }
    }

    private fun isNetworkFailure(normalized: String): Boolean {
        return "NO RESPONSE" in normalized ||
            "SHUTDOWN" in normalized ||
            "PEERERROR" in normalized ||
            "SOCKET" in normalized ||
            "READ BUFFER" in normalized ||
            "BROKEN PIPE" in normalized ||
            "TIMED OUT" in normalized || "TIMEOUT" in normalized ||
            "REFUSED" in normalized ||
            "UNREACHABLE" in normalized || "NO ROUTE" in normalized
    }

    /**
     * Whether a connection failure is potentially recoverable via reconnect.
     * Non-retryable: auth, camera, audio, encoder preparation failures.
     * Retryable: network timeouts, server refused, unreachable, generic IO errors.
     */
    fun isRetryable(reason: String): Boolean {
        val normalized = reason.uppercase()
        // Auth failures are permanent — wrong credentials
        if ("AUTH" in normalized) return false
        // Camera/audio errors are local device issues
        if ("AUDIO" in normalized) return false
        if ("CAMERA" in normalized || "PREVIEW" in normalized) return false
        // Encoder preparation failures are device capability issues
        if ("ENCODER_PREP" in normalized) return false
        if ("CAMERA_NOT_INIT" in normalized) return false
        // Malformed endpoint URL is a configuration error
        if ("MALFORMED" in normalized) return false
        // Everything else (timeout, refused, unreachable, IO errors) is retryable
        return true
    }

    fun buildFailureDetail(reason: String): String {
        val sanitizedReason = CredentialSanitizer.sanitize(reason)
        val normalized = sanitizedReason.uppercase()

        return when {
            "AUTH" in normalized ->
                "Authentication failed. Double-check stream key and account credentials."

            "TIMED OUT" in normalized || "TIMEOUT" in normalized ->
                "Connection timed out. Verify endpoint URL, internet access, and firewall/network restrictions."

            "REFUSED" in normalized || "UNREACHABLE" in normalized || "NO ROUTE" in normalized ->
                "Server is unreachable. Confirm the RTMP/RTMPS host, port, and that the ingest server is online."

            "AUDIO" in normalized ->
                "Audio initialization failed. Check microphone permission and whether another app is using the mic."

            "CAMERA" in normalized || "PREVIEW" in normalized ->
                "Camera initialization failed. Check camera permission and close other apps using the camera."

            "ENCODER_PREP_FAILED" in normalized ->
                "Device encoder setup failed. Try lowering resolution/FPS/bitrate in Video Settings."

            else ->
                "Could not connect to streaming endpoint. Verify endpoint URL and network, then retry. Detail: $sanitizedReason"
        }
    }
}
