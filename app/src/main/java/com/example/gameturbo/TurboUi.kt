package com.example.gameturbo

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

object S {
    private var sp: SharedPreferences? = null
    fun init(c: Context) {
        if (sp == null) sp = c.applicationContext.getSharedPreferences("gt", Context.MODE_PRIVATE)
    }
    fun i(k: String, d: Int): Int = sp?.getInt(k, d) ?: d
    fun b(k: String, d: Boolean): Boolean = sp?.getBoolean(k, d) ?: d
    fun s(k: String, d: String): String = sp?.getString(k, d) ?: d
    fun put(k: String, v: Int) { sp?.edit()?.putInt(k, v)?.apply() }
    fun put(k: String, v: Boolean) { sp?.edit()?.putBoolean(k, v)?.apply() }
    fun put(k: String, v: String) { sp?.edit()?.putString(k, v)?.apply() }
    fun reset() {
        val p = sp ?: return
        val e = p.edit()
        for (k in p.all.keys) {
            if (k != "macro") e.remove(k)
        }
        e.apply()
    }
}

object Th {
    val BG = 0xF0161625.toInt()
    val CARD = 0xFF23233B.toInt()
    val ACC = 0xFF8B5CF6.toInt()
    val ACC2 = 0xFFC4B5FD.toInt()
    val TXT = 0xFFFFFFFF.toInt()
    val SUB = 0xFF9CA3AF.toInt()
    val OFF = 0xFF4B5563.toInt()
    val OK = 0xFF22C55E.toInt()
    val RED = 0xFFEF4444.toInt()
}

fun rr(color: Int, r: Float, stroke: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(color)
    g.cornerRadius = r
    if (stroke != 0) g.setStroke(3, stroke)
    return g
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.tv(text: String, size: Float = 14f, color: Int = Th.TXT, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = size
    t.setTextColor(color)
    if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
    return t
}

fun Context.note(text: String): TextView {
    val t = tv(text, 12f, Th.SUB)
    t.setPadding(dp(4), dp(4), dp(4), dp(8))
    return t
}

fun Context.arrow(): TextView = tv("›", 26f, Th.TXT)

fun Context.card(): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.background = rr(Th.CARD, dp(14).toFloat())
    l.setPadding(dp(12), dp(6), dp(12), dp(6))
    val lp = LinearLayout.LayoutParams(-1, -2)
    lp.setMargins(0, 0, 0, dp(8))
    l.layoutParams = lp
    return l
}

fun Context.divider(): View {
    val v = View(this)
    v.setBackgroundColor(0x22FFFFFF)
    v.layoutParams = LinearLayout.LayoutParams(-1, 1)
    return v
}

fun Context.item(icon: String, title: String, subtitle: String, right: View?, onClick: (() -> Unit)?): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(0, dp(8), 0, dp(8))
    val ic = tv(icon, 22f, Th.ACC2)
    ic.gravity = Gravity.CENTER
    r.addView(ic, LinearLayout.LayoutParams(dp(40), -2))
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    col.addView(tv(title, 15f, Th.TXT, true))
    if (subtitle.isNotEmpty()) col.addView(tv(subtitle, 12f, Th.SUB))
    r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
    if (right != null) r.addView(right)
    if (onClick != null) r.setOnClickListener { onClick() }
    return r
}

fun Context.toggle(on: Boolean, onChange: (Boolean) -> Unit): FrameLayout {
    val f = FrameLayout(this)
    val knob = View(this)
    knob.background = rr(Color.WHITE, dp(11).toFloat())
    val kp = FrameLayout.LayoutParams(dp(22), dp(22))
    kp.setMargins(dp(3), dp(3), dp(3), dp(3))
    f.addView(knob, kp)
    f.layoutParams = LinearLayout.LayoutParams(dp(48), dp(28))
    var state = on
    fun paint() {
        f.background = rr(if (state) Th.ACC else Th.OFF, dp(14).toFloat())
        kp.gravity = if (state) Gravity.END else Gravity.START
        knob.layoutParams = kp
    }
    paint()
    f.setOnClickListener {
        state = !state
        paint()
        onChange(state)
    }
    return f
}

