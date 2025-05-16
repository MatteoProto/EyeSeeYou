package com.example.eyeSeeYou

import android.media.Image
import com.example.eyeSeeYou.MainProcessor.PointData
import com.example.eyeSeeYou.helpers.Zones
import com.google.ar.core.SemanticLabel
import java.nio.ByteOrder

class Identifier {

    fun identify(semantic: Image, depth: Image): MutableMap<SemanticLabel, Set<Zones>>{

        val map = assignLabeledPoints(semantic, depth)
        val nearbyObjects = getNearbyLabels(map, depth.height, depth.width)
        val labelsPosition = computeZonesForLabels(nearbyObjects, map, depth.height, depth.width)

        return labelsPosition
    }

    private fun findLabel(byte: Byte): SemanticLabel {
        return when (byte.toInt()) {
            1 -> SemanticLabel.SKY
            2 -> SemanticLabel.BUILDING
            3 -> SemanticLabel.TREE
            4 -> SemanticLabel.ROAD
            5 -> SemanticLabel.SIDEWALK
            6 -> SemanticLabel.TERRAIN
            7 -> SemanticLabel.STRUCTURE
            8 -> SemanticLabel.OBJECT
            9 -> SemanticLabel.VEHICLE
            10 -> SemanticLabel.PERSON
            11 -> SemanticLabel.WATER
            12 -> SemanticLabel.UNLABELED
            else -> SemanticLabel.SKY
        }
    }

    private fun assignLabeledPoints(
        semanticImage: Image,
        depthImage: Image,
        maxDistanceMeters: Float = 3.0f
    ): Map<SemanticLabel, MutableList<PointData>> {
        val labeledPoints = mutableMapOf<SemanticLabel, MutableList<PointData>>()

        try {
            val semanticWidth = semanticImage.width
            val semanticHeight = semanticImage.height
            val depthWidth = depthImage.width
            val depthHeight = depthImage.height
            val semanticBuffer = semanticImage.planes[0].buffer
            val semanticBytes = ByteArray(semanticBuffer.remaining())
            semanticBuffer.get(semanticBytes)
            val depthBuffer =
                depthImage.planes[0].buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            for (y in 0 until depthHeight) {
                for (x in 0 until depthWidth) {
                    val index = y * depthWidth + x

                    val semanticX = (x * semanticWidth) / depthWidth
                    val semanticY = (y * semanticHeight) / depthHeight
                    val semanticIndex = semanticY * semanticWidth + semanticX

                    val label = findLabel(semanticBytes[semanticIndex])
                    if (label == SemanticLabel.SKY || label == SemanticLabel.ROAD || label == SemanticLabel.SIDEWALK || label == SemanticLabel.TERRAIN || label == SemanticLabel.WATER) continue

                    val depthRaw = depthBuffer.get(index).toInt() and 0xFFFF
                    val depthMeters = depthRaw / 1000.0f

                    if (depthMeters in 0.0f..maxDistanceMeters) {
                        //Log.d("$label", "(width: $depthWidth, height: $depthHeight) (width: $semanticWidth, height: $semanticHeight) (x: $x, y: $y)")
                        labeledPoints.getOrPut(label) { mutableListOf() }
                            .add(PointData(x, y, depthMeters))
                    }
                }
            }
        } catch (_: Exception) {
        }

        return labeledPoints
    }

    private fun getNearbyLabels(
        labeledPoints: Map<SemanticLabel, List<PointData>>,
        height: Int,
        width: Int,
        maxDistanceMeters: Float = 1.0f
    ): Set<SemanticLabel> {

        val nearbyLabels = mutableSetOf<SemanticLabel>()

        val c = height / 2

        val high = width * 1 / 8
        val leftMedium = c - width * 7 / 80
        val rightMedium = c + width * 7 / 80


        for ((label, points) in labeledPoints) {
            val isClose = points.any { point ->
                point.y in leftMedium..rightMedium &&
                        point.x >= high &&
                        point.depth < maxDistanceMeters
            }

            if (isClose) {
                nearbyLabels.add(label)
            }
        }

        return nearbyLabels
    }



