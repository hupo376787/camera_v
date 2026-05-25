package com.example.camera_v.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat

class Camera2Controller(
    private val context: Context,
    private val onPhotoTaken: (ByteArray) -> Unit,
    private val onError: (String) -> Unit,
) {
    // Single lock protects camera state transitions across service callbacks.
    private val lock = Any()
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var cameraId: String? = null
    private var lensFacing: Int = CameraCharacteristics.LENS_FACING_BACK
    private var reconnectAttempts = 0

    private val thread = HandlerThread("Camera2Controller").apply { start() }
    private val handler = Handler(thread.looper)
    private val maxReconnectAttempts = DEFAULT_MAX_RECONNECT_ATTEMPTS

    fun setLensFacing(facing: Int) {
        synchronized(lock) {
            lensFacing = facing
        }
    }

    fun start(resolution: Size) {
        synchronized(lock) {
            closeLocked()
            val selected = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == lensFacing
            }
            if (selected == null) {
                onError("No camera found for facing=$lensFacing")
                return
            }
            reconnectAttempts = 0
            cameraId = selected
            imageReader = ImageReader.newInstance(
                resolution.width,
                resolution.height,
                ImageFormat.JPEG,
                2,
            ).apply {
                // JPEG bytes are forwarded to service so they can be persisted in MediaStore.
                setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.use { image ->
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        onPhotoTaken(bytes)
                    }
                }, handler)
            }
            previewTexture = SurfaceTexture(0).apply { setDefaultBufferSize(1, 1) }
            previewSurface = Surface(previewTexture)
            openCameraLocked()
        }
    }

    fun takePhoto(flashEnabled: Boolean, autoFocus: Boolean) {
        synchronized(lock) {
            val camera = cameraDevice ?: run {
                onError("Camera is not opened")
                return
            }
            val session = captureSession ?: run {
                onError("Capture session is not ready")
                return
            }
            val readerSurface = imageReader?.surface ?: run {
                onError("ImageReader is not ready")
                return
            }

            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(readerSurface)
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    if (autoFocus) CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    else CaptureRequest.CONTROL_AF_MODE_OFF,
                )
                set(
                    CaptureRequest.CONTROL_AE_MODE,
                    if (flashEnabled) CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    else CaptureRequest.CONTROL_AE_MODE_ON,
                )
            }
            session.capture(request.build(), null, handler)
        }
    }

    fun stop() {
        synchronized(lock) {
            closeLocked()
            thread.quitSafely()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCameraLocked() {
        val id = cameraId ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Camera permission denied")
            return
        }
        cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                synchronized(lock) {
                    cameraDevice = camera
                    reconnectAttempts = 0
                    createSessionLocked()
                }
            }

            override fun onDisconnected(camera: CameraDevice) {
                synchronized(lock) {
                    camera.close()
                    cameraDevice = null
                    if (reconnectAttempts >= maxReconnectAttempts) {
                        onError("Camera disconnected and reconnection limit reached")
                        return
                    }
                    reconnectAttempts += 1
                    val retryDelayMs = reconnectAttempts * RETRY_DELAY_STEP_MS
                    handler.postDelayed({
                        synchronized(lock) {
                            if (cameraDevice == null) {
                                openCameraLocked()
                            }
                        }
                    }, retryDelayMs)
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                synchronized(lock) {
                    camera.close()
                    cameraDevice = null
                    onError("Camera open error: $error")
                }
            }
        }, handler)
    }

    private fun createSessionLocked() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        val preview = previewSurface ?: return
        camera.createCaptureSession(
            listOf(reader.surface, preview),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    synchronized(lock) {
                        captureSession = session
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(preview)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        }
                        session.setRepeatingRequest(request.build(), null, handler)
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    onError("Camera session configure failed")
                }
            },
            handler,
        )
    }

    private fun closeLocked() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        previewSurface?.release()
        previewSurface = null
        previewTexture?.release()
        previewTexture = null
    }

    companion object {
        private const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 3
        private const val RETRY_DELAY_STEP_MS = 1000L
    }
}
