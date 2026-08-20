package com.example.motionoverlay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore delegate - single instance per Context
private val Context.overlayDataStore: DataStore<Preferences> by preferencesDataStore(name = "overlay_prefs")

/**
 * Overlay configuration data class persisted via DataStore
 * @param dotSpeed Float: Slow (0.5f), Medium (1.0f), Fast (2.0f) - multiplier for drift animation speed
 * @param dotOpacity Float: 0.1f (10%) to 1.0f (100%) - overall opacity multiplier
 * @param themeColor Int/Hex: Cyan, Neon Green, Soft Amber, White
 * @param hideInLandscape Boolean: auto-hide overlay in horizontal video orientation
 * @param timerDuration Timer duration: Never (0), 15 Mins, 30 Mins, 1 Hour (60) - minutes
 */
data class OverlaySettings(
    val dotSpeed: Float = 1.0f,
    val dotOpacity: Float = 0.85f,
    val themeColor: Int = 0xFF00BCD4.toInt(),
    val hideInLandscape: Boolean = false,
    val timerDuration: Int = TIMER_NEVER
) {
    companion object {
        // Predefined theme colors
        const val CYAN = 0xFF00BCD4.toInt() // #00BCD4 vibrant cyan
        const val CYAN_ACCENT = 0xFF00E5FF.toInt() // #00E5FF
        const val NEON_GREEN = 0xFF00E676.toInt() // Neon Green
        const val NEON_GREEN_ACCENT = 0xFF69F0AE.toInt()
        const val SOFT_AMBER = 0xFFFFAB40.toInt() // Soft Amber
        const val SOFT_AMBER_ACCENT = 0xFFFFD740.toInt()
        const val WHITE = 0xFFFFFFFF.toInt() // White

        val THEME_OPTIONS = listOf(
            CYAN to "Cyan",
            NEON_GREEN to "Neon Green",
            SOFT_AMBER to "Soft Amber",
            WHITE to "White"
        )

        // Speed presets
        const val SPEED_SLOW = 0.5f
        const val SPEED_MEDIUM = 1.0f
        const val SPEED_FAST = 2.0f

        // Timer durations (minutes) - Never, 15 Mins, 30 Mins, 1 Hour
        const val TIMER_NEVER = 0
        const val TIMER_15 = 15
        const val TIMER_30 = 30
        const val TIMER_60 = 60 // 1 Hour
        val TIMER_OPTIONS = listOf(
            TIMER_NEVER to "Never",
            TIMER_15 to "15 Mins",
            TIMER_30 to "30 Mins",
            TIMER_60 to "1 Hour"
        )
        // Helper to convert minutes to millis
        fun timerDurationMillis(minutes: Int): Long = when (minutes) {
            TIMER_15 -> 15 * 60 * 1000L
            TIMER_30 -> 30 * 60 * 1000L
            TIMER_60 -> 60 * 60 * 1000L
            else -> 0L // Never
        }
    }
}

/**
 * Jetpack DataStore wrapper for overlay configuration persistence.
 * Exposes saved preferences as Flow so MotionOverlayService updates dynamically.
 */
object OverlayPreferences {

    private val DOT_SPEED = floatPreferencesKey("dot_speed")
    private val DOT_OPACITY = floatPreferencesKey("dot_opacity")
    private val THEME_COLOR = intPreferencesKey("theme_color")
    private val HIDE_IN_LANDSCAPE = booleanPreferencesKey("hide_in_landscape")
    private val TIMER_DURATION = intPreferencesKey("timer_duration")

    /**
     * Expose saved preferences as Flow - service collects this to update rendering dynamically
     */
    fun overlaySettingsFlow(context: Context): Flow<OverlaySettings> {
        return context.overlayDataStore.data.map { prefs ->
            OverlaySettings(
                dotSpeed = prefs[DOT_SPEED] ?: OverlaySettings.SPEED_MEDIUM,
                dotOpacity = prefs[DOT_OPACITY] ?: 0.85f,
                themeColor = prefs[THEME_COLOR] ?: OverlaySettings.CYAN,
                hideInLandscape = prefs[HIDE_IN_LANDSCAPE] ?: false,
                timerDuration = prefs[TIMER_DURATION] ?: OverlaySettings.TIMER_NEVER
            )
        }
    }

    suspend fun updateDotSpeed(context: Context, speed: Float) {
        context.overlayDataStore.edit { prefs ->
            prefs[DOT_SPEED] = speed.coerceIn(0.2f, 3.0f)
        }
    }

    suspend fun updateDotOpacity(context: Context, opacity: Float) {
        context.overlayDataStore.edit { prefs ->
            prefs[DOT_OPACITY] = opacity.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun updateThemeColor(context: Context, color: Int) {
        context.overlayDataStore.edit { prefs ->
            prefs[THEME_COLOR] = color
        }
    }

    suspend fun updateHideInLandscape(context: Context, hide: Boolean) {
        context.overlayDataStore.edit { prefs ->
            prefs[HIDE_IN_LANDSCAPE] = hide
        }
    }

    suspend fun updateTimerDuration(context: Context, minutes: Int) {
        context.overlayDataStore.edit { prefs ->
            prefs[TIMER_DURATION] = minutes.coerceIn(0, 60)
        }
    }

    // Convenience for SharedPreferences fallback if needed (not used, but shows compatibility)
    // Uses same keys for potential migration
}
