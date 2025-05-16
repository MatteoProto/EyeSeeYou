package com.example.eyeSeeYou

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

@Composable
fun MainScreen(vocalAssistant: VocalAssistant) {
    var isTTSEnabled by remember { mutableStateOf(true) }
    var isVibrationEnabled by remember { mutableStateOf(true) }
    var startY by remember { mutableStateOf(0f) }


    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeightPx = androidx.compose.ui.platform.LocalDensity.current.run {
        configuration.screenHeightDp.dp.toPx()
    }
    val topAreaLimit = screenHeightPx * 0.15f
    val bottomAreaLimit = screenHeightPx * 0.85f
    val context = LocalContext.current

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color(0xFF121212)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> startY = offset.y },
                        onVerticalDrag = { change, dragAmount ->
                            if (startY <= topAreaLimit && dragAmount > 50) {
                                isVibrationEnabled = !isVibrationEnabled
                                val message = if (isVibrationEnabled) {
                                    context.getString(R.string.vibration_enabled)
                                } else {
                                    context.getString(R.string.vibration_disabled)
                                }
                                vocalAssistant.speak(message, force = true)
                                change.consume()
                            } else if (startY >= bottomAreaLimit && dragAmount < -50) {
                                isTTSEnabled = !isTTSEnabled
                                val message = if (isTTSEnabled) {
                                    context.getString(R.string.tts_enabled)
                                } else {
                                    context.getString(R.string.tts_disabled)
                                }
                                vocalAssistant.speak(message, force = true)
                                change.consume()
                            }
                        }
                    )
                }
        ) {

        }
    }
}