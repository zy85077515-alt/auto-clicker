package com.example.autoaim

import kotlin.math.sqrt

object TemplateMatcher {

    data class Result(val found: Boolean, val x: Int, val y: Int, val score: Float)

    /**
     * 归一化互相关（NCC）模板匹配，使用积分图加速方差计算。
     * screen/tmpl 为灰度数组；sw/sh、tw/th 为对应尺寸。
     * step 为扫描步长（越大越快但越不精确）；threshold 为匹配阈值。
     */
    fun match(
        screen: IntArray, sw: Int, sh: Int,
        tmpl: IntArray, tw: Int, th: Int,
        step: Int, threshold: Float
    ): Result {
        if (tw > sw || th > sh || tw <= 0 || th <= 0) return Result(false, 0, 0, 0f)

        var tSum = 0L
        var tSumSq = 0L
        for (v in tmpl) {
            val i = v.toLong()
            tSum += i
            tSumSq += i * i
        }
        val n = (tw * th).toDouble()
        val tMean = tSum / n
        val tStd = sqrt(tSumSq / n - tMean * tMean)
        if (tStd <= 0.0) return Result(false, 0, 0, 0f)

        val stride = sw + 1
        val iSum = LongArray(stride * (sh + 1))
        val iSq = LongArray(stride * (sh + 1))
        for (y in 0 until sh) {
            var rowSum = 0L
            var rowSq = 0L
            val base = (y + 1) * stride
            val prev = y * stride
            for (x in 0 until sw) {
                val v = screen[y * sw + x].toLong()
                rowSum += v
                rowSq += v * v
                iSum[base + x + 1] = iSum[prev + x + 1] + rowSum
                iSq[base + x + 1] = iSq[prev + x + 1] + rowSq
            }
        }

        val st = step.coerceAtLeast(1)
        var bestScore = -2.0
        var bx = 0
        var by = 0
        val maxX = sw - tw
        val maxY = sh - th
        for (y in 0..maxY step st) {
            for (x in 0..maxX step st) {
                val x2 = x + tw
                val y2 = y + th
                val s = iSum[y2 * stride + x2] - iSum[y * stride + x2] -
                        iSum[y2 * stride + x] + iSum[y * stride + x]
                val sSq = iSq[y2 * stride + x2] - iSq[y * stride + x2] -
                        iSq[y2 * stride + x] + iSq[y * stride + x]
                val meanW = s / n
                val varW = sSq / n - meanW * meanW
                if (varW <= 1e-6) continue

                var prod = 0.0
                for (ty in 0 until th) {
                    val sIdx = (y + ty) * sw + x
                    val tIdx = ty * tw
                    for (tx in 0 until tw) {
                        prod += screen[sIdx + tx].toDouble() * tmpl[tIdx + tx].toDouble()
                    }
                }
                val cov = prod - meanW * tSum - tMean * s + n * tMean * meanW
                val denom = sqrt(varW) * tStd
                val score = if (denom > 0) (cov / denom) else 0.0
                if (score > bestScore) {
                    bestScore = score
                    bx = x
                    by = y
                }
            }
        }

        val found = bestScore >= threshold
        return Result(found, bx, by, bestScore.toFloat())
    }
}
