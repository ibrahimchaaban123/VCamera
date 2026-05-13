package com.vcamera.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.appcompat.app.AppCompatActivity
import com.vcamera.app.R
import com.vcamera.app.service.VirtualCameraService

/**
 * Shows a live preview of what the virtual camera is outputting.
 */
class PreviewActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private var cameraService: VirtualCameraService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            cameraService = (service as VirtualCameraService.LocalBinder).getService()
            serviceBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            cameraService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preview)

        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.holder.addCallback(this)

        bindService(
            Intent(this, VirtualCameraService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Surface ready — can display preview
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface size changed
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Cleanup
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) unbindService(serviceConnection)
    }
}
