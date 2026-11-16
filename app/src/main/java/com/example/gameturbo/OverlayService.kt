package com.example.gameturbo

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var cross: CrosshairView
    private lateinit var hud: TextView
    private lateinit var bubble: TextView
    private var menu: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.CYAN, Color.YELLOW, Color.WHITE)
    private var colorIdx = 0

    private val ticker = object : Runnable {
        override fun run() {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            hud.text = "$time | $bat%\nRAM ${mi.availMem / 1048576} MB"
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

        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            textSize = 10f
            setPadding(10, 2, 10, 2)
            visibility = View.GONE
        }
        val hp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true)
        hp.x = 430
        hp.y = 4
        wm.addView(hud, hp)
        drag(hud) { }

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
        drag(bubble) {
            val p = bubble.layoutParams as LP
            toggleMenu(p.x, p.y)
        }
        handler.post(ticker)
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

    private fun drag(v: View, onTap: () -> Unit) {
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val p = view.layoutParams as LP
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
                        wm.updateViewLayout(view, p)
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
        add("Info jam/baterai/RAM") {
            hud.visibility = if (hud.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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
        listOfNotNull(cross, hud, bubble, menu).forEach { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
}
