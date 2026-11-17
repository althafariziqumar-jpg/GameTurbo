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
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {
    val voice = VoiceChanger()
    private lateinit var wm: WindowManager
    private lateinit var cross: CrosshairView
    private lateinit var bar: LinearLayout
    private lateinit var bubble: TextView
    private lateinit var tCpu: TextView
    private lateinit var tGpu: TextView
    private lateinit var tRam: TextView
    private lateinit var tBat: TextView
    private lateinit var tTemp: TextView
    private lateinit var tPing: TextView
    private lateinit var tHz: TextView
    private lateinit var tClock: TextView
    private lateinit var tTimer: TextView
    private var panel: TurboPanel? = null
    private var recView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val colors = intArrayOf(Color.RED, Color.GREEN, Color.CYAN, Color.YELLOW, Color.WHITE, 0xFFA855F7.toInt())
    private var frames = 0
    private var lastTick = 0L
    private var autoCount = 0
    private var timerEnd = 0L
    private var lastMacro = false
    @Volatile
    private var alive = true
    @Volatile
    private var pingMs = -1

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
            setVal(tPing, "", pingMs, " ms", 150)
            setVal(tHz, "", hz, "Hz", 1000)
            tClock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            if (timerEnd != 0L) {
                val left = timerLeft()
                if (left <= 0) {
                    timerEnd = 0L
                    tTimer.visibility = View.GONE
                    Toast.makeText(this@OverlayService, "Timer selesai", Toast.LENGTH_LONG).show()
                    if (S.b("tm_vib", true)) vibrate()
                } else {
                    tTimer.visibility = View.VISIBLE
                    tTimer.text = "⏳ " + fmt(left)
                }
            }

            val auto = S.i("mem_auto", 0)
            if (auto > 0) {
                autoCount++
                if (autoCount >= auto * 60) {
                    cleanRam()
                    autoCount = 0
                }
            } else {
                autoCount = 0
            }

            if (lastMacro != MacroService.running) {
                lastMacro = MacroService.running
                applyOptions()
            }
            panel?.tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        S.init(this)
        MacroService.load()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(1, buildNotification())

        cross = CrosshairView(this)
        wm.addView(cross, params(LP.MATCH_PARENT, LP.MATCH_PARENT, touch = false, full = true))
        buildBar()
        buildBubble()
        applyCrosshair()
        applyMonitor()
        applyOptions()

        Choreographer.getInstance().postFrameCallback(frameCb)
        startPing()
        handler.post(ticker)
    }

    private fun params(w: Int, h: Int, touch: Boolean, full: Boolean = false): LP {
        var flags = LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN
        if (!touch) flags = flags or LP.FLAG_NOT_TOUCHABLE
        if (full) flags = flags or LP.FLAG_LAYOUT_NO_LIMITS
        val p = LP(w, h, LP.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun metric(): TextView {
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.textSize = 11f
        t.setPadding(dp(5), 0, dp(5), 0)
        t.text = "--"
        return t
    }

    private fun buildBar() {
        val handle = TextView(this)
        handle.text = "✥"
        handle.setTextColor(Color.WHITE)
        handle.textSize = 16f
        handle.gravity = Gravity.CENTER
        handle.background = rr(0xFF29B6F6.toInt(), dp(8).toFloat())
        tCpu = metric()
        tGpu = metric()
        tRam = metric()
        tBat = metric()
        tTemp = metric()
        tPing = metric()
        tHz = metric()
        tClock = metric()
        tTimer = metric()
        tTimer.visibility = View.GONE
        bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.background = rr(0xCC202020.toInt(), dp(16).toFloat())
        bar.setPadding(dp(3), dp(3), dp(8), dp(3))
        bar.addView(handle, LinearLayout.LayoutParams(dp(28), dp(28)))
        for (t in listOf(tCpu, tGpu, tRam, tBat, tTemp, tPing, tHz, tClock, tTimer)) {
            bar.addView(t)
        }
        val dm = resources.displayMetrics
        val lp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true)
        lp.x = (dm.widthPixels * 0.27f).toInt()
        lp.y = (dm.heightPixels * 0.68f).toInt()
        wm.addView(bar, lp)
        drag(handle, bar) { }
    }

    private fun buildBubble() {
        bubble = TextView(this)
        bubble.text = "⚡"
        bubble.textSize = 20f
        bubble.setTextColor(Color.WHITE)
        bubble.gravity = Gravity.CENTER
        val bp = params(LP.WRAP_CONTENT, LP.WRAP_CONTENT, touch = true)
        bp.x = 20
        bp.y = 300
        wm.addView(bubble, bp)
        drag(bubble, bubble) { onBubbleTap() }
    }

    private fun onBubbleTap() {
        if (MacroService.running) {
            stopMacro()
            return
        }
        if (panel != null) closePanel() else openPanel()
    }

    private fun openPanel() {
        val p = TurboPanel(this, this)
        val dm = resources.displayMetrics
        val w = minOf(dp(360), (dm.widthPixels * 0.92f).toInt())
        val h = minOf(dp(560), (dm.heightPixels * 0.9f).toInt())
        val lp = params(w, h, touch = true)
        lp.gravity = Gravity.CENTER
        wm.addView(p.root, lp)
        panel = p
    }

    fun closePanel() {
        val p = panel ?: return
        panel = null
        voice.stop()
        try {
            wm.removeView(p.root)
        } catch (e: Exception) {
        }
    }

    fun quit() {
        stopSelf()
    }

    fun applyCrosshair() {
        cross.style = S.i("ch_style", 0)
        cross.color = colors[S.i("ch_color", 0)]
        cross.size = S.i("ch_size", 40).toFloat()
        cross.thick = S.i("ch_thick", 4).toFloat()
        cross.offX = S.i("ch_x", 0).toFloat()
        cross.offY = S.i("ch_y", 0).toFloat()
        cross.alpha = S.i("ch_alpha", 100) / 100f
        cross.visibility = if (S.b("ch_on", true)) View.VISIBLE else View.GONE
        cross.invalidate()
    }

    fun applyMonitor() {
        bar.visibility = if (S.b("mon_on", true)) View.VISIBLE else View.GONE
        bar.alpha = S.i("mon_alpha", 90) / 100f
        val sz = S.i("mon_size", 11).toFloat()
        val items = listOf(tCpu, tGpu, tRam, tBat, tTemp, tPing, tHz, tClock)
        val keys = listOf("mon_cpu", "mon_gpu", "mon_ram", "mon_bat", "mon_temp", "mon_ping", "mon_hz", "mon_clock")
        for (i in items.indices) {
            items[i].visibility = if (S.b(keys[i], true)) View.VISIBLE else View.GONE
            items[i].textSize = sz
        }
        tTimer.textSize = sz
    }

    fun applyOptions() {
        val s = dp(S.i("bub_size", 44))
        bubble.width = s
        bubble.height = s
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(if (MacroService.running) Th.RED else Th.ACC)
        bubble.background = g
        bubble.text = if (MacroService.running) "■" else "⚡"
        bubble.alpha = S.i("bub_alpha", 85) / 100f
        val lp = cross.layoutParams as LP
        if (S.b("keep_on", true)) {
            lp.flags = lp.flags or LP.FLAG_KEEP_SCREEN_ON
        } else {
            lp.flags = lp.flags and LP.FLAG_KEEP_SCREEN_ON.inv()
        }
        wm.updateViewLayout(cross, lp)
    }

    fun setDnd(on: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            S.put("dnd", false)
            Toast.makeText(this, "Izinkan akses 'Jangan Ganggu' untuk Game Turbo", Toast.LENGTH_LONG).show()
            val i = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            closePanel()
            return
        }
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL
        )
    }

    fun cleanRam(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var n = 0
        for (a in packageManager.getInstalledApplications(0)) {
            if (a.packageName != packageName && (a.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                am.killBackgroundProcesses(a.packageName)
                n++
            }
        }
        return n
    }

    fun ramInfo(): String {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / 1048576
        val free = mi.availMem / 1048576
        val pct = if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() else 0
        return "RAM terpakai " + pct + "% (bebas " + free + " MB dari " + total + " MB)"
    }

    fun startTimer(min: Int) {
        timerEnd = SystemClock.elapsedRealtime() + min * 60000L
    }

    fun stopTimer() {
        timerEnd = 0L
        tTimer.visibility = View.GONE
    }

    fun timerLeft(): Int {
        if (timerEnd == 0L) return 0
        val l = ((timerEnd - SystemClock.elapsedRealtime()) / 1000).toInt()
        return if (l < 0) 0 else l
    }

    private fun fmt(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "" + (if (m < 10) "0" else "") + m + ":" + (if (s < 10) "0" else "") + s
    }

    private fun vibrate() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }

    fun startRecord() {
        closePanel()
        MacroService.steps.clear()
        val v = RecordView(this) { stopRecord() }
        wm.addView(v, params(LP.MATCH_PARENT, LP.MATCH_PARENT, touch = true, full = true))
        recView = v
    }

    private fun stopRecord() {
        val v = recView
        recView = null
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (e: Exception) {
            }
        }
        MacroService.save()
        Toast.makeText(this, "Macro tersimpan: " + MacroService.steps.size + " langkah", Toast.LENGTH_SHORT).show()
    }

    fun playMacro() {
        val m = MacroService.inst
        if (m == null) {
            Toast.makeText(this, "Aktifkan layanan aksesibilitas dulu (menu Macro)", Toast.LENGTH_LONG).show()
            return
        }
        if (MacroService.steps.isEmpty()) {
            Toast.makeText(this, "Belum ada macro. Tekan Rekam dulu.", Toast.LENGTH_LONG).show()
            return
        }
        closePanel()
        m.play(S.i("mc_loops", 1), S.i("mc_speed", 100), S.i("mc_start", 2) * 1000L)
        applyOptions()
        Toast.makeText(this, "Macro mulai. Ketuk bubble untuk berhenti.", Toast.LENGTH_SHORT).show()
    }

    fun stopMacro() {
        MacroService.inst?.stop()
        MacroService.running = false
        applyOptions()
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

    private fun startPing() {
        Thread {
            while (alive) {
                if (S.b("mon_on", true) && S.b("mon_ping", true)) {
                    var ms = -1
                    try {
                        val t0 = SystemClock.elapsedRealtime()
                        val s = Socket()
                        s.connect(InetSocketAddress("1.1.1.1", 443), 1500)
                        ms = (SystemClock.elapsedRealtime() - t0).toInt()
                        s.close()
                    } catch (e: Exception) {
                    }
                    pingMs = ms
                }
                try {
                    Thread.sleep(3000)
                } catch (e: Exception) {
                }
            }
        }.start()
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
                        sx = p.x
                        sy = p.y
                        tx = e.rawX
                        ty = e.rawY
                        moved = false
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

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("gt", "Game Turbo", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(this, "gt")
            .setContentTitle("Game Turbo aktif")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacks(ticker)
        Choreographer.getInstance().removeFrameCallback(frameCb)
        voice.stopRecord()
        voice.stop()
        MacroService.inst?.stop()
        closePanel()
        for (v in listOf<View?>(cross, bar, bubble, recView)) {
            if (v != null) {
                try {
                    wm.removeView(v)
                } catch (e: Exception) {
                }
            }
        }
        super.onDestroy()
    }
}
