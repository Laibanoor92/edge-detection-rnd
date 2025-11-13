package com.example.minimalnativeapp.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2Manager(
    context: Context,
    private val frameListener: FrameListener
) {

    interface FrameListener {
        fun onFrameAvailable(bytes: ByteArray, width: Int, height: Int)
    }

    private val appContext = context.applicationContext
    private val cameraManager =
        appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    @Volatile private var previewSurfaceTexture: SurfaceTexture? = null
    @Volatile private var previewSurface: Surface? = null

    private var previewSize: Size? = null
    private var cameraId: String? = null

    fun setPreviewSurface(texture: SurfaceTexture, width: Int, height: Int) {
        previewSurface?.release()
        previewSurface = Surface(texture)
        previewSurfaceTexture = texture.apply {
            setDefaultBufferSize(width, height)
        }
        previewSize?.let { size ->
            previewSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
        }
    }

    fun openCamera() {
        val surface = previewSurface
        if (surface == null) {
            Log.w(TAG, "Preview surface is not ready yet; cannot open camera")
            return
        }

        try {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                throw SecurityException("Camera permission not granted")
            }

            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }

            startBackgroundThread()
            val targetCameraId = selectBackCameraId()
                ?: throw IllegalStateException("No back-facing camera available")
            cameraId = targetCameraId
            prepareImageReader(targetCameraId)

            cameraManager.openCamera(targetCameraId, stateCallback, backgroundHandler)
        } catch (ex: CameraAccessException) {
            Log.e(TAG, "Unable to open camera", ex)
            cameraOpenCloseLock.release()
        } catch (ex: InterruptedException) {
            throw RuntimeException("Interrupted while opening camera", ex)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected error opening camera", ex)
            cameraOpenCloseLock.release()
        }
    }

    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            previewSurface?.release()
            previewSurface = null
        } catch (ex: InterruptedException) {
            throw RuntimeException("Interrupted while closing camera", ex)
        } finally {
            cameraOpenCloseLock.release()
            stopBackgroundThread()
        }
    }

    private fun selectBackCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            lensFacing == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    private fun prepareImageReader(cameraId: String) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: throw IllegalStateException("Stream configuration unavailable for camera $cameraId")

        val size = chooseOptimalSize(configMap)
        previewSize = size
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            size.width,
            size.height,
            ImageFormat.YUV_420_888,
            IMAGE_READER_MAX_IMAGES
        ).apply {
            setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)
        }

        previewSurfaceTexture?.setDefaultBufferSize(size.width, size.height)
    }

    private fun chooseOptimalSize(configMap: StreamConfigurationMap): Size {
        val sizes = configMap.getOutputSizes(ImageFormat.YUV_420_888)
        require(!sizes.isNullOrEmpty()) { "No YUV_420_888 output sizes available" }

        val preferred = sizes.filter { it.width <= MAX_PREVIEW_WIDTH && it.height <= MAX_PREVIEW_HEIGHT }
        return (preferred.takeIf { it.isNotEmpty() } ?: sizes.toList())
            .maxByOrNull { it.width * it.height }!!
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createCaptureSession(device)
            cameraOpenCloseLock.release()
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            cameraDevice = null
            cameraOpenCloseLock.release()
        }

        override fun onError(device: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error $error on device ${device.id}")
            device.close()
            cameraDevice = null
            cameraOpenCloseLock.release()
        }
    }

    private fun createCaptureSession(device: CameraDevice) {
        val readerSurface = imageReader?.surface
            ?: throw IllegalStateException("ImageReader surface not ready")
        val preview = previewSurface
            ?: throw IllegalStateException("Preview surface not initialized")

        try {
            device.createCaptureSession(listOf(readerSurface, preview), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (device !== cameraDevice) return
                    captureSession = session
                    startRepeatingRequest(session, device, readerSurface, preview)
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera capture session configuration failed")
                }
            }, backgroundHandler)
        } catch (ex: CameraAccessException) {
            Log.e(TAG, "Failed to create capture session", ex)
        }
    }

    private fun startRepeatingRequest(
        session: CameraCaptureSession,
        device: CameraDevice,
        readerSurface: Surface,
        previewSurface: Surface
    ) {
        try {
            val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(readerSurface)
                addTarget(previewSurface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            session.setRepeatingRequest(requestBuilder.build(), null, backgroundHandler)
        } catch (ex: CameraAccessException) {
            Log.e(TAG, "Unable to start repeating request", ex)
        }
    }

    private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage() ?: return@OnImageAvailableListener

        try {
            val argb = convertYuvToArgb(image)
            frameListener.onFrameAvailable(argb, image.width, image.height)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to process camera frame", ex)
        } finally {
            image.close()
        }
    }

    private fun convertYuvToArgb(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val planes = image.planes

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val output = ByteArray(width * height * 4)

        val yData = yBuffer.toByteArray()
        val uData = uBuffer.toByteArray()
        val vData = vBuffer.toByteArray()

        var outputIndex = 0
        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val uvRowOffset = (row / 2) * uvRowStride
            for (col in 0 until width) {
                val uvOffset = uvRowOffset + (col / 2) * uvPixelStride

                val yValue = 0xFF and yData[yRowOffset + col].toInt()
                val uValue = (0xFF and uData[uvOffset].toInt()) - 128
                val vValue = (0xFF and vData[uvOffset].toInt()) - 128

                val r = (yValue + 1.370705f * vValue).toInt().coerceIn(0, 255)
                val g = (yValue - 0.337633f * uValue - 0.698001f * vValue).toInt().coerceIn(0, 255)
                val b = (yValue + 1.732446f * uValue).toInt().coerceIn(0, 255)

                output[outputIndex++] = r.toByte()
                output[outputIndex++] = g.toByte()
                output[outputIndex++] = b.toByte()
                output[outputIndex++] = 0xFF.toByte()
            }
        }

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        return output
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val bytes = ByteArray(remaining())
        get(bytes)
        return bytes
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("Camera2Background").also {
            it.start()
            backgroundHandler = Handler(it.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (ex: InterruptedException) {
            Log.e(TAG, "Interrupted while stopping background thread", ex)
        } finally {
            backgroundThread = null
            backgroundHandler = null
        }
    }

    companion object {
        private const val TAG = "Camera2Manager"
        private const val IMAGE_READER_MAX_IMAGES = 2
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
    }
}
