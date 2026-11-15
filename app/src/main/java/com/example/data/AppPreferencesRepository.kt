package com.example.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettingsState(
    val isDarkTheme: Boolean = true,
    val hasCompletedOnboarding: Boolean = false,
    val deviceName: String = "GestoShare (${Build.MODEL ?: "Device"})",
    val gestureConfidenceThreshold: Float = 0.80f,
    val requiredConsecutiveFrames: Int = 5,
    val commandCooldownMs: Long = 1200L,
    val holdDurationMs: Long = 200L,
    val useFrontCamera: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val soundFeedbackEnabled: Boolean = false,
    val gestureTransferModeEnabled: Boolean = false
)

class AppPreferencesRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gestoshare_settings", Context.MODE_PRIVATE)

    private val defaultDeviceName = "GestoShare (${Build.MODEL ?: "Device"})"

    private val _settings = MutableStateFlow(loadInitial())
    val settings: StateFlow<AppSettingsState> = _settings.asStateFlow()

    private fun loadInitial(): AppSettingsState {
        return AppSettingsState(
            isDarkTheme = prefs.getBoolean("is_dark_theme", true),
            hasCompletedOnboarding = prefs.getBoolean("onboarding_completed", false),
            deviceName = prefs.getString("device_name", defaultDeviceName)?.takeIf { it.isNotBlank() } ?: defaultDeviceName,
            gestureConfidenceThreshold = prefs.getFloat("confidence_threshold", 0.80f).coerceIn(0.50f, 0.95f),
            requiredConsecutiveFrames = prefs.getInt("consecutive_frames", 5).coerceIn(2, 10),
            commandCooldownMs = prefs.getLong("cooldown_ms", 1200L).coerceIn(0L, 4000L),
            holdDurationMs = prefs.getLong("hold_duration_ms", 200L).coerceIn(0L, 1500L),
            useFrontCamera = prefs.getBoolean("use_front_camera", true),
            hapticFeedbackEnabled = prefs.getBoolean("haptic_enabled", true),
            soundFeedbackEnabled = prefs.getBoolean("sound_enabled", false),
            gestureTransferModeEnabled = prefs.getBoolean("gesture_transfer_mode", false)
        )
    }

    fun update(transform: (AppSettingsState) -> AppSettingsState) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putBoolean("is_dark_theme", updated.isDarkTheme)
            .putBoolean("onboarding_completed", updated.hasCompletedOnboarding)
            .putString("device_name", updated.deviceName)
            .putFloat("confidence_threshold", updated.gestureConfidenceThreshold)
            .putInt("consecutive_frames", updated.requiredConsecutiveFrames)
            .putLong("cooldown_ms", updated.commandCooldownMs)
            .putLong("hold_duration_ms", updated.holdDurationMs)
            .putBoolean("use_front_camera", updated.useFrontCamera)
            .putBoolean("haptic_enabled", updated.hapticFeedbackEnabled)
            .putBoolean("sound_enabled", updated.soundFeedbackEnabled)
            .putBoolean("gesture_transfer_mode", updated.gestureTransferModeEnabled)
            .apply()
    }
}
