package com.example.eyeSeeYou.managers

import android.Manifest
import android.view.MotionEvent
import androidx.annotation.RequiresPermission
import com.example.eyeSeeYou.MainActivity

class TouchManager {
    private var y1 = 0f
    private var y2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    val SWIPE_THRESHOLD = 150

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun dispatchTouchEvent(event: MotionEvent,context: MainActivity): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                x1 = event.x
                y1 = event.y
            }
            MotionEvent.ACTION_UP -> {
                x2 = event.x
                y2 = event.y

                val deltaX = x2 - x1
                val deltaY = y2 - y1

                if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                    if (deltaX > SWIPE_THRESHOLD) {
                        context.toggleARCore()
                    } else if (deltaX < -SWIPE_THRESHOLD) {
                        context.togglePhoneVibration()
                    }
                } else {
                    if (deltaY > SWIPE_THRESHOLD) {
                        context.toggleWatchVibration()
                    } else if (deltaY < -SWIPE_THRESHOLD) {
                        context.toggleTTS()
                    }
                }
            }
        }
        return dispatchTouchEvent(event,context)
    }
}