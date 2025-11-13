package com.example.minimalnativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), Camera2Manager.FrameListener {

    private lateinit var cameraView: CameraGLView
    private lateinit var captureButton: Button
    private lateinit var cameraManager: Camera2Manager

    private var isCameraRunning = false

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.cameraGLView)
        captureButton = findViewById(R.id.captureBtn)
        cameraManager = Camera2Manager(this, this)

        captureButton.setOnClickListener {
            if (isCameraRunning) {
                stopCamera()
            } else {
                startCamera()
            }
        }

        requestCameraPermission()
        updateCaptureButton()
    }

    override fun onResume() {
        super.onResume()
        cameraView.onResume()
        if (!isCameraRunning && hasCameraPermission()) {
            startCamera()
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
        val processed = try {
            NativeBridge.processFrame(bytes, width, height)
        } catch (ex: UnsatisfiedLinkError) {
            Log.e(TAG, "Native processing not available", ex)
            bytes
        } catch (ex: Exception) {
            Log.e(TAG, "Native processing failed", ex)
            bytes
        }

        cameraView.updateTexture(processed, width, height)
    }

    private fun startCamera() {
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }

        if (isCameraRunning) return

        try {
            cameraManager.openCamera()
            isCameraRunning = true
            updateCaptureButton()
        } catch (ex: SecurityException) {
            Log.e(TAG, "Missing camera permission", ex)
            requestCameraPermission()
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to start camera", ex)
            Toast.makeText(this, "Unable to start camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopCamera() {
        if (!isCameraRunning) return
        cameraManager.closeCamera()
        isCameraRunning = false
        updateCaptureButton()
    }

    private fun updateCaptureButton() {
        captureButton.text = if (isCameraRunning) {
            getString(R.string.button_stop_camera)
        } else {
            getString(R.string.button_start_camera)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
