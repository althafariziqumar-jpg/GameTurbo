package com.example.gameturbo

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var cross: CrosshairView
    private lateinit var bar: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var tCpu: TextView
    private lateinit var tGpu: TextView
    private lateinit var tRam: TextView
    private lateinit var tBat: TextView
    private lateinit var tTemp: TextView
    private lateinit var tHz: TextView
    private lateinit var tClock: TextView
    private var menu: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.CYAN, Color.YELLOW, Color.WHITE)
    private var colorIdx = 0
    private var frames = 0
    private var lastTick = 0L

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frames++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val ramPct = if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() else 0
            val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val bat = if (level >= 0 && scale > 0) level * 100 / scale else 0
            val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
            val now = SystemClock.elapsedRealtime()
            val dt = if (lastTick == 0L) 1000L else now - lastTick
            lastTick = now
            val hz = (frames * 1000L / dt).toInt()
            frames = 0
            setVal(tCpu, "CPU ", readCpu(), "%", 75)
            setVal(tGpu, "GPU ", readGpu(), "%", 75)
            setVal(tRam, "RAM ", ramPct, "%", 75)
            setVal(tBat, "BAT ", bat, "%", 1000)
            setVal(tTemp, "", temp, "°C", 40)
            setVal(tHz, "", hz, "Hz", 1000)
            tClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())

        cross = CrosshairView(this)
        wm.addView(cross, params(LP.MATCH_PARENT, LP.MATCH_PARENT, touch = false, full = true))

        buildBar()

        bubble = TextView(this).apply {
            text = "GT"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xAA1565C0.toInt())
            width = 120
            height = 120
        }
        val bp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true)
        bp.x = 20
        bp.y = 300
        wm.addView(bubble, bp)
        drag(bubble, bubble) {
            val p = bubble.layoutParams as LP
            toggleMenu(p.x, p.y)
        }

        Choreographer.getInstance().postFrameCallback(frameCb)
        handler.post(ticker)
    }

    private fun metric(): TextView {
        return TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(14, 0, 14, 0)
            text = "--"
        }
    }

    private fun buildBar() {
        val handle = TextView(this).apply {
            text = "✥"
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(0xFF29B6F6.toInt())
                cornerRadius = 20f
            }
        }
        tCpu = metric()
        tGpu = metric()
        tRam = metric()
        tBat = metric()
        tTemp = metric()
        tHz = metric()
        tClock = metric()
        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xCC202020.toInt())
                cornerRadius = 40f
            }
            setPadding(8, 6, 16, 6)
        }
        bar.addView(handle, LinearLayout.LayoutParams(64, 64))
        for (t in listOf(tCpu, tGpu, tRam, tBat, tTemp, tHz, tClock)) {
            bar.addView(t)
        }
        val dm = resources.displayMetrics
        val lp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true)
        lp.x = (dm.widthPixels * 0.27f).toInt()
        lp.y = (dm.heightPixels * 0.68f).toInt()
        wm.addView(bar, lp)
        drag(handle, bar) { }
    }

    private fun setVal(t: TextView, label: String, v: Int, unit: String, warnAt: Int) {
        if (v < 0) {
            t.text = label + "--"
            t.setTextColor(Color.WHITE)
        } else {
            t.text = label + v + unit
            t.setTextColor(if (v >= warnAt) 0xFFFFA000.toInt() else Color.WHITE)
        }
    }

    private fun readCpu(): Int {
        var cur = 0L
        var max = 0L
        val n = Runtime.getRuntime().availableProcessors()
        for (i in 0 until n) {
            try {
                val base = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/"
                val c = File(base + "scaling_cur_freq").readText().trim().toLong()
                val m = File(base + "cpuinfo_max_freq").readText().trim().toLong()
                cur += c
                max += m
            } catch (e: Exception) {
            }
        }
        return if (max > 0) (cur * 100 / max).toInt() else -1
    }

    private fun readGpu(): Int {
        try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
            if (f.canRead()) {
                return f.readText().trim().split(" ")[0].toInt()
            }
        } catch (e: Exception) {
        }
        try {
            val f = File("/sys/class/kgsl/kgsl-3d0/gpubusy")
            if (f.canRead()) {
                val p = f.readText().trim().split(Regex("\\s+"))
                val busy = p[0].toLong()
                val total = p[1].toLong()
                if (total > 0) return (busy * 100 / total).toInt()
            }
        } catch (e: Exception) {
        }
        return -1
    }

    private fun params(w: Int, h: Int, touch: Boolean, full: Boolean = false): LP {
        var flags = LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_KEEP_SCREEN_ON
        if (!touch) flags = flags or LP.FLAG_NOT_TOUCHABLE
        if (full) flags = flags or LP.FLAG_LAYOUT_NO_LIMITS
        val p = LP(w, h, LP.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun drag(handleView: View, target: View, onTap: () -> Unit) {
        handleView.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val p = target.layoutParams as LP
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        p.x = sx + dx
                        p.y = sy + dy
                        wm.updateViewLayout(target, p)
                    }
                    MotionEvent.ACTION_UP -> if (!moved) onTap()
                }
                return true
            }
        })
    }

    private fun toggleMenu(x: Int, y: Int) {
        menu?.let {
            wm.removeView(it)
            menu = null
            return
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
        }
        fun btn(t: String, a: () -> Unit): Button {
            return Button(this).apply {
                text = t
                isAllCaps = false
                textSize = 12f
                minHeight = 0
                minimumHeight = 0
                setPadding(8, 4, 8, 4)
                setOnClickListener { a() }
            }
        }
        fun add(t: String, a: () -> Unit) {
            col.addView(btn(t, a))
        }
        add("Crosshair ON/OFF") {
            cross.visibility = if (cross.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        add("Ganti bentuk") { cross.style = (cross.style + 1) % 4; cross.invalidate() }
        add("Ganti warna") {
            colorIdx = (colorIdx + 1) % colors.size
            cross.color = colors[colorIdx]
            cross.invalidate()
        }
        add("Ukuran +") { cross.size += 8; cross.invalidate() }
        add("Ukuran -") { if (cross.size > 12) cross.size -= 8; cross.invalidate() }
        add("Tebal +/-") {
            cross.thick = if (cross.thick >= 10) 2f else cross.thick + 2
            cross.invalidate()
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun nudge(t: String, dx: Float, dy: Float) {
            row.addView(btn(t) {
                cross.offX += dx
                cross.offY += dy
                cross.invalidate()
            })
        }
        nudge("◀", -2f, 0f)
        nudge("▶", 2f, 0f)
        nudge("▲", 0f, -2f)
        nudge("▼", 0f, 2f)
        col.addView(row)
        add("Reset posisi") { cross.offX = 0f; cross.offY = 0f; cross.invalidate() }
        add("Info bar ON/OFF") {
            bar.visibility = if (bar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        add("Bersihkan RAM") { clearRam() }
        add("Tutup Game Turbo") { stopSelf() }

        val sv = ScrollView(this).apply {
            setBackgroundColor(0xEE212121.toInt())
            addView(col)
        }
        val h = (resources.displayMetrics.heightPixels * 0.8).toInt()
        val lp = params(LP.WRAP_CONTENT, h, touch = true)
        lp.x = x + 130
        lp.y = 20
        wm.addView(sv, lp)
        menu = sv
    }

    private fun clearRam() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .forEach { am.killBackgroundProcesses(it.packageName); n++ }
        Toast.makeText(this, "Proses latar belakang dibersihkan ($n app)", Toast.LENGTH_SHORT).show()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gt", "Game Turbo", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "gt")
            .setContentTitle("Game Turbo aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        listOfNotNull(cross, bar, bubble, menu).forEach { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}
