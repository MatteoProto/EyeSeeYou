package com.example.eyeSeeYou.managers

import android.media.Image
import android.util.Log
import com.example.eyeSeeYou.helpers.Point2D
import com.example.eyeSeeYou.helpers.Point3D
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.exceptions.NotYetAvailableException
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.use

private const val MIN_EXPECTED_STEP_RISE = 0.18f        // Minima alzata/discesa attesa (0cm)
private const val MAX_EXPECTED_STEP_RISE = 0.30f       // Massima alzata/discesa attesa (30cm)
private const val MIN_EXPECTED_STEP_ADVANCE_XZ = 0.10f // Minimo avanzamento nel piano XZ (0cm)
private const val MAX_EXPECTED_STEP_ADVANCE_XZ = 0.45f // Massimo avanzamento nel piano XZ (45cm)
private const val MAX_ALLOWED_LATERAL_OFFSET = 0.30f   // Massima deviazione laterale permessa (30cm)
private const val MIN_HEIGHT_DIFFERENCE = 0.22f        // Minima differenza di altezza per uno scalino
private const val MAX_HEIGHT_DIFFERENCE = 0.35f        // Massima differenza di altezza per uno scalino
private const val MAX_DEPTH_DIFFERENCE = 0.50f         // Massima differenza di prodondità per uno scalino

