package com.vcamera.app.camera

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.Log
import android.view.Surface
import com.vcamera.app.model.CameraTransform
import com.vcamera.app.model.VideoSource
import kotlinx.coroutines.*

class CameraStreamManager(private val context: Context) {
    private val TAG = "CameraStreamManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var canvasSurface: Surface? = null
    private var currentSource: VideoSource = VideoSource.None
    private var isRunning = false
    var transform: CameraTransform = CameraTransform.DEFAULT

    fun setOutputSurface(s: Surface, w: Int, h: Int) { canvasSurface = s }

    fun setSource(source: VideoSource) {
        isRunning = false
        delay100()
        currentSource = source
        isRunning = true
        when (source) {
            is VideoSource.None -> showBlack()
            is VideoSource.Image -> startImage(source)
            is VideoSource.LocalVideo -> startVideoSafe(source.uri)
            is VideoSource.NetworkStream -> showBlack()
            is VideoSource.DeviceCamera -> showBlack()
        }
    }

    private fun delay100() { Thread.sleep(100) }

    private fun startImage(src: VideoSource.Image) {
        scope.launch(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = context.contentResolver.openInputStream(src.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                } ?: return@launch
                while (isRunning) { drawBitmap(bmp); delay(33) }
                bmp.recycle()
            } catch (e: Exception) { Log.e(TAG, "Image error: ${e.message}") }
        }
    }

    private fun startVideoSafe(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 5000L
                var pos = 0L
                val step = 100L
                while (isRunning) {
                    val frame = retriever.getFrameAtTime(
                        pos * 1000,
                        android.media.MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    frame?.let { drawBitmap(it) }
                    pos += step
                    if (pos >= duration) pos = 0L
                    delay(step)
                }
                retriever.release()
            } catch (e: Exception) {
                Log.e(TAG, "Video error: ${e.message}")
            }
        }
    }

    private fun drawBitmap(bmp: Bitmap) {
        val s = canvasSurface ?: return
        try {
            val c = s.lockCanvas(null) ?: return
            try {
                c.drawColor(Color.BLACK)
                val m = Matrix()
                val scale = maxOf(c.width.toFloat()/bmp.width, c.height.toFloat()/bmp.height) * transform.zoom
                val cx = c.width/2f; val cy = c.height/2f
                m.postScale(scale, scale, cx, cy)
                m.postRotate(transform.rotation, cx, cy)
                if (transform.flipHorizontal) m.postScale(-1f, 1f, cx, cy)
                if (transform.flipVertical) m.postScale(1f, -1f, cx, cy)
                c.drawBitmap(bmp, m, null)
            } finally { s.unlockCanvasAndPost(c) }
        } catch (e: Exception) { Log.e(TAG, "Draw error: ${e.message}") }
    }

    private fun showBlack() {
        scope.launch {
            while (isRunning) {
                try {
                    val s = canvasSurface ?: break
                    val c = s.lockCanvas(null) ?: break
                    try { c.drawColor(Color.BLACK) } finally { s.unlockCanvasAndPost(c) }
                } catch (_: Exception) {}
                delay(200)
            }
        }
    }

    fun getCurrentSource() = currentSource
    fun release() { isRunning = false; scope.cancel() }
}
