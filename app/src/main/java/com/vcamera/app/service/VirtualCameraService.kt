package com.vcamera.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.app.NotificationCompat
import com.vcamera.app.R
import com.vcamera.app.camera.CameraStreamManager
import com.vcamera.app.model.CameraTransform
import com.vcamera.app.model.VideoSource
import com.vcamera.app.ui.MainActivity

/**
 * Foreground service that keeps the virtual camera running even when the app is in background.
 * Intercepts camera requests from other apps and injects the selected video/image source.
 */
class VirtualCameraService : Service() {

    private val TAG = "VirtualCameraService"
    private val CHANNEL_ID = "vcamera_channel"
    private val NOTIF_ID = 1001

    // Camera components
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var streamManager: CameraStreamManager? = null

    // Current config
    private var currentSource: VideoSource = VideoSource.None
    private var currentTransform: CameraTransform = CameraTransform.DEFAULT
    private var isActive = false

    // Output resolution
    private val OUTPUT_WIDTH = 1280
    private val OUTPUT_HEIGHT = 720

    inner class LocalBinder : Binder() {
        fun getService(): VirtualCameraService = this@VirtualCameraService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        streamManager = CameraStreamManager(this)
        Log.d(TAG, "VirtualCameraService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startVirtualCamera()
            ACTION_STOP -> stopSelf()
            ACTION_SET_SOURCE -> {
                val source = intent.getParcelableExtra<android.os.Parcelable>("source")
                // Handle source update
            }
        }
        return START_STICKY
    }

    fun startVirtualCamera() {
        if (isActive) return
        isActive = true
        startForeground(NOTIF_ID, buildNotification())
        setupImageReader()
        Log.d(TAG, "Virtual camera started")
    }

    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            OUTPUT_WIDTH, OUTPUT_HEIGHT,
            PixelFormat.RGBA_8888, 2
        ).apply {
            streamManager?.setOutputSurface(surface, OUTPUT_WIDTH, OUTPUT_HEIGHT)
        }
    }

    fun setSource(source: VideoSource) {
        currentSource = source
        streamManager?.setSource(source)
        updateNotification()
        Log.d(TAG, "Source set to: ${source.getLabel()}")
    }

    fun setTransform(transform: CameraTransform) {
        currentTransform = transform
        streamManager?.transform = transform
    }

    fun getOutputSurface(): Surface? = imageReader?.surface

    fun isRunning(): Boolean = isActive

    fun getCurrentSource(): VideoSource = currentSource

    fun getCurrentTransform(): CameraTransform = currentTransform

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, VirtualCameraService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                if (currentSource.isValid())
                    "Active: ${currentSource.getLabel()}"
                else
                    "Virtual camera running"
            )
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VCamera Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Virtual Camera background service"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
        captureSession?.close()
        cameraDevice?.close()
        imageReader?.close()
        streamManager?.release()
        Log.d(TAG, "VirtualCameraService destroyed")
    }

    companion object {
        const val ACTION_START = "com.vcamera.START"
        const val ACTION_STOP = "com.vcamera.STOP"
        const val ACTION_SET_SOURCE = "com.vcamera.SET_SOURCE"

        fun start(context: Context) {
            val intent = Intent(context, VirtualCameraService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VirtualCameraService::class.java))
        }
    }
}
