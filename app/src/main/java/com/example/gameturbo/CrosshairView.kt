package com.example.gameturbo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

const val CROSS_STYLES = 12

fun drawCross(cv: Canvas, p: Paint, style: Int, cx: Float, cy: Float, size: Float, thick: Float) {
    p.strokeWidth = thick
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
        3 -> {
            val gap = size / 3
            p.style = Paint.Style.STROKE
            cv.drawLine(cx - size, cy, cx - gap, cy, p)
            cv.drawLine(cx + gap, cy, cx + size, cy, p)
            cv.drawLine(cx, cy - size, cx, cy - gap, p)
            cv.drawLine(cx, cy + gap, cx, cy + size, p)
        }
        4 -> {
            val d = size * 0.7f
            p.style = Paint.Style.STROKE
            cv.drawLine(cx - d, cy - d, cx + d, cy + d, p)
            cv.drawLine(cx - d, cy + d, cx + d, cy - d, p)
        }
        5 -> {
            val d = size * 0.8f
            p.style = Paint.Style.STROKE
            cv.drawRect(cx - d, cy - d, cx + d, cy + d, p)
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy, thick, p)
        }
        6 -> {
            val d = size * 0.9f
            val l = size * 0.45f
            p.style = Paint.Style.STROKE
            cv.drawLine(cx - d, cy - d, cx - d + l, cy - d, p)
            cv.drawLine(cx - d, cy - d, cx - d, cy - d + l, p)
            cv.drawLine(cx + d, cy - d, cx + d - l, cy - d, p)
            cv.drawLine(cx + d, cy - d, cx + d, cy - d + l, p)
            cv.drawLine(cx - d, cy + d, cx - d + l, cy + d, p)
            cv.drawLine(cx - d, cy + d, cx - d, cy + d - l, p)
            cv.drawLine(cx + d, cy + d, cx + d - l, cy + d, p)
            cv.drawLine(cx + d, cy + d, cx + d, cy + d - l, p)
        }
        7 -> {
            val gap = size / 3
            p.style = Paint.Style.STROKE
            cv.drawLine(cx - size, cy, cx - gap, cy, p)
            cv.drawLine(cx + gap, cy, cx + size, cy, p)
            cv.drawLine(cx, cy - size, cx, cy - gap, p)
            cv.drawLine(cx, cy + gap, cx, cy + size, p)
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy, thick * 0.8f, p)
        }
        8 -> {
            p.style = Paint.Style.STROKE
            cv.drawCircle(cx, cy, size, p)
            cv.drawCircle(cx, cy, size * 0.55f, p)
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy, thick * 0.6f, p)
        }
        9 -> {
            val gap = size / 4
            p.style = Paint.Style.STROKE
            cv.drawLine(cx - size, cy, cx - gap, cy, p)
            cv.drawLine(cx + gap, cy, cx + size, cy, p)
            cv.drawLine(cx, cy + gap, cx, cy + size, p)
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy, thick * 0.7f, p)
        }
        10 -> {
            val path = Path()
            path.moveTo(cx, cy - size)
            path.lineTo(cx + size, cy)
            path.lineTo(cx, cy + size)
            path.lineTo(cx - size, cy)
            path.close()
            p.style = Paint.Style.STROKE
            cv.drawPath(path, p)
            p.style = Paint.Style.FILL
            cv.drawCircle(cx, cy, thick * 0.8f, p)
        }
        else -> {
            val gap = size * 0.45f
            p.style = Paint.Style.STROKE
            cv.drawCircle(cx, cy, size * 0.8f, p)
            cv.drawLine(cx - size, cy, cx - gap, cy, p)
            cv.drawLine(cx + gap, cy, cx + size, cy, p)
            cv.drawLine(cx, cy - size, cx, cy - gap, p)
            cv.drawLine(cx, cy + gap, cx, cy + size, p)
        }
    }
}

class CrosshairView(c: Context) : View(c) {
    var style = 0
    var size = 40f
    var thick = 4f
    var color = Color.RED
    var offX = 0f
    var offY = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        p.color = if (S.b("ch_custom", false)) {
            Color.HSVToColor(floatArrayOf(S.i("ch_hue", 0).toFloat(), 1f, 1f))
        } else {
            color
        }
        drawCross(cv, p, style, width / 2f + offX, height / 2f + offY, size, thick)
    }
}

class CrosshairPreview(c: Context, private val idx: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(cv: Canvas) {
        p.color = Color.WHITE
        val m = minOf(width, height) / 2f
        drawCross(cv, p, idx, width / 2f, height / 2f, m * 0.62f, 3f)
    }
}
