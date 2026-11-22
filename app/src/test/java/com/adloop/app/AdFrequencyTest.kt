package com.adloop.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyTest {

    @Test
    fun adFrequencyMode_resolvesCorrectDefaultsAndValues() {
        assertEquals(AdFrequencyMode.BALANCED, AdFrequencyMode.fromName("BALANCED"))
        assertEquals(AdFrequencyMode.RELAXED, AdFrequencyMode.fromName("relaxed"))
        assertEquals(AdFrequencyMode.RAPID, AdFrequencyMode.fromName("rapid"))
        assertEquals(AdFrequencyMode.BALANCED, AdFrequencyMode.fromName("unknown"))
        assertEquals(AdFrequencyMode.BALANCED, AdFrequencyMode.fromName(null))

        assertEquals(5, AdFrequencyMode.BALANCED.cooldownSeconds)
        assertEquals(15, AdFrequencyMode.RELAXED.cooldownSeconds)
        assertEquals(0, AdFrequencyMode.RAPID.cooldownSeconds)
    }

    @Test
    fun cooldownCalculation_computesRemainingSecondsAccurately() {
        val mode = AdFrequencyMode.BALANCED // 5 seconds
        val completedAt = 100_000L

        // 2 seconds later -> 3 seconds remaining
        val at102 = 102_000L
        val elapsed = ((at102 - completedAt) / 1000).toInt()
        val remaining = (mode.cooldownSeconds - elapsed).coerceAtLeast(0)
        assertEquals(3, remaining)

        // 6 seconds later -> 0 seconds remaining
        val at106 = 106_000L
        val elapsedAfter = ((at106 - completedAt) / 1000).toInt()
        val remainingAfter = (mode.cooldownSeconds - elapsedAfter).coerceAtLeast(0)
        assertEquals(0, remainingAfter)
    }

    @Test
    fun rapidMode_hasZeroCooldown() {
        val mode = AdFrequencyMode.RAPID
        assertEquals(0, mode.cooldownSeconds)
    }
}
