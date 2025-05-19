package com.example.eyeSeeYou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.eyeSeeYou.helpers.CameraPermissionHelper
import com.example.eyeSeeYou.helpers.DepthSettings
import com.example.eyeSeeYou.helpers.EisSettings
import com.example.eyeSeeYou.helpers.FullScreenHelper
import com.example.eyeSeeYou.helpers.VoiceMessage
import com.example.eyeSeeYou.samplerender.SampleRender
import com.google.android.gms.wearable.Wearable
import com.google.ar.core.CameraConfig.TargetFps
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.examples.java.common.helpers.ARCoreSessionLifecycleHelper
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.util.EnumSet
import java.util.Locale


class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private val VIBRATE_MESSAGE_PATH = "/vibrate_request"
    private val TAG = "PhoneMainActivity"

    // Grid point for step detection------------------------------------------------------
    private val gridPointImageViews = mutableListOf<ImageView>()
    private val screenCoordinates = mutableMapOf<String, Point2D>()
    // Definisci gli ID degli ImageView della griglia
    private val gridPointIds = listOf(
        R.id.point11, R.id.point12, R.id.point13, R.id.point14,
        R.id.point21, R.id.point22, R.id.point23, R.id.point24,
        R.id.point31, R.id.point32, R.id.point33, R.id.point34
    )
    //---------------------------------------------------------------------------------

    // Display info
    var screenWidth: Float = 0f
    var screenHeight: Float = 0f

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var view: MainView
    private lateinit var renderer: MainRenderer
    private lateinit var processor: MainProcessor
    private lateinit var renderRunnable: Runnable
    lateinit var preferencesManager: PreferencesManager
    lateinit var vocalAssistant: VocalAssistant
    lateinit var vibrationManager: VibrationManager
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechIntent: Intent

    val depthSettings = DepthSettings()
    private val eisSettings = EisSettings()

    private var torch = false
    private var torchTimer: Handler? = null

    private val renderHandler = Handler(Looper.getMainLooper())
    private val desiredFps = 30L

    private var y1 = 0f
    private var y2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    val SWIPE_THRESHOLD = 150

    private var isARCoreManuallyPaused = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupARCore()
        setupVoiceCommandListener()

        // Get screen dimension
        screenWidth = view.surfaceView.width.toFloat()
        screenHeight = view.surfaceView.width.toFloat()

        // Trova gli ImageView e aggiungili alla lista
        gridPointIds.forEach { id ->
            findViewById<ImageView>(id)?.let {
                gridPointImageViews.add(it)
            }
        }
        // Ottieni le coordinate dopo che il layout è stato completato.
        // Usiamo il container della griglia per il post, ma qualsiasi vista andrebbe bene.
        val gridContainer = findViewById<View>(R.id.grid_container)
        gridContainer.post {
            getImageViewCoordinates() // Prende le coordinate delle image view e le normalizza
            // Passa una copia della lista per evitare problemi di concorrenza se il renderer la modifica
            processor.setScreenGridPoints(screenCoordinates)
            Log.d("MainActivity", "Coordinate dei punti inviate al processor: $screenCoordinates")
        }

    }

    fun sendMessageToWearables(message: String) {
        // Ottieni i nodi (smartwatch) connessi
        Wearable.getNodeClient(this).connectedNodes.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                val nodes = task.result
                if (nodes.isEmpty()) {
                    Log.w(TAG, "Nessun dispositivo Wear OS connesso trovato.")
                    Toast.makeText(this, "Nessun smartwatch connesso", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                Log.d(TAG, "Nodi trovati: ${nodes.size}")
                val data = message.toByteArray(Charsets.UTF_8)
                // Invia il messaggio a tutti i nodi connessi
                nodes.forEach { node ->
                    Wearable.getMessageClient(this).sendMessage(
                        node.id,               // ID del nodo destinatario
                        VIBRATE_MESSAGE_PATH,  // Path univoco del messaggio
                        data                   // Payload (dati), null in questo caso
                    ).apply {
                        addOnSuccessListener {
                            Log.d(TAG, "Messaggio inviato con successo a ${node.displayName} (${node.id})")
                            Toast.makeText(this@MainActivity, "Richiesta vibrazione inviata", Toast.LENGTH_SHORT).show()
                        }
                        addOnFailureListener { e ->
                            Log.e(TAG, "Errore invio messaggio a ${node.displayName} (${node.id}): ${e.message}")
                            Toast.makeText(this@MainActivity, "Errore invio richiesta", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Log.e(TAG, "Errore nel recuperare i nodi connessi: ${task.exception}")
                Toast.makeText(this, "Errore nel trovare lo smartwatch", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
    Prende le coordinate sullo schermo delle image view
     */
    private fun getImageViewCoordinates() {
        screenCoordinates.clear()
        val locationOnScreen = IntArray(2) // Array per memorizzare le coordinate assolute [x, y]

        gridPointImageViews.forEachIndexed { index, imageView ->
            imageView.getLocationInWindow(locationOnScreen) // Coordinate assolute

            val centerX = locationOnScreen[0]
            val centerY = locationOnScreen[1]

            // Creo il nuovo punto da aggiungere all'array screenCoordinates
            val newPoint = Point2D(centerX, centerY)
            screenCoordinates[resources.getResourceEntryName(imageView.id)] = newPoint
            //screenCoordinates.add(PointF(centerX, centerY))
            Log.d("MainActivity", "Punto ${index + 1} (${resources.getResourceEntryName(imageView.id)}): centro ($centerX, $centerY)")
        }
    }

    fun configureSession(session: Session) {
        applyCameraConfigFilter(session)
        session.configure(
            session.config.apply {
                lightEstimationMode = Config.LightEstimationMode.DISABLED

                focusMode = Config.FocusMode.AUTO

                semanticMode =
                    if (session.isSemanticModeSupported(Config.SemanticMode.ENABLED)) {
                        Config.SemanticMode.ENABLED
                    } else {
                        Config.SemanticMode.DISABLED
                    }
                depthMode =
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        Config.DepthMode.AUTOMATIC
                    } else {
                        Config.DepthMode.DISABLED
                    }
                imageStabilizationMode =
                    if (session.isImageStabilizationModeSupported(Config.ImageStabilizationMode.EIS) && eisSettings.isEisEnabled()) {
                        Config.ImageStabilizationMode.EIS
                    } else {
                        Config.ImageStabilizationMode.OFF
                    }

                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
        )
    }

    private fun configureTorch(session: Session) {
        session.configure(session.config.apply {
            flashMode = if (torch) {
                Config.FlashMode.TORCH
            } else {
                Config.FlashMode.OFF
            }
        })
    }

    override fun onResume() {
        super.onResume()
        speechRecognizer.startListening(speechIntent)
        if (isARCoreManuallyPaused) {
            arCoreSessionHelper.session?.let {
                try {
                    it.pause()
                    Log.d(TAG, "ARCore session re-paused in onResume due to isARCoreManuallyPaused flag.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error re-pausing ARCore session in onResume", e)
                }
            }
            renderHandler.removeCallbacks(renderRunnable)
            view.showArPausedImage(true)
        } else {
            view.showArPausedImage(false)
            if (view.surfaceView.renderMode == GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                Log.d(TAG, "onResume: Starting periodic render requests for non-manually-paused ARCore.")
                renderHandler.post(renderRunnable)
            }
            view.surfaceView.requestRender()
        }

    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Stopping periodic render requests.")
        renderHandler.removeCallbacks(renderRunnable)
    }

    override fun onDestroy() {
        vocalAssistant.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }

    fun setTorch(torchEnabled: Boolean, session: Session) {
        if (torch == torchEnabled && torchEnabled) {
            torchTimer?.removeCallbacksAndMessages(null)
            torchTimer = Handler(Looper.getMainLooper())
            torchTimer?.postDelayed({
                torch = false
                configureTorch(session)
            }, 30_000)
            return
        }

        torch = torchEnabled
        configureTorch(session)

        if (torchEnabled) {
            torchTimer?.removeCallbacksAndMessages(null)
            torchTimer = Handler(Looper.getMainLooper())
            torchTimer?.postDelayed({
                torch = false
                configureTorch(session)
            }, 30_000)
        } else {
            torchTimer?.removeCallbacksAndMessages(null)
        }
    }

    private fun applyCameraConfigFilter(session: Session) {
            val filter = CameraConfigFilter(session)
            filter.targetFps = EnumSet.of(TargetFps.TARGET_FPS_30)
            val cameraConfigList = session.getSupportedCameraConfigs(filter)

            if (cameraConfigList.isNotEmpty()) {
                session.cameraConfig = cameraConfigList[0]
                Log.i(TAG, "Configurazione della camera impostata per target 30 fps.")
            } else {
                Log.w(TAG, "Nessuna configurazione della camera trovata che supporti un target di 30 fps.")
            }
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            // Use toast instead of snack bar here since the activity will exit.
            Toast.makeText(
                this,
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            )
                .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this)
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupVoiceCommandListener()
        }
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    private fun setupARCore() {
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        preferencesManager = PreferencesManager(this)

        if (preferencesManager.getPreferredLanguage().isEmpty()) {
            val systemLocale = Locale.getDefault().language
            preferencesManager.setPreferredLanguage(systemLocale)
        }

        vocalAssistant = VocalAssistant(this, preferencesManager)
        vibrationManager = VibrationManager(this, preferencesManager)

        arCoreSessionHelper.exceptionCallback =
            { exception ->
                val message = when (exception) {
                    is UnavailableUserDeclinedInstallationException -> getString(R.string.error_install_ar)
                    is UnavailableApkTooOldException -> getString(R.string.error_update_arcore)
                    is UnavailableSdkTooOldException -> getString(R.string.error_update_app)
                    is UnavailableDeviceNotCompatibleException -> getString(R.string.error_not_compatible)
                    is CameraNotAvailableException -> getString(R.string.error_camera_unavailable)
                    else -> getString(R.string.error_generic, exception.localizedMessage ?: "Unknown error")
                }

                Log.e(TAG, "ARCore exception", exception)
                view.snackbarHelper.showError(this@MainActivity, message)
            }

        // Session configuration and settings
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)

        // Set up the Hello AR renderer.
        processor = MainProcessor()
        renderer = MainRenderer(this, processor,vocalAssistant)
        lifecycle.addObserver(renderer)

        // Set up Hello AR UI.
        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        // Sets up an example renderer using our HelloARRenderer.
        SampleRender(view.surfaceView, renderer, assets)

        depthSettings.onCreate(this)
        eisSettings.onCreate(this)

        renderRunnable = object : Runnable {
            override fun run() {
                arCoreSessionHelper.session?.let {
                    view.surfaceView.requestRender()
                }
                renderHandler.postDelayed(this, 1000L / desiredFps)
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
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
                        toggleARCore()
                    } else if (deltaX < -SWIPE_THRESHOLD) {
                        togglePhoneVibration()
                    }
                } else {
                    if (deltaY > SWIPE_THRESHOLD) {
                        toggleWatchVibration()
                    } else if (deltaY < -SWIPE_THRESHOLD) {
                        toggleTTS()
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleTTS() {
        val newValue = !preferencesManager.isTTSEnabled()
        preferencesManager.setTTSEnabled(newValue)
        vibrationManager.shortVibration(force = true)

        val type = if (newValue) VoiceMessage.TTS_ENABLED else VoiceMessage.TTS_DISABLED
        vocalAssistant.playMessage(type, force = true)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleWatchVibration() {
        val newValue = !preferencesManager.isWatchVibrationActive()
        preferencesManager.setWatchVibrationActive(newValue)
        vibrationManager.longVibration()

        val type = if (newValue) VoiceMessage.WATCH_VIBRATION_ENABLED else VoiceMessage.WATCH_VIBRATION_DISABLED
        vocalAssistant.playMessage(type, force = true)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun togglePhoneVibration() {
        val newValue = !preferencesManager.isPhoneVibrationEnabled()
        preferencesManager.setPhoneVibrationEnabled(newValue)
        vibrationManager.longVibration()

        val type = if (newValue) VoiceMessage.VIBRATION_ENABLED else VoiceMessage.VIBRATION_DISABLED
        vocalAssistant.playMessage(type, force = true)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleARCore() {
        vibrationManager.shortVibration(force = true)
        val session = arCoreSessionHelper.session

        if (session == null) {
            Log.w(TAG, "ARCore session is null, cannot toggle.")
            vocalAssistant.playMessage(VoiceMessage.WARNING, force = true)
            return
        }

        if (!isARCoreManuallyPaused) {
            try {
                session.pause()
                isARCoreManuallyPaused = true
                renderHandler.removeCallbacks(renderRunnable)
                view.showArPausedImage(true)
                Log.d(TAG, "ARCore session MANUALLY PAUSED via toggle.")
                vocalAssistant.playMessage(VoiceMessage.PAUSE, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing ARCore session via toggle", e)
            }
        } else {
            try {
                configureSession(session)
                configureTorch(session)
                view.showArPausedImage(false)
                session.resume()
                isARCoreManuallyPaused = false
                if (view.surfaceView.renderMode == GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
                    renderHandler.post(renderRunnable)
                }
                Log.d(TAG, "ARCore session MANUALLY RESUMED via toggle.")
                vocalAssistant.playMessage(VoiceMessage.RESUME, force = true)

            } catch (e: Exception) {
                Log.e(TAG, "Error resuming ARCore session via toggle", e)
            }
        }
    }

    private fun setupVoiceCommandListener() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        // Richiesta permesso microfono
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            @RequiresPermission(Manifest.permission.VIBRATE)
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.uppercase(Locale.getDefault()) ?: return

                // Caricamento comandi vocali da strings.xml
                val startCommands = resources.getStringArray(R.array.start).map { it.uppercase() }
                val stopCommands = resources.getStringArray(R.array.stop).map { it.uppercase() }
                val vibrationCommands = resources.getStringArray(R.array.vibration_commands).map { it.uppercase() }
                val disableVibrationCommands = resources.getStringArray(R.array.disable_vibration_commands).map { it.uppercase() }
                val watchVibrationCommands = resources.getStringArray(R.array.watch_vibration_commands).map { it.uppercase() }
                val disableWatchVibrationCommands = resources.getStringArray(R.array.disable_watch_vibration_commands).map { it.uppercase() }
                val speechCommands = resources.getStringArray(R.array.enable_speech_commands).map { it.uppercase() }
                val disableSpeechCommands = resources.getStringArray(R.array.disable_speech_commands).map { it.uppercase() }

                // Controllo dei comandi
                when {
                    startCommands.any { it in command } -> toggleARCore()
                    stopCommands.any { it in command } -> toggleARCore()
                    vibrationCommands.any { it in command } ->  preferencesManager.setPhoneVibrationEnabled(true)
                    disableVibrationCommands.any { it in command } ->  preferencesManager.setPhoneVibrationEnabled(false)
                    watchVibrationCommands.any { it in command } ->  preferencesManager.setWatchVibrationActive(true)
                    disableWatchVibrationCommands.any { it in command } ->  preferencesManager.setWatchVibrationActive(false)
                    speechCommands.any { it in command } -> preferencesManager.setTTSEnabled(true)
                    disableSpeechCommands.any { it in command } -> preferencesManager.setTTSEnabled(false)

                }

                speechRecognizer.startListening(speechIntent)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Riavvia su errore
                speechRecognizer.startListening(speechIntent)
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Avvia ascolto iniziale
        speechRecognizer.startListening(speechIntent)
    }

}