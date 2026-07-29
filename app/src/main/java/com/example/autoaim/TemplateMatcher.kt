package com.example.autoaim

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
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

/**
 * OpenCV 特征点匹配（ORB + 单应矩阵）。
 * 相比纯 NCC 模板匹配，对目标缩放 / 旋转具有不变性，
 * 适合实战中目标大小、角度变化较大的场景。
 */
object TemplateMatcher {

    private const val TAG = "TemplateMatcher"
    private const val MAX_FEATURES = 2000

    data class Result(val found: Boolean, val cx: Int, val cy: Int, val score: Float)

    private var initialized = false
    private var orb: ORB? = null
    private var matcher: BFMatcher? = null

    private var tmplKp: MatOfKeyPoint? = null
    private var tmplDesc: Mat? = null
    private var tmplW = 0
    private var tmplH = 0

    @Synchronized
    private fun ensureInit() {
        if (initialized) return
        try {
            if (!OpenCVLoader.initLocal()) {
                Log.w(TAG, "OpenCVLoader.initLocal 返回 false，将依赖自动加载")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "OpenCVLoader.initLocal 异常: ${e.message}")
        }
        orb = ORB.create(MAX_FEATURES)
        matcher = BFMatcher.create(ORB.NORM_HAMMING, true) // crossCheck
        initialized = true
    }

    private fun bitmapToGray(bmp: Bitmap): Mat {
        val argb = Mat()
        val copy = if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(copy, argb)
        val gray = Mat()
        Imgproc.cvtColor(argb, gray, Imgproc.COLOR_RGBA2GRAY)
        argb.release()
        return gray
    }

    fun setTemplate(tmpl: Bitmap) {
        ensureInit()
        val gray = bitmapToGray(tmpl)
        tmplW = gray.width()
        tmplH = gray.height()
        tmplKp?.release()
        tmplDesc?.release()
        tmplKp = MatOfKeyPoint()
        tmplDesc = Mat()
        orb!!.detectAndCompute(gray, Mat(), tmplKp, tmplDesc)
        gray.release()
        Log.i(TAG, "模板特征点数量: ${tmplKp?.toList()?.size ?: 0}")
    }

    fun match(screen: Bitmap, minGood: Int): Result {
        ensureInit()
        val tkp = tmplKp
        val tdesc = tmplDesc
        if (tkp == null || tdesc == null || tdesc.empty() || tkp.toList().isEmpty()) {
            return Result(false, 0, 0, 0f)
        }
        val gray = bitmapToGray(screen)
        val kp = MatOfKeyPoint()
        val desc = Mat()
        orb!!.detectAndCompute(gray, Mat(), kp, desc)
        gray.release()
        if (desc.empty() || kp.toList().isEmpty()) {
            kp.release()
            desc.release()
            return Result(false, 0, 0, 0f)
        }
        val matches = MatOfDMatch()
        matcher!!.match(desc, tdesc, matches)
        val all = matches.toArray().toMutableList()
        matches.release()
        desc.release()
        all.sortBy { it.distance }
        val good = all.take(minOf(all.size, 120))
        if (good.size < minGood) {
            kp.release()
            return Result(false, 0, 0, 0f)
        }
        val src = ArrayList<Point>() // 模板点 (train)
        val dst = ArrayList<Point>() // 屏幕点 (query)
        val tList = tkp.toList()
        val sList = kp.toList()
        for (m in good) {
            src.add(tList[m.trainIdx].pt)
            dst.add(sList[m.queryIdx].pt)
        }
        kp.release()
        val srcMat = MatOfPoint2f(*src.toTypedArray())
        val dstMat = MatOfPoint2f(*dst.toTypedArray())
        val h = Calib3d.findHomography(srcMat, dstMat, Calib3d.RANSAC, 4.0)
        srcMat.release()
        dstMat.release()
        if (h == null || h.empty()) {
            return Result(false, 0, 0, good.size.toFloat())
        }
        val center = MatOfPoint2f(Point(tmplW / 2.0, tmplH / 2.0))
        val dstCenter = MatOfPoint2f()
        Core.perspectiveTransform(center, dstCenter, h)
        val p = dstCenter.toArray()[0]
        center.release()
        dstCenter.release()
        h.release()
        return Result(true, p.x.toInt(), p.y.toInt(), good.size.toFloat())
    }

    fun release() {
        tmplKp?.release()
        tmplDesc?.release()
        tmplKp = null
        tmplDesc = null
    }
}
