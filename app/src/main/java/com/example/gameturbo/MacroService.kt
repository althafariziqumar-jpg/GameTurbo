package com.example.gameturbo

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class Step(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val dur: Long, val delay: Long)

class MacroService : AccessibilityService() {
    companion object {
        var inst: MacroService? = null
        val steps = ArrayList<Step>()
        @Volatile
        var running = false

        fun save() {
            val sb = StringBuilder()
            for (s in steps) {
                sb.append(s.x1).append(',').append(s.y1).append(',')
                sb.append(s.x2).append(',').append(s.y2).append(',')
                sb.append(s.dur).append(',').append(s.delay).append(';')
            }
            S.put("macro", sb.toString())
        }

        fun load() {
            steps.clear()
            val raw = S.s("macro", "")
            for (part in raw.split(";")) {
                if (part.isEmpty()) continue
                val f = part.split(",")
                if (f.size < 6) continue
                try {
                    steps.add(Step(f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toLong(), f[5].toLong()))
                } catch (e: Exception) {
                }
            }
        }
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        S.init(this)
        load()
        inst = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        inst = null
        return super.onUnbind(intent)
    }

    fun play(loops: Int, speed: Int, startDelayMs: Long) {
        if (steps.isEmpty() || running) return
        running = true
        h.postDelayed({ runStep(0, 0, loops, speed) }, startDelayMs)
    }

    fun stop() {
        running = false
        h.removeCallbacksAndMessages(null)
    }

    private fun runStep(i: Int, round: Int, loops: Int, speed: Int) {
        if (!running) return
        if (steps.isEmpty()) {
            running = false
            return
        }
        if (i >= steps.size) {
            val next = round + 1
            if (loops != 0 && next >= loops) {
                running = false
                return
            }
            runStep(0, next, loops, speed)
            return
        }
        val s = steps[i]
        val wait = maxOf(10L, s.delay * 100 / speed)
        h.postDelayed({
            if (running) {
                val p = Path()
                p.moveTo(s.x1, s.y1)
                if (s.x2 != s.x1 || s.y2 != s.y1) p.lineTo(s.x2, s.y2)
                val d = maxOf(20L, s.dur * 100 / speed)
                val g = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(p, 0L, d))
                    .build()
                val cb = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        runStep(i + 1, round, loops, speed)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        runStep(i + 1, round, loops, speed)
                    }
                }
                if (!dispatchGesture(g, cb, null)) running = false
            }
        }, wait)
    }
}

class RecordView(c: Context, private val onDone: () -> Unit) : FrameLayout(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var downT = 0L
    private var lastEnd = 0L
    private var sx = 0f
    private var sy = 0f
    private val info: TextView

    init {
        setWillNotDraw(false)
        setBackgroundColor(0x44000000)
        info = c.tv("", 12f)
        val bar = LinearLayout(c)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = rr(Th.BG, c.dp(16).toFloat(), Th.ACC)
        bar.setPadding(c.dp(12), c.dp(6), c.dp(8), c.dp(6))
        bar.addView(info)
        val undo = c.btn("Batal terakhir", Th.OFF) { undoLast() }
        val done = c.btn("Selesai") { onDone() }
        val lp1 = LinearLayout.LayoutParams(-2, -2)
        lp1.setMargins(c.dp(8), 0, 0, 0)
        val lp2 = LinearLayout.LayoutParams(-2, -2)
        lp2.setMargins(c.dp(8), 0, 0, 0)
        bar.addView(undo, lp1)
        bar.addView(done, lp2)
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = c.dp(8)
        addView(bar, lp)
        refreshInfo()
    }

    private fun refreshInfo() {
        info.text = "Rekam: ketuk / geser layar (" + MacroService.steps.size + " langkah)"
    }

    private fun undoLast() {
        if (MacroService.steps.isNotEmpty()) {
            MacroService.steps.removeAt(MacroService.steps.size - 1)
        }
        refreshInfo()
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            downT = SystemClock.uptimeMillis()
            sx = e.rawX
            sy = e.rawY
        } else if (e.action == MotionEvent.ACTION_UP) {
            val now = SystemClock.uptimeMillis()
            val dur = (now - downT).coerceIn(40L, 3000L)
            val gap = if (lastEnd == 0L) 300L else (downT - lastEnd).coerceIn(30L, 15000L)
            val moved = Math.hypot((e.rawX - sx).toDouble(), (e.rawY - sy).toDouble()) > 40.0
            val ex = if (moved) e.rawX else sx
            val ey = if (moved) e.rawY else sy
            val d = if (moved) maxOf(dur, 120L) else dur
            MacroService.steps.add(Step(sx, sy, ex, ey, d, gap))
            lastEnd = now
            refreshInfo()
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        for (i in MacroService.steps.indices) {
            val s = MacroService.steps[i]
            val x1 = s.x1 - loc[0]
            val y1 = s.y1 - loc[1]
            if (s.x2 != s.x1 || s.y2 != s.y1) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = 0xAAC4B5FD.toInt()
                canvas.drawLine(x1, y1, s.x2 - loc[0], s.y2 - loc[1], paint)
            }
            paint.style = Paint.Style.FILL
            paint.color = 0xCC8B5CF6.toInt()
            canvas.drawCircle(x1, y1, 30f, paint)
            paint.color = Color.WHITE
            paint.textSize = 26f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("" + (i + 1), x1, y1 + 9f, paint)
        }
    }
}
