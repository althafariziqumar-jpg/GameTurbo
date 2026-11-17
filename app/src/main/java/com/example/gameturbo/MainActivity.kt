package com.example.gameturbo

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MainActivity : Activity() {
    private val bg = 0xFF080B16.toInt()
    private val cardBg = 0xFF111830.toInt()
    private val line = 0x33FFFFFF
    private val acc = 0xFF8B5CF6.toInt()
    private val cyan = 0xFF5CE1FF.toInt()
    private val sub = 0xFF8C95AD.toInt()
    private val okc = 0xFF22C55E.toInt()
    private val badc = 0xFFEF4444.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        S.init(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val need = ArrayList<String>()
        if (checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.RECORD_AUDIO")
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            need.add("android.permission.POST_NOTIFICATIONS")
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        show()
    }

    private fun show() {
        val name = S.s("player_name", "")
        if (name.isEmpty()) showLogin() else showHome(name)
    }

    private fun page(content: LinearLayout) {
        val sv = ScrollView(this)
        sv.setBackgroundColor(bg)
        sv.isFillViewport = true
        content.setPadding(dp(20), dp(24), dp(20), dp(24))
        sv.addView(content)
        setContentView(sv)
    }

    private fun lpTop(top: Int): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(-1, -2)
        p.setMargins(0, dp(top), 0, 0)
        return p
    }

    private fun word(t: String, color: Int): TextView {
        val v = tv(t, 28f, color, true)
        v.letterSpacing = 0.12f
        v.setPadding(dp(5), 0, dp(5), 0)
        return v
    }

    private fun brand(): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER_HORIZONTAL
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(word("BOLAY", Color.WHITE))
        row.addView(word("X", cyan))
        row.addView(word("UNAR", Color.WHITE))
        box.addView(row)
        val bar = View(this)
        bar.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x008B5CF6, acc, 0x008B5CF6)
        )
        val bp = LinearLayout.LayoutParams(dp(220), dp(2))
        bp.setMargins(0, dp(8), 0, dp(8))
        box.addView(bar, bp)
        val tag = tv("G A M E   T U R B O", 11f, sub)
        box.addView(tag)
        return box
    }

    private fun showLogin() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.addView(View(this), LinearLayout.LayoutParams(-1, dp(40)))
        root.addView(brand())

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = rr(cardBg, dp(20).toFloat(), line)
        box.setPadding(dp(18), dp(18), dp(18), dp(18))
        box.addView(tv("Masuk", 20f, Color.WHITE, true))
        val hint = tv("Nama pemain", 12f, sub)
        hint.setPadding(0, dp(14), 0, dp(6))
        box.addView(hint)

        val et = EditText(this)
        et.setSingleLine()
        et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(16))
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(sub)
        et.hint = "contoh: Rezz"
        et.background = rr(0x22FFFFFF, dp(12).toFloat(), line)
        et.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(et, LinearLayout.LayoutParams(-1, -2))

        val note = tv("Data hanya disimpan di HP ini.", 11f, sub)
        note.setPadding(0, dp(8), 0, dp(14))
        box.addView(note)

        val go = tv("Masuk", 15f, Color.WHITE, true)
        go.gravity = Gravity.CENTER
        go.setPadding(dp(12), dp(14), dp(12), dp(14))
        go.background = rr(acc, dp(14).toFloat())
        go.setOnClickListener {
            val n = et.text.toString().trim()
            if (n.length < 3) {
                Toast.makeText(this, "Nama minimal 3 huruf", Toast.LENGTH_SHORT).show()
            } else {
                S.put("player_name", n)
                if (S.s("player_id", "").isEmpty()) {
                    S.put("player_id", UUID.randomUUID().toString().substring(0, 8).uppercase())
                }
                show()
            }
        }
        box.addView(go, LinearLayout.LayoutParams(-1, -2))
        root.addView(box, lpTop(32))
        page(root)
    }

    private fun statusRow(label: String, value: String, ok: Boolean, onClick: () -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(10), 0, dp(10))
        r.addView(tv("●", 12f, if (ok) okc else badc))
        val l = tv(label, 14f, Color.WHITE, true)
        l.setPadding(dp(10), 0, 0, 0)
        r.addView(l, LinearLayout.LayoutParams(0, -2, 1f))
        r.addView(tv(value, 12f, if (ok) sub else cyan, !ok))
        if (!ok) r.setOnClickListener { onClick() }
        return r
    }

    private fun shizukuAction(s: Int) {
        if (s == 0) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
        } else if (s == 1) {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) startActivity(i)
        } else if (s == 2) {
            MacroService.askPermission()
        }
    }

    private fun mainCard(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1E2A5E.toInt(), 0xFF111830.toInt()))
        g.cornerRadius = dp(24).toFloat()
        g.setStroke(2, 0x338B5CF6)
        c.background = g
        c.setPadding(dp(18), dp(18), dp(18), dp(18))

        val icon = tv("🚀", 26f)
        icon.gravity = Gravity.CENTER
        icon.background = rr(0x22FFFFFF, dp(16).toFloat(), line)
        c.addView(icon, LinearLayout.LayoutParams(dp(60), dp(60)))

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), 0, dp(8), 0)
        col.addView(tv("Masuk ke", 13f, sub))
        col.addView(tv("Game Turbo", 22f, Color.WHITE, true))
        c.addView(col, LinearLayout.LayoutParams(0, -2, 1f))

        val go = tv("→", 24f, bg, true)
        go.gravity = Gravity.CENTER
        val oval = GradientDrawable()
        oval.shape = GradientDrawable.OVAL
        oval.setColor(Color.WHITE)
        go.background = oval
        c.addView(go, LinearLayout.LayoutParams(dp(52), dp(52)))

        c.setOnClickListener { start() }
        return c
    }

    private fun showHome(name: String) {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(tv("Home", 28f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        val idBox = LinearLayout(this)
        idBox.orientation = LinearLayout.VERTICAL
        idBox.gravity = Gravity.END
        val idl = tv("PLAYER ID", 10f, sub)
        idl.letterSpacing = 0.2f
        val idv = tv(S.s("player_id", "--------"), 16f, Color.WHITE)
        idv.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        idBox.addView(idl)
        idBox.addView(idv)
        head.addView(idBox)
        root.addView(head)

        val b = brand()
        root.addView(b, lpTop(28))
        val hi = tv("Selamat datang, " + name, 13f, sub)
        hi.gravity = Gravity.CENTER
        root.addView(hi, lpTop(14))

        root.addView(mainCard(), lpTop(24))

        val st = LinearLayout(this)
        st.orientation = LinearLayout.VERTICAL
        st.background = rr(cardBg, dp(20).toFloat(), line)
        st.setPadding(dp(18), dp(12), dp(18), dp(12))
        val stt = tv("STATUS", 11f, sub)
        stt.letterSpacing = 0.2f
        stt.setPadding(0, dp(4), 0, dp(2))
        st.addView(stt)

        val overlayOk = Settings.canDrawOverlays(this)
        st.addView(statusRow("Tampil di atas aplikasi", if (overlayOk) "Aktif" else "Izinkan", overlayOk) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        })
        val micOk = checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED
        st.addView(statusRow("Mikrofon", if (micOk) "Aktif" else "Izinkan", micOk) {
            requestPermissions(arrayOf("android.permission.RECORD_AUDIO"), 2)
        })
        var sz = 0
        if (MacroService.granted()) {
            sz = 3
        } else if (MacroService.binderAlive()) {
            sz = 2
        } else if (MacroService.installed(this)) {
            sz = 1
        }
        var szText = "Pasang"
        if (sz == 3) szText = "Siap"
        if (sz == 2) szText = "Izinkan"
        if (sz == 1) szText = "Jalankan"
        val szNow = sz
        st.addView(statusRow("Shizuku (Macro)", szText, sz == 3) { shizukuAction(szNow) })
        root.addView(st, lpTop(16))

        val foot = LinearLayout(this)
        foot.orientation = LinearLayout.HORIZONTAL
        foot.gravity = Gravity.CENTER
        val off = tv("Matikan Turbo", 13f, badc, true)
        off.setPadding(dp(14), dp(12), dp(14), dp(12))
        off.setOnClickListener {
            Triggers.destroy()
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Turbo dimatikan", Toast.LENGTH_SHORT).show()
        }
        val ren = tv("Ganti nama", 13f, sub, true)
        ren.setPadding(dp(14), dp(12), dp(14), dp(12))
        ren.setOnClickListener {
            S.put("player_name", "")
            show()
        }
        foot.addView(off)
        foot.addView(ren)
        root.addView(foot, lpTop(12))

        val ver = tv("Bolay X Unar  •  v1.0", 11f, sub)
        ver.gravity = Gravity.CENTER
        root.addView(ver, lpTop(4))
        page(root)
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampil di atas aplikasi lain'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        Triggers.restore(this)
        moveTaskToBack(true)
    }
}
