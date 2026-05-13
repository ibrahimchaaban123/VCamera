package com.vcamera.app.model

import android.net.Uri

/**
 * Represents a video/image source for the virtual camera
 */
sealed class VideoSource {

    /** No source selected */
    object None : VideoSource()

    /** Static image from device storage */
    data class Image(
        val uri: Uri,
        val displayName: String = "Image"
    ) : VideoSource()

    /** Video file from device storage */
    data class LocalVideo(
        val uri: Uri,
        val displayName: String = "Video",
        val durationMs: Long = 0L
    ) : VideoSource()

    /** Network/RTMP/HLS stream */
    data class NetworkStream(
        val url: String,
        val displayName: String = "Network Stream"
    ) : VideoSource()

    /** Live device camera feed (passthrough) */
    data class DeviceCamera(
        val cameraId: String = "0",
        val displayName: String = "Device Camera"
    ) : VideoSource()

    fun isValid(): Boolean = this !is None

    fun getLabel(): String = when (this) {
        is None -> "None"
        is Image -> displayName
        is LocalVideo -> displayName
        is NetworkStream -> displayName
        is DeviceCamera -> displayName
    }
}
