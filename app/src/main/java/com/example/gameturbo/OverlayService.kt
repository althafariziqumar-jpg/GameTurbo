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
    private var menu: LinearLayout? = null
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
            hud.text = "$time | Baterai $bat% | RAM bebas ${mi.availMem / 1048576} MB"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())

        cross = CrosshairView(this)
        wm.addView(cross, params(LP.MATCH_PARENT, LP.MATCH_PARENT, touch = false))

        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x88000000.toInt())
            textSize = 11f
            setPadding(12, 4, 12, 4)
            visibility = View.GONE
        }
        wm.addView(hud, params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = false, gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        bubble = TextView(this).apply {
            text = "GT"
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xAA1565C0.toInt())
            width = 120; height = 120
        }
        val bp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true).apply { x = 20; y = 300 }
        wm.addView(bubble, bp)
        bubble.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val p = v.layoutParams as LP
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        p.x = sx + dx; p.y = sy + dy
                        wm.updateViewLayout(v, p)
                    }
                    MotionEvent.ACTION_UP -> if (!moved) toggleMenu(p.x, p.y)
                }
                return true
            }
        })
        handler.post(ticker)
    }

    private fun params(w: Int, h: Int, touch: Boolean, gravity: Int = Gravity.TOP or Gravity.START) =
        LP(
            w, h, LP.TYPE_APPLICATION_OVERLAY,
            (if (touch) 0 else LP.FLAG_NOT_TOUCHABLE) or LP.FLAG_NOT_FOCUSABLE or
                LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).also { it.gravity = gravity }

    private fun toggleMenu(x: Int, y: Int) {
        if (menu != null) { wm.removeView(menu); menu = null; return }
        val m = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE212121.toInt())
            setPadding(16, 16, 16, 16)
        }
        fun btn(t: String, a: () -> Unit) = m.addView(Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { a() }
        })
        btn("Crosshair ON/OFF") { cross.visibility = if (cross.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        btn("Ganti bentuk") { cross.style = (cross.style + 1) % 4; cross.invalidate() }
        btn("Ganti warna") { colorIdx = (colorIdx + 1) % colors.size; cross.color = colors[colorIdx]; cross.invalidate() }
        btn("Ukuran +") { cross.size += 8; cross.invalidate() }
        btn("Ukuran -") { if (cross.size > 12) cross.size -= 8; cross.invalidate() }
        btn("Tebal +/-") { cross.thick = if (cross.thick >= 10) 2f else cross.thick + 2; cross.invalidate() }
        btn("Info (jam/baterai/RAM)") { hud.visibility = if (hud.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        btn("Bersihkan RAM") { clearRam() }
        btn("Tutup Game Turbo") { stopSelf() }
        wm.addView(m, params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true).apply { this.x = x + 130; this.y = y })
        menu = m
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
