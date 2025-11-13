package com.example.minimalnativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var cameraView: GLSurfaceView
    private lateinit var captureButton: Button

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op for now */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.cameraGLView)
        captureButton = findViewById(R.id.captureBtn)

        captureButton.setOnClickListener {
            // TODO: add capture logic
        }

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        val permissionState = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
