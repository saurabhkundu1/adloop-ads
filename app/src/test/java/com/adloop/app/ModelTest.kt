package com.adloop.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {

    @Test
    fun walletAndEarning_holdExpectedValues() {
        val earnings = listOf(
            Earning(name = "Test Sponsor", source = "Direct sponsor", amount = 100),
            Earning(name = "Partner Brand", source = "Provider · AdNetwork", amount = 50)
        )
        val wallet = Wallet(balance = 150, history = earnings)

        assertEquals(150, wallet.balance)
        assertEquals(2, wallet.history.size)
        assertEquals("Test Sponsor", wallet.history[0].name)
        assertEquals(100, wallet.history[0].amount)
        assertEquals("Provider · AdNetwork", wallet.history[1].source)
    }

    @Test
    fun feedIndexCalculations_stayWithinBounds() {
        val totalCampaigns = 5
        var index = 0

        // Advance
        index = (index + 1).coerceAtMost(totalCampaigns - 1)
        assertEquals(1, index)

        // Previous
        index = (index - 1).coerceAtLeast(0)
        assertEquals(0, index)

        // Previous at boundary
        index = (index - 1).coerceAtLeast(0)
        assertEquals(0, index)

        // Advance to end
        index = 4
        index = (index + 1).coerceAtMost(totalCampaigns - 1)
        assertEquals(4, index)
    }
}