class StepDetector {
    private var lastCameraPose: Pose? = null    // Ultima inquadratura della fotocamera
    private val activeStepDetected = mutableListOf<Pose>() // Scalini rilevati stessa scala
    private val allActiveStepDetected = mutableListOf<Pose>() // Tutti gli scalini rilevati ancora non raggiunti
    //--------------------------------STEP PROCESSING--------------------------------
    fun processStep(frame: Frame, screenGridPoints: Map<String, Point2D>): FloatArray {
        var results = FloatArray(4)
        /*
        results[0] = dislivello
        results[1] = distanza dal dislivello
        results[2] = distanza dal gradino più vicino
        results[3] = scalino si/no/buca (1/0/-1)
         */
        results[0] = 0f
        results[1] = 0f
        results[2] = 1f
        results[3] = 0f
        var depthPoints = mutableMapOf<String, Point3D>()

        var cameraMoved = false

        if(screenGridPoints.isEmpty()) {    // Non sono ancora state calcolate le coordinate dei punti sullo schermo
            return results
        }

        val cameraPose = frame.camera.pose  // Prendo la camera Pose corrente

        if (hasCameraMoved(cameraPose)) {   // Controllo se l'inquadratura è cambiata
            // Inquadratura cambiata
            lastCameraPose = cameraPose
            cameraMoved = true
        }else{
            cameraMoved = false
        }

        for (activePose in allActiveStepDetected) {    // Controllo se siamo sopra/molto vicino a uno scalino
            val distance = getPoseDistance(cameraPose, activePose)
            if (distance < 0.3f) {  // Siamo a meno di 30cm dallo scalino
                results[2] = distance   // ritorno la distanza dallo scalino più vicino
                allActiveStepDetected.remove(activePose) // rimuovo la Pose perché suppongo che lo scalino venga percorso
                return results
                //break
            }
        }

        try {
            frame.acquireRawDepthImage16Bits().use { depthImage -> // Use the depth image
                if (depthImage == null) {
                    Log.w("Step", "Immagine di profondità non disponibile per questo frame.")
                    return results
                }
                // Calcolo le coordinate dei punti nel mondo reale
                depthPoints = getPoints(frame, screenGridPoints, depthImage)
                //Log.d("Test", "DIPPoints: $depthPoints")
            }
        } catch (e: NotYetAvailableException) {
            Log.w("ARCoreVision", "Immagine di profondità non ancora disponibile.");
        } catch (e: Exception) { // Gestisce altre eccezioni potenziali (es. BufferUnderflow)
            Log.e("ARCoreVision", "Errore durante l'elaborazione della profondità: " + e.message);
        }
        //Log.i("Points", "PUNTOOOO: x=${punto?.x} y=${punto?.y} z=${punto?.z}")

        // Differenza "y" tra 1 riga e 3 riga
        var diff_col_1 = getHeightDifference(depthPoints["point11"], depthPoints["point31"])
        var diff_col_2 = getHeightDifference(depthPoints["point12"], depthPoints["point32"])
        var diff_col_3 = getHeightDifference(depthPoints["point13"], depthPoints["point33"])
        var diff_col_4 = getHeightDifference(depthPoints["point14"], depthPoints["point34"])
        var height_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

        // Differenza "z" tra 1 riga e 3 riga
        diff_col_1 = getDepthDifference(depthPoints["point11"], depthPoints["point31"])
        diff_col_2 = getDepthDifference(depthPoints["point12"], depthPoints["point32"])
        diff_col_3 = getDepthDifference(depthPoints["point13"], depthPoints["point33"])
        diff_col_4 = getDepthDifference(depthPoints["point14"], depthPoints["point34"])
        var depth_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

        var distance = getStepDistance(depthPoints["point31"], depthPoints["point32"], depthPoints["point33"], depthPoints["point34"])

        if( abs(height_diff) >= MAX_HEIGHT_DIFFERENCE && depth_diff >= MAX_DEPTH_DIFFERENCE && distance <= 120 ){
            // Dislivello >= 40cm, prodondità >= 40cm e distanza <= 120 (abbastanza vicina per capire che è una buca) = BUCA
            results[0] = height_diff
            results[1] = distance
            results[2] = 1f
            results[3] = -1f
            return results
        }else if(abs(height_diff) >= MIN_HEIGHT_DIFFERENCE && depth_diff <= MAX_DEPTH_DIFFERENCE){
            // Dislivello tra 15cm e 40cm rilevato, profondità <= 40 = SCALINO
            depthPoints["point32"]?.pose?.let { pose ->

                val belongsToCurrentStair = belongToExistingStair(pose, activeStepDetected.lastOrNull())
                distance = getStepDistance(depthPoints["point31"], depthPoints["point32"], depthPoints["point33"], depthPoints["point34"])
                if( cameraMoved ){
                    // Log.d("POSE_LOGIC", "Camera moved significantly. Starting new staircase context.")
                    activeStepDetected.clear() // Inizia una nuova lista per la nuova scala/contesto
                    activeStepDetected.add(pose)
                    allActiveStepDetected.add(pose)
                    // Prepara e ritorna i risultati
                    results[0] = height_diff
                    results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                    results[3] = 1f
                    Log.d("POSE_LOGIC","Nuova scala (camera mossa). Aggiunto: ${pose}")
                    return results
                }else if (!belongsToCurrentStair){
                    // La camera non si è mossa molto, ma questo gradino non segue l'ultimo.
                    // Potrebbe essere il primo gradino in assoluto, o un gradino che devia dalla sequenza.
                    // Se devia, potresti voler iniziare una nuova scala.
                    if (activeStepDetected.isNotEmpty()) {
                        // Log.d("POSE_LOGIC", "Gradino non continua la sequenza. Potrebbe essere una nuova scala vicina. Ricomincio activeStepDetected.")
                        activeStepDetected.clear() // Opzionale: decidi se resettare o meno in questo caso
                    }
                    activeStepDetected.add(pose)
                    allActiveStepDetected.add(pose)
                    // Prepara e ritorna i risultati
                    results[0] = height_diff
                    results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                    results[3] = 1f
                    Log.d("POSE_LOGIC","Nuovo gradino (non in sequenza o primo). Aggiunto: ${pose}")
                    return results
                }
            }

        }else{  // Differenza "y" tra 2 riga e 3 riga (più vicino alla camera)
            diff_col_1 = getHeightDifference(depthPoints["point21"], depthPoints["point31"])
            diff_col_2 = getHeightDifference(depthPoints["point22"], depthPoints["point32"])
            diff_col_3 = getHeightDifference(depthPoints["point23"], depthPoints["point33"])
            diff_col_3 = getHeightDifference(depthPoints["point24"], depthPoints["point34"])
            height_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

            // Differenza "z" tra 2 riga e 3 riga
            diff_col_1 = getDepthDifference(depthPoints["point21"], depthPoints["point31"])
            diff_col_2 = getDepthDifference(depthPoints["point22"], depthPoints["point32"])
            diff_col_3 = getDepthDifference(depthPoints["point23"], depthPoints["point33"])
            diff_col_4 = getDepthDifference(depthPoints["point24"], depthPoints["point34"])
            depth_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

            if( abs(height_diff) <= MAX_HEIGHT_DIFFERENCE && abs(depth_diff) >= MAX_DEPTH_DIFFERENCE && distance <= 120 ){
                // Dislivello >= 40cm, prodondità >= 40cm e distanza <= 120 (abbastanza vicina per capire che è una buca)
                //muro o ostacolo grosso
                results[0] = height_diff
                results[1] = distance
                results[2] = 1f
                results[3] = -1f
                return results
            }else if(abs(height_diff) >= MIN_HEIGHT_DIFFERENCE && abs(depth_diff) <= MAX_DEPTH_DIFFERENCE){    // Dislivello di 15 cm rilevato

                // se è stata cambiata l'inquadratura o non appartiene a una scala già rilevata
                // salvo la Pose del punto centrale più vicino (32 in questo caso)
                depthPoints["point32"]?.pose?.let { pose ->
                    val belongsToCurrentStair = belongToExistingStair(pose, activeStepDetected.lastOrNull())
                    distance = getStepDistance(depthPoints["point31"], depthPoints["point32"], depthPoints["point33"], depthPoints["point34"])
                    if( cameraMoved ){
                        // Log.d("POSE_LOGIC", "Camera moved significantly. Starting new staircase context.")
                        activeStepDetected.clear() // Inizia una nuova lista per la nuova scala/contesto
                        activeStepDetected.add(pose)
                        allActiveStepDetected.add(pose)
                        // Prepara e ritorna i risultati
                        results[0] = height_diff
                        results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                        results[3] = 1f
                        Log.d("POSE_LOGIC","Nuova scala (camera mossa). Aggiunto: ${pose}")
                        return results
                    }else if (!belongsToCurrentStair){
                        // La camera non si è mossa molto, ma questo gradino non segue l'ultimo.
                        // Potrebbe essere il primo gradino in assoluto, o un gradino che devia dalla sequenza.
                        // Se devia, potresti voler iniziare una nuova scala.
                        if (activeStepDetected.isNotEmpty()) {
                            // Log.d("POSE_LOGIC", "Gradino non continua la sequenza. Potrebbe essere una nuova scala vicina. Ricomincio activeStepDetected.")
                            activeStepDetected.clear() // Opzionale: decidi se resettare o meno in questo caso
                        }
                        activeStepDetected.add(pose)
                        allActiveStepDetected.add(pose)
                        // Prepara e ritorna i risultati
                        results[0] = height_diff
                        results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                        results[3] = 1f
                        Log.d("POSE_LOGIC","Nuovo gradino (non in sequenza o primo). Aggiunto: ${pose}")
                        return results
                    }
                }
            }else{  // Differenza "y" tra 1 riga e 2 riga (più lontano dalla camera)
                diff_col_1 = getHeightDifference(depthPoints["point11"], depthPoints["point21"])
                diff_col_2 = getHeightDifference(depthPoints["point12"], depthPoints["point22"])
                diff_col_3 = getHeightDifference(depthPoints["point13"], depthPoints["point23"])
                diff_col_3 = getHeightDifference(depthPoints["point14"], depthPoints["point24"])
                height_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

                // Differenza "z" tra 1 riga e 2 riga
                diff_col_1 = getDepthDifference(depthPoints["point11"], depthPoints["point21"])
                diff_col_2 = getDepthDifference(depthPoints["point12"], depthPoints["point22"])
                diff_col_3 = getDepthDifference(depthPoints["point13"], depthPoints["point23"])
                diff_col_4 = getDepthDifference(depthPoints["point14"], depthPoints["point24"])
                depth_diff = (diff_col_1 + diff_col_2 + diff_col_3 + diff_col_4)/4

                if( abs(height_diff) >= MAX_HEIGHT_DIFFERENCE && depth_diff >= MAX_DEPTH_DIFFERENCE && distance <= 120 ){
                    // Dislivello >= 40cm, prodondità >= 40cm e distanza <= 120 (abbastanza vicina per capire che è una buca)
                    //muro o ostacolo grosso
                    results[0] = height_diff
                    results[1] = distance
                    results[2] = 1f
                    results[3] = -1f
                    return results
                }else if(abs(height_diff) >= MIN_HEIGHT_DIFFERENCE && depth_diff <= MAX_DEPTH_DIFFERENCE) {   // Dislivello di 15 cm rilevato

                    // se è stata cambiata l'inquadratura o non appartiene a una scala già rilevata
                    // salvo la Pose del punto centrale più vicino (22 in questo caso)
                    depthPoints["point22"]?.pose?.let { pose ->
                        val belongsToCurrentStair = belongToExistingStair(pose, activeStepDetected.lastOrNull())
                        distance = getStepDistance(depthPoints["point21"], depthPoints["point22"], depthPoints["point23"], depthPoints["point24"])
                        if( cameraMoved ){
                            // Log.d("POSE_LOGIC", "Camera moved significantly. Starting new staircase context.")
                            activeStepDetected.clear() // Inizia una nuova lista per la nuova scala/contesto
                            activeStepDetected.add(pose)
                            allActiveStepDetected.add(pose)
                            // Prepara e ritorna i risultati
                            results[0] = height_diff
                            results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                            results[3] = 1f
                            Log.d("POSE_LOGIC","Nuova scala (camera mossa). Aggiunto: ${pose}")
                            return results
                        }else if (!belongsToCurrentStair){
                            // La camera non si è mossa molto, ma questo gradino non segue l'ultimo.
                            // Potrebbe essere il primo gradino in assoluto, o un gradino che devia dalla sequenza.
                            // Se devia, potresti voler iniziare una nuova scala.
                            if (activeStepDetected.isNotEmpty()) {
                                // Log.d("POSE_LOGIC", "Gradino non continua la sequenza. Potrebbe essere una nuova scala vicina. Ricomincio activeStepDetected.")
                                activeStepDetected.clear() // Opzionale: decidi se resettare o meno in questo caso
                            }
                            activeStepDetected.add(pose)
                            allActiveStepDetected.add(pose)
                            // Prepara e ritorna i risultati
                            results[0] = height_diff
                            results[1] = distance // Assicurati che distance sia calcolata correttamente qui
                            results[3] = 1f
                            Log.d("POSE_LOGIC","Nuovo gradino (non in sequenza o primo). Aggiunto: ${pose}")
                            return results
                        }
                    }
                }
            }
        }
        return results
    }

