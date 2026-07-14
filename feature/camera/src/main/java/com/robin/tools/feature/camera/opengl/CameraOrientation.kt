package com.robin.tools.feature.camera.opengl

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager

/**
 * Helpers for Camera2 → OpenGL preview orientation.
 *
 * Camera sensors are typically mounted at 90°/270° relative to the device's natural
 * portrait orientation. SurfaceTexture's transform matrix only handles buffer layout
 * (crop / Y-flip); sensor-to-display rotation must be applied by the app.
 */
object CameraOrientation {

    /**
     * Degrees to rotate the camera buffer so it appears upright on the current display.
     * Same convention as Camera1 [android.hardware.Camera.setDisplayOrientation].
     */
    fun relativeImageRotation(
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        frontFacing: Boolean
    ): Int {
        return if (frontFacing) {
            (sensorOrientationDegrees + displayRotationDegrees) % 360
        } else {
            (sensorOrientationDegrees - displayRotationDegrees + 360) % 360
        }
    }

    /**
     * Current default-display rotation in degrees (0 / 90 / 180 / 270).
     *
     * Safe with Application context (does not call [Context.getDisplay], which throws
     * on non-visual contexts on Android 12+).
     */
    fun displayRotationDegrees(context: Context): Int {
        val rotation = resolveDisplayRotation(context)
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun resolveDisplayRotation(context: Context): Int {
        // Prefer DisplayManager.DEFAULT_DISPLAY — works for Application context.
        val dm = context.getSystemService(DisplayManager::class.java)
        val defaultDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        if (defaultDisplay != null) {
            return defaultDisplay.rotation
        }

        // Fallback for unusual devices / older paths.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.let { return it.rotation }
            } catch (_: UnsupportedOperationException) {
                // Application / non-visual context — ignore.
            }
        }

        @Suppress("DEPRECATION")
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}
