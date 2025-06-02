package com.example.eyeSeeYou

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.eyeSeeYou.managers.ArCoreManager
import com.example.eyeSeeYou.managers.PreferencesManager
import com.example.eyeSeeYou.managers.VibrationManager
import com.example.eyeSeeYou.managers.VocalAssistant
import com.example.eyeSeeYou.managers.VoiceManager
import com.example.eyeSeeYou.managers.WearableManager
import com.example.eyeSeeYou.helpers.CameraPermissionHelper
import com.example.eyeSeeYou.helpers.DepthSettings
import com.example.eyeSeeYou.helpers.EisSettings
import com.example.eyeSeeYou.helpers.FullScreenHelper
import com.example.eyeSeeYou.helpers.Point2D
import com.example.eyeSeeYou.enums.VoiceMessage
import com.example.eyeSeeYou.samplerender.SampleRender
import com.google.ar.core.examples.java.common.helpers.ARCoreSessionLifecycleHelper
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
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

    private lateinit var renderer: MainRenderer
    private lateinit var processor: MainProcessor
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var arCoreManager: ArCoreManager
    private lateinit var voiceManager: VoiceManager
    private lateinit var wearableManager: WearableManager
    internal lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    internal lateinit var view: MainView
    internal lateinit var vocalAssistant: VocalAssistant
    internal lateinit var vibrationManager: VibrationManager


    val depthSettings = DepthSettings()
    private val eisSettings = EisSettings()

    private var y1 = 0f
    private var y2 = 0f
    private var x1 = 0f
    private var x2 = 0f
    val SWIPE_THRESHOLD = 150

    private var isARCoreManuallyPaused = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        preferencesManager = PreferencesManager(this)

        if (preferencesManager.getPreferredLanguage().isEmpty()) {
            val systemLocale = Locale.getDefault().language
            preferencesManager.setPreferredLanguage(systemLocale)
        }

        vocalAssistant = VocalAssistant(this, preferencesManager)
        vibrationManager = VibrationManager(this, preferencesManager)
        wearableManager = WearableManager(this, preferencesManager)

        arCoreManager = ArCoreManager(this, depthSettings, eisSettings, arCoreSessionHelper)
        arCoreManager.setup()

        processor = MainProcessor()
        renderer = MainRenderer(this, processor, wearableManager, arCoreManager)
        lifecycle.addObserver(renderer)

        view = MainView(this)
        lifecycle.addObserver(view)
        setContentView(view.root)

        SampleRender(view.surfaceView, renderer, assets)

        arCoreManager.startSession(view)

        voiceManager = VoiceManager(this)
        voiceManager.setup()

        screenWidth = view.surfaceView.width.toFloat()
        screenHeight = view.surfaceView.height.toFloat()
        gridPointIds.forEach { id ->
            findViewById<ImageView>(id)?.let {
                gridPointImageViews.add(it)
            }
        }
        val gridContainer = findViewById<View>(R.id.grid_container)
        gridContainer.post {
            getImageViewCoordinates()
            processor.setScreenGridPoints(screenCoordinates)
            Log.d("MainActivity", "Coordinates sent to processor: $screenCoordinates")
        }

    }

    /**
    Prende le coordinate sullo schermo delle image view
     */
    private fun getImageViewCoordinates() {
        screenCoordinates.clear()
        val locationOnScreen = IntArray(2)

        gridPointImageViews.forEachIndexed { index, imageView ->
            imageView.getLocationInWindow(locationOnScreen)

            val centerX = locationOnScreen[0]
            val centerY = locationOnScreen[1]

            val newPoint = Point2D(centerX, centerY)
            screenCoordinates[resources.getResourceEntryName(imageView.id)] = newPoint
            Log.d("MainActivity", "Point ${index + 1} (${resources.getResourceEntryName(imageView.id)}): center ($centerX, $centerY)")
        }
    }

    override fun onResume() {
        super.onResume()
        voiceManager.startListening()

        if (isARCoreManuallyPaused) {
            arCoreManager.pauseSession()
            arCoreManager.stopRendering()
            view.showArPausedImage(true)
        } else {
            view.showArPausedImage(false)
            arCoreManager.startRendering()
            arCoreManager.requestRender(view)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Stopping periodic render requests.")
        arCoreManager.stopRendering()
    }

    override fun onDestroy() {
        vocalAssistant.shutdown()
        voiceManager.destroy()
        super.onDestroy()
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
                "Camera permission is needed to run this application",
                Toast.LENGTH_LONG
            ).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
        }

        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            voiceManager.setup()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
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
    fun toggleTTS() {
        val newValue = !preferencesManager.isTTSEnabled()
        preferencesManager.setTTSEnabled(newValue)

        val type = if (newValue) VoiceMessage.TTS_ENABLED else VoiceMessage.TTS_DISABLED
        vocalAssistant.playMessage(type, force = true)
        vibrationManager.longVibration()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun toggleWatchVibration() {
        val newValue = !preferencesManager.isWatchVibrationActive()
        preferencesManager.setWatchVibrationActive(newValue)

        val type = if (newValue) VoiceMessage.WATCH_VIBRATION_ENABLED else VoiceMessage.WATCH_VIBRATION_DISABLED
        vocalAssistant.playMessage(type, force = true)
        vibrationManager.longVibration()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun togglePhoneVibration() {
        val newValue = !preferencesManager.isPhoneVibrationEnabled()
        preferencesManager.setPhoneVibrationEnabled(newValue)

        val type = if (newValue) VoiceMessage.VIBRATION_ENABLED else VoiceMessage.VIBRATION_DISABLED
        vocalAssistant.playMessage(type, force = true)
        vibrationManager.longVibration()
    }

    @RequiresPermission(Manifest.permission.VIBRATE)
    fun toggleARCore() {
        vibrationManager.longVibration()

        if (!arCoreManager.isSessionAvailable()) {
            Log.w(TAG, "ARCore session is null, cannot toggle.")
            vocalAssistant.playMessage(VoiceMessage.WARNING, force = true)
            return
        }

        if (!isARCoreManuallyPaused) {
            try {
                arCoreManager.pause()
                isARCoreManuallyPaused = true
                arCoreManager.stopRendering()
                view.showArPausedImage(true)
                Log.d(TAG, "ARCore session MANUALLY PAUSED via toggle.")
                vocalAssistant.playMessage(VoiceMessage.PAUSE, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error pausing ARCore session via toggle", e)
            }
        } else {
            try {
                arCoreManager.resume()
                isARCoreManuallyPaused = false
                arCoreManager.startRendering()
                view.showArPausedImage(false)
                Log.d(TAG, "ARCore session MANUALLY RESUMED via toggle.")
                vocalAssistant.playMessage(VoiceMessage.RESUME, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming ARCore session via toggle", e)
            }
        }
    }
}
