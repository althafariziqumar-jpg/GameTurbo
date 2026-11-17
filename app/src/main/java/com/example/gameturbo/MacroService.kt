package com.example.gameturbo

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import rikka.shizuku.Shizuku

class Step(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val dur: Long, val delay: Long)

class MacroService {
    companion object {
        val steps = ArrayList<Step>()

        @Volatile
        var running = false

        private val engine = MacroService()

        val inst: MacroService?
            get() = if (granted()) engine else null

        fun installed(c: Context): Boolean {
            return try {
                c.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun binderAlive(): Boolean {
            return try {
                Shizuku.pingBinder()
            } catch (e: Throwable) {
                false
            }
        }

        fun granted(): Boolean {
            return try {
                Shizuku.pingBinder() && !Shizuku.isPreV11() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        }

        fun askPermission() {
            try {
                Shizuku.requestPermission(7001)
            } catch (e: Throwable) {
            }
        }

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

    private var sh: Process? = null
    private var worker: Thread? = null

    fun play(loops: Int, speed: Int, startDelayMs: Long) {
        if (steps.isEmpty() || running) return
        running = true
        val list = ArrayList(steps)
        val t = Thread {
            try {
                Thread.sleep(startDelayMs)
                var round = 0
                while (running && list.isNotEmpty()) {
                    for (s in list) {
                        if (!running) break
                        Thread.sleep(maxOf(10L, s.delay * 100 / speed))
                        if (!running) break
                        val d = maxOf(20L, s.dur * 100 / speed)
                        send("input swipe " + s.x1.toInt() + " " + s.y1.toInt() + " " + s.x2.toInt() + " " + s.y2.toInt() + " " + d + " >/dev/null 2>&1 &")
                    }
                    round++
                    if (loops != 0 && round >= loops) break
                }
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
            }
            running = false
            closeShell()
        }
        worker = t
        t.start()
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        closeShell()
    }

    private fun openShell(): Process? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            m.invoke(null, arrayOf("sh"), null, null) as Process
        } catch (e: Throwable) {
            null
        }
    }

    private fun send(cmd: String) {
        for (attempt in 0..1) {
            try {
                var p = sh
                if (p == null) {
                    p = openShell()
                    sh = p
                }
                if (p == null) return
                val o = p.outputStream
                o.write((cmd + "\n").toByteArray())
                o.flush()
                return
            } catch (e: Throwable) {
                sh = null
            }
        }
    }

    private fun closeShell() {
        val p = sh
        sh = null
        if (p != null) {
            try {
                p.destroy()
            } catch (e: Throwable) {
            }
        }
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
