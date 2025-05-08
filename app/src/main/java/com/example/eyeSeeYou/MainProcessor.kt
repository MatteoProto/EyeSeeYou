package com.example.eyeSeeYou

import android.media.Image
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException

class MainProcessor {
    fun processFrame(
        frame: Frame,
        semanticImage: Image?,
        depthImage: Image?
    ) {
        try {
            val semantic = semanticImage ?: frame.acquireSemanticImage()
            val depth = depthImage ?: frame.acquireDepthImage16Bits()
            semantic.close()
            depth.close()
        } catch (e: NotYetAvailableException) {
            // Le immagini non sono ancora pronte: normale
        }
    }

    private fun findObstacles(image: Image) {
        // algoritmo obstacle detection
    }
}