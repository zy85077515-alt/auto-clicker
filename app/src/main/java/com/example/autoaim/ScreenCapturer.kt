package com.example.autoaim

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper

class ScreenCapturer(context: Context, resultCode: Int, data: android.content.Intent) {

    private val mpm = context.getSystemService(MediaProjectionManager::class.java)
    private val projection: MediaProjection = mpm.getMediaProjection(resultCode, data)
    private val metrics = context.resources.displayMetrics
    val width = metrics.widthPixels
    val height = metrics.heightPixels

    private val imageReader: ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    private val virtualDisplay: VirtualDisplay

    init {
        virtualDisplay = projection.createVirtualDisplay(
            "AutoAimCapture",
            width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )
    }

    /** 获取最新一帧并转为 Bitmap（调用方负责 recycle） */
    fun capture(): Bitmap? {
        val image = imageReader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val padded = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            padded.copyPixelsFromBuffer(buffer)
            image.close()
            if (rowPadding == 0) {
                padded
            } else {
                val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
                padded.recycle()
                cropped
            }
        } catch (e: Exception) {
            image.close()
            null
        }
    }

    fun release() {
        try { virtualDisplay.release() } catch (_: Exception) {}
        try { projection.stop() } catch (_: Exception) {}
        try { imageReader.close() } catch (_: Exception) {}
    }
}
