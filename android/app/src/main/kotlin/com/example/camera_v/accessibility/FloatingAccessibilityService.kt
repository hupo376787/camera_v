package com.example.camera_v.accessibility

import android.accessibilityservice.AccessibilityService
import androidx.core.content.ContextCompat
import android.view.accessibility.AccessibilityEvent
import com.example.camera_v.service.FloatingCameraService

class FloatingAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return
        }
        if (event.packageName?.toString() != packageName) {
            return
        }
        // Accessibility layer only forwards trigger events to service, no camera logic here.
        ContextCompat.startForegroundService(this, FloatingCameraService.buildTakePhotoIntent(this))
    }

    override fun onInterrupt() = Unit
}
