package com.example.autoaim

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.math.maxOf
import kotlin.math.sqrt

class AutoAimService : Service() {

    private val NOTIF_ID = 1001
    private lateinit var capturer: ScreenCapturer
    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private val thread = HandlerThread("autoaim")
    private lateinit var handler: Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private var paused = false
    private var templateBitmap: Bitmap? = null
    private var loadedTemplatePath: String? = null
    private var screenW = 0
    private var screenH = 0
    private var scale = 1f

    companion object {
        const val EXTRA_RESULT_CODE = "rc"
        const val EXTRA_DATA = "data"
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        thread.start()
        handler = Handler(thread.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "capture" -> {
                if (::capturer.isInitialized) openCapture()
                return START_STICKY
            }
            "pick" -> {
                openPick()
                return START_STICKY
            }
            "stop" -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (::capturer.isInitialized) return START_STICKY

        startForegroundWithNotification()
        val rc = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        capturer = ScreenCapturer(this, rc, data)
        screenW = capturer.width
        screenH = capturer.height
        scale = Config.maxDim.toFloat() / maxOf(screenW, screenH).toFloat()
        setupOverlay()
        isRunning = true
        scheduleTick()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val ch = "autoaim_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(ch, "自动瞄准", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
        val notif: Notification = NotificationCompat.Builder(this, ch)
            .setContentTitle("自动瞄准点击器运行中")
            .setContentText("通过悬浮窗控制暂停/结束")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun setupOverlay() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null)
        overlayView = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.x = 0
        params.y = 60
        windowManager.addView(view, params)

        view.findViewById<Button>(R.id.btnPause).setOnClickListener { togglePause() }
        view.findViewById<Button>(R.id.btnStop).setOnClickListener { stopSelf() }
        view.findViewById<Button>(R.id.btnCapture).setOnClickListener { openCapture() }
        view.findViewById<Button>(R.id.btnPick).setOnClickListener { openPick() }
    }

    private fun togglePause() {
        paused = !paused
        overlayView?.findViewById<Button>(R.id.btnPause)?.text = if (paused) "继续" else "暂停"
        updateStatus(if (paused) "已暂停" else "运行中")
    }

    private fun openCapture() {
        val frame = capturer.capture()
        if (frame != null) FrameHolder.lastFrame = frame
        startActivity(Intent(this, CropActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openPick() {
        startActivity(Intent(this, PickPointActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun updateStatus(text: String) {
        mainHandler.post { overlayView?.findViewById<TextView>(R.id.tvStatus)?.text = text }
    }

    private fun scheduleTick() {
        handler.postDelayed({ tick() }, Config.intervalMs)
    }

    private fun tick() {
        if (!isRunning) return
        if (!paused) doAim()
        scheduleTick()
    }

    private fun doAim() {
        try {
            val path = Config.templatePath
            if (path != loadedTemplatePath) {
                templateBitmap = if (path != null && File(path).exists())
                    BitmapFactory.decodeFile(path) else null
                loadedTemplatePath = path
                if (templateBitmap != null) {
                    val tw = maxOf(1, (templateBitmap!!.width * scale).toInt())
                    val th = maxOf(1, (templateBitmap!!.height * scale).toInt())
                    val scaledTmpl = ImageUtils.downscaleTo(templateBitmap!!, tw, th)
                    TemplateMatcher.setTemplate(scaledTmpl)
                    scaledTmpl.recycle()
                }
            }
            val tmpl = templateBitmap
            if (tmpl == null) {
                updateStatus("尚未设置识别模板")
                return
            }

            val frame = capturer.capture() ?: return
            FrameHolder.lastFrame = frame
            val scaledScreen = ImageUtils.downscale(frame, Config.maxDim)
            val res = TemplateMatcher.match(scaledScreen, Config.minMatches)
            scaledScreen.recycle()
            frame.recycle()

            val cx = screenW / 2f
            val cy = screenH / 2f
            if (!res.found) {
                updateStatus("未识别到目标")
                return
            }
            val tx = res.cx / scale
            val ty = res.cy / scale
            val offX = cx - tx
            val offY = cy - ty
            val dist = sqrt(offX * offX + offY * offY)
            if (dist <= Config.centerTolerance) {
                updateStatus("已对准中心  score=%.2f  点击".format(res.score))
                if (Config.clickAfterAim) doClick()
                if (!Config.loop) stopSelf()
                return
            }
            updateStatus("识别到 score=%.2f 偏移=%.0f 移动中".format(res.score, dist))
            val g = GestureService.instance
            if (g == null) {
                updateStatus("无障碍服务未开启，无法移动/点击")
                return
            }
            val dir = if (Config.invert) -1 else 1
            val sx = if (Config.dragStartX < 0) cx else Config.dragStartX
            val sy = if (Config.dragStartY < 0) cy else Config.dragStartY
            val ex = sx + offX * Config.sensitivity * dir
            val ey = sy + offY * Config.sensitivity * dir
            g.swipe(sx, sy, ex, ey, 150)
        } catch (e: Exception) {
            updateStatus("错误: ${e.message}")
        }
    }

    private fun doClick() {
        val g = GestureService.instance ?: return
        val x = if (Config.clickMode == 0) screenW / 2f else Config.clickX
        val y = if (Config.clickMode == 0) screenH / 2f else Config.clickY
        // 每次点击的触摸时长 = 基础 ± 随机[0, tapJitterMs]，避免机械固定
        val jitter = Config.tapJitterMs
        val range = if (jitter > 0) (-jitter..jitter) else (0..0)
        val dur = (Config.tapBaseMs + range.random()).coerceAtLeast(1)
        g.tap(x, y, dur)
    }

    override fun onDestroy() {
        isRunning = false
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        if (::capturer.isInitialized) capturer.release()
        handler.removeCallbacksAndMessages(null)
        thread.quitSafely()
        super.onDestroy()
    }
}
