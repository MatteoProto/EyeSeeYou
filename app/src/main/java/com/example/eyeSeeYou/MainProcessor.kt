package com.example.eyeSeeYou

import android.media.Image
import android.util.Log
import com.example.eyeSeeYou.managers.ObjectDetector
import com.example.eyeSeeYou.managers.StepDetector
import com.example.eyeSeeYou.helpers.Point2D
import com.example.eyeSeeYou.enums.VoiceMessage
import com.example.eyeSeeYou.enums.Zones
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlin.math.abs

class MainProcessor {

    data class PointData(val x: Int, val y: Int, val depth: Float)

    private val objectDetector: ObjectDetector = ObjectDetector()
    private val stepDetector: StepDetector = StepDetector()

    private val history: ArrayDeque<Map<SemanticLabel, Set<Zones>>> = ArrayDeque()

    private val screenGridPoints = mutableMapOf<String, Point2D>()

    private var stepUp = 0
    private var stepDown = 0

    fun setScreenGridPoints(points: Map<String, Point2D>) {
        //synchronized(pointsLock) {
        screenGridPoints.clear()
        screenGridPoints.putAll(points)
        Log.d("MainRenderer", "Ricevute ${points.size} coordinate di punti.")
        //}
    }

    fun processFrame(frame: Frame, semanticImage: Image?, depthImage: Image?): VoiceMessage? {
        try {
            val start = System.currentTimeMillis()
            val semantic = semanticImage ?: frame.acquireSemanticImage()
            val depth = depthImage ?: frame.acquireDepthImage16Bits()

            /** Compute obstacles */
            val labelsPosition = objectDetector.identify(semantic, depth)
            if (history.size >= 3) {
                history.removeFirst()
            }
            history.addLast(labelsPosition)
            val (_,labelMap) = objectDetector.computeStableZoneLabels(history)

            /** Compute steps */
            val res = stepDetector.processStep(frame, screenGridPoints)
            val drop = res[0]       //dislivello
            var distance = res[1] // distanza dal dislivello
            val distanceFromCloserStep = res[2]
            val type = res[3]
            distance = abs(distance)*100

            if( distanceFromCloserStep >= 0f && distanceFromCloserStep < 0.3f ){
                labelMap.put(Zones.ALMOST_ON_STEP, true)
            }
            if( type == -1f ){
                // È stata rilevata una BUCA
                if( distance <= 100 ){ // Prossimità della buca rilevata ora
                    labelMap.put(Zones.PIT, true)
                }
            } else if (type == 1f){
                // È stato rilevato uno SCALINO
                if( drop > 0 ){ // Determina se UP o DOWN usando il dislivello originale
                    stepDown = 0
                    stepUp++
                    if( distance <= 1.2 || stepUp >= 3 ){
                        Log.d("step","GRADINO SU")
                        labelMap.put(Zones.STEP_UP, true)
                        stepUp = 0
                    }
                } else {
                    stepUp = 0
                    stepDown++
                    if( distance <= 1.2 || stepDown >= 3 ){
                        Log.d("step","GRADINO GIU")
                        labelMap.put(Zones.STEP_DOWN, true)
                        stepDown = 0
                    }
                }
            }
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

        val stepUp = map[Zones.STEP_UP] == true
        val stepDown = map[Zones.STEP_DOWN] == true
        val almostOnStep = map[Zones.ALMOST_ON_STEP] == true
        val pit = map[Zones.PIT] == true

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

            almostOnStep -> VoiceMessage.WARNING_ALMOST_ON_STEP
            stepUp -> VoiceMessage.WARNING_STEPUP
            stepDown -> VoiceMessage.WARNING_STEPDOWN
            pit -> VoiceMessage.WARNING_STEP_PIT

            high -> VoiceMessage.WARNING_OBSTACLE_HIGH
            low -> VoiceMessage.WARNING_OBSTACLE_LOW
            else -> null
        }
    }


}