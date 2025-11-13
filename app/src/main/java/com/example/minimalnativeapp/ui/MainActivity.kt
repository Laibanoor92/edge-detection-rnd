package com.example.minimalnativeapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.minimalnativeapp.R
import com.example.minimalnativeapp.camera.Camera2Manager
import com.example.minimalnativeapp.gl.CameraGLView
import com.example.minimalnativeapp.nativebridge.NativeBridge
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class MainActivity : AppCompatActivity(), Camera2Manager.FrameListener {

    private lateinit var previewView: TextureView
    private lateinit var cameraView: CameraGLView
    private lateinit var captureButton: Button
    private lateinit var toggleButton: Button
    private lateinit var cameraManager: Camera2Manager

    private var isCameraRunning = false
    private var showProcessed = true

    private var lastProcessedFrame: ByteArray? = null
    private var lastRawFrame: ByteArray? = null
    private var lastFrameWidth: Int = 0
    private var lastFrameHeight: Int = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val granted = REQUIRED_PERMISSIONS.all { permission ->
                results[permission] == true || hasPermission(permission)
            }
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permissions_required_message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val surfaceListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            cameraManager.setPreviewSurface(surface, width, height)
            if (hasAllPermissions()) {
                startCamera()
            }
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            cameraManager.setPreviewSurface(surface, width, height)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            stopCamera()
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.cameraPreview)
        cameraView = findViewById(R.id.cameraGLView)
        captureButton = findViewById(R.id.captureBtn)
        toggleButton = findViewById(R.id.toggleBtn)
        cameraManager = Camera2Manager(this, this)

        previewView.surfaceTextureListener = surfaceListener

        captureButton.setText(R.string.button_capture_frame)
        captureButton.setOnClickListener { saveLastFrame() }

        toggleButton.setOnClickListener {
            showProcessed = !showProcessed
            updateToggleButton()
            cameraView.setDisplayModeProcessed(showProcessed)
        }
        updateToggleButton()
    }

    override fun onResume() {
        super.onResume()
        cameraView.onResume()
        if (!previewView.isAvailable) {
            previewView.surfaceTextureListener = surfaceListener
        } else {
            cameraManager.setPreviewSurface(
                previewView.surfaceTexture,
                previewView.width,
                previewView.height
            )
        }
        if (hasAllPermissions()) {
            startCamera()
        } else {
            requestRequiredPermissions()
        }
    }

    override fun onPause() {
        if (isCameraRunning) {
            stopCamera()
        }
        cameraView.onPause()
        super.onPause()
    }

    override fun onFrameAvailable(bytes: ByteArray, width: Int, height: Int) {
        val rawCopy = bytes.clone()
        val processed = try {
            NativeBridge.processFrame(bytes, width, height)
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "Native processing library not loaded", error)
            rawCopy
        } catch (error: Exception) {
            Log.e(TAG, "Native processing failed", error)
            rawCopy
        }

        lastRawFrame = rawCopy
        lastProcessedFrame = processed.clone()
        lastFrameWidth = width
        lastFrameHeight = height

        cameraView.updateRawFrame(rawCopy, width, height)
        cameraView.updateTexture(processed, width, height)
        cameraView.setDisplayModeProcessed(showProcessed)
    }

    private fun startCamera() {
        if (!hasAllPermissions()) {
            requestRequiredPermissions()
            return
        }
        if (!previewView.isAvailable || isCameraRunning) return

        try {
            cameraManager.openCamera()
            isCameraRunning = true
        } catch (error: SecurityException) {
            Log.e(TAG, "Missing permissions when starting camera", error)
            requestRequiredPermissions()
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start camera", error)
            Toast.makeText(this, R.string.error_start_camera, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCamera() {
        if (!isCameraRunning) return
        cameraManager.closeCamera()
        isCameraRunning = false
    }

    private fun saveLastFrame() {
        if (!hasStoragePermission()) {
            requestRequiredPermissions()
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_SHORT).show()
            return
        }

        val frame = lastProcessedFrame
        if (frame == null || lastFrameWidth <= 0 || lastFrameHeight <= 0) {
            Toast.makeText(this, R.string.capture_no_frame, Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = Bitmap.createBitmap(lastFrameWidth, lastFrameHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(frame))

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            Toast.makeText(this, R.string.capture_download_dir_failed, Toast.LENGTH_SHORT).show()
            return
        }

        val outputFile = File(downloadsDir, OUTPUT_FILENAME)
        try {
            FileOutputStream(outputFile).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Bitmap compression returned false.")
                }
            }
            MediaScannerConnection.scanFile(
                this,
                arrayOf(outputFile.absolutePath),
                arrayOf("image/png"),
                null
            )
            Toast.makeText(this, getString(R.string.capture_saved, outputFile.absolutePath), Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Processed frame saved to ${outputFile.absolutePath}")
        } catch (error: IOException) {
            Log.e(TAG, "Failed to save processed frame", error)
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestRequiredPermissions() {
        permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            when {
                permission == Manifest.permission.WRITE_EXTERNAL_STORAGE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> true
                else -> hasPermission(permission)
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateToggleButton() {
        toggleButton.setText(
            if (showProcessed) R.string.button_show_raw else R.string.button_show_edges
        )
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val OUTPUT_FILENAME = "processed.png"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}
