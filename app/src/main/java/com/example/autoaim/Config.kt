package com.example.autoaim

import android.content.Context
import android.content.SharedPreferences

object Config {
    lateinit var prefs: SharedPreferences

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("autoaim_cfg", Context.MODE_PRIVATE)
    }

    var loop: Boolean
        get() = prefs.getBoolean("loop", true)
        set(v) = prefs.edit().putBoolean("loop", v).apply()

    var sensitivity: Float
        get() = prefs.getFloat("sensitivity", 1.0f)
        set(v) = prefs.edit().putFloat("sensitivity", v).apply()

    var threshold: Float
        get() = prefs.getFloat("threshold", 0.75f)
        set(v) = prefs.edit().putFloat("threshold", v).apply()

    // OpenCV 特征匹配：判定为命中所需的最少好匹配数（越大越严格）
    var minMatches: Int
        get() = prefs.getInt("minMatches", 12)
        set(v) = prefs.edit().putInt("minMatches", v).apply()

    var intervalMs: Long
        get() = prefs.getLong("intervalMs", 500)
        set(v) = prefs.edit().putLong("intervalMs", v).apply()

    var centerTolerance: Int
        get() = prefs.getInt("centerTolerance", 25)
        set(v) = prefs.edit().putInt("centerTolerance", v).apply()

    var maxDim: Int
        get() = prefs.getInt("maxDim", 640)
        set(v) = prefs.edit().putInt("maxDim", v).apply()

    var step: Int
        get() = prefs.getInt("step", 3)
        set(v) = prefs.edit().putInt("step", v).apply()

    var invert: Boolean
        get() = prefs.getBoolean("invert", false)
        set(v) = prefs.edit().putBoolean("invert", v).apply()

    var clickAfterAim: Boolean
        get() = prefs.getBoolean("clickAfterAim", true)
        set(v) = prefs.edit().putBoolean("clickAfterAim", v).apply()

    var clickMode: Int
        get() = prefs.getInt("clickMode", 0)
        set(v) = prefs.edit().putInt("clickMode", v).apply()

    var clickX: Float
        get() = prefs.getFloat("clickX", 0f)
        set(v) = prefs.edit().putFloat("clickX", v).apply()

    var clickY: Float
        get() = prefs.getFloat("clickY", 0f)
        set(v) = prefs.edit().putFloat("clickY", v).apply()

    // 点击的基础触摸时长（毫秒），默认 90ms
    var tapBaseMs: Long
        get() = prefs.getLong("tapBaseMs", 90)
        set(v) = prefs.edit().putLong("tapBaseMs", v).apply()

    // 点击触摸时长的随机抖动范围：实际时长 = 基础 ± [0, tapJitterMs]，默认 ±20ms
    var tapJitterMs: Int
        get() = prefs.getInt("tapJitterMs", 20)
        set(v) = prefs.edit().putInt("tapJitterMs", v).apply()

    // 点击坐标的随机微抖动范围（像素）：实际落点 = 目标 ± [0, tapPosJitterPx]
    var tapPosJitterPx: Int
        get() = prefs.getInt("tapPosJitterPx", 3)
        set(v) = prefs.edit().putInt("tapPosJitterPx", v).apply()

    // 点击前的随机延迟区间（毫秒）：每次点击前等待 [tapPreDelayMinMs, tapPreDelayMaxMs] 随机时长
    var tapPreDelayMinMs: Int
        get() = prefs.getInt("tapPreDelayMinMs", 100)
        set(v) = prefs.edit().putInt("tapPreDelayMinMs", v).apply()

    var tapPreDelayMaxMs: Int
        get() = prefs.getInt("tapPreDelayMaxMs", 1000)
        set(v) = prefs.edit().putInt("tapPreDelayMaxMs", v).apply()

    var dragStartX: Float
        get() = prefs.getFloat("dragStartX", -1f)
        set(v) = prefs.edit().putFloat("dragStartX", v).apply()

    var dragStartY: Float
        get() = prefs.getFloat("dragStartY", -1f)
        set(v) = prefs.edit().putFloat("dragStartY", v).apply()

    var templatePath: String?
        get() = if (prefs.contains("templatePath")) prefs.getString("templatePath", "") else null
        set(v) = if (v == null) prefs.edit().remove("templatePath").apply()
        else prefs.edit().putString("templatePath", v).apply()
}
