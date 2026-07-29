package com.example.autoaim

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PickPointActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_point)
        val crosshair = findViewById<ImageView>(R.id.crosshair)
        val root = findViewById<View>(R.id.root)
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    crosshair.x = event.rawX - crosshair.width / 2f
                    crosshair.y = event.rawY - crosshair.height / 2f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Config.clickX = event.rawX
                    Config.clickY = event.rawY
                    Config.clickMode = 1
                    Toast.makeText(
                        this,
                        "已设置点击点 (%.0f, %.0f)".format(event.rawX, event.rawY),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
