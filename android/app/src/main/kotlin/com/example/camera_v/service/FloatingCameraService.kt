package com.example.camera_v.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.camera_v.R
import com.example.camera_v.camera.Camera2Controller
import com.example.camera_v.media.MediaStoreHelper
import com.example.camera_v.overlay.FloatingBallView

class FloatingCameraService : Service() {
    private lateinit var cameraController: Camera2Controller
    private lateinit var mediaStoreHelper: MediaStoreHelper
    private var floatingBallView: FloatingBallView? = null
    private var lensFacing = CameraCharacteristics.LENS_FACING_BACK
    private var cameraReady = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        mediaStoreHelper = MediaStoreHelper(this)
        cameraController = Camera2Controller(
            context = this,
            onPhotoTaken = { bytes ->
                // Persist every JPEG through MediaStore to make it visible in system gallery apps.
                val uri = mediaStoreHelper.saveJpeg(bytes)
                if (uri == null) {
                    notifyError("Saving photo failed")
                } else {
                    notifyPhotoSaved(uri.toString())
                }
            },
            onError = { message -> notifyError(message) },
            onReady = {
                cameraReady = true
            },
        )
        startForegroundServiceInternal()
        startCamera()
        showFloatingBall()
        notifyStatus(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> stopSelfSafely()
            ACTION_TAKE_PHOTO -> takePhoto()
            ACTION_TOGGLE_FLOATING_BALL -> toggleFloatingBall()
            ACTION_SWITCH_CAMERA -> switchCamera()
            ACTION_START_SERVICE, null -> notifyStatus(true)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideFloatingBall()
        cameraReady = false
        cameraController.stop()
        isRunning = false
        notifyStatus(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceInternal() {
        val channelId = "floating_camera_channel_id"
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_desc)
                enableLights(false)
                lightColor = Color.GREEN
            }
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_desc))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(101, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(101, notification)
        }
    }

    private fun startCamera() {
        cameraReady = false
        val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
        val (width, height) = parseResolution(prefs.getString("resolution", "1920x1080") ?: "1920x1080")
        cameraController.setLensFacing(lensFacing)
        cameraController.start(Size(width, height))
    }

    private fun takePhoto() {
        if (!cameraReady || !cameraController.isReady()) {
            notifyError("Camera is not ready yet, please retry")
            return
        }
        val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
        cameraController.takePhoto(
            flashEnabled = prefs.getBoolean("flashEnabled", false),
            autoFocus = prefs.getBoolean("autoFocus", true),
        )
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        startCamera()
    }

    private fun parseResolution(resolution: String): Pair<Int, Int> {
        val split = resolution.split("x")
        if (split.size != 2) return 1920 to 1080
        return (split[0].toIntOrNull() ?: 1920) to (split[1].toIntOrNull() ?: 1080)
    }

    private fun toggleFloatingBall() {
        if (floatingBallView == null) showFloatingBall() else hideFloatingBall()
    }

    private fun showFloatingBall() {
        // Defensive check: service can also be recreated by system outside MainActivity flow.
        if (!Settings.canDrawOverlays(this)) {
            notifyError("Overlay permission not granted, cannot show floating ball")
            return
        }
        if (floatingBallView != null) return
        val windowManager = getSystemService(WindowManager::class.java)
        val params = FloatingBallView.createLayoutParams()
        // Floating ball click is user-driven and triggers compliant background capture.
        floatingBallView = FloatingBallView(
            context = this,
            windowManager = windowManager,
            params = params,
            onBallClicked = { takePhoto() },
            onError = { error -> notifyError(error) },
        )
        runCatching {
            windowManager.addView(floatingBallView, params)
        }.onFailure { error ->
            Log.e(TAG, "Adding floating ball failed", error)
            floatingBallView = null
            notifyError("Failed to show floating ball: ${error.message ?: "unknown error"}")
        }
    }

    private fun hideFloatingBall() {
        val windowManager = getSystemService(WindowManager::class.java)
        runCatching {
            floatingBallView?.let(windowManager::removeView)
        }.onFailure { error ->
            Log.e(TAG, "Removing floating ball failed", error)
            notifyError("Failed to hide floating ball: ${error.message ?: "unknown error"}")
        }
        floatingBallView = null
    }

    private fun stopSelfSafely() {
        notifyStatus(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyPhotoSaved(uri: String) {
        sendBroadcast(
            Intent(ACTION_CALLBACK_PHOTO_SAVED)
                .putExtra(EXTRA_URI, uri),
        )
    }

    private fun notifyStatus(running: Boolean) {
        sendBroadcast(
            Intent(ACTION_CALLBACK_STATUS_CHANGED)
                .putExtra(EXTRA_RUNNING, running),
        )
    }

    private fun notifyError(message: String) {
        sendBroadcast(
            Intent(ACTION_CALLBACK_ERROR)
                .putExtra(EXTRA_ERROR, message),
        )
    }

    companion object {
        const val ACTION_START_SERVICE = "com.example.camera_v.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.camera_v.action.STOP_SERVICE"
        const val ACTION_TAKE_PHOTO = "com.example.camera_v.action.TAKE_PHOTO"
        const val ACTION_TOGGLE_FLOATING_BALL = "com.example.camera_v.action.TOGGLE_FLOATING_BALL"
        const val ACTION_SWITCH_CAMERA = "com.example.camera_v.action.SWITCH_CAMERA"

        const val ACTION_CALLBACK_PHOTO_SAVED = "com.example.camera_v.callback.PHOTO_SAVED"
        const val ACTION_CALLBACK_STATUS_CHANGED = "com.example.camera_v.callback.STATUS_CHANGED"
        const val ACTION_CALLBACK_ERROR = "com.example.camera_v.callback.ERROR"

        const val EXTRA_URI = "extra_uri"
        const val EXTRA_RUNNING = "extra_running"
        const val EXTRA_ERROR = "extra_error"
        private const val TAG = "FloatingCameraService"
        @Volatile
        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning

        fun buildTakePhotoIntent(context: Context): Intent {
            return Intent(context, FloatingCameraService::class.java).setAction(ACTION_TAKE_PHOTO)
        }
    }
}
