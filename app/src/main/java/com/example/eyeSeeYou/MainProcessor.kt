package com.example.eyeSeeYou

import android.media.Image
import android.util.Log
import com.example.eyeSeeYou.helpers.VoiceMessage
import com.example.eyeSeeYou.helpers.Zones
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.exceptions.NotYetAvailableException

class MainProcessor {

    data class PointData(val x: Int, val y: Int, val depth: Float)

    private val identifier: Identifier = Identifier()

    private val history: ArrayDeque<Map<SemanticLabel, Set<Zones>>> = ArrayDeque()

    fun processFrame(frame: Frame, semanticImage: Image?, depthImage: Image?): VoiceMessage? {
        try {
            val start = System.currentTimeMillis()
            val semantic = semanticImage ?: frame.acquireSemanticImage()
            val depth = depthImage ?: frame.acquireDepthImage16Bits()

            /** Compute obstacles */
            val labelsPosition = identifier.identify(semantic, depth)
            if (history.size >= 3) {
                history.removeFirst()
            }
            history.addLast(labelsPosition)
            val (_,labelMap) = identifier.computeStableZoneLabels(history)

            /** Compute steps */
            //Parte di leo

            semantic.close()
            depth.close()

            val output = computeIndication(labelMap)
            Log.d("TIME", "${System.currentTimeMillis() - start}")

            return output
        } catch (e: NotYetAvailableException) {
            // Images still not ready
            return null
        }
    }

    private fun computeIndication(map: Map<Zones, Boolean>): VoiceMessage? {
        val left = map[Zones.LEFT] == true
        val right = map[Zones.RIGHT] == true
        val center = map[Zones.CENTER] == true
        val high = map[Zones.HIGH] == true
        val low = map[Zones.LOW] == true
        val leftWall = map[Zones.LEFT_WALL] == true
        val rightWall = map[Zones.RIGHT_WALL] == true

        return when {
            left && right && center && leftWall && rightWall -> VoiceMessage.WARNING_OBSTACLE_STOP
            left && right && high && leftWall && rightWall -> VoiceMessage.WARNING_OBSTACLE_STOP
            left && right && low && leftWall && rightWall -> VoiceMessage.WARNING_OBSTACLE_STOP

            left && right && center && leftWall -> VoiceMessage.WARNING_OBSTACLE_LEFT_HUGE
            left && right && high && leftWall -> VoiceMessage.WARNING_OBSTACLE_LEFT_HUGE
            left && right && low && leftWall -> VoiceMessage.WARNING_OBSTACLE_LEFT_HUGE

            left && right && center && rightWall -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE
            left && right && high && rightWall -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE
            left && right && low && rightWall -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE

            left && right && center -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE
            left && right && high -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE
            left && right && low -> VoiceMessage.WARNING_OBSTACLE_RIGHT_HUGE

            center && right -> VoiceMessage.WARNING_OBSTACLE_RIGHT_BIG
            high && right -> VoiceMessage.WARNING_OBSTACLE_RIGHT_BIG
            low && right -> VoiceMessage.WARNING_OBSTACLE_RIGHT_BIG

            center && left -> VoiceMessage.WARNING_OBSTACLE_LEFT_BIG
            high && left -> VoiceMessage.WARNING_OBSTACLE_LEFT_BIG
            low && left -> VoiceMessage.WARNING_OBSTACLE_LEFT_BIG

            left && right -> VoiceMessage.WARNING_OBSTACLE_NARROW

            center -> VoiceMessage.WARNING_OBSTACLE_CENTER
            right -> VoiceMessage.WARNING_OBSTACLE_RIGHT
            left -> VoiceMessage.WARNING_OBSTACLE_LEFT
            high -> VoiceMessage.WARNING_OBSTACLE_HIGH
            low -> VoiceMessage.WARNING_OBSTACLE_LOW

            /** Mancano i casi degli scalini */

            else -> null
        }
    }
}