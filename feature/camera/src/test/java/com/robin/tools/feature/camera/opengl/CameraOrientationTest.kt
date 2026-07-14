package com.robin.tools.feature.camera.opengl

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrientationTest {

    @Test
    fun backCamera_portraitDisplay_is90() {
        // Typical phone: back sensor 90°, natural portrait display 0°.
        assertEquals(
            90,
            CameraOrientation.relativeImageRotation(
                sensorOrientationDegrees = 90,
                displayRotationDegrees = 0,
                frontFacing = false
            )
        )
    }

    @Test
    fun frontCamera_portraitDisplay_is270() {
        // Typical phone: front sensor 270°, natural portrait display 0°.
        assertEquals(
            270,
            CameraOrientation.relativeImageRotation(
                sensorOrientationDegrees = 270,
                displayRotationDegrees = 0,
                frontFacing = true
            )
        )
    }

    @Test
    fun backCamera_landscapeDisplay_compensates() {
        assertEquals(
            0,
            CameraOrientation.relativeImageRotation(
                sensorOrientationDegrees = 90,
                displayRotationDegrees = 90,
                frontFacing = false
            )
        )
    }
}
