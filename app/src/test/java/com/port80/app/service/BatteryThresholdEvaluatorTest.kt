package com.port80.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the pure battery threshold decision logic
 * used by [BatteryMonitor].
 */
class BatteryThresholdEvaluatorTest {

    @Test
    fun `healthy battery produces no event`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 5, criticalThreshold = 2)
        assertNull(evaluator.evaluate(100))
        assertNull(evaluator.evaluate(50))
        assertNull(evaluator.evaluate(6))
    }

    @Test
    fun `low battery fires warning exactly once`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 5, criticalThreshold = 2)
        assertEquals(BatteryEvent.LOW_WARNING, evaluator.evaluate(5))
        assertNull(evaluator.evaluate(4))
        assertNull(evaluator.evaluate(3))
    }

    @Test
    fun `critical battery fires every time`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 5, criticalThreshold = 2)
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(2))
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(1))
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(0))
    }

    @Test
    fun `critical takes precedence over low warning`() {
        // Degenerate config where low == critical: only critical fires.
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 2, criticalThreshold = 2)
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(2))
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(1))
    }

    @Test
    fun `boundary values fire at exactly the threshold`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 10, criticalThreshold = 3)
        assertNull(evaluator.evaluate(11))
        assertEquals(BatteryEvent.LOW_WARNING, evaluator.evaluate(10))
        assertNull(evaluator.evaluate(10)) // one-shot: already warned
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(3))
    }

    @Test
    fun `reset re-arms the one-shot low warning`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 5, criticalThreshold = 2)
        assertEquals(BatteryEvent.LOW_WARNING, evaluator.evaluate(5))
        assertNull(evaluator.evaluate(5))
        evaluator.reset()
        assertEquals(BatteryEvent.LOW_WARNING, evaluator.evaluate(5))
    }

    @Test
    fun `drop from warning level straight to critical fires critical`() {
        val evaluator = BatteryThresholdEvaluator(lowThreshold = 5, criticalThreshold = 2)
        assertEquals(BatteryEvent.LOW_WARNING, evaluator.evaluate(5))
        assertEquals(BatteryEvent.CRITICAL, evaluator.evaluate(2))
    }
}
