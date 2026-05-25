package com.example.camera_v.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreHelper(private val context: Context) {
    fun saveJpeg(bytes: ByteArray): Uri? {
        val name = "IMG_${timestamp()}.jpg"
        // Android 10+ path uses scoped storage and keeps files in Pictures/CameraV.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToMediaStoreQ(name, bytes)
        } else {
            saveLegacy(name, bytes)
        }
    }

    private fun saveToMediaStoreQ(name: String, bytes: ByteArray): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraV/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    private fun saveLegacy(name: String, bytes: ByteArray): Uri? {
        val folder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CameraV",
        ).apply { mkdirs() }
        val file = File(folder, name)
        FileOutputStream(file).use { it.write(bytes) }
        return Uri.fromFile(file)
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    }
}
