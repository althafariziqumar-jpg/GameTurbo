package com.example.gameturbo

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object Mc {
    val BG = 0xF0161625.toInt()
    val BTN = 0xFF23233B.toInt()
    val ACC = 0xFF8B5CF6.toInt()
    val ACC2 = 0xFFC4B5FD.toInt()
    val TXT = 0xFFFFFFFF.toInt()
    val DIM = 0xFF9CA3AF.toInt()
    val RED = 0xFFEF4444.toInt()
    val DARK = 0xFF161625.toInt()
}

class Trig(var id: Int, var bx: Int, var by: Int, var mode: Int, var hold: Int, var gap: Int, var count: Int) {
    val targets = ArrayList<IntArray>()
}

object Sh {
    private var proc: Process? = null
    private var fast = false
    private var triedFast = false
    private val ex = Executors.newSingleThreadExecutor()

    fun tap(x: Int, y: Int, hold: Int) {
        ex.execute { run(x, y, hold) }
    }

    fun close() {
        ex.execute { shut() }
    }

    private fun shut() {
        val p = proc
        proc = null
        if (p != null) {
            try {
                p.outputStream.close()
            } catch (e: Throwable) {
            }
            try {
                p.destroy()
            } catch (e: Throwable) {
            }
        }
    }

    private fun newProc(cmd: Array<String>): Process? {
        return try {
            val cls = Class.forName("rikka.shizuku.Shizuku")
            val m = cls.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            m.isAccessible = true
            m.invoke(null, cmd, null, null) as Process
        } catch (e: Throwable) {
            null
        }
    }

    private fun openFast(): Process? {
        val apk = Triggers.apk()
        if (apk.isEmpty()) return null
        val p = newProc(arrayOf("sh", "-c", "exec app_process -Djava.class.path=" + apk + " /system/bin com.example.gameturbo.InjectorKt")) ?: return null
        val latch = CountDownLatch(1)
        var ok = false
        val watcher = Thread {
            try {
                val r = p.inputStream.bufferedReader()
                ok = r.readLine() == "READY"
                latch.countDown()
                if (ok) {
                    while (true) {
                        val l = r.readLine() ?: break
                        if (l == "ERR") {
                            ex.execute {
                                fast = false
                                shut()
                            }
                            break
                        }
                    }
                }
            } catch (e: Throwable) {
            }
            latch.countDown()
        }
        watcher.isDaemon = true
        watcher.start()
        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (e: Throwable) {
        }
        if (!ok) {
            try {
                p.destroy()
            } catch (e: Throwable) {
            }
            return null
        }
        return p
    }

    private fun ensure(): Boolean {
        if (proc != null) return true
        if (!triedFast || fast) {
            triedFast = true
            val p = openFast()
            if (p != null) {
                proc = p
                fast = true
                return true
            }
            fast = false
        }
        val q = newProc(arrayOf("sh"))
        proc = q
        return q != null
    }

    private fun run(x: Int, y: Int, hold: Int) {
        for (attempt in 0..1) {
            try {
                if (!ensure()) return
                val cmd = if (fast) {
                    "T " + x + " " + y + " " + hold
                } else {
                    "input swipe " + x + " " + y + " " + x + " " + y + " " + hold + " >/dev/null 2>&1 &"
                }
                val o = proc!!.outputStream
                o.write((cmd + "\n").toByteArray())
                o.flush()
                return
            } catch (e: Throwable) {
                proc = null
            }
        }
    }
}

class TargetView(c: Context, private val label: String, private val sel: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width * 0.30f
        p.style = Paint.Style.FILL
        p.color = 0x338B5CF6
        canvas.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 4f
        p.color = if (sel) 0xFFE879F9.toInt() else 0x99E879F9.toInt()
        canvas.drawCircle(cx, cy, r, p)
        canvas.drawLine(cx - r - 10f, cy, cx + r + 10f, cy, p)
        canvas.drawLine(cx, cy - r - 10f, cx, cy + r + 10f, p)
        p.style = Paint.Style.FILL
        p.textSize = width * 0.26f
        p.textAlign = Paint.Align.CENTER
        p.color = Color.WHITE
        canvas.drawText(label, cx, cy + p.textSize * 0.35f, p)
    }
}

