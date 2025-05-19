package com.example.eyeSeeYou.helpers

import androidx.annotation.StringRes
import com.example.eyeSeeYou.R

enum class VoiceMessage(
    @StringRes val resId: Int,
    val wearableCommand: WearableCommand? = null
) {
    //Obstacle
    WARNING_STEPUP(R.string.warning_stepup, WearableCommand.DOWN),
    WARNING_STEPDOWN(R.string.warning_stepdown, WearableCommand.UP),
    WARNING(R.string.warning, WearableCommand.GENERICS),
    WARNING_OBSTACLE_LEFT(R.string.warning_obstacle_left, WearableCommand.SX),
    WARNING_OBSTACLE_RIGHT(R.string.warning_obstacle_right, WearableCommand.DX),
    WARNING_OBSTACLE_CENTER(R.string.warning_obstacle_center, WearableCommand.UP),
    WARNING_OBSTACLE_LOW(R.string.warning_obstacle_low, WearableCommand.DOWN),
    WARNING_OBSTACLE_HIGH(R.string.warning_obstacle_high, WearableCommand.UP),
    WARNING_OBSTACLE_LEFT_BIG(R.string.warning_obstacle_left_big, WearableCommand.SX),
    WARNING_OBSTACLE_RIGHT_BIG(R.string.warning_obstacle_right_big, WearableCommand.DX),
    WARNING_OBSTACLE_NARROW(R.string.warning_obstacle_narrow, WearableCommand.GENERICS),
    WARNING_OBSTACLE_LEFT_HUGE(R.string.warning_obstacle_left_huge, WearableCommand.SX),
    WARNING_OBSTACLE_RIGHT_HUGE(R.string.warning_obstacle_right_huge, WearableCommand.DX),
    WARNING_OBSTACLE_STOP(R.string.warning_obstacle_stop, WearableCommand.DANGEROUS),
    WARNING_ALMOST_ON_STEP(R.string.warning_almost_on_a_step, WearableCommand.DOWN),
    WARNING_STEP_PIT(R.string.warning_pit, WearableCommand.DOWN),

    //Vocal Commands
    PAUSE(R.string.pause),
    RESUME(R.string.resume),
    TTS_ENABLED(R.string.tts_enabled),
    TTS_DISABLED(R.string.tts_disabled),
    VIBRATION_ENABLED(R.string.vibration_enabled),
    VIBRATION_DISABLED(R.string.vibration_disabled),
    WATCH_VIBRATION_ENABLED(R.string.watch_vibration_enabled),
    WATCH_VIBRATION_DISABLED(R.string.watch_vibration_disabled),
}

enum class WearableCommand {
    SX, DX, UP, DOWN, DANGEROUS, GENERICS
}