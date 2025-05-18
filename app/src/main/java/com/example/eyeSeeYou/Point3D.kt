package com.example.eyeSeeYou

import com.google.ar.core.Pose

/**
Classe usata per salvare le coordinate dei punti nel mondo reale (nella depth map) e la Pose di quel punto
 */
class Point3D (
    var x: Float,
    var y: Float,
    var z: Float,
    var pose: Pose
)