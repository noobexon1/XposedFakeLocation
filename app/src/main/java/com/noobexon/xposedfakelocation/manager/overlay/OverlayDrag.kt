package com.noobexon.xposedfakelocation.manager.overlay

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlin.math.hypot

private class DragTracker {
    var startX = 0f
    var startY = 0f
    var lastX = 0f
    var lastY = 0f
    var dragging = false
}

/**
 * Moves an overlay window using raw screen coordinates. Local pointer coordinates can't be used
 * because the window itself moves under the finger.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberOverlayDragModifier(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onTap: (() -> Unit)? = null,
): Modifier {
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnTap by rememberUpdatedState(onTap)
    return remember(touchSlop) {
        val tracker = DragTracker()
        Modifier.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    tracker.startX = event.rawX
                    tracker.startY = event.rawY
                    tracker.lastX = event.rawX
                    tracker.lastY = event.rawY
                    tracker.dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Stay put until the finger leaves the touch slop, so a tap never nudges the window.
                    if (!tracker.dragging &&
                        hypot(event.rawX - tracker.startX, event.rawY - tracker.startY) >= touchSlop
                    ) {
                        tracker.dragging = true
                    }
                    if (tracker.dragging) {
                        val dx = event.rawX - tracker.lastX
                        val dy = event.rawY - tracker.lastY
                        tracker.lastX = event.rawX
                        tracker.lastY = event.rawY
                        currentOnDrag(dx, dy)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val tap = currentOnTap
                    if (tap != null && !tracker.dragging) tap() else currentOnDragEnd()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    currentOnDragEnd()
                    true
                }
                else -> false
            }
        }
    }
}
