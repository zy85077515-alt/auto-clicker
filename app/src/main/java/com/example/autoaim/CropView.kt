package com.example.autoaim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var bitmap: Bitmap? = null
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var startX = 0f
    private var startY = 0f
    private var curX = 0f
    private var curY = 0f
    private val rectPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    fun setBitmap(b: Bitmap) {
        bitmap = b
        if (width > 0 && height > 0) layoutBitmap()
        invalidate()
    }

    private fun layoutBitmap() {
        val b = bitmap ?: return
        scale = minOf(width.toFloat() / b.width, height.toFloat() / b.height)
        offsetX = (width - b.width * scale) / 2f
        offsetY = (height - b.height * scale) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBitmap()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val b = bitmap ?: return
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(b, 0f, 0f, null)
        canvas.restore()
        canvas.drawRect(startX, startY, curX, curY, rectPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x; startY = event.y; curX = event.x; curY = event.y
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                curX = event.x; curY = event.y; invalidate()
            }
        }
        return true
    }

    fun getCropRect(): Rect? {
        val b = bitmap ?: return null
        val left = minOf(startX, curX)
        val top = minOf(startY, curY)
        val right = maxOf(startX, curX)
        val bottom = maxOf(startY, curY)
        if (right - left < 10 || bottom - top < 10) return null
        val bx = ((left - offsetX) / scale).toInt().coerceIn(0, b.width)
        val by = ((top - offsetY) / scale).toInt().coerceIn(0, b.height)
        val bw = ((right - left) / scale).toInt().coerceIn(0, b.width - bx)
        val bh = ((bottom - top) / scale).toInt().coerceIn(0, b.height - by)
        if (bw <= 0 || bh <= 0) return null
        return Rect(bx, by, bx + bw, by + bh)
    }
}
