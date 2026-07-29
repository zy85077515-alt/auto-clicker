package com.example.autoaim

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

object TemplateMatcher {

    private var detector: ORB? = null
    private var matcher: BFMatcher? = null
    private var tmplKp: MatOfKeyPoint? = null
    private var tmplDesc: Mat? = null
    private var tmplW = 0
    private var tmplH = 0

    /** 最近一次设置模板的结果说明，便于 UI 给出明确提示 */
    var lastTemplateError: String? = null
        private set

    /** 模板是否已成功建立（有可用特征描述子） */
    fun isTemplateReady(): Boolean = tmplDesc != null && !tmplDesc!!.empty()

    /** 清空模板状态（如重新截取时） */
    fun clearTemplate() {
        tmplKp?.release(); tmplKp = null
        tmplDesc?.release(); tmplDesc = null
        lastTemplateError = null
    }

    init {
        // Maven 包 org.opencv:opencv 需手动加载原生库，否则所有 OpenCV 调用会抛 UnsatisfiedLinkError
        try {
            System.loadLibrary("opencv_java4")
        } catch (e: Throwable) {
            Log.e("AutoAim", "OpenCV native load failed: ${e.message}")
        }
        try {
            detector = ORB.create()
            detector?.setMaxFeatures(2000)
            matcher = BFMatcher.create(Core.NORM_HAMMING, true) // crossCheck
        } catch (e: Throwable) {
            Log.e("AutoAim", "OpenCV init failed: ${e.message}")
        }
    }

    data class Result(
        val found: Boolean,
        val cx: Double,
        val cy: Double,
        val score: Float,
        val detail: String = ""
    ) {
        companion object {
            fun miss(d: String) = Result(false, 0.0, 0.0, 0f, d)
        }
    }

    /** 设置模板：内部统一缩放到最长边 96px，避免按屏幕缩放被压得过小导致特征丢失 */
    fun setTemplate(bmp: Bitmap) {
        lastTemplateError = null
        try {
            val side = 96
            val ratio = side.toFloat() / maxOf(bmp.width, bmp.height)
            val tw = maxOf(1, (bmp.width * ratio).toInt())
            val th = maxOf(1, (bmp.height * ratio).toInt())
            val scaled = ImageUtils.downscaleTo(bmp, tw, th)
            val mat = Mat()
            Utils.bitmapToMat(scaled, mat)
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
            tmplW = scaled.width
            tmplH = scaled.height
            tmplKp = MatOfKeyPoint()
            tmplDesc = Mat()
            detector?.detectAndCompute(gray, Mat(), tmplKp, tmplDesc)
            mat.release()
            gray.release()
            scaled.recycle()
            // 特征过少会导致永远匹配不到，这里记录明确原因
            if (tmplDesc == null || tmplDesc!!.empty()) {
                lastTemplateError = "模板无特征(关键点过少)，请改选纹理/边缘丰富区域"
                Log.w("AutoAim", "setTemplate: 模板无可用特征")
            }
        } catch (e: Throwable) {
            lastTemplateError = "模板加载失败: ${e.message}"
            Log.e("AutoAim", "setTemplate failed: ${e.message}")
        }
    }

    fun match(screen: Bitmap, minMatches: Int): Result {
        if (detector == null || matcher == null) return Result.miss("OpenCV未初始化")
        if (tmplDesc == null || tmplDesc!!.empty()) return Result.miss("模板未设置/为空")
        // 所有原生 Mat 必须在 finally 中释放，否则每帧泄漏 native 内存最终导致进程被系统回收
        val sMat = Mat()
        val sGray = Mat()
        val kp = MatOfKeyPoint()
        val desc = Mat()
        val raw = MatOfDMatch()
        val src = MatOfPoint2f()
        val dst = MatOfPoint2f()
        val dstCorners = MatOfPoint2f()
        var h: Mat? = null
        try {
            Utils.bitmapToMat(screen, sMat)
            Imgproc.cvtColor(sMat, sGray, Imgproc.COLOR_RGBA2GRAY)
            detector!!.detectAndCompute(sGray, Mat(), kp, desc)
            if (desc.empty()) return Result.miss("屏幕无特征")
            matcher!!.match(desc, tmplDesc, raw)
            val list = raw.toList()
            if (list.isEmpty()) return Result.miss("无匹配")
            // 按汉明距离排序，取距离足够小的前若干个作为好匹配（阈值 64）
            val sorted = list.sortedBy { it.distance }
            val good = sorted.takeWhile { it.distance < 64.0 }
            if (good.size < minMatches) return Result.miss("好匹配不足 ${good.size}/$minMatches")
            val tmplPts = tmplKp!!.toList()
            val scrPts = kp.toList()
            src.release(); src.fromArray(*good.map { tmplPts[it.trainIdx].pt }.toTypedArray())
            dst.release(); dst.fromArray(*good.map { scrPts[it.queryIdx].pt }.toTypedArray())
            h = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0)
            if (h == null || h.empty()) return Result.miss("单应失败 g=${good.size}")
            // 用单应矩阵把模板四角映射到屏幕，求中心
            val corners = arrayOf(
                Point(0.0, 0.0),
                Point(tmplW.toDouble(), 0.0),
                Point(tmplW.toDouble(), tmplH.toDouble()),
                Point(0.0, tmplH.toDouble())
            )
            Core.perspectiveTransform(MatOfPoint2f(*corners), dstCorners, h)
            val pts = dstCorners.toList()
            val cx = pts.map { it.x }.average()
            val cy = pts.map { it.y }.average()
            return Result(true, cx, cy, good.size.toFloat(), "OK g=${good.size}")
        } finally {
            sMat.release(); sGray.release()
            kp.release(); desc.release(); raw.release()
            src.release(); dst.release(); dstCorners.release()
            h?.release()
        }
    }
}
