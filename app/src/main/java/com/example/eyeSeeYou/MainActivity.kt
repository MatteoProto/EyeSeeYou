package com.example.eyeSeeYou

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.eyeSeeYou.samplerender.SampleRender
import com.example.eyeSeeYou.theme.EyeSeeYouTheme
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.examples.java.common.helpers.ARCoreSessionLifecycleHelper
import com.example.eyeSeeYou.helpers.CameraPermissionHelper
import com.example.eyeSeeYou.helpers.DepthSettings
import com.example.eyeSeeYou.helpers.EisSettings
import com.example.eyeSeeYou.helpers.FullScreenHelper
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var view: MainView

    lateinit var renderer: MainRenderer
    lateinit var processor: MainProcessor

    lateinit var preferencesManager: PreferencesManager
    lateinit var vocalAssistant : VocalAssistant
    lateinit var vibrationManager : VibrationManager
    private val REQUEST_RECORD_AUDIO_PERMISSION = 101


    val depthSettings = DepthSettings()
    val eisSettings = EisSettings()

    var torch = false
    var torchTimer: Handler? = null
    var isPaused = false //METTERE IN STOP ARCORE

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent

    private var y1 = 0f
    private var y2 = 0f
    private var screenHeight = 0

    private val topExcludedAreaHeight = 200  // in pixel
    private val bottomExcludedAreaHeight = 200  // in pixel

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleTTS() {
        val newValue = !preferencesManager.isTTSEnabled()
        preferencesManager.setTTSEnabled(newValue)
        vibrationManager.shortVibration(force = true)

        val type = if (newValue) MessageType.TTS_ENABLED else MessageType.TTS_DISABLED
        vocalAssistant.playMessage(type, force = true)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleWatchVibration() {
        val newValue = !preferencesManager.isWatchVibrationActive()
        preferencesManager.setWatchVibrationActive(newValue)
        vibrationManager.longVibration()

        val type = if (newValue) MessageType.VIBRATION_ENABLED else MessageType.VIBRATION_DISABLED
        vocalAssistant.playMessage(type, force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)

        if (preferencesManager.getPreferredLanguage().isEmpty()) {
            val systemLocale = Locale.getDefault().language
            preferencesManager.setPreferredLanguage(systemLocale)
        }

        //Imposta il layout XML con il bottone
        setContentView(R.layout.activity_main)

        //Inizializza VocalAssistant e VibrationManager
        vocalAssistant = VocalAssistant(this, preferencesManager)
        vibrationManager = VibrationManager(this, preferencesManager)

        //Inizializza SpeechRecognizer e intent
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferencesManager.getPreferredLanguage())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parla ora…")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val spokenText = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.get(0)?.lowercase() ?: return
                handleVoiceCommand(spokenText)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        //Setup bottone per avviare riconoscimento vocale
        val btnTranscribe = findViewById<Button>(R.id.btn_transcribe)
        btnTranscribe.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO_PERMISSION
                )
            }
        }

        screenHeight = resources.displayMetrics.heightPixels

        if (preferencesManager.isTTSEnabled()) {
            vocalAssistant.playMessage(MessageType.WELCOME)
        }

        //Setup ARCore
        setupARCore()
    }

    private fun setupARCore() {
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this).apply {
            exceptionCallback = { exception ->
                val message = when (exception) {
                    is UnavailableUserDeclinedInstallationException -> getString(R.string.error_install_ar)
                    is UnavailableApkTooOldException -> getString(R.string.error_update_arcore)
                    is UnavailableSdkTooOldException -> getString(R.string.error_update_app)
                    is UnavailableDeviceNotCompatibleException -> getString(R.string.error_not_compatible)
                    is CameraNotAvailableException -> getString(R.string.error_camera_unavailable)
                    else -> getString(R.string.error_generic, exception.localizedMessage ?: "Errore sconosciuto")
                }

                Log.e(TAG, "ARCore exception", exception)
                view.snackbarHelper.showError(this@MainActivity, message)
            }
            beforeSessionResume = ::configureSession
        }
        lifecycle.addObserver(arCoreSessionHelper)

        //Inizializza logica e rendering
        processor = MainProcessor()
        renderer = MainRenderer(this, vocalAssistant, vibrationManager, processor)
        lifecycle.addObserver(renderer)

        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        SampleRender(view.surfaceView, renderer, assets)

        //Touch invisibile per mettere in pausa/riprendere la sintesi vocale
        view.surfaceView.setOnTouchListener { _, event ->
            if (event.action != MotionEvent.ACTION_UP) return@setOnTouchListener false

            val x = event.x
            val y = event.y
            val width = view.surfaceView.width
            val height = view.surfaceView.height

            if (x > width * 0.25 && x < width * 0.75 && y > height * 0.25 && y < height * 0.75) {
                togglePause()
                true
            } else {
                false
            }
        }

        depthSettings.onCreate(this)
        eisSettings.onCreate(this)
    }

    private fun configureSession(session: Session) {
        session.configure(session.config.apply {
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            focusMode = Config.FocusMode.AUTO
            semanticMode = if (session.isSemanticModeSupported(Config.SemanticMode.ENABLED)) {
                Config.SemanticMode.ENABLED
            } else Config.SemanticMode.DISABLED
            depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Config.DepthMode.AUTOMATIC
            } else Config.DepthMode.DISABLED
            imageStabilizationMode =
                if (session.isImageStabilizationModeSupported(Config.ImageStabilizationMode.EIS)
                    && eisSettings.isEisEnabled()) {
                    Config.ImageStabilizationMode.EIS
                } else {
                    Config.ImageStabilizationMode.OFF
                }
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        })
    }

    private fun configureTorch(session: Session) {
        session.configure(session.config.apply {
            flashMode = if (torch) Config.FlashMode.TORCH else Config.FlashMode.OFF
        })
    }

    fun setTorch(torchEnabled: Boolean, session: Session) {
        if (torch == torchEnabled && torchEnabled) {
            torchTimer?.removeCallbacksAndMessages(null)
            torchTimer = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    torch = false
                    configureTorch(session)
                }, 30_000)
            }
            return
        }

        torch = torchEnabled
        configureTorch(session)

        if (torchEnabled) {
            torchTimer?.removeCallbacksAndMessages(null)
            torchTimer = Handler(Looper.getMainLooper()).apply {
                postDelayed({
                    torch = false
                    configureTorch(session)
                }, 30_000)
            }
        } else {
            torchTimer?.removeCallbacksAndMessages(null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                this,
                getString(R.string.camera_permission_required),
                Toast.LENGTH_LONG
            ).show()

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechRecognizer.startListening(recognizerIntent)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO_PERMISSION)
        }
    }

    private fun handleVoiceCommand(command: String) {
        val lowerCmd = command.lowercase()

        when {
            // Pause synonyms
            listOf("pausa", "ferma", "stop").any { lowerCmd.contains(it) } -> {
                if (!isPaused) togglePause()
            }

            // Resume synonyms
            listOf("riprendi", "continua", "start").any { lowerCmd.contains(it) } -> {
                if (isPaused) togglePause()
            }

            // Change language to Italian
            listOf("italiano", "italia", "lingua italiana").any { lowerCmd.contains(it) } -> {
                preferencesManager.setPreferredLanguage("it")
                vocalAssistant.speak(getString(R.string.language_changed_it))
                recreate()
            }

            // Change language to English
            listOf("inglese", "english", "english language").any { lowerCmd.contains(it) } -> {
                preferencesManager.setPreferredLanguage("en")
                vocalAssistant.speak(getString(R.string.language_changed_en))
                recreate()
            }

            else -> {
                vocalAssistant.speak(getString(R.string.command_not_recognized))
            }
        }
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> y1 = event.y
            MotionEvent.ACTION_UP -> {
                y2 = event.y
                val deltaY = y1 - y2

                // SWIPE VERSO IL BASSO in area top (primi 200 px)
                if (y1 < topExcludedAreaHeight && deltaY < -150) {
                    toggleWatchVibration()
                }
                // SWIPE VERSO L'ALTO in area bottom (ultimi 200 px)
                else if (y1 > screenHeight - bottomExcludedAreaHeight && deltaY > 150) {
                    toggleTTS()
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    //STOP AND PAUSA AR
    fun togglePause() {
        isPaused = !isPaused
        val type = if (isPaused) {
            MessageType.PAUSE
        } else {
            MessageType.RESUME
        }
        vocalAssistant.playMessage(type)
    }

    override fun onDestroy() {
        vocalAssistant.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }
}