    private fun getPoseDistance(p1: Pose, p2: Pose): Float {
        val dx = p1.tx() - p2.tx()
        val dy = (p1.ty()-1f) - p2.ty() // -1 metro di altezza smartphone
        val dz = p1.tz() - p2.tz()

        val distance = sqrt(dx * dx + dy * dy + dz * dz)    // distanza euclidea

        return distance
    }

    private fun belongToExistingStair(newStepPose: Pose, lastStepInStaircase: Pose?): Boolean {
        if (lastStepInStaircase == null) {
            // Non c'è un gradino precedente in questa sequenza, quindi non può essere "il successivo".
            // Potrebbe essere il primo di una nuova scala.
            return false
        }

        val dx = newStepPose.tx() - lastStepInStaircase.tx() // Diff. laterale
        val dy = newStepPose.ty() - lastStepInStaircase.ty() // Diff. altezza (positiva = newStep più alto)
        val dz = newStepPose.tz() - lastStepInStaircase.tz() // Diff. profondità (positiva = newStep più lontano)

        val absDy = abs(dy)
        val advanceXZ = sqrt(dx * dx + dz * dz) // Distanza percorsa nel piano orizzontale (XZ)

        Log.d("test","Altezza:$absDy, $advanceXZ, $dx")

        // 1. L'altezza è quella di un tipico gradino (sia in salita che in discesa)?
        val isHeightCorrect = absDy >= MIN_EXPECTED_STEP_RISE && absDy <= MAX_EXPECTED_STEP_RISE
        if (!isHeightCorrect) {
            Log.d("StepCheck", "Fallito: Altezza non corretta. dy: $dy")
            return false
        }

        // 2. L'avanzamento nel piano XZ è quello di una tipica pedata?
        val isAdvanceCorrect = advanceXZ >= MIN_EXPECTED_STEP_ADVANCE_XZ && advanceXZ <= MAX_EXPECTED_STEP_ADVANCE_XZ
        if (!isAdvanceCorrect) {
            Log.d("StepCheck", "Fallito: Avanzamento XZ non corretto. advanceXZ: $advanceXZ")
            return false
        }

        // 3. Il gradino non è troppo disassato lateralmente rispetto al precedente?
        //    (abs(dx) qui è una semplificazione; una metrica migliore userebbe la direzione della scala)
        if (abs(dx) > MAX_ALLOWED_LATERAL_OFFSET) {
            Log.d("StepCheck", "Fallito: Deviazione laterale eccessiva. dx: $dx")
            return false
        }

        // Potresti aggiungere un controllo sull'orientamento se le Pose sono affidabili.
        // Ad esempio, le normali dei gradini dovrebbero essere simili.

        // Se tutti i controlli passano, è un plausibile gradino successivo
        return true
    }

