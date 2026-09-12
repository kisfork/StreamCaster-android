package com.port80.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors battery level during streaming.
 *
 * When battery drops below the low threshold (default 5%), shows a warning.
 * When it drops below critical threshold (default 2%), auto-stops the stream
 * to prevent data loss (recording needs to be finalized).
 *
 * Threshold decisions are delegated to [BatteryThresholdEvaluator] so the
 * logic is unit-testable on JVM without a Context.
 */
class BatteryMonitor(
    private val context: Context,
    lowThreshold: Int = 5,
    criticalThreshold: Int = 2,
    private val onLowBattery: () -> Unit,
    private val onCriticalBattery: () -> Unit
) {
    companion object {
        private const val TAG = "BatteryMonitor"
    }

    private val evaluator = BatteryThresholdEvaluator(lowThreshold, criticalThreshold)

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun startMonitoring() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percent = (level * 100) / scale
                    _batteryPercent.value = percent
                    handleBatteryPercent(percent)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        RedactingLogger.d(TAG, "Battery monitoring started (low=${evaluator.lowThreshold}%, critical=${evaluator.criticalThreshold}%)")
    }

    fun stopMonitoring() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) { /* already unregistered */ }
        }
        receiver = null
        evaluator.reset()
        RedactingLogger.d(TAG, "Battery monitoring stopped")
    }

    private fun handleBatteryPercent(percent: Int) {
        when (evaluator.evaluate(percent)) {
            BatteryEvent.LOW_WARNING -> {
                RedactingLogger.w(TAG, "Low battery warning: $percent%")
                onLowBattery()
            }
            BatteryEvent.CRITICAL -> {
                RedactingLogger.w(TAG, "CRITICAL battery: $percent%")
                onCriticalBattery()
            }
            null -> Unit
        }
    }
}

/**
 * Pure threshold decision logic for battery-driven stream events.
 * Separated from [BatteryMonitor] so it is testable on JVM.
 */
internal class BatteryThresholdEvaluator(
    val lowThreshold: Int,
    val criticalThreshold: Int
) {
    private var hasWarnedLow = false

    /**
     * Evaluate a battery percent and return the event it triggers.
     * CRITICAL takes precedence over LOW_WARNING, and the low warning
     * fires only once per [reset] cycle.
     */
    fun evaluate(percent: Int): BatteryEvent? = when {
        percent <= criticalThreshold -> BatteryEvent.CRITICAL
        percent <= lowThreshold && !hasWarnedLow -> {
            hasWarnedLow = true
            BatteryEvent.LOW_WARNING
        }
        else -> null
    }

    /** Clear the one-shot low-warning latch (e.g. when monitoring restarts). */
    fun reset() {
        hasWarnedLow = false
    }
}

internal enum class BatteryEvent { LOW_WARNING, CRITICAL }
