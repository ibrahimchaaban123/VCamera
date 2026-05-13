package com.vcamera.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vcamera.app.R
import com.vcamera.app.model.VideoSource

/**
 * Dedicated activity for picking a video/image source.
 * Returns the selected source via setResult.
 */
class SourcePickerActivity : AppCompatActivity() {

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            returnSource(VideoSource.Image(uri, getFileName(uri)))
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private val videoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            returnSource(VideoSource.LocalVideo(uri, getFileName(uri)))
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_picker)

        val type = intent.getStringExtra(EXTRA_SOURCE_TYPE)
        when (type) {
            TYPE_IMAGE -> imagePicker.launch("image/*")
            TYPE_VIDEO -> videoPicker.launch("video/*")
            else -> {
                Toast.makeText(this, "Unknown source type", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun returnSource(source: VideoSource) {
        val data = Intent().apply {
            putExtra(RESULT_SOURCE_LABEL, source.getLabel())
            when (source) {
                is VideoSource.Image -> {
                    putExtra(RESULT_SOURCE_TYPE, TYPE_IMAGE)
                    putExtra(RESULT_SOURCE_URI, source.uri.toString())
                }
                is VideoSource.LocalVideo -> {
                    putExtra(RESULT_SOURCE_TYPE, TYPE_VIDEO)
                    putExtra(RESULT_SOURCE_URI, source.uri.toString())
                }
                else -> {}
            }
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun getFileName(uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: uri.lastPathSegment ?: "Media"
    }

    companion object {
        const val EXTRA_SOURCE_TYPE = "source_type"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val RESULT_SOURCE_TYPE = "result_type"
        const val RESULT_SOURCE_URI = "result_uri"
        const val RESULT_SOURCE_LABEL = "result_label"
    }
}