    fun hasCameraMoved(currentPose: Pose): Boolean {
        lastCameraPose?.let { last ->
            val dx = currentPose.tx() - last.tx()
            val dy = currentPose.ty() - last.ty()
            val dz = currentPose.tz() - last.tz()

            val distanceMoved = sqrt(dx * dx + dy * dy + dz * dz)
            return distanceMoved > 0.4f  // 20 cm soglia di movimento
        }
        return true  // Se non c'è un valore precedente, supponi che si sia mosso
    }

    fun getHeightDifference(p1: Point3D?, p2: Point3D?): Float {
        return if (p1 != null && p2 != null) {
            p1.y - p2.y
        } else {
            0f // oppure 0f se preferisci restituire uno zero
        }
    }

    fun getDepthDifference(p1: Point3D?, p2: Point3D?): Float {
        return if (p1 != null && p2 != null) {
            p1.z - p2.z
        } else {
            0f // oppure 0f se preferisci restituire uno zero
        }
    }

    fun getStepDistance(p1: Point3D?, p2: Point3D?, p3: Point3D?, p4: Point3D?): Float{
        return if (p1 != null && p2 != null && p3 != null && p4 != null) {
            ((p1.z + p2.z + p3.z + p4.z)/4)
        } else {
            0f
        }
    }

