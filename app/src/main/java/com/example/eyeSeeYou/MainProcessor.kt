package com.example.eyeSeeYou

import android.media.Image
import android.util.Log
import com.example.eyeSeeYou.helpers.Zones
import com.google.ar.core.Frame
import com.google.ar.core.SemanticLabel
import com.google.ar.core.exceptions.NotYetAvailableException

class MainProcessor {

    data class PointData(val x: Int, val y: Int, val depth: Float)

    private val identifier: Identifier = Identifier()

    private val history: ArrayDeque<Map<SemanticLabel, Set<Zones>>> = ArrayDeque()

    private lateinit var output: String

    fun processFrame(frame: Frame, semanticImage: Image?, depthImage: Image?): String {
        try {
            val start = System.currentTimeMillis()
            val semantic = semanticImage ?: frame.acquireSemanticImage()
            val depth = depthImage ?: frame.acquireDepthImage16Bits()

            val labelsPosition = identifier.identify(semantic, depth)
            if (history.size >= 5) {
                history.removeFirst()
            }
            history.addLast(labelsPosition)
            val cuteLabels = computeStableLabelPosition(history)

            output = ""
            for ((label, zones) in cuteLabels) {
                output =(output + "  Label: $label → Zones: ${zones.joinToString()} \n")
            }


            ////Log.d("OUTPUT", output)
            Log.d("TIME", "${System.currentTimeMillis() - start}")
            semantic.close()
            depth.close()
            return output
        } catch (e: NotYetAvailableException) {
            // Images still not ready
            return ""
        }
    }

    private fun computeStableLabelPosition(
        history: Collection<Map<SemanticLabel, Set<Zones>>>,
        minOccurrences: Int = 3
    ): MutableMap<SemanticLabel, Set<Zones>> {
        val labelOccurrences = mutableMapOf<SemanticLabel, Int>()
        val labelZones = mutableMapOf<SemanticLabel, MutableSet<Zones>>()

        for (frameMap in history) {
            for ((label, zones) in frameMap) {
                labelOccurrences[label] = labelOccurrences.getOrDefault(label, 0) + 1
                labelZones.getOrPut(label) { mutableSetOf() }.addAll(zones)
            }
        }

        val stableLabelsPosition = mutableMapOf<SemanticLabel, Set<Zones>>()
        for ((label, count) in labelOccurrences) {
            if (count >= minOccurrences) {
                stableLabelsPosition[label] = labelZones[label] ?: emptySet()
            }
        }

        return stableLabelsPosition
    }

    fun getMostRelevantZones(): Set<Zones> {
        val stableLabels = computeStableLabelPosition(history)
        val allZones = mutableSetOf<Zones>()
        for ((_, zones) in stableLabels) {
            allZones.addAll(zones)
        }
        return allZones
    }
}