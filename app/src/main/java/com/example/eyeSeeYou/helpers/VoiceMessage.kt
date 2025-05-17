package com.example.eyeSeeYou.helpers

import androidx.annotation.StringRes
import com.example.eyeSeeYou.R

enum class VoiceMessage(@StringRes val resId: Int) {
    WELCOME(R.string.welcome),
    WARNING_STEPUP(R.string.warning_stepup),
    WARNING_STEPDOWN(R.string.warning_stepdown),
    WARNING(R.string.warning),
    PAUSE(R.string.pause),
    RESUME(R.string.resume),
    TTS_ENABLED(R.string.tts_enabled),
    TTS_DISABLED(R.string.tts_disabled),
    VIBRATION_ENABLED(R.string.vibration_enabled),
    VIBRATION_DISABLED(R.string.vibration_disabled),
    WARNING_OBSTACLE(R.string.warning_obstacle),
    WARNING_OBSTACLE_LEFT(R.string.warning_obstacle_left),
    WARNING_OBSTACLE_RIGHT(R.string.warning_obstacle_right),
    WARNING_OBSTACLE_CENTER(R.string.warning_obstacle_center),
    WARNING_OBSTACLE_LOW(R.string.warning_obstacle_low),
    WARNING_OBSTACLE_HIGH(R.string.warning_obstacle_high),
    WARNING_OBSTACLE_LEFT_BIG(R.string.warning_obstacle_left_big),
    WARNING_OBSTACLE_RIGHT_BIG(R.string.warning_obstacle_right_big),
    WARNING_OBSTACLE_NARROW(R.string.warning_obstacle_narrow),
    WARNING_OBSTACLE_LEFT_HUGE(R.string.warning_obstacle_left_huge),
    WARNING_OBSTACLE_RIGHT_HUGE(R.string.warning_obstacle_right_huge),
    WARNING_OBSTACLE_STOP(R.string.warning_obstacle_stop)
}