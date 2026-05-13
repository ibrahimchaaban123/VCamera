package com.vcamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.media.ImageReader
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vcamera.app.model.CameraTransform
import com.vcamera.app.model.VideoSource
import kotlinx.coroutines.*

/**
 * Manages the active video/image source and streams frames to the output surface.
 */
class CameraStreamManager(private val context: Context) {

    private val TAG = "CameraStreamManager"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var exoPlayer: ExoPlayer? = null
    private var frameProcessor: FrameProcessor? = null
    private var outputSurface: Surface? = null
    private var currentSource: VideoSource = VideoSource.None
    private var isRunning = false

    var transform: CameraTransform = CameraTransform.DEFAULT
        set(value) {
            field = value
            frameProcessor?.transform = value
        }

    fun setOutputSurface(surface: Surface, width: Int, height: Int) {
        outputSurface = surface
        frameProcessor = FrameProcessor().apply {
            init(surface, width, height)
            this.transform = this@CameraStreamManager.transform
        }
        Log.d(TAG, "Output surface set: ${width}x${height}")
    }

    fun setSource(source: VideoSource) {
        stopCurrentSource()
        currentSource = source
        startSource(source)
        Log.d(TAG, "Source changed to: ${source.getLabel()}")
    }

    private fun startSource(source: VideoSource) {
        isRunning = true
        when (source) {
            is VideoSource.None -> {
                // Show black frame
                showBlackFrame()
            }
            is VideoSource.Image -> {
                startImageSource(source)
            }
            is VideoSource.LocalVideo -> {
                startVideoSource(source.uri.toString())
            }
            is VideoSource.NetworkStream -> {
                startVideoSource(source.url)
            }
            is VideoSource.DeviceCamera -> {
                startDeviceCamera(source.cameraId)
            }
        }
    }

    private fun startImageSource(source: VideoSource.Image) {
        scope.launch {
            try {
                val bitmap = context.contentResolver.openInputStream(source.uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@launch

                // Render image repeatedly at 30fps
                while (isRunning) {
                    renderBitmapToSurface(bitmap)
                    delay(33) // ~30 fps
                }
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Image source error: ${e.message}")
            }
        }
    }

    private fun startVideoSource(url: String) {
        scope.launch(Dispatchers.Main) {
            try {
                val processor = frameProcessor ?: return@launch
                val st = processor.getSurfaceTexture() ?: return@launch
                val inputSurface = Surface(st)

                exoPlayer = ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri(url)
                    setMediaItem(mediaItem)
                    setVideoSurface(inputSurface)
                    repeatMode = Player.REPEAT_MODE_ALL
                    prepare()
                    playWhenReady = true

                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (isPlaying) {
                                startRenderLoop()
                            }
                        }
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Video source error: ${e.message}")
            }
        }
    }

    private fun startDeviceCamera(cameraId: String) {
        // In a full implementation, use Camera2 API here
        // For now, show a placeholder
        Log.d(TAG, "Device camera passthrough: $cameraId")
        showBlackFrame()
    }

    private fun startRenderLoop() {
        scope.launch {
            while (isRunning) {
                frameProcessor?.drawFrame()
                delay(16) // ~60 fps
            }
        }
    }

    private fun renderBitmapToSurface(bitmap: Bitmap) {
        val surface = outputSurface ?: return
        try {
            val canvas: Canvas = surface.lockCanvas(null)
            canvas.drawColor(Color.BLACK)

            // Scale bitmap to fill surface
            val scaleX = canvas.width.toFloat() / bitmap.width
            val scaleY = canvas.height.toFloat() / bitmap.height
            val scale = maxOf(scaleX, scaleY)

            val scaledW = bitmap.width * scale
            val scaledH = bitmap.height * scale
            val left = (canvas.width - scaledW) / 2f
            val top = (canvas.height - scaledH) / 2f

            val dst = android.graphics.RectF(left, top, left + scaledW, top + scaledH)
            canvas.drawBitmap(bitmap, null, dst, null)
            surface.unlockCanvasAndPost(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Render error: ${e.message}")
        }
    }

    private fun showBlackFrame() {
        scope.launch {
            while (isRunning && currentSource is VideoSource.None) {
                val surface = outputSurface ?: break
                try {
                    val canvas = surface.lockCanvas(null)
                    canvas.drawColor(Color.BLACK)
                    surface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) { /* ignore */ }
                delay(100)
            }
        }
    }

    private fun stopCurrentSource() {
        isRunning = false
        exoPlayer?.run {
            stop()
            release()
        }
        exoPlayer = null
    }

    fun getCurrentSource(): VideoSource = currentSource

    fun release() {
        stopCurrentSource()
        frameProcessor?.release()
        frameProcessor = null
        scope.cancel()
        Log.d(TAG, "CameraStreamManager released")
    }
}
