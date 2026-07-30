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

    private data class TmplLevel(val kp: MatOfKeyPoint, val desc: Mat, val w: Int, val h: Int) {
        fun release() { kp.release(); desc.release() }
    }
    private var levels: List<TmplLevel> = emptyList()

    /** 最近一次设置模板的结果说明，便于 UI 给出明确提示 */
    var lastTemplateError: String? = null
        private set
    /** 最近一次匹配的诊断信息（特征数、最佳好匹配数等） */
    var lastDiagnostic: String? = null
        private set

    // 多尺度：覆盖目标在屏幕中相对截取时 0.5x ~ 3x 的尺寸变化
    private val SCALES = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.2f, 3.0f)

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

    /** 模板是否已成功建立（有可用特征描述子） */
    fun isTemplateReady(): Boolean = levels.isNotEmpty() && levels.any { !it.desc.empty() }

    /** 清空模板状态 */
    fun clearTemplate() {
        levels.forEach { it.release() }
        levels = emptyList()
        lastTemplateError = null
        lastDiagnostic = null
    }

    /**
     * 设置模板：按与屏幕相同的缩放系数 screenScale 把模板放到 640 坐标系，
     * 并在该尺寸附近生成多个尺度，保证与目标在屏幕中的实际尺寸一致。
     */
    fun setTemplate(bmp: Bitmap, screenScale: Float) {
        clearTemplate()
        lastTemplateError = null
        try {
            // 模板在 640 坐标系下的基础尺寸（与屏幕同一坐标系 → 尺寸天然对应）
            val baseW = (bmp.width * screenScale).toInt().coerceIn(48, 320)
            val baseH = (bmp.height * screenScale).toInt().coerceIn(48, 320)
            val built = mutableListOf<TmplLevel>()
            for (f in SCALES) {
                val w = maxOf(16, (baseW * f).toInt())
                val h = maxOf(16, (baseH * f).toInt())
                val scaled = ImageUtils.downscaleTo(bmp, w, h)
                val mat = Mat()
                Utils.bitmapToMat(scaled, mat)
                val gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                val kp = MatOfKeyPoint()
                val desc = Mat()
                detector?.detectAndCompute(gray, Mat(), kp, desc)
                mat.release(); gray.release(); scaled.recycle()
                if (!desc.empty()) built.add(TmplLevel(kp, desc, w, h))
            }
            levels = built
                if (levels.isEmpty()) {
                    lastTemplateError = "模板区域太单调，找不到特征，请改选有纹理/文字/边缘的地方"
                    Log.w("AutoAim", "setTemplate: 模板无可用特征")
                }
        } catch (e: Throwable) {
            lastTemplateError = "模板加载失败: ${e.message}"
            Log.e("AutoAim", "setTemplate failed: ${e.message}")
        }
    }

    fun match(screen: Bitmap, minMatches: Int): Result {
        if (detector == null || matcher == null) return Result.miss("识别引擎未启动")
        if (levels.isEmpty()) return Result.miss("还没截取模板")
        val sMat = Mat()
        val sGray = Mat()
        val kp = MatOfKeyPoint()
        val desc = Mat()
        val raw = MatOfDMatch()
        try {
            Utils.bitmapToMat(screen, sMat)
            Imgproc.cvtColor(sMat, sGray, Imgproc.COLOR_RGBA2GRAY)
            detector!!.detectAndCompute(sGray, Mat(), kp, desc)
            if (desc.empty()) return Result.miss("当前画面太干净，找不到特征点")
            val scrPts = kp.toList()

            var bestGood = 0
            var bestResult: Result? = null
            lvlLoop@ for (lvl in levels) {
                val tmplPts = lvl.kp.toList()
                matcher!!.match(desc, lvl.desc, raw)
                val list = raw.toList()
                if (list.isEmpty()) continue
                val sorted = list.sortedBy { it.distance }
                // 按汉明距离排序，取距离足够小的前若干个作为好匹配（阈值 64）
                val good = sorted.takeWhile { it.distance < 64.0 }
                if (good.size > bestGood) bestGood = good.size
                if (good.size < minMatches) continue
                val src = MatOfPoint2f()
                val dst = MatOfPoint2f()
                try {
                    src.fromArray(*good.map { tmplPts[it.trainIdx].pt }.toTypedArray())
                    dst.fromArray(*good.map { scrPts[it.queryIdx].pt }.toTypedArray())
                    val h = Calib3d.findHomography(src, dst, Calib3d.RANSAC, 5.0)
                    try {
                        if (h.empty()) continue@lvlLoop
                        // 用单应矩阵把模板四角映射到屏幕，求中心
                        val corners = arrayOf(
                            Point(0.0, 0.0),
                            Point(lvl.w.toDouble(), 0.0),
                            Point(lvl.w.toDouble(), lvl.h.toDouble()),
                            Point(0.0, lvl.h.toDouble())
                        )
                        val dstCorners = MatOfPoint2f()
                        Core.perspectiveTransform(MatOfPoint2f(*corners), dstCorners, h)
                        val pts = dstCorners.toList()
                        val cx = pts.map { it.x }.average()
                        val cy = pts.map { it.y }.average()
                        val r = Result(true, cx, cy, good.size.toFloat(), "OK g=${good.size}")
                        if (bestResult == null || r.score > bestResult!!.score) bestResult = r
                    } finally {
                        h.release()
                    }
                } finally {
                    src.release(); dst.release()
                }
            }
            lastDiagnostic = "画面找到 ${scrPts.size} 个特征点，最好一档对上 $bestGood 个（需要 $minMatches 个）"
            if (bestResult != null) return bestResult!!
            return Result.miss("相似点只有 $bestGood 个（需要 $minMatches 个）")
        } finally {
            sMat.release(); sGray.release()
            kp.release(); desc.release(); raw.release()
        }
    }
}
