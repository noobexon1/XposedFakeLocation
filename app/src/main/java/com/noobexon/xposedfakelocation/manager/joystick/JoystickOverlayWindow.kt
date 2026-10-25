package com.noobexon.xposedfakelocation.manager.joystick

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hosts Compose [content] in a draggable `TYPE_APPLICATION_OVERLAY` window. The window is
 * not focusable, so the app underneath keeps keyboard focus and touches outside the overlay.
 */
class JoystickOverlayWindow(
    private val context: Context,
    lifecycleOwner: LifecycleOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    initialPosition: Pair<Int, Int>?,
    private val onPositionCommitted: (x: Int, y: Int) -> Unit,
    content: @Composable () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val (startX, startY) = initialPosition ?: defaultPosition()
        x = startX
        y = startY
    }

    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        setContent(content)
        // Re-clamp when the content resizes (e.g. bubble expanding near an edge).
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (sizeChanged && clampToScreen()) commitPosition()
        }
    }

    private var attached = false

    fun show() {
        if (attached) return
        windowManager.addView(view, params)
        attached = true
    }

    fun moveBy(dx: Float, dy: Float) {
        params.x += dx.roundToInt()
        params.y += dy.roundToInt()
        clampToScreen()
    }

    fun commitPosition() = onPositionCommitted(params.x, params.y)

    /**
     * Keeps the overlay fully on screen; also call after rotation or a display size change.
     * Returns whether the position had to change.
     */
    fun clampToScreen(): Boolean {
        if (!attached) return false
        val (width, height) = screenSize()
        val clampedX = params.x.coerceIn(0, max(0, width - view.width))
        val clampedY = params.y.coerceIn(0, max(0, height - view.height))
        val moved = clampedX != params.x || clampedY != params.y
        params.x = clampedX
        params.y = clampedY
        windowManager.updateViewLayout(view, params)
        return moved
    }

    fun remove() {
        if (attached) {
            windowManager.removeView(view)
            attached = false
        }
        view.disposeComposition()
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }

    private fun defaultPosition(): Pair<Int, Int> {
        val (_, height) = screenSize()
        val margin = (DEFAULT_MARGIN_DP * context.resources.displayMetrics.density).roundToInt()
        return margin to (height * DEFAULT_Y_FRACTION).roundToInt()
    }

    private companion object {
        const val DEFAULT_MARGIN_DP = 24
        const val DEFAULT_Y_FRACTION = 0.55f
    }
}