    /**
    1.  Prende le coordinate dei punti nella griglia sullo schermo
    2.  Calcola i relativi punti sulla depth image
    INPUT: frame, lista point sullo schermo (x, y)
    OUTPUT: lista point mondo reale (x, y, z)
     */
    fun getPoints(frame: Frame, screenGridPoints: Map<String, Point2D>, depthImage: Image): MutableMap<String, Point3D> {
        val points3D = mutableMapOf<String, Point3D>()

        val depthPlane = depthImage.planes[0]

        for ((id, gridPoint) in screenGridPoints) {
            val hitResults = frame.hitTest(gridPoint.x.toFloat(), gridPoint.y.toFloat())


            val validHits = hitResults
                .filter { it.trackable is Point || it.trackable is DepthPoint }

            // Calcolo la media delle coordinate dei punti rilevati
            if (validHits.isNotEmpty()) {
                var sumX = 0f
                var sumY = 0f
                var sumZ = 0f

                for (hit in validHits) {
                    val pose = hit.hitPose
                    sumX += pose.tx()
                    sumY += pose.ty()
                    sumZ += pose.tz()
                }
                val count = validHits.size
                val avgX = sumX / count
                val avgY = sumY / count
                val avgZ = sumZ / count

                val avgPose = validHits[0].hitPose // Usi uno a caso per compatibilità
                Log.d("RaycastPoint", "id=$id x=${avgX} y=${avgY} z=${avgZ}")

                val newPoint = Point3D(avgX, avgY, avgZ, avgPose)
                points3D.put(id, newPoint)
            }
            /*if ((trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) ||
                    (trackable is Point && trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL) ||
                    trackable is DepthPoint) {
                 */
        }
        return points3D
    }

    //------------------------------------------------------------------------------------------------
}