object Triggers {
    const val MAX = 6
    private var app: Context? = null
    private var wm: WindowManager? = null
    private val list = ArrayList<Trig>()
    private var sel = 0
    private var editing = false
    private var snapshot = ""
    private var nextId = 1
    private val handler = Handler(Looper.getMainLooper())
    private val views = ArrayList<View>()
    private val loops = HashMap<Int, Runnable>()
    private var editor: LinearLayout? = null
    private var edBody: LinearLayout? = null
    private var edCount: TextView? = null
    private var settingsOpen = false
    private var onBack: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null

    fun size(): Int = list.size

    fun apk(): String {
        val a = app ?: return ""
        return a.applicationInfo.sourceDir ?: ""
    }

    fun restore(c: Context) {
        setup(c)
        if (!editing) rebuild()
    }

    fun reload() {
        decode(S.s("mt_data", ""))
        sel = 0
        if (!editing) rebuild()
    }

    fun setVisible(on: Boolean) {
        S.put("mt_on", on)
        if (!editing) rebuild()
    }

    fun openEditor(c: Context, back: (() -> Unit)?, done: (() -> Unit)?) {
        setup(c)
        if (editing) return
        onBack = back
        onDone = done
        snapshot = encode()
        editing = true
        settingsOpen = false
        if (list.isEmpty()) addTrigger()
        buildEditor()
        rebuild()
    }

    fun destroy() {
        for (r in loops.values) handler.removeCallbacks(r)
        loops.clear()
        clearViews()
        closeEditor()
        Sh.close()
    }

    private fun setup(c: Context) {
        if (app == null) {
            val a = c.applicationContext
            app = a
            wm = a.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            S.init(a)
            decode(S.s("mt_data", ""))
        }
    }

