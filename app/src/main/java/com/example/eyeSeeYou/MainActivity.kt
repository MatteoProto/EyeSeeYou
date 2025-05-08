package com.example.eyeSeeYou

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.eyeSeeYou.helpers.CameraPermissionHelper
import com.example.eyeSeeYou.helpers.DepthSettings
import com.example.eyeSeeYou.helpers.EisSettings
import com.example.eyeSeeYou.helpers.FullScreenHelper
import com.example.eyeSeeYou.samplerender.SampleRender
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.examples.java.common.helpers.ARCoreSessionLifecycleHelper
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
    private lateinit var renderer: MainRenderer
    private lateinit var processor: MainProcessor

    val depthSettings = DepthSettings()
    private val eisSettings = EisSettings()

    private var torch = false
    private var torchTimer: Handler? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Session initialization
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        arCoreSessionHelper.exceptionCallback =
            { exception ->
                val message =
                    when (exception) {
                        is UnavailableUserDeclinedInstallationException ->
                            "Please install Google Play Services for AR"

                        is UnavailableApkTooOldException -> "Please update ARCore"
                        is UnavailableSdkTooOldException -> "Please update this app"
                        is UnavailableDeviceNotCompatibleException -> "This device does not support AR"
                        is CameraNotAvailableException -> "Camera not available. Try restarting the app."
                        else -> "Failed to create AR session: $exception"
                    }
                Log.e(TAG, "ARCore threw an exception", exception)
                view.snackbarHelper.showError(this, message)
            }

        // Session configuration and settings
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        //arCoreSessionHelper.beforeSessionResume = ::configureCamera
        lifecycle.addObserver(arCoreSessionHelper)

        // Set up the Hello AR renderer.
        processor = MainProcessor()
        renderer = MainRenderer(this, processor)
        lifecycle.addObserver(renderer)

        // Set up Hello AR UI.
        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        // Sets up an example renderer using our HelloARRenderer.
        SampleRender(view.surfaceView, renderer, assets)

        depthSettings.onCreate(this)
        eisSettings.onCreate(this)

    }

//    fun configureCamera(session: Session) {
//        val filter = CameraConfigFilter(session)
//        filter.setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
//    }

    fun configureSession(session: Session) {
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
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }
}