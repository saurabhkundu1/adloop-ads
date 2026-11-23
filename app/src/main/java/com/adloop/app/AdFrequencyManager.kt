package com.adloop.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

enum class AdFrequencyMode(
    val title: String,
    val description: String,
    val cooldownSeconds: Int,
) {
    BALANCED("Balanced", "Standard 5-second interval between sponsor clips", 5),
    RELAXED("Relaxed", "Extended 15-second breathing room between ads", 15),
    RAPID("Rapid", "Continuous feed with no transition delays", 0);

    companion object {
        fun fromName(name: String?): AdFrequencyMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
    }
}

class AdFrequencyManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("adloop_frequency_prefs", Context.MODE_PRIVATE)

    var mode: AdFrequencyMode
        get() = AdFrequencyMode.fromName(prefs.getString(KEY_MODE, AdFrequencyMode.BALANCED.name))
        set(value) {
            prefs.edit { putString(KEY_MODE, value.name) }
        }

    var lastAdCompletedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_COMPLETED, 0L)
        private set(value) {
            prefs.edit { putLong(KEY_LAST_COMPLETED, value) }
        }

    fun recordAdCompleted(currentTimeMillis: Long = System.currentTimeMillis()) {
        lastAdCompletedTimestamp = currentTimeMillis
    }

    fun remainingCooldownSeconds(currentTimeMillis: Long = System.currentTimeMillis()): Int {
        val cooldown = mode.cooldownSeconds
        if (cooldown <= 0) return 0
        val last = lastAdCompletedTimestamp
        if (last <= 0L) return 0
        val elapsedSeconds = ((currentTimeMillis - last) / 1000L).coerceAtMost(cooldown.toLong()).toInt()
        return (cooldown - elapsedSeconds).coerceAtLeast(0)
    }

    fun canViewNextAd(currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return remainingCooldownSeconds(currentTimeMillis) == 0
    }

    companion object {
        const val KEY_MODE = "ad_frequency_mode"
        const val KEY_LAST_COMPLETED = "ad_last_completed_timestamp"
    }
}
