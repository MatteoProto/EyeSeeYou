package com.example.eyeSeeYou

import android.opengl.GLSurfaceView
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.eyeSeeYou.helpers.SnackbarHelper
import com.example.eyeSeeYou.helpers.TapHelper

/** Contains UI elements for Hello AR. */
class MainView(val activity: MainActivity) : DefaultLifecycleObserver {
    val root: View = View.inflate(activity, R.layout.activity_main, null)
    val surfaceView: GLSurfaceView = root.findViewById(R.id.surfaceview)

    val snackbarHelper = SnackbarHelper()
    val tapHelper = TapHelper(activity).also { surfaceView.setOnTouchListener(it) }

    override fun onResume(owner: LifecycleOwner) {
        surfaceView.onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        surfaceView.onPause()
    }
}
