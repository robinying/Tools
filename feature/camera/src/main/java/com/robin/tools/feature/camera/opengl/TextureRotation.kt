package com.robin.tools.feature.camera.opengl

/**
 * Texture coordinates for a full-screen quad.
 *
 * Vertex order matches [GlUtil.cubeVertices]: BL, BR, TL, TR
 * (bottom-left → bottom-right → top-left → top-right in NDC / GL space).
 *
 * Base UV is **GL-native** (v = 0 at bottom) — correct for sampling an FBO rendered
 * with OpenGL. SurfaceTexture's matrix already handles OES buffer layout/Y-flip.
 *
 * [rotationDegrees] = clockwise degrees to rotate **image content**
 * (Camera1 / JPEG orientation convention).
 *
 * Mapping derivation (v-up image coords, CW 90 around center):
 * screen (sx,sy) samples original (u,v) = (1 - sy, sx) after inverse of CW 90.
 */
object TextureRotation {

    /** Identity sampling of a GL FBO / 2D texture. */
    private val NO_ROTATION = floatArrayOf(
        0.0f, 0.0f, // BL
        1.0f, 0.0f, // BR
        0.0f, 1.0f, // TL
        1.0f, 1.0f  // TR
    )

    /**
     * Content rotated 90° clockwise.
     * screen BL→(1,0), BR→(1,1), TL→(0,0), TR→(0,1)
     */
    private val ROTATION_90 = floatArrayOf(
        1.0f, 0.0f,
        1.0f, 1.0f,
        0.0f, 0.0f,
        0.0f, 1.0f
    )

    /** Content rotated 180°. */
    private val ROTATION_180 = floatArrayOf(
        1.0f, 1.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        0.0f, 0.0f
    )

    /**
     * Content rotated 270° clockwise (= 90° counter-clockwise).
     * screen BL→(0,1), BR→(0,0), TL→(1,1), TR→(1,0)
     */
    private val ROTATION_270 = floatArrayOf(
        0.0f, 1.0f,
        0.0f, 0.0f,
        1.0f, 1.0f,
        1.0f, 0.0f
    )

    /**
     * OES input UVs in [0,1] with v=0 at bottom.
     * Pair with [android.graphics.SurfaceTexture.getTransformMatrix] only — no extra flip.
     */
    val OES_NO_ROTATION = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    fun asFloatArray(
        rotationDegrees: Int,
        flipHorizontal: Boolean = false,
        flipVertical: Boolean = false
    ): FloatArray {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        var coords = when (normalized) {
            90 -> ROTATION_90.copyOf()
            180 -> ROTATION_180.copyOf()
            270 -> ROTATION_270.copyOf()
            else -> NO_ROTATION.copyOf()
        }
        if (flipHorizontal) coords = flipHorizontal(coords)
        if (flipVertical) coords = flipVertical(coords)
        return coords
    }

    private fun flipHorizontal(c: FloatArray): FloatArray = floatArrayOf(
        c[2], c[3],
        c[0], c[1],
        c[6], c[7],
        c[4], c[5]
    )

    private fun flipVertical(c: FloatArray): FloatArray = floatArrayOf(
        c[4], c[5],
        c[6], c[7],
        c[0], c[1],
        c[2], c[3]
    )
}
