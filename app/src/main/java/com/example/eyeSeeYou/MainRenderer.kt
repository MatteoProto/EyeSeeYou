package com.example.eyeSeeYou

import android.media.Image
import android.opengl.GLES30
import android.opengl.Matrix
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
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
import com.example.eyeSeeYou.samplerender.arcore.SpecularCubemapFilter
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.IOException
import java.nio.ByteBuffer

/** Renders the HelloAR application using google example Renderer. */
class MainRenderer(val activity: MainActivity,val processor: MainProcessor) :
    SampleRender.Renderer, DefaultLifecycleObserver {
    companion object {
        val TAG = "HelloArRenderer"

        private val Z_NEAR = 0.1f
        private val Z_FAR = 100f

        // Assumed distance from the device camera to the surface on which user will try to place
        // objects.
        // This value affects the apparent scale of objects while the tracking method of the
        // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
        // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
        // values for AR experiences where users are expected to place objects on surfaces close to the
        // camera. Use larger values for experiences where the user will likely be standing and trying
        // to
        // place an object on the ground or floor in front of them.
        val APPROXIMATE_DISTANCE_METERS = 2.0f

        val CUBEMAP_RESOLUTION = 16
        val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 32
    }

    lateinit var render: SampleRender
    lateinit var planeRenderer: PlaneRenderer
    lateinit var backgroundRenderer: BackgroundRenderer
    lateinit var virtualSceneFramebuffer: Framebuffer

    private var active =true
    private var showSemanticImage = false
    private var hasSetTextureNames = false

    // Point Cloud
    lateinit var pointCloudVertexBuffer: VertexBuffer
    lateinit var pointCloudMesh: Mesh
    lateinit var pointCloudShader: Shader

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    var lastPointCloudTimestamp: Long = 0

    // Environmental HDR
    lateinit var dfgTexture: Texture
    lateinit var cubemapFilter: SpecularCubemapFilter

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    val viewMatrix = FloatArray(16)
    val projectionMatrix = FloatArray(16)

    val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

    val session
        get() = activity.arCoreSessionHelper.session

    val displayRotationHelper = DisplayRotationHelper(activity)
    val trackingStateHelper = TrackingStateHelper(activity)



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

            cubemapFilter =
                SpecularCubemapFilter(render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES)
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
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.textureId)
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
                    .setVec4("u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f))
                    .setFloat("u_PointSize", 5.0f)

            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null)
            val pointCloudVertexBuffers = arrayOf(pointCloudVertexBuffer)
            pointCloudMesh =
                Mesh(render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers)

        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
            showError("Failed to read a required asset file: $e")
        }
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        displayRotationHelper.onSurfaceChanged(width, height)
        virtualSceneFramebuffer.resize(width, height)
    }

    /** Gruppo di funzioni eseguite a ogni frame */
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
            session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
            hasSetTextureNames = true
        }

        displayRotationHelper.updateSessionIfNeeded(session)

        val camera = frame.camera

        // Comunicazione degli errori e gestione della torcia
        val message: String? =
            when {
                camera.trackingState == TrackingState.PAUSED &&
                        camera.trackingFailureReason == TrackingFailureReason.NONE ->
                    activity.getString(R.string.searching_planes)
                camera.trackingState == TrackingState.PAUSED ->
                    TrackingStateHelper.getTrackingFailureReasonString(camera)
                session.hasTrackingPlane()->
                    "Looking for obstacles"
                session.hasTrackingPlane()-> null
                else -> activity.getString(R.string.searching_planes)
            }
        if (message == null) {
            activity.view.snackbarHelper.hide(activity)
        } else if(camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_LIGHT || camera.trackingFailureReason == TrackingFailureReason.INSUFFICIENT_FEATURES){
            activity.view.snackbarHelper.showMessage(activity, message)
            (activity as? MainActivity)?.setTorch(true,session)
        } else {
            activity.view.snackbarHelper.showMessage(activity, message)
        }

        var semantic:Image? = null
        var depth:Image? = null

        //Gestisce i tocchi
        activity.view.tapHelper.poll()?.let {
            handleTap()
        }

        //Decide se e cosa mostrare a schermo
       if (active){
            if (showSemanticImage){
                semantic = drawRendering(render,session,camera,frame)
            } else {
                depth = drawRendering(render,session,camera,frame)
            }
       } else {
            trackingStateHelper.updateKeepScreenOnFlag(TrackingState.PAUSED)
            return
        }

        //Avvia le funzioni di analisi di frame e processing dei dati
       processor.processFrame(frame,semantic,depth)

        semantic?.close()
        depth?.close()
    }

    /** Funzione che si occupa della creazione dell'immagine da renderizzare a schermo */
    private fun drawRendering(render: SampleRender,session: Session, camera: Camera, frame: Frame): Image?{

        var image:Image? = null



        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        if (!::render.isInitialized) {
            Log.w("MainRenderer", "Render non inizializzato, frame ignorato")
            return null
        }

        try {
            backgroundRenderer.setUseDepthVisualization(
                render,
                activity.depthSettings.depthColorVisualizationEnabled()
            )
            backgroundRenderer.setUseOcclusion(render, activity.depthSettings.useDepthForOcclusion())
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read a required asset file", e)
            showError("Failed to read a required asset file: $e")
            return null
        }

        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame)

        if (camera.trackingState == TrackingState.TRACKING) {
            try {
                if (showSemanticImage) {
                    val semanticImage = frame.acquireSemanticImage()
                    image = semanticImage
                    semanticImage.use { semanticImage ->
                        backgroundRenderer.setUseDepthVisualization(render, false)
                        backgroundRenderer.updateCameraSemanticTexture(semanticImage)
                        semanticImage.close()
                    }
                } else {
                    val depthImage = frame.acquireDepthImage16Bits()
                    image = depthImage
                    depthImage.use { depthImage ->
                        backgroundRenderer.setUseDepthVisualization(render, true)
                        backgroundRenderer.updateCameraDepthTexture(depthImage)
                        depthImage.close()
                    }
                }
            } catch (e: NotYetAvailableException) {
                // Le immagini non sono ancora pronte: normale
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
        frame.acquirePointCloud().use { pointCloud ->
            if (pointCloud.timestamp > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.points)
                lastPointCloudTimestamp = pointCloud.timestamp
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
            render.draw(pointCloudMesh, pointCloudShader)
        }

        // Visualize planes.
        planeRenderer.drawPlanes(
            render,
            session.getAllTrackables<Plane>(Plane::class.java),
            camera.displayOrientedPose,
            projectionMatrix
        )

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)

        return image
    }

    /** Checks if we detected at least one plane. */
    private fun Session.hasTrackingPlane() =
        getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }

    /** Update state based on the current frame's light estimation. */
    fun handleTap() {
        active = true
        showSemanticImage = !showSemanticImage
        Log.d("MainRenderer", "Tap detected: $showSemanticImage")
    }

    private fun showError(errorMessage: String) =
        activity.view.snackbarHelper.showError(activity, errorMessage)
    }