package com.example.eyeSeeYou

import android.opengl.GLSurfaceView
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

    lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    lateinit var view: MainView
    private lateinit var renderer: MainRenderer
    private lateinit var processor: MainProcessor
    private lateinit var renderRunnable: Runnable
    lateinit var preferencesManager: PreferencesManager
    lateinit var vocalAssistant: VocalAssistant

    val depthSettings = DepthSettings()
    private val eisSettings = EisSettings()

    private var torch = false
    private var torchTimer: Handler? = null

    private val renderHandler = Handler(Looper.getMainLooper())
    private val desiredFps = 30L


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupARCore()
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
        if (view.surfaceView.renderMode == GLSurfaceView.RENDERMODE_WHEN_DIRTY) {
            Log.d(TAG, "onResume: Starting periodic render requests.")
            renderHandler.post(renderRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Stopping periodic render requests.")
        renderHandler.removeCallbacks(renderRunnable)
    }

    override fun onDestroy() {
        vocalAssistant.shutdown()
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
            finish()
        }
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
}