fun Context.slider(title: String, min: Int, max: Int, key: String, def: Int, unit: String, onChange: () -> Unit): LinearLayout {
    val box = LinearLayout(this)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(0, dp(6), 0, dp(6))
    val head = LinearLayout(this)
    head.orientation = LinearLayout.HORIZONTAL
    val value = tv("" + S.i(key, def) + unit, 13f, Th.ACC2, true)
    head.addView(tv(title, 13f, Th.TXT), LinearLayout.LayoutParams(0, -2, 1f))
    head.addView(value)
    val sb = SeekBar(this)
    sb.max = max - min
    sb.progress = S.i(key, def) - min
    sb.progressTintList = ColorStateList.valueOf(Th.ACC)
    sb.thumbTintList = ColorStateList.valueOf(Th.ACC2)
    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
            val v = p + min
            value.text = "" + v + unit
            if (fromUser) {
                S.put(key, v)
                onChange()
            }
        }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
    box.addView(head)
    box.addView(sb)
    return box
}

fun Context.choice(labels: List<String>, key: String, def: Int, onChange: () -> Unit): LinearLayout {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.setPadding(0, dp(6), 0, dp(6))
    val views = ArrayList<TextView>()
    fun paint() {
        val sel = S.i(key, def)
        for (i in views.indices) {
            views[i].background = rr(if (i == sel) Th.ACC else Th.OFF, dp(10).toFloat())
        }
    }
    for (i in labels.indices) {
        val t = tv(labels[i], 11f, Th.TXT, true)
        t.gravity = Gravity.CENTER
        t.setPadding(dp(2), dp(7), dp(2), dp(7))
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(2), 0, dp(2), 0)
        t.setOnClickListener {
            S.put(key, i)
            paint()
            onChange()
        }
        views.add(t)
        row.addView(t, lp)
    }
    paint()
    return row
}

fun Context.btn(text: String, color: Int = Th.ACC, onClick: () -> Unit): TextView {
    val t = tv(text, 13f, Th.TXT, true)
    t.gravity = Gravity.CENTER
    t.setPadding(dp(10), dp(10), dp(10), dp(10))
    t.background = rr(color, dp(12).toFloat())
    t.setOnClickListener { onClick() }
    return t
}

fun Context.rowOf(vararg vs: View): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    for (v in vs) {
        val lp = LinearLayout.LayoutParams(0, -2, 1f)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        r.addView(v, lp)
    }
    return r
}

fun Context.tile(icon: String, label: String, onClick: () -> Unit): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.gravity = Gravity.CENTER
    l.background = rr(Th.CARD, dp(12).toFloat(), 0x558B5CF6)
    l.setPadding(dp(4), dp(10), dp(4), dp(10))
    val ic = tv(icon, 24f, Th.ACC2)
    ic.gravity = Gravity.CENTER
    val lb = tv(label, 11f, Th.TXT)
    lb.gravity = Gravity.CENTER
    l.addView(ic)
    l.addView(lb)
    l.setOnClickListener { onClick() }
    return l
}

class VoiceChanger {
    private var data: ShortArray? = null
    @Volatile
    var recording = false
    private var track: AudioTrack? = null
    private val rate = 16000

    fun hasData(): Boolean = data != null

    fun record(seconds: Int, onDone: () -> Unit) {
        if (recording) return
        recording = true
        Thread {
            val total = rate * seconds
            val out = ShortArray(total)
            var pos = 0
            try {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val r = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 4096))
                r.startRecording()
                while (pos < total && recording) {
                    val n = r.read(out, pos, minOf(1024, total - pos))
                    if (n <= 0) break
                    pos += n
                }
                r.stop()
                r.release()
                data = if (pos > 0) out.copyOf(pos) else null
            } catch (e: Exception) {
                data = null
            }
            recording = false
            Handler(Looper.getMainLooper()).post { onDone() }
        }.start()
    }

    fun stopRecord() {
        recording = false
    }

    fun play(pitchPct: Int) {
        val d = data ?: return
        stop()
        try {
            val sr = rate * pitchPct / 100
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sr)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(fmt)
                .setBufferSizeInBytes(d.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(d, 0, d.size)
            t.play()
            track = t
        } catch (e: Exception) {
        }
    }

    fun stop() {
        val t = track
        track = null
        if (t != null) {
            try { t.stop() } catch (e: Exception) { }
            try { t.release() } catch (e: Exception) { }
        }
    }
}
