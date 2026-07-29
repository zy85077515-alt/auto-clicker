package com.example.autoaim

import android.graphics.Bitmap

object ImageUtils {

    /** 将 Bitmap 转为 0~255 灰度数组 */
    fun toGrayscale(src: Bitmap): IntArray {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return gray
    }

    /** 按最长边缩放到 maxDim */
    fun downscale(src: Bitmap, maxDim: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src
        val scale = maxDim.toFloat() / maxOf(w, h)
        val nw = maxOf(1, (w * scale).toInt())
        val nh = maxOf(1, (h * scale).toInt())
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }

    /** 缩放到指定宽高 */
    fun downscaleTo(src: Bitmap, w: Int, h: Int): Bitmap {
        if (w <= 0 || h <= 0) return src
        return Bitmap.createScaledBitmap(src, w, h, true)
    }
}
