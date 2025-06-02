package com.example.eyeSeeYou.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.eyeSeeYou.MainActivity
import com.example.eyeSeeYou.MainView
import com.example.eyeSeeYou.R
import com.example.eyeSeeYou.helpers.DepthSettings
import com.example.eyeSeeYou.helpers.EisSettings
import com.example.eyeSeeYou.helpers.SnackbarHelper
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

class ArCoreManager(
    private val context: MainActivity,
    private val depthSettings: DepthSettings,
    private val eisSettings: EisSettings,
    private val arCoreSessionHelper: ARCoreSessionLifecycleHelper,
    ) {
    private val TAG = "ArCoreManager"

    private val desiredFps = 30L
    private var torch = false
    private var torchTimer: Handler? = null
    private val snackbarHelper = SnackbarHelper()
    private lateinit var renderRunnable: Runnable
    private val renderHandler = Handler(Looper.getMainLooper())

    fun setup() {
        arCoreSessionHelper.exceptionCallback = { exception ->
                val message = when (exception) {
                    is UnavailableUserDeclinedInstallationException -> context.getString(R.string.error_install_ar)
                    is UnavailableApkTooOldException -> context.getString(R.string.error_update_arcore)
                    is UnavailableSdkTooOldException -> context.getString(R.string.error_update_app)
                    is UnavailableDeviceNotCompatibleException -> context.getString(R.string.error_not_compatible)
                    is CameraNotAvailableException -> context.getString(R.string.error_camera_unavailable)
                    else -> context.getString(R.string.error_generic, exception.localizedMessage ?: "Unknown error")
                }

                Log.e(TAG, "ARCore exception", exception)
                snackbarHelper.showError(context, message)
            }
        arCoreSessionHelper.beforeSessionResume = ::configureSession
        context.lifecycle.addObserver(arCoreSessionHelper)
    }

    fun startSession(view: MainView) {
        depthSettings.onCreate(context)
        eisSettings.onCreate(context)

        renderRunnable = object : Runnable {
            override fun run() {
                arCoreSessionHelper.session?.let {
                    view.surfaceView.requestRender()
                }
                renderHandler.postDelayed(this, 1000L / desiredFps)
            }
        }
    }

    fun configureSession(session: Session) {
        applyCameraConfigFilter(session)
        session.configure(
            session.config.apply {
                lightEstimationMode = Config.LightEstimationMode.DISABLED
                focusMode = Config.FocusMode.AUTO

                semanticMode = if (session.isSemanticModeSupported(Config.SemanticMode.ENABLED)) {
                    Config.SemanticMode.ENABLED
                } else {
                    Config.SemanticMode.DISABLED
                }

                depthMode = if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }

                imageStabilizationMode = if (
                    session.isImageStabilizationModeSupported(Config.ImageStabilizationMode.EIS) &&
                    eisSettings.isEisEnabled()
                ) {
                    Config.ImageStabilizationMode.EIS
                } else {
                    Config.ImageStabilizationMode.OFF
                }

                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            }
        )
        configureTorch(session)
    }

    fun setTorch(torchEnabled: Boolean, session: Session) {
        if (torch == torchEnabled && torchEnabled) {
            restartTorchTimer(session)
            return
        }

        torch = torchEnabled
        configureTorch(session)

        if (torchEnabled) {
            restartTorchTimer(session)
        } else {
            torchTimer?.removeCallbacksAndMessages(null)
        }
    }

    private fun restartTorchTimer(session: Session) {
        torchTimer?.removeCallbacksAndMessages(null)
        torchTimer = Handler(Looper.getMainLooper())
        torchTimer?.postDelayed({
            torch = false
            configureTorch(session)
        }, 30_000)
    }

    private fun configureTorch(session: Session) {
        session.configure(session.config.apply {
            flashMode = if (torch) Config.FlashMode.TORCH else Config.FlashMode.OFF
        })
    }

    private fun applyCameraConfigFilter(session: Session) {
        val filter = CameraConfigFilter(session).apply {
            targetFps = EnumSet.of(TargetFps.TARGET_FPS_30)
        }

        val cameraConfigList = session.getSupportedCameraConfigs(filter)

        if (cameraConfigList.isNotEmpty()) {
            session.cameraConfig = cameraConfigList[0]
            Log.i(TAG, "Camera set to 30fps.")
        } else {
            Log.w(TAG, "Unable to configure camera")
        }
    }

    fun pauseSession() {
        arCoreSessionHelper.session?.let {
            try {
                it.pause()
                Log.d(TAG, "ARCore session re-paused safely.")
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing ARCore session", e)
            }
        }
    }

    fun startRendering() {
        renderHandler.post(renderRunnable)
    }

    fun stopRendering() {
        renderHandler.removeCallbacks(renderRunnable)
    }

    fun requestRender(view: MainView) {
        arCoreSessionHelper.session?.let {
            view.surfaceView.requestRender()
        }
    }

    fun pause() {
        arCoreSessionHelper.session?.pause()
    }

    fun resume() {
        arCoreSessionHelper.session?.resume()
    }

    fun isSessionAvailable(): Boolean {
        return arCoreSessionHelper.session != null
    }
}