    private fun computeZonesForLabels(
        nearObjects: Set<SemanticLabel>,
        labeledPoints: Map<SemanticLabel, List<PointData>>,
        height: Int,
        width: Int
    ): MutableMap<SemanticLabel, Set<Zones>> {

        val zoneMap = mutableMapOf<SemanticLabel, Set<Zones>>()
        val c = height / 2

        val low = width * 11 / 16
        val medium = width * 1 / 4
        val high = width * 1 / 8

        val rightCenter = c - width / 16
        val rightExtreme = c - width * 3 / 16
        val rightMedium = c - width * 7 / 80

        val leftCenter = c + width / 16
        val leftMedium = c + width * 7 / 80
        val leftExtreme = c + width * 3 / 16

        for (label in nearObjects) {
            val points = labeledPoints[label] ?: continue
            val zones = mutableSetOf<Zones>()

            var hasHigh = false
            var hasLow = false
            var hasCenter = false

            var hasLeft = false
            var hasRight = false
            var hasWallLeft = false
            var hasWallRight = false

            var hasLeftCenter = false
            var hasRightCenter = false
            var hasLeftLow = false
            var hasRightLow = false

            for ((x, y, _) in points) {
                // Short-circuit if all zones found
                if (zones.size == Zones.entries.size) break

                when {
                    // Central vertical positioning
                    y in rightCenter..leftCenter && x in (medium..low) -> {
                        if (!hasCenter) {
                            zones.add(Zones.CENTER)
                            hasCenter = true
                        }
                    }

                    y in rightCenter..leftCenter && x >= low -> {
                        if (!hasLow) {
                            hasLow = true
                        }
                    }

                    y in rightCenter..leftCenter && x in (high..medium) -> {
                        if (!hasHigh) {
                            hasHigh = true
                        }
                    }

                    // Horizontal positioning
                    y in rightExtreme..rightMedium && x in (medium..low) -> {
                        if (!hasRight) {
                            zones.add(Zones.RIGHT)
                            hasRight = true
                        }
                    }

                    y in leftMedium..leftExtreme && x in (medium..low) -> {
                        if (!hasLeft) {
                            zones.add(Zones.LEFT)
                            hasLeft = true
                        }
                    }

                    // Wall extremes
                    y < rightExtreme -> {
                        if (!hasWallLeft) {
                            zones.add(Zones.LEFT_WALL)
                            hasWallLeft = true
                        }
                    }

                    y > leftExtreme -> {
                        if (!hasWallRight) {
                            zones.add(Zones.RIGHT_WALL)
                            hasWallRight = true
                        }
                    }

                    // Transitional areas
                    y in (rightMedium..rightCenter) && x in (medium..low) -> {
                        hasLeftCenter = true
                    }

                    y in (leftCenter..leftMedium) && x in (medium..low) -> {
                        hasRightCenter = true
                    }

                    y in (rightExtreme..rightMedium) && x >= low -> {
                        hasLeftLow = true
                    }

                    y in (leftMedium..leftExtreme) && x >= low -> {
                        hasRightLow = true
                    }
                }
            }

            if (hasLeftCenter && !hasLeft && !hasCenter) zones.add(Zones.CENTER)
            if (hasRightCenter && !hasRight && !hasCenter) zones.add(Zones.CENTER)
            if (hasLeftLow && !hasLeft && !hasLow) zones.add(Zones.LOW)
            if (hasRightLow && !hasRight && !hasLow) zones.add(Zones.LOW)
            if (hasHigh && !hasCenter) zones.add(Zones.HIGH)
            if (hasLow && !hasCenter) zones.add(Zones.LOW)

            zoneMap[label] = zones
        }

        return zoneMap
    }
}