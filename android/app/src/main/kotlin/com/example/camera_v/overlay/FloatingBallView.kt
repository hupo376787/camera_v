package com.example.camera_v.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.camera_v.R
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FloatingBallView(
    context: Context,
    private val windowManager: WindowManager,
    private val params: WindowManager.LayoutParams,
    private val onBallClicked: () -> Unit,
    private val onError: (String) -> Unit = {},
) : FrameLayout(context) {

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        // Visual floating trigger shown above all apps.
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_menu_camera)
            background = context.getDrawable(R.drawable.bg_ball)
            // Parent onTouchEvent handles gestures to distinguish drag vs click.
            isClickable = false
            isFocusable = false
            setPadding(16, 16, 16, 16)
            layoutParams = LayoutParams(DEFAULT_BALL_SIZE_PX, DEFAULT_BALL_SIZE_PX)
        }
        addView(icon)
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.floating_ball_content_description)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downX = params.x
                downY = params.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }
                params.x = downX + dx
                params.y = downY + dy
                updateLayoutSafely()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onBallClicked.invoke()
                } else {
                    snapToEdge()
                }
                isDragging = false
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun snapToEdge() {
        val displayWidth = context.resources.displayMetrics.widthPixels
        val rightEdge = (displayWidth - width).coerceAtLeast(0)
        params.x = if (params.x < displayWidth / 2) 0 else rightEdge
        updateLayoutSafely()
    }

    private fun updateLayoutSafely() {
        runCatching {
            windowManager.updateViewLayout(this, params)
        }.onFailure { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            onError("Failed to update floating ball position: $detail")
        }
    }

    companion object {
        private const val DEFAULT_BALL_SIZE_PX = 140
        private const val EDGE_MARGIN_PX = 32

        fun createLayoutParams(context: Context): WindowManager.LayoutParams {
            val bounds = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val initialX = (bounds.width() - DEFAULT_BALL_SIZE_PX - EDGE_MARGIN_PX).coerceAtLeast(0)
            val lowerThirdTop = bounds.height() * 2 / 3
            val initialY = (lowerThirdTop - (DEFAULT_BALL_SIZE_PX / 2))
                .coerceIn(0, (bounds.height() - DEFAULT_BALL_SIZE_PX - EDGE_MARGIN_PX).coerceAtLeast(0))
            return WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = initialX
                y = initialY
            }
        }
    }
}
