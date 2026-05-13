package com.vcamera.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vcamera.app.R
import com.vcamera.app.databinding.ActivityMainBinding
import com.vcamera.app.model.CameraTransform
import com.vcamera.app.model.VideoSource
import com.vcamera.app.service.VirtualCameraService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var cameraService: VirtualCameraService? = null
    private var serviceBound = false

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startService()
        } else {
            Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    // Image picker
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val source = VideoSource.Image(uri, getFileName(uri))
            viewModel.setSource(source)
            cameraService?.setSource(source)
            updateSourceUI(source)
        }
    }

    // Video picker
    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val source = VideoSource.LocalVideo(uri, getFileName(uri))
            viewModel.setSource(source)
            cameraService?.setSource(source)
            updateSourceUI(source)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VirtualCameraService.LocalBinder
            cameraService = binder.getService()
            serviceBound = true
            updateServiceUI(true)

            // Restore state
            viewModel.source.value?.let { cameraService?.setSource(it) }
            viewModel.transform.value?.let { cameraService?.setTransform(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cameraService = null
            serviceBound = false
            updateServiceUI(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Toggle service button
        binding.btnToggleService.setOnClickListener {
            if (serviceBound) {
                stopService()
            } else {
                checkPermissionsAndStart()
            }
        }

        // Source selection
        binding.btnPickImage.setOnClickListener {
            imagePicker.launch("image/*")
        }

        binding.btnPickVideo.setOnClickListener {
            videoPicker.launch("video/*")
        }

        binding.btnNetworkStream.setOnClickListener {
            showNetworkStreamDialog()
        }

        binding.btnClearSource.setOnClickListener {
            val source = VideoSource.None
            viewModel.setSource(source)
            cameraService?.setSource(source)
            updateSourceUI(source)
        }

        // Transform controls
        binding.btnZoomIn.setOnClickListener {
            viewModel.zoomIn()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnZoomOut.setOnClickListener {
            viewModel.zoomOut()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnRotateLeft.setOnClickListener {
            viewModel.rotateLeft()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnRotateRight.setOnClickListener {
            viewModel.rotateRight()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnFlipH.setOnClickListener {
            viewModel.flipHorizontal()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnFlipV.setOnClickListener {
            viewModel.flipVertical()
            cameraService?.setTransform(viewModel.transform.value!!)
        }

        binding.btnResetTransform.setOnClickListener {
            viewModel.resetTransform()
            cameraService?.setTransform(CameraTransform.DEFAULT)
            binding.seekbarZoom.progress = 50
        }

        // Zoom seekbar
        binding.seekbarZoom.max = 100
        binding.seekbarZoom.progress = 50
        binding.seekbarZoom.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Map 0-100 → 4.0 to 0.25 (inverted: higher progress = more zoom)
                    val zoom = 4f - (progress / 100f) * (4f - 0.25f)
                    viewModel.setZoom(zoom)
                    cameraService?.setTransform(viewModel.transform.value!!)
                    binding.tvZoomLevel.text = "%.1fx".format(1f / zoom)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun observeViewModel() {
        viewModel.source.observe(this) { source ->
            updateSourceUI(source)
        }

        viewModel.transform.observe(this) { transform ->
            binding.tvZoomLevel.text = "%.1fx".format(1f / transform.zoom)
        }
    }

    private fun showNetworkStreamDialog() {
        val input = android.widget.EditText(this).apply {
            hint = "rtmp://... or https://..."
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Network Stream URL")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    val source = VideoSource.NetworkStream(url, "Stream: $url")
                    viewModel.setSource(source)
                    cameraService?.setSource(source)
                    updateSourceUI(source)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSourceUI(source: VideoSource) {
        binding.tvCurrentSource.text = when (source) {
            is VideoSource.None -> "No source selected"
            else -> "Source: ${source.getLabel()}"
        }
        binding.tvSourceStatus.text = if (source.isValid()) "✓ Active" else "○ Inactive"
        binding.tvSourceStatus.setTextColor(
            ContextCompat.getColor(this,
                if (source.isValid()) android.R.color.holo_green_dark
                else android.R.color.darker_gray
            )
        )
    }

    private fun updateServiceUI(running: Boolean) {
        binding.btnToggleService.text = if (running) "Stop Virtual Camera" else "Start Virtual Camera"
        binding.btnToggleService.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (running) android.R.color.holo_red_light else android.R.color.holo_green_dark
        )
        binding.layoutControls.visibility = if (running) View.VISIBLE else View.GONE
        binding.tvServiceStatus.text = if (running) "🟢 Running" else "🔴 Stopped"
    }

    private fun checkPermissionsAndStart() {
        val needed = buildList {
            if (!hasPermission(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) {
                if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) add(Manifest.permission.READ_MEDIA_IMAGES)
                if (!hasPermission(Manifest.permission.READ_MEDIA_VIDEO)) add(Manifest.permission.READ_MEDIA_VIDEO)
                if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (needed.isEmpty()) {
            startService()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun startService() {
        VirtualCameraService.start(this)
        val intent = Intent(this, VirtualCameraService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        viewModel.setServiceRunning(true)
    }

    private fun stopService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        VirtualCameraService.stop(this)
        updateServiceUI(false)
        viewModel.setServiceRunning(false)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "Unknown"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
        }
    }
}
