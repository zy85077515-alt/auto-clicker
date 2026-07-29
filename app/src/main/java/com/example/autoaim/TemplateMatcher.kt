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
        } catch (e: Throwable) {
            Log.e("AutoAim", "setTemplate failed: ${e.message}")
        }
    }

    fun match(screen: Bitmap, minMatches: Int): Result {
        if (detector == null || matcher == null) return Result.miss("OpenCV未初始化")
        if (tmplDesc == null || tmplDesc!!.empty()) return Result.miss("模板未设置/为空")
        val sMat = Mat()
        Utils.bitmapToMat(screen, sMat)
        val sGray = Mat()
        Imgproc.cvtColor(sMat, sGray, Imgproc.COLOR_RGBA2GRAY)
        val kp = MatOfKeyPoint()
        val desc = Mat()
        detector!!.detectAndCompute(sGray, Mat(), kp, desc)
        if (desc.empty()) {
            sMat.release(); sGray.release()
            return Result.miss("屏幕无特征")
        }
        val raw = MatOfDMatch()
        matcher!!.match(desc, tmplDesc, raw)
        val list = raw.toList()
        if (list.isEmpty()) {
            sMat.release(); sGray.release()
            return Result.miss("无匹配")
        }
        // 按汉明距离排序，取距离足够小的前若干个作为好匹配（阈值 64）
        val sorted = list.sortedBy { it.distance }
        val good = sorted.takeWhile { it.distance < 64.0 }
        if (good.size < minMatches) {
            sMat.release(); sGray.release()
            return Result.miss("好匹配不足 ${good.size}/$minMatches")
        }
        val tmplPts = tmplKp!!.toList()
        val scrPts = kp.toList()
        val src = MatOfPoint2f(*good.map { tmplPts[it.trainIdx].pt }.toTypedArray())
        val dst = MatOfPoint2f(*good.map { scrPts[it.queryIdx].pt }.toTypedArray())
        val h = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0)
        if (h == null || h.empty()) {
            sMat.release(); sGray.release()
            return Result.miss("单应失败 g=${good.size}")
        }
        // 用单应矩阵把模板四角映射到屏幕，求中心
        val corners = arrayOf(
            Point(0.0, 0.0),
            Point(tmplW.toDouble(), 0.0),
            Point(tmplW.toDouble(), tmplH.toDouble()),
            Point(0.0, tmplH.toDouble())
        )
        val dstCorners = MatOfPoint2f()
        Core.perspectiveTransform(MatOfPoint2f(*corners), dstCorners, h)
        val pts = dstCorners.toList()
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()
        sMat.release(); sGray.release()
        return Result(true, cx, cy, good.size.toFloat(), "OK g=${good.size}")
    }
}
