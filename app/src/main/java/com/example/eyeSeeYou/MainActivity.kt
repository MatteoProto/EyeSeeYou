package com.example.eyeSeeYou

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
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


     val depthSettings = DepthSettings()
     val eisSettings = EisSettings()

     var torch = false
     var torchTimer: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        vocalAssistant = VocalAssistant(this, preferencesManager)
        vibrationManager = VibrationManager(this)

        // UI con Jetpack Compose
        setContent {
            EyeSeeYouTheme {
                MainScreen(vocalAssistant)
            }
        }

        if (preferencesManager.isTTSEnabled()) {
            vocalAssistant.speak("Benvenuto in EyeSeeYou.")
        }

        // Setup ARCore
        setupARCore()
    }

    private fun setupARCore() {
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        arCoreSessionHelper.exceptionCallback = { exception ->
            val message = when (exception) {
                is UnavailableUserDeclinedInstallationException -> "Installa Google Play Services for AR"
                is UnavailableApkTooOldException -> "Aggiorna ARCore"
                is UnavailableSdkTooOldException -> "Aggiorna l'app"
                is UnavailableDeviceNotCompatibleException -> "Dispositivo non compatibile con AR"
                is CameraNotAvailableException -> "Fotocamera non disponibile, riavvia l'app"
                else -> "Errore nella sessione AR: $exception"
            }
            Log.e(TAG, "ARCore exception", exception)
            view.snackbarHelper.showError(this, message)
        }

        arCoreSessionHelper.beforeSessionResume = ::configureSession
        lifecycle.addObserver(arCoreSessionHelper)

        processor = MainProcessor()
        renderer = MainRenderer(this, vocalAssistant, vibrationManager, processor)
        lifecycle.addObserver(renderer)

        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        SampleRender(view.surfaceView, renderer, assets)

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

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleTTS() {
        val newValue = !preferencesManager.isTTSEnabled()
        preferencesManager.setTTSEnabled(newValue)
        vibrationManager.shortVibration()
        val message = if (newValue) "Sintesi vocale attivata" else "Sintesi vocale disattivata"
        vocalAssistant.speak(message)
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    private fun toggleWatchVibration() {
        val newValue = !preferencesManager.isWatchVibrationActive()
        preferencesManager.setWatchVibrationActive(newValue)
        vibrationManager.longVibration()
        val message = if (newValue) "Vibrazione orologio attivata" else "Vibrazione orologio disattivata"
        vocalAssistant.speak(message)
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
                "La fotocamera è necessaria per usare l'app",
                Toast.LENGTH_LONG
            ).show()

            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }
}