    private fun dp(v: Int): Int {
        val a = app ?: return v
        return (v * a.resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun sz(): Int = dp(S.i("mt_size", 56))

    private fun real(): DisplayMetrics {
        val dm = DisplayMetrics()
        wm?.defaultDisplay?.getRealMetrics(dm)
        return dm
    }

    private fun toast(msg: String) {
        val a = app ?: return
        Toast.makeText(a, msg, Toast.LENGTH_SHORT).show()
    }

    private fun lp(w: Int, h: Int, x: Int, y: Int, touch: Boolean): LP {
        var f = LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_IN_SCREEN or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_SPLIT_TOUCH
        if (!touch) f = f or LP.FLAG_NOT_TOUCHABLE
        val p = LP(w, h, LP.TYPE_APPLICATION_OVERLAY, f, PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.START
        p.x = x
        p.y = y
        if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return p
    }

    private fun encode(): String {
        val sb = StringBuilder()
        for (t in list) {
            sb.append(t.id).append(',').append(t.bx).append(',').append(t.by).append(',')
            sb.append(t.mode).append(',').append(t.hold).append(',').append(t.gap).append(',')
            sb.append(t.count).append(',')
            for (g in t.targets) {
                sb.append(g[0]).append(':').append(g[1]).append('|')
            }
            sb.append(';')
        }
        return sb.toString()
    }

    private fun decode(raw: String) {
        list.clear()
        for (part in raw.split(";")) {
            if (part.isEmpty()) continue
            val f = part.split(",")
            if (f.size < 8) continue
            try {
                val t = Trig(f[0].toInt(), f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt())
                for (g in f[7].split("|")) {
                    if (g.isEmpty()) continue
                    val xy = g.split(":")
                    t.targets.add(intArrayOf(xy[0].toInt(), xy[1].toInt()))
                }
                list.add(t)
                if (t.id >= nextId) nextId = t.id + 1
            } catch (e: Exception) {
            }
        }
    }

    private fun save() {
        S.put("mt_data", encode())
    }

    private fun clearViews() {
        val w = wm
        for (v in views) {
            try {
                w?.removeViewImmediate(v)
            } catch (e: Exception) {
            }
        }
        views.clear()
    }

    private fun bringEditorFront() {
        val e = editor ?: return
        val w = wm ?: return
        val p = e.layoutParams as LP
        try {
            w.removeViewImmediate(e)
        } catch (x: Exception) {
        }
        try {
            w.addView(e, p)
        } catch (x: Exception) {
        }
    }

    private fun rebuild() {
        val w = wm ?: return
        clearViews()
        if (!editing && !S.b("mt_on", true)) return
        val s = sz()
        for (i in list.indices) {
            val t = list[i]
            val v = makeButton(t, i)
            try {
                w.addView(v, lp(s, s, t.bx, t.by, true))
                views.add(v)
            } catch (e: Exception) {
            }
        }
        if (editing) {
            val ms = dp(44)
            for (i in list.indices) {
                val t = list[i]
                for (gi in t.targets.indices) {
                    val g = t.targets[gi]
                    val m = makeMarker(t, gi, i == sel, ms)
                    try {
                        w.addView(m, lp(ms, ms, g[0] - ms / 2, g[1] - ms / 2, true))
                        views.add(m)
                    } catch (e: Exception) {
                    }
                }
            }
            bringEditorFront()
        }
    }

    private fun applyLook() {
        val w = wm ?: return
        val s = sz()
        val a = S.i("mt_alpha", 67) / 100f
        for (v in views) {
            if (v.tag == "b") {
                val p = v.layoutParams as LP
                p.width = s
                p.height = s
                try {
                    w.updateViewLayout(v, p)
                } catch (e: Exception) {
                }
                v.alpha = a
            }
        }
    }

    private fun paintBtn(v: TextView, selected: Boolean, active: Boolean) {
        val cols = if (active) {
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFC4B5FD.toInt())
        } else {
            intArrayOf(0xFFA78BFA.toInt(), 0xFF6D28D9.toInt())
        }
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, cols)
        g.shape = GradientDrawable.OVAL
        if (selected) g.setStroke(6, Mc.ACC2) else g.setStroke(3, 0x88FFFFFF.toInt())
        v.background = g
        v.setTextColor(if (active) Mc.DARK else Color.WHITE)
    }

    private fun makeButton(t: Trig, idx: Int): TextView {
        val v = TextView(app!!)
        v.tag = "b"
        v.text = "" + t.id
        v.textSize = 16f
        v.gravity = Gravity.CENTER
        v.alpha = S.i("mt_alpha", 67) / 100f
        paintBtn(v, editing && idx == sel, false)
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val p = view.layoutParams as LP
                if (editing) {
                    if (e.action == MotionEvent.ACTION_DOWN) {
                        sx = p.x
                        sy = p.y
                        tx = e.rawX
                        ty = e.rawY
                        moved = false
                    } else if (e.action == MotionEvent.ACTION_MOVE) {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 8 || abs(dy) > 8) moved = true
                        if (moved) {
                            p.x = sx + dx
                            p.y = sy + dy
                            t.bx = p.x
                            t.by = p.y
                            wm?.updateViewLayout(view, p)
                        }
                    } else if (e.action == MotionEvent.ACTION_UP) {
                        if (!moved) {
                            sel = list.indexOf(t)
                            handler.post {
                                rebuild()
                                renderEditor()
                            }
                        }
                    }
                    return true
                }
                if (e.action == MotionEvent.ACTION_DOWN) {
                    down(t, view as TextView)
                } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    up(t, view as TextView)
                }
                return true
            }
        })
        return v
    }

    private fun makeMarker(t: Trig, gi: Int, selected: Boolean, ms: Int): View {
        val v = TargetView(app!!, "" + t.id, selected)
        v.tag = "m"
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(view: View, e: MotionEvent): Boolean {
                val p = view.layoutParams as LP
                if (e.action == MotionEvent.ACTION_DOWN) {
                    sx = p.x
                    sy = p.y
                    tx = e.rawX
                    ty = e.rawY
                } else if (e.action == MotionEvent.ACTION_MOVE) {
                    p.x = sx + (e.rawX - tx).toInt()
                    p.y = sy + (e.rawY - ty).toInt()
                    wm?.updateViewLayout(view, p)
                    if (gi < t.targets.size) {
                        val g = t.targets[gi]
                        g[0] = p.x + ms / 2
                        g[1] = p.y + ms / 2
                    }
                }
                return true
            }
        })
        return v
    }

    private fun fireOnce(t: Trig) {
        for (g in t.targets) {
            Sh.tap(g[0], g[1], t.hold)
        }
    }

    private fun burst(t: Trig) {
        val n = maxOf(1, t.count)
        for (i in 0 until n) {
            handler.postDelayed({ fireOnce(t) }, (i * t.gap).toLong())
        }
    }

    private fun startLoop(t: Trig) {
        if (loops.containsKey(t.id)) return
        val r = object : Runnable {
            override fun run() {
                fireOnce(t)
                handler.postDelayed(this, maxOf(30, t.gap).toLong())
            }
        }
        loops[t.id] = r
        handler.post(r)
    }

    private fun stopLoop(t: Trig) {
        val r = loops.remove(t.id)
        if (r != null) handler.removeCallbacks(r)
    }

    private fun down(t: Trig, v: TextView) {
        if (!MacroService.granted()) {
            toast("Shizuku belum siap. Buka menu Macro di panel Turbo.")
            return
        }
        if (t.targets.isEmpty()) {
            toast("Trigger ini belum punya target. Buka editor lalu pasang target.")
            return
        }
        if (t.mode == 0) {
            paintBtn(v, false, true)
            burst(t)
        } else if (t.mode == 1) {
            paintBtn(v, false, true)
            startLoop(t)
        } else {
            if (loops.containsKey(t.id)) {
                stopLoop(t)
                paintBtn(v, false, false)
            } else {
                startLoop(t)
                paintBtn(v, false, true)
            }
        }
    }

    private fun up(t: Trig, v: TextView) {
        if (t.mode == 1) {
            stopLoop(t)
            paintBtn(v, false, false)
        } else if (t.mode == 0) {
            handler.postDelayed({ paintBtn(v, false, false) }, 120L)
        }
    }

    private fun addTrigger() {
        if (list.size >= MAX) {
            toast("Maksimal " + MAX + " trigger")
            return
        }
        val dm = real()
        val s = sz()
        val off = list.size * dp(12)
        val t = Trig(nextId++, dm.widthPixels / 2 - s / 2 + off, dm.heightPixels / 2 - s / 2 + off, 0, 40, 150, 1)
        list.add(t)
        sel = list.size - 1
    }

    private fun addTarget() {
        if (list.isEmpty()) {
            toast("Tambah trigger dulu")
            return
        }
        val t = list[sel]
        if (t.targets.size >= 3) {
            toast("Maksimal 3 target per trigger")
            return
        }
        val dm = real()
        t.targets.add(intArrayOf(dm.widthPixels / 2 + t.targets.size * dp(40), dm.heightPixels / 2))
    }

    private fun deleteSel() {
        if (list.isEmpty()) return
        val t = list.removeAt(sel)
        stopLoop(t)
        if (sel >= list.size) sel = maxOf(0, list.size - 1)
        rebuild()
        renderEditor()
    }

    private fun closeEditor() {
        val e = editor
        editor = null
        edBody = null
        edCount = null
        editing = false
        if (e != null) {
            try {
                wm?.removeViewImmediate(e)
            } catch (x: Exception) {
            }
        }
    }

    // kind: 0 = selesai (simpan), 1 = kembali (simpan), 2 = batal
    private fun finish(kind: Int) {
        val d = onDone
        val b = onBack
        if (kind == 2) {
            decode(snapshot)
            sel = 0
        } else {
            save()
            S.put("mt_on", true)
        }
        closeEditor()
        rebuild()
        if (kind == 1) b?.invoke() else d?.invoke()
    }

    private fun lm(w: Int, h: Int, weight: Float): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.setMargins(dp(3), dp(3), dp(3), dp(3))
        return p
    }

    private fun edBtn(text: String, bg: Int, fg: Int, click: () -> Unit): TextView {
        val t = app!!.tv(text, 12f, fg, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(8), dp(11), dp(8), dp(11))
        t.background = rr(bg, dp(12).toFloat(), 0x338B5CF6)
        t.setOnClickListener { click() }
        return t
    }

    private fun buildEditor() {
        val c = app!!
        val dm = real()
        val land = dm.widthPixels > dm.heightPixels
        val w = if (land) minOf(dp(340), (dm.widthPixels * 0.5f).toInt()) else (dm.widthPixels * 0.94f).toInt()
        val root = LinearLayout(c)
        root.orientation = LinearLayout.VERTICAL
        root.background = rr(Mc.BG, dp(20).toFloat(), Mc.ACC)
        root.setPadding(dp(12), dp(10), dp(12), dp(10))

        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val grip = c.tv("⠿", 22f, Mc.DIM, true)
        grip.setPadding(dp(4), dp(4), dp(10), dp(4))
        grip.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val p = root.layoutParams as LP
                if (e.action == MotionEvent.ACTION_DOWN) {
                    sx = p.x
                    sy = p.y
                    tx = e.rawX
                    ty = e.rawY
                } else if (e.action == MotionEvent.ACTION_MOVE) {
                    p.x = sx + (e.rawX - tx).toInt()
                    p.y = sy + (e.rawY - ty).toInt()
                    wm?.updateViewLayout(root, p)
                }
                return true
            }
        })
        val back = c.tv("‹", 30f, Mc.TXT, true)
        back.setPadding(dp(2), 0, dp(10), 0)
        back.setOnClickListener { handler.post { finish(1) } }
        val title = c.tv("⚡ Macro", 20f, Mc.ACC, true)
        val count = c.tv("0/" + MAX, 12f, Mc.ACC2, true)
        count.setPadding(dp(10), dp(4), dp(10), dp(4))
        count.background = rr(0x338B5CF6, dp(10).toFloat())
        edCount = count
        val cancel = edBtn("✕", Mc.BTN, Mc.TXT) { handler.post { finish(2) } }
        val ok = edBtn("✓", Mc.ACC, Mc.DARK) { handler.post { finish(0) } }
        head.addView(grip)
        head.addView(back)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(count, lm(-2, -2, 0f))
        head.addView(cancel, lm(dp(44), -2, 0f))
        head.addView(ok, lm(dp(44), -2, 0f))

        val body = LinearLayout(c)
        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(c)
        sv.addView(body)
        root.addView(head)
        root.addView(sv, LinearLayout.LayoutParams(-1, -2))

        editor = root
        edBody = body
        wm!!.addView(root, lp(w, LP.WRAP_CONTENT, dm.widthPixels - w - dp(8), dp(8), true))
        renderEditor()
    }

    private fun valSlider(title: String, min: Int, max: Int, unit: String, getter: () -> Int, setter: (Int) -> Unit, step: Int): LinearLayout {
        val c = app!!
        val box = LinearLayout(c)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(4), 0, dp(4))
        val value = c.tv("" + getter() + unit, 13f, Mc.ACC2, true)
        val head = LinearLayout(c)
        head.orientation = LinearLayout.HORIZONTAL
        head.addView(c.tv(title, 13f, Mc.DIM), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(value)
        val sb = SeekBar(c)
        sb.max = max - min
        sb.progress = getter().coerceIn(min, max) - min
        sb.progressTintList = ColorStateList.valueOf(Mc.ACC)
        sb.thumbTintList = ColorStateList.valueOf(Mc.ACC2)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    setter(p + min)
                    value.text = "" + (p + min) + unit
                }
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        if (step > 0) {
            val minus = c.tv("−", 24f, Mc.TXT, true)
            minus.setPadding(dp(10), 0, dp(10), 0)
            minus.setOnClickListener {
                val x = (getter() - step).coerceIn(min, max)
                setter(x)
                value.text = "" + x + unit
                sb.progress = x - min
            }
            val plus = c.tv("+", 24f, Mc.TXT, true)
            plus.setPadding(dp(10), 0, dp(10), 0)
            plus.setOnClickListener {
                val x = (getter() + step).coerceIn(min, max)
                setter(x)
                value.text = "" + x + unit
                sb.progress = x - min
            }
            row.addView(minus)
            row.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(plus)
        } else {
            row.addView(sb, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(head)
        box.addView(row)
        return box
    }

    private fun renderEditor() {
        val body = edBody ?: return
        val c = app!!
        body.removeAllViews()
        edCount?.text = "" + list.size + "/" + MAX

        val r1 = LinearLayout(c)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.addView(edBtn("＋ Trigger", Mc.BTN, Mc.TXT) {
            addTrigger()
            rebuild()
            renderEditor()
        }, lm(0, -2, 1f))
        r1.addView(edBtn("◎ Target", Mc.BTN, Mc.TXT) {
            addTarget()
            rebuild()
            renderEditor()
        }, lm(0, -2, 1f))
        body.addView(r1)

        val has = list.isNotEmpty()
        val chips = LinearLayout(c)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.gravity = Gravity.CENTER_VERTICAL
        chips.setPadding(dp(3), dp(4), dp(3), dp(4))
        if (!has) {
            chips.addView(c.tv("Belum ada trigger", 12f, Mc.DIM))
        } else {
            val lb = c.tv("Pilih:", 12f, Mc.DIM)
            lb.setPadding(0, 0, dp(6), 0)
            chips.addView(lb)
            for (i in list.indices) {
                val on = i == sel
                chips.addView(edBtn("" + list[i].id, if (on) Mc.ACC else Mc.BTN, if (on) Mc.DARK else Mc.TXT) {
                    sel = i
                    rebuild()
                    renderEditor()
                }, lm(dp(38), -2, 0f))
            }
        }
        body.addView(chips)

        val r2 = LinearLayout(c)
        r2.orientation = LinearLayout.HORIZONTAL
        r2.addView(edBtn("🗑 Hapus", Mc.BTN, Mc.RED) { deleteSel() }, lm(0, -2, 1f))
        r2.addView(edBtn("⚙ Atur", if (settingsOpen) Mc.ACC else Mc.BTN, if (settingsOpen) Mc.DARK else Mc.TXT) {
            settingsOpen = !settingsOpen
            renderEditor()
        }, lm(0, -2, 1f))
        body.addView(r2)

        if (has && list[sel].targets.isEmpty()) {
            val hint = c.tv("Tekan ◎ Target, lalu geser lingkaran ke tombol di game.", 11f, Mc.DIM)
            hint.setPadding(dp(4), dp(2), dp(4), dp(2))
            body.addView(hint)
        }

        if (settingsOpen && has) {
            val t = list[sel]
            val card = LinearLayout(c)
            card.orientation = LinearLayout.VERTICAL
            card.setPadding(dp(10), dp(8), dp(10), dp(8))
            card.background = rr(Mc.BTN, dp(14).toFloat(), 0x228B5CF6)
            card.addView(edBtn("⚡ Preset Gloo Wall (tahan & cepat)", Mc.ACC, Mc.DARK) {
                t.mode = 1
                t.hold = 20
                t.gap = 45
                renderEditor()
            }, lm(-1, -2, 0f))
            card.addView(c.tv("Mode tekan", 12f, Mc.DIM))
            val modes = LinearLayout(c)
            modes.orientation = LinearLayout.HORIZONTAL
            val names = listOf("Sekali", "Tahan", "Toggle")
            for (i in names.indices) {
                modes.addView(edBtn(names[i], if (t.mode == i) Mc.ACC else Mc.BG, if (t.mode == i) Mc.DARK else Mc.TXT) {
                    t.mode = i
                    renderEditor()
                }, lm(0, -2, 1f))
            }
            card.addView(modes)
            card.addView(valSlider("Durasi tekan", 20, 500, " ms", { t.hold }, { t.hold = it }, 10))
            card.addView(valSlider("Jeda ulang", 30, 1000, " ms", { t.gap }, { t.gap = it }, 5))
            card.addView(valSlider("Jumlah ketukan (mode Sekali)", 1, 20, "x", { t.count }, { t.count = it }, 1))
            card.addView(c.tv("Target: " + t.targets.size + "/3", 12f, Mc.DIM))
            card.addView(edBtn("Hapus target terakhir", Mc.BG, Mc.TXT) {
                if (t.targets.isNotEmpty()) {
                    t.targets.removeAt(t.targets.size - 1)
                    rebuild()
                    renderEditor()
                }
            })
            body.addView(card)
        }

        body.addView(valSlider("Ukuran tombol", 32, 100, "dp", { S.i("mt_size", 56) }, {
            S.put("mt_size", it)
            applyLook()
        }, 2))
        body.addView(valSlider("Transparansi tombol", 20, 100, "%", { S.i("mt_alpha", 67) }, {
            S.put("mt_alpha", it)
            applyLook()
        }, 0))
    }
}
