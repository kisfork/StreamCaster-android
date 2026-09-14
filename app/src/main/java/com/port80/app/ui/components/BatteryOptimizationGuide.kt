package com.port80.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Asks the user to exempt StreamCaster from battery optimization before going live.
 *
 * Why: Android's deep Doze mode (screen off + device stationary + on battery,
 * roughly 30 minutes in) ignores wake locks and blocks network access outside
 * maintenance windows. A live stream from a phone propped on a tripod dies even
 * though the app holds a PARTIAL_WAKE_LOCK and runs a foreground service.
 *
 * Exempted apps are excluded from Doze restrictions, so the stream survives.
 * Aggressive OEM battery management (Samsung, Xiaomi, Huawei, etc.) can also
 * kill the foreground service; the OEM-specific guidance below covers that.
 */
@Composable
fun BatteryOptimizationGuide(
    onAllow: () -> Unit,
    onStreamAnyway: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onStreamAnyway,
        title = { Text("Allow Background Streaming?") },
        text = {
            Column {
                Text(getBatteryGuideText())
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Stream Anyway starts now and is not asked again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                requestBatteryExemption(context)
                onAllow()
            }) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = onStreamAnyway) { Text("Stream Anyway") }
        }
    )
}

/** Check if the app is still subject to battery optimization / Doze. */
fun isAppBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Open the system's one-tap exemption request for this app
 * (requires the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission).
 * Falls back to the optimization settings list if the dialog is unavailable.
 */
fun requestBatteryExemption(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        openBatterySettings(context)
    }
}

/** Open the battery optimization settings list (no special permission needed). */
fun openBatterySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to general battery settings
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** OEM-specific guidance text. Manufacturer is a parameter so this is unit-testable on JVM. */
fun getBatteryGuideText(manufacturer: String? = Build.MANUFACTURER): String {
    val maker = (manufacturer ?: "").lowercase()
    return when {
        maker.contains("samsung") ->
            "Samsung devices may kill background apps. " +
                "Go to Settings → Apps → StreamCaster → Battery → Unrestricted " +
                "to prevent stream interruptions."
        maker.contains("xiaomi") || maker.contains("redmi") ->
            "Xiaomi/Redmi devices aggressively kill background apps. " +
                "Go to Settings → Apps → StreamCaster → Autostart and " +
                "Battery Saver → No Restrictions."
        maker.contains("huawei") || maker.contains("honor") ->
            "Huawei/Honor devices may stop background streaming. " +
                "Go to Settings → Apps → StreamCaster → Battery → Unmanaged."
        maker.contains("oppo") || maker.contains("realme") ||
            maker.contains("oneplus") ->
            "Go to Settings → Battery → App Battery Management → StreamCaster → " +
                "Don't Optimize."
        else ->
            "When the screen is off and the phone is stationary on battery, Android " +
                "enters Doze mode after about 30 minutes. Doze ignores the app's wake " +
                "lock and blocks its network access, which stops the stream. " +
                "Allowing background activity for StreamCaster keeps long streams alive."
    }
}
