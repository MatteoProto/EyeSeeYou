package com.example.eyeSeeYou

import android.media.Image
import android.opengl.GLES30
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.eyeSeeYou.helpers.DisplayRotationHelper
import com.example.eyeSeeYou.helpers.TrackingStateHelper
import com.example.eyeSeeYou.samplerender.Framebuffer
import com.example.eyeSeeYou.samplerender.GLError
import com.example.eyeSeeYou.samplerender.Mesh
import com.example.eyeSeeYou.samplerender.SampleRender
import com.example.eyeSeeYou.samplerender.Shader
import com.example.eyeSeeYou.samplerender.Texture
import com.example.eyeSeeYou.samplerender.VertexBuffer
import com.example.eyeSeeYou.samplerender.arcore.BackgroundRenderer
import com.example.eyeSeeYou.samplerender.arcore.PlaneRenderer
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.IOException
import java.nio.ByteBuffer

/** Renders the HelloAR application using google example Renderer. */
class MainRenderer(
    val activity: MainActivity,
    private val vocalAssistant: VocalAssistant,
    private val vibrationManager: VibrationManager,
    private val processor: MainProcessor) :

    SampleRender.Renderer, DefaultLifecycleObserver {

        companion object {
        const val TAG = "HelloArRenderer"

        private const val Z_NEAR = 0.1f
        private const val Z_FAR = 100f
    }

    lateinit var render: SampleRender
    private lateinit var planeRenderer: PlaneRenderer
    private lateinit var backgroundRenderer: BackgroundRenderer
    private lateinit var virtualSceneFramebuffer: Framebuffer

    private var activeCamera = true
    private var showSemanticImage = false
    private var hasSetTextureNames = false

    // Point Cloud
    private lateinit var pointCloudVertexBuffer: VertexBuffer
    private lateinit var pointCloudMesh: Mesh
    private lateinit var pointCloudShader: Shader

    // Environmental HDR
    private lateinit var dfgTexture: Texture

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)


    private var lastAlertTime = 0L //TEMPORANEA


    private val session
        get() = activity.arCoreSessionHelper.session

    private val displayRotationHelper = DisplayRotationHelper(activity)
    private val trackingStateHelper = TrackingStateHelper(activity)


    override fun onResume(owner: LifecycleOwner) {
        displayRotationHelper.onResume()
        hasSetTextureNames = false
    }

    override fun onPause(owner: LifecycleOwner) {
        displayRotationHelper.onPause()
    }

    override fun onSurfaceCreated(render: SampleRender) {
        // Prepare the rendering objects.
        // This involves reading shaders and 3D model files, so may throw an IOException.
        try {
            planeRenderer = PlaneRenderer(render)
            backgroundRenderer = BackgroundRenderer(render)
            virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

            // Load environmental lighting values lookup table
            dfgTexture =
                Texture(
                    render,
                    Texture.Target.TEXTURE_2D,
                    Texture.WrapMode.CLAMP_TO_EDGE,
                    /*useMipmaps=*/ false
                )
            // The dfg.raw file is a raw half-float texture with two channels.
            val dfgResolution = 64
            val dfgChannels = 2
            val halfFloatSize = 2

            val buffer: ByteBuffer =
                ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
            activity.assets.open("models/dfg.raw").use { it.read(buffer.array()) }

            // SampleRender abstraction leaks here.
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.getTextureId())
            GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture")
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                /*level=*/ 0,
                GLES30.GL_RG16F,
                /*width=*/ dfgResolution,
                /*height=*/ dfgResolution,
                /*border=*/ 0,
                GLES30.GL_RG,
                GLES30.GL_HALF_FLOAT,
                buffer
            )
            GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D")

            // Point cloud
            pointCloudShader =
                Shader.createFromAssets(
                    render,
                    "shaders/point_cloud.vert",
                    "shaders/point_cloud.frag",
                    /*defines=*/ null
                )
                    .setVec4(
                        "u_Color",
                        floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
                    )
                    .setFloat("u_PointSize", 5.0f)

            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null)
            val pointCloudVertexBuffers = arrayOf(pointCloudVertexBuffer)
            pointCloudMesh =
                Mesh(
                    render,
                    Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/
                    null,
                    pointCloudVertexBuffers
                )

        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
            showError("Failed to read a required asset file: $e")
        }
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer.resize(width, height)
    }

    /** Group of functions executed for each frame. */
    override fun onDrawFrame(render: SampleRender) {
        val session = session ?: return
        this.render = render
        val frame =
            try {
                session.update()
            } catch (e: CameraNotAvailableException) {
                Log.e(TAG, "Camera not available during onDrawFrame", e)
                showError("Camera not available. Try restarting the app.")
                return
            }
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.getTextureId()))
            hasSetTextureNames = true
        }

        displayRotationHelper.updateSessionIfNeeded(session)

        val camera = frame.camera

        // Error communication and torch management
        val message: String? =
            when {
                camera.trackingState == TrackingState.PAUSED &&
                        camera.trackingFailureReason == TrackingFailureReason.NONE ->
                    activity.getString(R.string.searching_planes)

                camera.trackingState == TrackingState.PAUSED ->
                    TrackingStateHelper.getTrackingFailureReasonString(camera)

                session.hasTrackingPlane() ->
                    "Looking for obstacles"
                else -> null
            }


        var semantic: Image? = null
        var depth: Image? = null

        // Handle tap events
        activity.view.tapHelper.poll()?.let {
            handleTap()
        }
        val image: Image?
        // Decides if and what to show
        if (activeCamera) {
            image = drawRendering(render, camera, frame)
            if (showSemanticImage) {
                semantic = image
            } else {
                depth = image
            }
        } else {
            trackingStateHelper.updateKeepScreenOnFlag(TrackingState.PAUSED)
            image = null
        }

        // Starts frame processing
        val output = processor.processFrame(frame, semantic, depth)

        if (message == null) {
            activity.view.snackbarHelper.hide(activity)
            if (session.hasTrackingPlane()) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAlertTime > 3000) {
                    vocalAssistant.speak("Attenzione, ostacolo davanti a te.")
                    vibrationManager.shortVibration()
                    lastAlertTime = currentTime
                }
            }
        } else if (camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT || camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_FEATURES) {
            activity.view.snackbarHelper.showMessage(activity, message)
            (activity as? MainActivity)?.setTorch(true, session)
        } else {
            activity.view.snackbarHelper.showMessage(activity, output)
        }
        image?.close()
    }

    /** Function that draws the rendering. */
    private fun drawRendering(
        render: SampleRender,
        camera: Camera,
        frame: Frame
    ): Image? {
        var image: Image? = null

        if (!::render.isInitialized) {
            Log.w("MainRenderer", "Render not initialized")
            return null
        }

        try {
            backgroundRenderer.setUseDepthVisualization(
                render,
                activity.depthSettings.depthColorVisualizationEnabled()
            )
            backgroundRenderer.setUseOcclusion(
                render,
                activity.depthSettings.useDepthForOcclusion()
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
            showError("Failed to read a required asset file: $e")
            return null
        }

        backgroundRenderer.updateDisplayGeometry(frame)

        if (camera.trackingState == TrackingState.TRACKING) {
            try {
                if (showSemanticImage) {
                    val semanticImage = frame.acquireSemanticImage()
                    image = semanticImage
                    backgroundRenderer.setUseDepthVisualization(render, false)
                    backgroundRenderer.updateCameraSemanticTexture(semanticImage)
                } else {
                    val depthImage = frame.acquireDepthImage16Bits()
                    image = depthImage
                    backgroundRenderer.setUseDepthVisualization(render, true)
                    backgroundRenderer.updateCameraDepthTexture(depthImage)
                }
            } catch (e: NotYetAvailableException) {
                // Images still not ready
            }
        }

        trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)

        // -- Draw background
        if (frame.timestamp != 0L) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render)
        }

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return image
        }

        // -- Draw non-occluded virtual objects (planes, point cloud)

        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0)

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)

        return image
    }

    /** Checks if we detected at least one plane. */
    private fun Session.hasTrackingPlane() =
        getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }

    /** Update state based on the current frame's light estimation. */
    private fun handleTap() {
        activeCamera = true
        showSemanticImage = !showSemanticImage
        Log.d("MainRenderer", "Tap detected: $showSemanticImage")
    }

    private fun showError(errorMessage: String) =
        activity.view.snackbarHelper.showError(activity, errorMessage)
}