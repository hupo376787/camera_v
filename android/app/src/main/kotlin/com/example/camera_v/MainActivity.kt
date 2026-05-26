package com.example.camera_v

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import java.io.ByteArrayOutputStream
import java.util.UUID
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera_v.service.FloatingCameraService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private companion object {
        const val REQUEST_PICK_COPY_FOLDER = 501
    }

    private lateinit var channel: MethodChannel
    private var eventReceiverRegistered = false
    private var pendingCopyResult: MethodChannel.Result? = null
    private var pendingCopyUris: List<String> = emptyList()

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                FloatingCameraService.ACTION_CALLBACK_PHOTO_SAVED -> {
                    if (!::channel.isInitialized) return
                    val uri = intent.getStringExtra(FloatingCameraService.EXTRA_URI) ?: return
                    channel.invokeMethod("onPhotoSaved", uri)
                }

                FloatingCameraService.ACTION_CALLBACK_STATUS_CHANGED -> {
                    if (!::channel.isInitialized) return
                    channel.invokeMethod(
                        "onServiceStatusChanged",
                        intent.getBooleanExtra(FloatingCameraService.EXTRA_RUNNING, false),
                    )
                }

                FloatingCameraService.ACTION_CALLBACK_ERROR -> {
                    if (!::channel.isInitialized) return
                    val message = intent.getStringExtra(FloatingCameraService.EXTRA_ERROR) ?: return
                    channel.invokeMethod("onError", message)
                }

                FloatingCameraService.ACTION_CALLBACK_CLOSE_APP -> {
                    // This callback must still close the task even if Flutter has not finished initializing.
                    if (!isFinishing) {
                        finishAndRemoveTask()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter().apply {
            addAction(FloatingCameraService.ACTION_CALLBACK_PHOTO_SAVED)
            addAction(FloatingCameraService.ACTION_CALLBACK_STATUS_CHANGED)
            addAction(FloatingCameraService.ACTION_CALLBACK_ERROR)
            addAction(FloatingCameraService.ACTION_CALLBACK_CLOSE_APP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(eventReceiver, filter)
        }
        eventReceiverRegistered = true
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
        if (::channel.isInitialized) {
            channel.invokeMethod("onServiceStatusChanged", isFloatingServiceRunning())
        }
    }

    override fun onDestroy() {
        if (eventReceiverRegistered) {
            unregisterReceiver(eventReceiver)
            eventReceiverRegistered = false
        }
        super.onDestroy()
    }

    private fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        // Flutter only talks through this channel; all camera work stays inside foreground service.
        when (call.method) {
            "startService" -> {
                val permissionError = validateStartServicePermissions()
                if (permissionError != null) {
                    result.error("permission", permissionError, null)
                    return
                }
                val started = startFloatingService(FloatingCameraService.ACTION_START_SERVICE)
                if (started) {
                    result.success(null)
                } else {
                    result.error("service_start_failed", "Failed to start foreground service", null)
                }
            }

            "isServiceRunning" -> {
                result.success(isFloatingServiceRunning())
            }

            "requestServiceStatus" -> {
                channel.invokeMethod("onServiceStatusChanged", isFloatingServiceRunning())
                result.success(null)
            }

            "stopService" -> {
                val dispatched = startFloatingService(FloatingCameraService.ACTION_STOP_SERVICE)
                if (dispatched) {
                    result.success(null)
                } else {
                    result.error("service_stop_failed", "Failed to stop foreground service", null)
                }
            }

            "takePhoto" -> {
                val dispatched = startFloatingService(FloatingCameraService.ACTION_TAKE_PHOTO)
                if (dispatched) {
                    result.success(null)
                } else {
                    result.error("service_command_failed", "Failed to dispatch take photo command", null)
                }
            }

            "toggleFloatingBall" -> {
                val dispatched = startFloatingService(FloatingCameraService.ACTION_TOGGLE_FLOATING_BALL)
                if (dispatched) {
                    result.success(null)
                } else {
                    result.error(
                        "service_command_failed",
                        "Failed to dispatch floating ball toggle command",
                        null,
                    )
                }
            }

            "switchCamera" -> {
                val dispatched = startFloatingService(FloatingCameraService.ACTION_SWITCH_CAMERA)
                if (dispatched) {
                    result.success(null)
                } else {
                    result.error("service_command_failed", "Failed to dispatch switch camera command", null)
                }
            }

            "updateSettings" -> {
                val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("resolution", args["resolution"] as? String ?: "max")
                    .putBoolean("flashEnabled", args["flashEnabled"] as? Boolean ?: false)
                    .putBoolean("autoFocus", args["autoFocus"] as? Boolean ?: true)
                    .apply()
                if (isFloatingServiceRunning()) {
                    startFloatingService(FloatingCameraService.ACTION_REFRESH_SETTINGS)
                }
                result.success(null)
            }

            "getSettings" -> {
                val prefs = getSharedPreferences("floating_camera_prefs", Context.MODE_PRIVATE)
                result.success(
                    mapOf(
                        "resolution" to (prefs.getString("resolution", "max") ?: "max"),
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

            "deletePhotos" -> {
                val uris: List<*> = (call.arguments as? Map<*, *>)?.get("uris") as? List<*>
                    ?: emptyList<Any>()
                result.success(deletePhotos(uris.mapNotNull { it as? String }))
            }

            "copyPhotosToPickedFolder" -> {
                val uris: List<*> = (call.arguments as? Map<*, *>)?.get("uris") as? List<*>
                    ?: emptyList<Any>()
                pickFolderAndCopyPhotos(uris.mapNotNull { it as? String }, result)
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

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_PICK_COPY_FOLDER) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        val result = pendingCopyResult ?: return
        val uris = pendingCopyUris
        pendingCopyResult = null
        pendingCopyUris = emptyList()

        val treeUri = data?.data
        if (resultCode != RESULT_OK || treeUri == null) {
            result.success(null)
            return
        }

        runCatching {
            val flags = data.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            if (flags != 0) {
                contentResolver.takePersistableUriPermission(treeUri, flags)
            } else {
                channel.invokeMethod("onError", "系统未返回可持久保存的文件夹访问权限，本次复制仍会继续")
            }
        }.onFailure { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            channel.invokeMethod("onError", "无法持久保存文件夹访问权限，本次复制仍会继续：$detail")
        }
        result.success(copyPhotosToTree(uris, treeUri))
    }

    private fun validateStartServicePermissions(): String? {
        if (!ensureCameraPermission()) {
            return "Camera permission is required"
        }
        if (!Settings.canDrawOverlays(this)) {
            return "Overlay permission is required"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Notification permission is required for Android 13 and above"
        }
        return null
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

    private fun startFloatingService(action: String): Boolean {
        val intent = Intent(this, FloatingCameraService::class.java).setAction(action)
        return runCatching {
            ContextCompat.startForegroundService(this, intent) != null
        }.getOrElse { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            channel.invokeMethod(
                "onError",
                "Failed to execute service command ($action): $detail",
            )
            false
        }
    }

    private fun isFloatingServiceRunning(): Boolean {
        return FloatingCameraService.isServiceRunning()
    }

    private fun pickFolderAndCopyPhotos(uriStrings: List<String>, result: MethodChannel.Result) {
        if (pendingCopyResult != null) {
            result.error("folder_picker_busy", "文件夹选择器已打开，请先完成当前选择", null)
            return
        }
        pendingCopyResult = result
        pendingCopyUris = uriStrings

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_COPY_FOLDER)
        } catch (error: ActivityNotFoundException) {
            pendingCopyResult = null
            pendingCopyUris = emptyList()
            result.error(
                "folder_picker_unavailable",
                "无法打开系统文件夹选择器，请确认设备已安装文件管理器后重试",
                null,
            )
        }
    }

    private fun loadGalleryUris(): List<String> {
        val uris = mutableListOf<String>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        // RELATIVE_PATH was introduced in API 29, and minSdk 31 guarantees availability without extra checks.
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

    private fun deletePhotos(uriStrings: List<String>): Int {
        return uriStrings.count { uriString ->
            runCatching {
                contentResolver.delete(Uri.parse(uriString), null, null) > 0
            }.getOrDefault(false)
        }
    }

    private fun copyPhotosToTree(uriStrings: List<String>, treeUri: Uri): Int {
        return uriStrings.count { uriString ->
            copyPhotoToTree(uriString, treeUri)
        }
    }

    private fun copyPhotoToTree(uriString: String, treeUri: Uri): Boolean {
        val sourceUri = Uri.parse(uriString)
        val bytes = loadPhotoBytes(uriString) ?: return false
        val mimeType = contentResolver.getType(sourceUri) ?: "image/jpeg"
        val name = displayName(sourceUri) ?: fallbackPhotoName(mimeType)
        val targetFolderUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        return runCatching {
            val targetUri = DocumentsContract.createDocument(
                contentResolver,
                targetFolderUri,
                mimeType,
                name,
            ) ?: return@runCatching false
            contentResolver.openOutputStream(targetUri)?.use { output ->
                output.write(bytes)
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private fun displayName(uri: Uri): String? {
        return contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
        }
    }

    private fun fallbackPhotoName(mimeType: String): String {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        return "IMG_${UUID.randomUUID()}.$extension"
    }
}
