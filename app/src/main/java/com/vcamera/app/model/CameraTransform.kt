package com.vcamera.app.model

/**
 * Transformation parameters applied to the camera output
 */
data class CameraTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,      // degrees: 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val translateX: Float = 0f,    // -1.0 to 1.0
    val translateY: Float = 0f,    // -1.0 to 1.0
    val zoom: Float = 1f           // 0.5 = zoom in 2x, 2.0 = zoom out
) {
    companion object {
        val DEFAULT = CameraTransform()
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 4.0f
    }

    fun withZoom(newZoom: Float) = copy(zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    fun withRotation(degrees: Float) = copy(rotation = degrees % 360f)
    fun withFlipH() = copy(flipHorizontal = !flipHorizontal)
    fun withFlipV() = copy(flipVertical = !flipVertical)
    fun reset() = DEFAULT
}
