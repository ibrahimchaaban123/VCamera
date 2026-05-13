package com.vcamera.app.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.vcamera.app.model.CameraTransform
import com.vcamera.app.model.VideoSource

class MainViewModel : ViewModel() {

    private val _source = MutableLiveData<VideoSource>(VideoSource.None)
    val source: LiveData<VideoSource> = _source

    private val _transform = MutableLiveData(CameraTransform.DEFAULT)
    val transform: LiveData<CameraTransform> = _transform

    private val _serviceRunning = MutableLiveData(false)
    val serviceRunning: LiveData<Boolean> = _serviceRunning

    fun setSource(source: VideoSource) {
        _source.value = source
    }

    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    // --- Transform operations ---

    fun zoomIn() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withZoom(current.zoom * 0.8f) // zoom in
    }

    fun zoomOut() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withZoom(current.zoom * 1.25f) // zoom out
    }

    fun setZoom(zoom: Float) {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withZoom(zoom)
    }

    fun rotateLeft() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withRotation(current.rotation - 90f)
    }

    fun rotateRight() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withRotation(current.rotation + 90f)
    }

    fun flipHorizontal() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withFlipH()
    }

    fun flipVertical() {
        val current = _transform.value ?: CameraTransform.DEFAULT
        _transform.value = current.withFlipV()
    }

    fun resetTransform() {
        _transform.value = CameraTransform.DEFAULT
    }
}
