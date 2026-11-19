package com.example.gameturbo

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestActivity : Activity() {
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private val text = StringBuilder()
    private val wmApp: WindowManager by lazy {
        applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    private var lastErr = ""
    private var padA: TextView? = null
    private var padB: TextView? = null

    @Volatile
    private var running = false

    @Volatile
    private var hitsA = 0

    @Volatile
    private var bDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        S.init(this)
        window.statusBarColor = 0xFF0B0B18.toInt()
        window.navigationBarColor = 0xFF0B0B18.toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0B0B18.toInt())
        root.setPadding(dp(16), dp(20), dp(16), dp(16))
        root.addView(tv("Tes Macro", 22f, Th.ACC, true))
        root.addView(note("Tes ini mengecek apakah Shizuku dan pengetuk macro bekerja di HP Anda. Ikuti petunjuk di layar dan jangan sentuh layar sampai diminta."))
        root.addView(rowOf(btn("Jalankan tes") { start() }, btn("Salin hasil", Th.OFF) { copyResult() }))
        logView = tv("", 12f, Color.WHITE)
        logView.typeface = Typeface.MONOSPACE
        scroll = ScrollView(this)
        scroll.addView(logView)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun say(s: String) {
        runOnUiThread {
            text.append(s).append('\n')
            logView.text = text.toString()
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun copyResult() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("tes", text.toString()))
        Toast.makeText(this, "Hasil disalin", Toast.LENGTH_SHORT).show()
    }

    private fun start() {
        if (running) return
        running = true
        text.setLength(0)
        logView.text = ""
        Thread {
            try {
                runTests()
            } catch (e: Throwable) {
                say("Galat tes: " + e)
            }
            removePads()
            running = false
            say("== Selesai ==")
        }.start()
    }

    private fun yn(b: Boolean): String = if (b) "ya" else "TIDAK"

    private fun newProc(cmd: Array<String>): Process? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            m.invoke(null, cmd, null, null) as Process
        } catch (e: Throwable) {
            lastErr = "" + (e.cause ?: e)
            null
        }
    }

    private fun pump(p: Process, out: CopyOnWriteArrayList<String>) {
        val t = Thread {
            try {
                val r = BufferedReader(InputStreamReader(p.inputStream))
                while (true) {
                    val l = r.readLine() ?: break
                    out.add(l)
                }
            } catch (e: Throwable) {
            }
        }
        t.isDaemon = true
        t.start()
    }

    private fun waitLines(l: CopyOnWriteArrayList<String>, n: Int, ms: Long) {
        var t = 0L
        while (l.size < n && t < ms) {
            Thread.sleep(100)
            t += 100
        }
    }

    private fun lp(w: Int, h: Int, x: Int, y: Int): LP {
        val p = LP(
            w, h, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_SPLIT_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = x
        p.y = y
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun onUi(block: () -> Unit) {
        val l = CountDownLatch(1)
        runOnUiThread {
            try {
                block()
            } catch (e: Throwable) {
                say("UI galat: " + e)
            }
            l.countDown()
        }
        l.await(3, TimeUnit.SECONDS)
    }

    private fun showPads(cx: Int, cy: Int) {
        onUi {
            val s = dp(150)
            val a = tv("Tes A\n0", 16f, Color.WHITE, true)
            a.gravity = Gravity.CENTER
            a.background = rr(0xDD6D28D9.toInt(), dp(16).toFloat(), Color.WHITE)
            a.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    hitsA++
                    a.text = "Tes A\n" + hitsA
                }
                true
            }
            wmApp.addView(a, lp(s, s, cx - s / 2, cy - s / 2))
            padA = a

            val b = tv("TAHAN\njari di sini", 14f, Color.WHITE, true)
            b.gravity = Gravity.CENTER
            b.background = rr(0xDD0369A1.toInt(), dp(16).toFloat(), Color.WHITE)
            b.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) bDown = true
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) bDown = false
                true
            }
            wmApp.addView(b, lp(s, s, dp(24), cy - s / 2))
            padB = b
        }
    }

    private fun removePads() {
        onUi {
            val a = padA
            val b = padB
            padA = null
            padB = null
            try {
                if (a != null) wmApp.removeViewImmediate(a)
            } catch (e: Throwable) {
            }
            try {
                if (b != null) wmApp.removeViewImmediate(b)
            } catch (e: Throwable) {
            }
        }
    }

    private fun sendFast(p: Process, x: Int, y: Int, n: Int) {
        try {
            val o = p.outputStream
            for (i in 0 until n) {
                o.write(("T " + x + " " + y + " 40\n").toByteArray())
                o.flush()
                Thread.sleep(300)
            }
        } catch (e: Throwable) {
            say("Kirim gagal: " + e)
        }
    }

    private fun sendSlow(x: Int, y: Int, n: Int): Boolean {
        val ps = newProc(arrayOf("sh"))
        if (ps == null) {
            say("GAGAL membuka shell: " + lastErr)
            return false
        }
        try {
            val o = ps.outputStream
            for (i in 0 until n) {
                o.write(("input swipe " + x + " " + y + " " + x + " " + y + " 40 >/dev/null 2>&1 &\n").toByteArray())
                o.flush()
                Thread.sleep(300)
            }
        } catch (e: Throwable) {
            say("Kirim gagal: " + e)
        }
        Thread.sleep(1200)
        try {
            ps.destroy()
        } catch (e: Throwable) {
        }
        return true
    }

    private fun runTests() {
        say("== Info ==")
        say("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), " + Build.MANUFACTURER + " " + Build.MODEL)
        val inst = MacroService.installed(this)
        val alive = MacroService.binderAlive()
        val ok = MacroService.granted()
        say("Shizuku terpasang: " + yn(inst))
        say("Shizuku berjalan: " + yn(alive))
        say("Izin Shizuku: " + yn(ok))
        try {
            say("Versi Shizuku: " + Shizuku.getVersion() + ", uid: " + Shizuku.getUid())
        } catch (e: Throwable) {
            say("Versi Shizuku: tidak terbaca")
        }
        val ov = Settings.canDrawOverlays(this)
        say("Izin tampil di atas aplikasi: " + yn(ov))
        if (!ok) {
            say("BERHENTI: Shizuku belum siap. Jalankan Shizuku, lalu izinkan Game Turbo (menu Macro).")
            return
        }
        if (!ov) {
            say("BERHENTI: izinkan 'Tampil di atas aplikasi lain' untuk Game Turbo.")
            return
        }
        val dm = DisplayMetrics()
        wmApp.defaultDisplay.getRealMetrics(dm)
        val cx = dm.widthPixels / 2
        val cy = dm.heightPixels / 2
        say("Layar: " + dm.widthPixels + "x" + dm.heightPixels)

        say("== Tes 1: shell Shizuku ==")
        val p1 = newProc(arrayOf("sh", "-c", "id"))
        if (p1 == null) {
            say("GAGAL: " + lastErr)
            return
        }
        val o1 = CopyOnWriteArrayList<String>()
        pump(p1, o1)
        waitLines(o1, 1, 3000)
        say(if (o1.isEmpty()) "tidak ada jawaban" else o1[0])
        try {
            p1.destroy()
        } catch (e: Throwable) {
        }

        say("== Tes 2: pengetuk cepat ==")
        val apk = applicationInfo.sourceDir
        val pi = newProc(arrayOf("sh", "-c", "exec app_process -Djava.class.path=" + apk + " /system/bin com.example.gameturbo.InjectorKt"))
        val oi = CopyOnWriteArrayList<String>()
        var ready = false
        if (pi == null) {
            say("GAGAL memulai: " + lastErr)
        } else {
            pump(pi, oi)
            waitLines(oi, 2, 5000)
            for (l in oi) say("  " + l)
            ready = oi.isNotEmpty() && oi[0] == "READY"
            if (oi.isEmpty()) say("  tidak ada jawaban dalam 5 detik")
        }

        showPads(cx, cy)
        Thread.sleep(600)

        var fastHits = -1
        if (ready && pi != null) {
            say("== Tes 3: ketuk lewat pengetuk cepat ==")
            say("Jangan sentuh layar...")
            hitsA = 0
            sendFast(pi, cx, cy, 5)
            Thread.sleep(800)
            fastHits = hitsA
            say("Ketukan diterima: " + fastHits + "/5")
            if (oi.size > 2) {
                for (i in 2 until oi.size) say("  " + oi[i])
            }
        }

        say("== Tes 4: ketuk lewat 'input' (cadangan) ==")
        say("Jangan sentuh layar...")
        hitsA = 0
        var slowHits = -1
        if (sendSlow(cx, cy, 5)) {
            slowHits = hitsA
            say("Ketukan diterima: " + slowHits + "/5")
        }

        say("== Tes 5: ketuk saat jari menahan layar ==")
        say("TAHAN jari Anda di kotak BIRU (kiri) sekarang dan jangan dilepas sampai ada tulisan 'Lepaskan'.")
        var held = -1
        var waited = 0
        while (!bDown && waited < 100) {
            Thread.sleep(100)
            waited++
        }
        if (!bDown) {
            say("Dilewati: kotak biru tidak ditekan.")
        } else {
            hitsA = 0
            if (ready && pi != null) {
                sendFast(pi, cx, cy, 5)
                Thread.sleep(800)
            } else {
                sendSlow(cx, cy, 5)
            }
            held = hitsA
            say("Ketukan diterima saat jari menahan: " + held + "/5")
            say("Lepaskan jari Anda.")
        }

        say("== Kesimpulan ==")
        if (fastHits == 5) {
            say("Pengetuk cepat: OK")
        } else if (ready) {
            say("Pengetuk cepat: jalan, tapi ketukan tidak sampai (" + fastHits + "/5)")
        } else {
            say("Pengetuk cepat: tidak jalan (aplikasi memakai cadangan)")
        }
        say("Cadangan (input): " + slowHits + "/5")
        if (held >= 0) {
            say("Saat jari menahan layar: " + held + "/5" + (if (held < 5) "  -> ketukan macro DITOLAK saat ada jari lain" else "  -> OK"))
        }
        if (fastHits <= 0 && slowHits <= 0) {
            say("Ketukan tidak sampai sama sekali. Di Opsi pengembang, nyalakan 'Debugging USB (Pengaturan keamanan)' atau 'Nonaktifkan pemantauan izin' jika ada, lalu mulai ulang Shizuku.")
        }
        if (pi != null) {
            try {
                pi.outputStream.close()
            } catch (e: Throwable) {
            }
            try {
                pi.destroy()
            } catch (e: Throwable) {
            }
        }
    }
}
