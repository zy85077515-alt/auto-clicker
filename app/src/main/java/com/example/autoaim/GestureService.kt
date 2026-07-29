package com.example.autoaim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class GestureService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GestureService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** 模拟滑动：从 (x1,y1) 到 (x2,y2)，durationMs 为滑动时长 */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        if (x1 == x2 && y1 == y2) return false
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    /** 模拟点击，durationMs 为触摸屏幕时长（毫秒），实际值由调用方叠加随机抖动 */
    fun tap(x: Float, y: Float, durationMs: Long = 50L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
}
