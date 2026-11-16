package com.example.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class CrosshairView(c: Context) : View(c) {
    var style = 0
    var size = 40f
    var thick = 4f
    var color = Color.RED
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        p.color = color
        p.strokeWidth = thick
        val cx = width / 2f
        val cy = height / 2f
        when (style) {
            0 -> {
                p.style = Paint.Style.STROKE
                cv.drawLine(cx - size, cy, cx + size, cy, p)
                cv.drawLine(cx, cy - size, cx, cy + size, p)
            }
            1 -> {
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy, size / 4, p)
            }
            2 -> {
                p.style = Paint.Style.STROKE
                cv.drawCircle(cx, cy, size, p)
                p.style = Paint.Style.FILL
                cv.drawCircle(cx, cy, thick, p)
            }
            else -> {
                val gap = size / 3
                p.style = Paint.Style.STROKE
                cv.drawLine(cx - size, cy, cx - gap, cy, p)
                cv.drawLine(cx + gap, cy, cx + size, cy, p)
                cv.drawLine(cx, cy - size, cx, cy - gap, p)
                cv.drawLine(cx, cy + gap, cx, cy + size, p)
            }
        }
    }
}
