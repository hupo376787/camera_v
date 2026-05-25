package com.example.camera_v

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import java.io.ByteArrayOutputStream
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera_v.service.FloatingCameraService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private lateinit var channel: MethodChannel

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FloatingCameraService.ACTION_CALLBACK_PHOTO_SAVED -> {
                    val uri = intent.getStringExtra(FloatingCameraService.EXTRA_URI) ?: return
                    channel.invokeMethod("onPhotoSaved", uri)
                }

                FloatingCameraService.ACTION_CALLBACK_STATUS_CHANGED -> {
                    channel.invokeMethod(
                        "onServiceStatusChanged",
                        intent.getBooleanExtra(FloatingCameraService.EXTRA_RUNNING, false),
                    )
                }

                FloatingCameraService.ACTION_CALLBACK_ERROR -> {
                    val message = intent.getStringExtra(FloatingCameraService.EXTRA_ERROR) ?: return
                    channel.invokeMethod("onError", message)
                }
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            "floating_camera_channel",
        )
        channel.setMethodCallHandler(::onMethodCall)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(FloatingCameraService.ACTION_CALLBACK_PHOTO_SAVED)
            addAction(FloatingCameraService.ACTION_CALLBACK_STATUS_CHANGED)
            addAction(FloatingCameraService.ACTION_CALLBACK_ERROR)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(eventReceiver, filter)
        }
    }

    override fun onPause() {
        unregisterReceiver(eventReceiver)
        super.onPause()
    }

    private fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        // Flutter only talks through this channel; all camera work stays inside foreground service.
        when (call.method) {
            "startService" -> {
                if (!ensureCameraPermission()) {
                    result.error("permission", "Camera permission is required", null)
                    return
                }
                startFloatingService(FloatingCameraService.ACTION_START_SERVICE)
                result.success(null)
            }

            "stopService" -> {
                startFloatingService(FloatingCameraService.ACTION_STOP_SERVICE)
                result.success(null)
            }

            "takePhoto" -> {
                startFloatingService(FloatingCameraService.ACTION_TAKE_PHOTO)
                result.success(null)
            }

            "toggleFloatingBall" -> {
                startFloatingService(FloatingCameraService.ACTION_TOGGLE_FLOATING_BALL)
                result.success(null)
            }

            "switchCamera" -> {
                startFloatingService(FloatingCameraService.ACTION_SWITCH_CAMERA)
                result.success(null)
            }

            "updateSettings" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("resolution", args["resolution"] as? String ?: "1920x1080")
                    .putBoolean("flashEnabled", args["flashEnabled"] as? Boolean ?: false)
                    .putBoolean("autoFocus", args["autoFocus"] as? Boolean ?: true)
                    .apply()
                result.success(null)
            }

            "getSettings" -> {
                val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
                result.success(
                    mapOf(
                        "resolution" to (prefs.getString("resolution", "1920x1080") ?: "1920x1080"),
                        "flashEnabled" to prefs.getBoolean("flashEnabled", false),
                        "autoFocus" to prefs.getBoolean("autoFocus", true),
                    ),
                )
            }

            "getGalleryPhotos" -> {
                result.success(loadGalleryUris())
            }

            "getPhotoBytes" -> {
                val uriString = (call.arguments as? Map<*, *>)?.get("uri") as? String
                if (uriString.isNullOrBlank()) {
                    result.success(null)
                    return
                }
                result.success(loadPhotoBytes(uriString))
            }

            "openOverlayPermission" -> {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
                result.success(null)
            }

            "openAccessibilityPermission" -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                result.success(null)
            }

            "openNotificationPermission" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        301,
                    )
                }
                result.success(null)
            }

            "openCameraPermissionPage" -> {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 302)
                result.success(null)
            }

            else -> result.notImplemented()
        }
    }

    private fun ensureCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 401)
        return false
    }

    private fun startFloatingService(action: String) {
        val intent = Intent(this, FloatingCameraService::class.java).setAction(action)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadGalleryUris(): List<String> {
        val uris = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        // RELATIVE_PATH was introduced in API 29 and is available because this app targets minSdk 31.
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Pictures/CameraV/%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sort,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "$id")
                uris.add(uri.toString())
            }
        }
        return uris
    }

    private fun loadPhotoBytes(uriString: String): ByteArray? {
        return runCatching {
            val uri = Uri.parse(uriString)
            contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        }.getOrNull()
    }
}
