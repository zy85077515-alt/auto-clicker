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
                Config.templatePath = file.absolutePath
                Toast.makeText(this, "模板已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请先拖动选择区域", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
        findViewById<Button>(R.id.btnCancel).setOnClickListener { finish() }
    }
}
