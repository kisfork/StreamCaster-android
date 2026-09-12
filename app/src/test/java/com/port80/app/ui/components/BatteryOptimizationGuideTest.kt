package com.port80.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the battery-optimization guide text.
 * Pure JVM — the manufacturer is injected so no Android Build access is needed.
 */
class BatteryOptimizationGuideTest {

    @Test
    fun `generic guidance explains Doze and its trigger conditions`() {
        val text = getBatteryGuideText("Google")
        assertTrue(text.contains("Doze"))
        assertTrue(text.contains("screen is off"))
        assertTrue(text.contains("network"))
    }

    @Test
    fun `null manufacturer falls back to generic guidance`() {
        val text = getBatteryGuideText(null)
        assertTrue(text.contains("Doze"))
    }

    @Test
    fun `OEM branches mention the manufacturer's settings path`() {
        assertTrue(getBatteryGuideText("Samsung").contains("Samsung"))
        assertTrue(getBatteryGuideText("Xiaomi").contains("Xiaomi"))
        assertTrue(getBatteryGuideText("Redmi").contains("Xiaomi/Redmi"))
        assertTrue(getBatteryGuideText("Huawei").contains("Huawei"))
        assertTrue(getBatteryGuideText("Honor").contains("Huawei/Honor"))
        assertTrue(getBatteryGuideText("OnePlus").contains("App Battery Management"))
        assertTrue(getBatteryGuideText("OPPO").contains("App Battery Management"))
        assertTrue(getBatteryGuideText("realme").contains("App Battery Management"))
    }

    @Test
    fun `manufacturer matching is case-insensitive`() {
        assertTrue(getBatteryGuideText("SAMSUNG").contains("Samsung"))
    }

    @Test
    fun `OEM guidance does not reference Doze wording meant for stock Android`() {
        // OEM killers strike even without Doze, so those texts stay OEM-specific.
        assertFalse(getBatteryGuideText("Samsung").contains("Doze"))
    }
}
