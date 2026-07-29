package com.example.autoaim

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class CropActivity : AppCompatActivity() {

    private lateinit var cropView: CropView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop)
        cropView = findViewById(R.id.cropView)
        FrameHolder.lastFrame?.let { cropView.setBitmap(it) }

        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            val rect: Rect? = cropView.getCropRect()
            val src = FrameHolder.lastFrame
            if (rect != null && src != null) {
                val cropped = Bitmap.createBitmap(src, rect.left, rect.top, rect.width(), rect.height())
                val file = File(filesDir, "template.png")
                FileOutputStream(file).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
                cropped.recycle()
                // 截完即释放上一帧，降低后台内存占用（避免 OOM 把进程连同无障碍服务一起挤崩）
                src.recycle()
                FrameHolder.lastFrame = null
                Config.templatePath = file.absolutePath
                // 通知自动瞄准服务强制重新加载模板（路径不变也要刷新）
                AutoAimService.pendingTemplateReload = true
                Toast.makeText(this, "模板已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请先拖动选择区域", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }
}
