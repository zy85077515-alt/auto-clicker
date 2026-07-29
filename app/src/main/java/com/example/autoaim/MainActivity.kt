package com.example.autoaim

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val REQ_MEDIA = 1001
    private val REQ_OVERLAY = 1002
    private val REQ_POST_NOTIF = 1003

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.init(this)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_POST_NOTIF
                )
            }
        }

        loadSettingsToUI()

        findViewById<Button>(R.id.btnStart).setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            if (!AutoAimService.isRunning) {
                toast("请先启动服务")
            } else {
                startService(Intent(this, AutoAimService::class.java).setAction("capture"))
            }
        }
        findViewById<Button>(R.id.btnPick).setOnClickListener {
            startActivity(Intent(this, PickPointActivity::class.java))
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettingsFromUI()
            toast("设置已保存")
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<Button>(R.id.btnStart).text = if (AutoAimService.isRunning) "停止" else "启动"
        updateHint()
    }

    private fun updateHint() {
        val sb = StringBuilder()
        sb.append("无障碍服务: ").append(if (GestureService.instance != null) "已开启" else "未开启").append("\n")
        sb.append("悬浮窗权限: ").append(if (Settings.canDrawOverlays(this)) "已授予" else "未授予").append("\n")
        sb.append("识别模板: ").append(if (Config.templatePath != null) "已设置" else "未设置")
        findViewById<TextView>(R.id.tvHint).text = sb.toString()
    }

    private fun onStartClicked() {
        val btn = findViewById<Button>(R.id.btnStart)
        if (AutoAimService.isRunning) {
            stopService(Intent(this, AutoAimService::class.java))
            btn.text = "启动"
            updateHint()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                ), REQ_OVERLAY
            )
            toast("请先授予悬浮窗权限")
            return
        }
        if (GestureService.instance == null) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            toast("请先在无障碍中开启本应用的服务")
            return
        }
        saveSettingsFromUI()
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_MEDIA)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, AutoAimService::class.java)
            i.putExtra(AutoAimService.EXTRA_RESULT_CODE, resultCode)
            i.putExtra(AutoAimService.EXTRA_DATA, data)
            ContextCompat.startForegroundService(this, i)
            findViewById<Button>(R.id.btnStart).text = "停止"
        }
    }

    private fun loadSettingsToUI() {
        findViewById<EditText>(R.id.etSensitivity).setText(Config.sensitivity.toString())
        findViewById<EditText>(R.id.etThreshold).setText(Config.threshold.toString())
        findViewById<EditText>(R.id.etInterval).setText(Config.intervalMs.toString())
        findViewById<EditText>(R.id.etTolerance).setText(Config.centerTolerance.toString())
        findViewById<EditText>(R.id.etTapBase).setText(Config.tapBaseMs.toString())
        findViewById<EditText>(R.id.etTapJitter).setText(Config.tapJitterMs.toString())
        findViewById<EditText>(R.id.etTapPosJitter).setText(Config.tapPosJitterPx.toString())
        findViewById<EditText>(R.id.etTapDelayMin).setText(Config.tapPreDelayMinMs.toString())
        findViewById<EditText>(R.id.etTapDelayMax).setText(Config.tapPreDelayMaxMs.toString())
        findViewById<CheckBox>(R.id.cbLoop).isChecked = Config.loop
        findViewById<CheckBox>(R.id.cbClick).isChecked = Config.clickAfterAim
        findViewById<CheckBox>(R.id.cbInvert).isChecked = Config.invert
        val rg = findViewById<RadioGroup>(R.id.rgClickMode)
        rg.check(if (Config.clickMode == 0) R.id.rbCenter else R.id.rbPoint)
    }

    private fun saveSettingsFromUI() {
        Config.sensitivity = findViewById<EditText>(R.id.etSensitivity).text.toString().toFloatOrNull() ?: 1f
        Config.threshold = findViewById<EditText>(R.id.etThreshold).text.toString().toFloatOrNull() ?: 0.75f
        Config.intervalMs = findViewById<EditText>(R.id.etInterval).text.toString().toLongOrNull() ?: 500L
        Config.centerTolerance = findViewById<EditText>(R.id.etTolerance).text.toString().toIntOrNull() ?: 15
        Config.tapBaseMs = findViewById<EditText>(R.id.etTapBase).text.toString().toLongOrNull() ?: 50L
        Config.tapJitterMs = findViewById<EditText>(R.id.etTapJitter).text.toString().toIntOrNull() ?: 50
        Config.tapPosJitterPx = findViewById<EditText>(R.id.etTapPosJitter).text.toString().toIntOrNull() ?: 3
        Config.tapPreDelayMinMs = findViewById<EditText>(R.id.etTapDelayMin).text.toString().toIntOrNull() ?: 100
        Config.tapPreDelayMaxMs = findViewById<EditText>(R.id.etTapDelayMax).text.toString().toIntOrNull() ?: 1000
        Config.loop = findViewById<CheckBox>(R.id.cbLoop).isChecked
        Config.clickAfterAim = findViewById<CheckBox>(R.id.cbClick).isChecked
        Config.invert = findViewById<CheckBox>(R.id.cbInvert).isChecked
        Config.clickMode = if (findViewById<RadioButton>(R.id.rbCenter).isChecked) 0 else 1
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
