package com.example.gameturbo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class TurboPanel(private val ctx: Context, private val svc: OverlayService) {
    companion object {
        var live: TurboPanel? = null

        fun reopen(svc: OverlayService) {
            val l = live
            if (l != null && l.root.isAttachedToWindow) return
            try {
                val m = OverlayService::class.java.getDeclaredMethod("openPanel")
                m.isAccessible = true
                m.invoke(svc)
            } catch (e: Throwable) {
            }
        }
    }

    val root = LinearLayout(ctx)
    private val scroll = ScrollView(ctx)
    private val body = LinearLayout(ctx)
    private val stack = ArrayList<String>()
    private var cur = ""
    private var timerStatus: TextView? = null
    private var voiceStatus: TextView? = null
    private val title: TextView
    private val backBtn: TextView

    init {
        live = this
        Triggers.restore(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.background = rr(Th.BG, ctx.dp(20).toFloat(), Th.ACC)
        root.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        root.alpha = S.i("panel_alpha", 100) / 100f

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        backBtn = ctx.tv("‹", 30f, Th.TXT, true)
        backBtn.setPadding(0, 0, ctx.dp(12), 0)
        backBtn.setOnClickListener { goBack() }
        title = ctx.tv("⚡ TURBO", 22f, Th.ACC, true)
        val close = ctx.tv("✕", 22f, Th.TXT, true)
        close.setPadding(ctx.dp(12), 0, 0, 0)
        close.setOnClickListener { svc.closePanel() }
        head.addView(backBtn)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(close)

        body.orientation = LinearLayout.VERTICAL
        body.setPadding(0, ctx.dp(8), 0, ctx.dp(4))
        scroll.addView(body)

        root.addView(head)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        go("main", false)
    }

    fun tick() {
        val t = timerStatus
        if (t != null && cur == "timer") t.text = timerText()
    }

    private fun go(p: String, push: Boolean = true) {
        if (push && cur.isNotEmpty()) stack.add(cur)
        cur = p
        render()
    }

    private fun goBack() {
        if (stack.isNotEmpty()) {
            cur = stack.removeAt(stack.size - 1)
            render()
        } else {
            svc.closePanel()
        }
    }

    private fun add(v: View) {
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, ctx.dp(4), 0, ctx.dp(4))
        body.addView(v, lp)
    }

    private fun render(keep: Boolean = false) {
        val y = scroll.scrollY
        body.removeAllViews()
        timerStatus = null
        voiceStatus = null
        backBtn.visibility = if (cur == "main") View.GONE else View.VISIBLE
        title.text = when (cur) {
            "voice" -> "Voice Changer"
            "crosshair" -> "Crosshair"
            "monitor" -> "Monitor"
            "extra" -> "Pengaturan Tambahan"
            "macro" -> "Macro"
            "speed" -> "Percepat Game"
            "memory" -> "Bersihkan Memori"
            "timer" -> "Timer"
            "settings" -> "Pengaturan"
            else -> "⚡ TURBO"
        }
        when (cur) {
            "voice" -> pgVoice()
            "crosshair" -> pgCrosshair()
            "monitor" -> pgMonitor()
            "extra" -> pgExtra()
            "macro" -> pgMacro()
            "speed" -> pgSpeed()
            "memory" -> pgMemory()
            "timer" -> pgTimer()
            "settings" -> pgSettings()
            else -> pgMain()
        }
        if (keep) {
            scroll.post { scroll.scrollTo(0, y) }
        } else {
            scroll.scrollTo(0, 0)
        }
    }

    private fun pgMain() {
        val c1 = ctx.card()
        c1.addView(ctx.item("🎤", "Voice Changer", "Ubah suara kamu", ctx.arrow()) { go("voice") })
        c1.addView(ctx.divider())
        c1.addView(ctx.item("⌖", "Crosshair", "Aktifkan bidikan",
            ctx.toggle(S.b("ch_on", true)) {
                S.put("ch_on", it)
                svc.applyCrosshair()
            }) { go("crosshair") })
        c1.addView(ctx.divider())
        c1.addView(ctx.item("📈", "Monitor", "Lihat performa game", ctx.arrow()) { go("monitor") })
        body.addView(c1)

        val c2 = ctx.card()
        c2.addView(ctx.item("🎮", "Pengaturan Tambahan", "Fitur lainnya", ctx.arrow()) { go("extra") })
        body.addView(c2)

        add(ctx.rowOf(
            ctx.tile("🚀", "Percepat\nGame") { go("speed") },
            ctx.tile("🧹", "Bersihkan\nMemori") { go("memory") },
            ctx.tile("⏳", "Timer\n ") { go("timer") },
            ctx.tile("⚙", "Pengaturan\n ") { go("settings") }
        ))

        val foot = LinearLayout(ctx)
        foot.orientation = LinearLayout.HORIZONTAL
        foot.gravity = Gravity.CENTER_VERTICAL
        foot.background = rr(0x00000000, ctx.dp(12).toFloat(), Th.ACC)
        foot.setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        foot.addView(ctx.tv("⚡ TURBO v1.0", 13f, Th.ACC2), LinearLayout.LayoutParams(0, -2, 1f))
        val running = MacroService.running
        foot.addView(ctx.tv("● ", 13f, if (running) Th.RED else Th.OK))
        foot.addView(ctx.tv(if (running) "Macro berjalan" else "Siap Digunakan", 13f, Th.TXT))
        add(foot)
    }

    private fun pgVoice() {
        val presets = intArrayOf(70, 100, 145, 190)
        val c = ctx.card()
        c.addView(ctx.note("Android tidak mengizinkan suara diubah langsung di dalam game. Rekam suara dulu, lalu putar dengan nada yang dipilih."))
        body.addView(c)

        val c2 = ctx.card()
        c2.addView(ctx.tv("Preset suara", 13f, Th.TXT))
        c2.addView(ctx.choice(listOf("Berat", "Normal", "Tinggi", "Tupai"), "vc_preset", 1) {
            S.put("vc_pitch", presets[S.i("vc_preset", 1)])
            render()
        })
        c2.addView(ctx.slider("Nada", 50, 200, "vc_pitch", 100, "%") {})
        c2.addView(ctx.slider("Durasi rekam", 2, 10, "vc_sec", 4, " dtk") {})
        body.addView(c2)

        val st = ctx.tv(if (svc.voice.hasData()) "Rekaman siap diputar" else "Belum ada rekaman", 13f, Th.ACC2)
        st.setPadding(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4))
        voiceStatus = st
        body.addView(st)
        add(ctx.rowOf(
            ctx.btn("🎙 Rekam") { recordVoice() },
            ctx.btn("▶ Putar") { svc.voice.play(S.i("vc_pitch", 100)) },
            ctx.btn("■ Stop", Th.OFF) {
                svc.voice.stop()
                svc.voice.stopRecord()
            }
        ))
    }

    private fun recordVoice() {
        if (ctx.checkSelfPermission("android.permission.RECORD_AUDIO") != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ctx, "Izinkan mikrofon dulu: buka aplikasi Game Turbo", Toast.LENGTH_LONG).show()
            return
        }
        voiceStatus?.text = "Merekam... bicaralah sekarang"
        svc.voice.record(S.i("vc_sec", 4)) {
            voiceStatus?.text = if (svc.voice.hasData()) "Rekaman siap diputar" else "Rekaman gagal"
        }
    }

    private fun styleGrid(): LinearLayout {
        val sel = S.i("ch_style", 0)
        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        for (r in 0 until 3) {
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until 4) {
                val i = r * 4 + k
                val pv = CrosshairPreview(ctx, i)
                pv.background = rr(if (i == sel) Th.ACC else Th.OFF, ctx.dp(10).toFloat())
                pv.setOnClickListener {
                    S.put("ch_style", i)
                    svc.applyCrosshair()
                    render(true)
                }
                val lp = LinearLayout.LayoutParams(0, ctx.dp(56), 1f)
                lp.setMargins(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3))
                row.addView(pv, lp)
            }
            box.addView(row)
        }
        return box
    }

    private fun pgCrosshair() {
        val c = ctx.card()
        c.addView(ctx.item("⌖", "Crosshair aktif", "",
            ctx.toggle(S.b("ch_on", true)) {
                S.put("ch_on", it)
                svc.applyCrosshair()
            }, null))
        body.addView(c)

        val c1 = ctx.card()
        c1.addView(ctx.tv("Bentuk (" + CROSS_STYLES + " pilihan)", 13f, Th.TXT))
        c1.addView(styleGrid())
        body.addView(c1)

        val c2 = ctx.card()
        c2.addView(ctx.tv("Warna", 13f, Th.TXT))
        c2.addView(ctx.choice(listOf("Merah", "Hijau", "Cyan", "Kuning", "Putih", "Ungu"), "ch_color", 0) {
            S.put("ch_custom", false)
            svc.applyCrosshair()
            render(true)
        })
        c2.addView(ctx.item("🎨", "Warna kustom", "",
            ctx.toggle(S.b("ch_custom", false)) {
                S.put("ch_custom", it)
                svc.applyCrosshair()
                render(true)
            }, null))
        if (S.b("ch_custom", false)) {
            val bar = View(ctx)
            val cols = IntArray(7)
            for (i in 0..6) {
                cols[i] = Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))
            }
            val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, cols)
            g.cornerRadius = ctx.dp(4).toFloat()
            bar.background = g
            c2.addView(bar, LinearLayout.LayoutParams(-1, ctx.dp(8)))
            c2.addView(ctx.slider("Warna", 0, 360, "ch_hue", 0, "°") { svc.applyCrosshair() })
        }
        body.addView(c2)

        val c3 = ctx.card()
        c3.addView(ctx.slider("Ukuran", 10, 120, "ch_size", 40, " px") { svc.applyCrosshair() })
        c3.addView(ctx.slider("Ketebalan", 1, 12, "ch_thick", 4, " px") { svc.applyCrosshair() })
        c3.addView(ctx.slider("Transparansi", 20, 100, "ch_alpha", 100, "%") { svc.applyCrosshair() })
        c3.addView(ctx.slider("Geser kiri / kanan", -100, 100, "ch_x", 0, " px") { svc.applyCrosshair() })
        c3.addView(ctx.slider("Geser atas / bawah", -100, 100, "ch_y", 0, " px") { svc.applyCrosshair() })
        body.addView(c3)

        add(ctx.btn("Reset crosshair", Th.OFF) {
            S.put("ch_style", 0)
            S.put("ch_color", 0)
            S.put("ch_custom", false)
            S.put("ch_hue", 0)
            S.put("ch_size", 40)
            S.put("ch_thick", 4)
            S.put("ch_alpha", 100)
            S.put("ch_x", 0)
            S.put("ch_y", 0)
            svc.applyCrosshair()
            render()
        })
    }

    private fun pgMonitor() {
        val c = ctx.card()
        c.addView(ctx.item("📈", "Tampilkan bar monitor", "",
            ctx.toggle(S.b("mon_on", true)) {
                S.put("mon_on", it)
                svc.applyMonitor()
            }, null))
        body.addView(c)

        val c2 = ctx.card()
        c2.addView(ctx.tv("Data yang ditampilkan", 13f, Th.ACC2, true))
        val names = listOf("CPU", "GPU", "RAM", "Baterai", "Suhu", "Ping", "Refresh rate (Hz)", "Jam")
        val keys = listOf("mon_cpu", "mon_gpu", "mon_ram", "mon_bat", "mon_temp", "mon_ping", "mon_hz", "mon_clock")
        for (i in names.indices) {
            val k = keys[i]
            c2.addView(ctx.item("•", names[i], "",
                ctx.toggle(S.b(k, true)) {
                    S.put(k, it)
                    svc.applyMonitor()
                }, null))
        }
        c2.addView(ctx.slider("Ukuran teks", 8, 18, "mon_size", 11, " sp") { svc.applyMonitor() })
        c2.addView(ctx.slider("Transparansi", 30, 100, "mon_alpha", 90, "%") { svc.applyMonitor() })
        body.addView(c2)
        body.addView(ctx.note("Geser bar dengan menahan tombol ✥ di kirinya. CPU dan GPU adalah perkiraan, dan GPU hanya tampil jika HP mengizinkan. Ping diukur ke internet, bukan ke server game."))
    }

    private fun pgExtra() {
        val c = ctx.card()
        c.addView(ctx.item("🕹", "Macro", "Trigger & rekaman ketukan", ctx.arrow()) { go("macro") })
        c.addView(ctx.divider())
        c.addView(ctx.item("☀", "Layar tetap menyala", "Cegah layar mati saat bermain",
            ctx.toggle(S.b("keep_on", true)) {
                S.put("keep_on", it)
                svc.applyOptions()
            }, null))
        c.addView(ctx.divider())
        c.addView(ctx.item("🔕", "Jangan Ganggu", "Blokir notifikasi (butuh izin)",
            ctx.toggle(S.b("dnd", false)) {
                S.put("dnd", it)
                svc.setDnd(it)
                render()
            }, null))
        body.addView(c)
    }

    private fun macroState(): Int {
        if (MacroService.granted()) return 3
        if (MacroService.binderAlive()) return 2
        return if (MacroService.installed(ctx)) 1 else 0
    }

    private fun pgMacro() {
        val st = macroState()
        val c = ctx.card()
        val a = ctx.tv("Trigger: " + Triggers.size() + "  |  Rekaman: " + MacroService.steps.size + " langkah", 13f, Th.TXT, true)
        a.setPadding(0, ctx.dp(6), 0, ctx.dp(2))
        c.addView(a)
        val label = when (st) {
            0 -> "Shizuku: belum terpasang"
            1 -> "Shizuku: terpasang, belum dijalankan"
            2 -> "Shizuku: berjalan, belum diizinkan"
            else -> "Shizuku: siap ✓"
        }
        val b = ctx.tv(label, 12f, if (st == 3) Th.OK else Th.RED)
        b.setPadding(0, 0, 0, ctx.dp(6))
        c.addView(b)
        body.addView(c)

        if (st == 0) {
            add(ctx.btn("Pasang Shizuku") {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                svc.closePanel()
            })
        } else if (st == 1) {
            add(ctx.btn("Buka Shizuku") {
                val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                    svc.closePanel()
                }
            })
        } else if (st == 2) {
            add(ctx.btn("Izinkan akses Shizuku") {
                MacroService.askPermission()
                svc.closePanel()
            })
        }
        if (st != 3) {
            body.addView(ctx.note("Jalankan Shizuku lewat Debugging nirkabel (butuh Wi-Fi). Perlu diulang setelah HP dihidupkan ulang."))
        }

        val ct = ctx.card()
        ct.addView(ctx.item("🎯", "Tombol trigger", "Tombol melayang untuk mengetuk target",
            ctx.toggle(S.b("mt_on", true)) { Triggers.setVisible(it) }, null))
        body.addView(ct)
        add(ctx.btn("✎ Buka Editor Trigger") {
            svc.closePanel()
            Triggers.openEditor(ctx, { TurboPanel.reopen(svc) }, null)
        })
        body.addView(ctx.note("Buka game dulu, lalu buka editor lewat bubble ⚡ agar target bisa dipasang tepat di atas tombol game."))

        body.addView(ctx.tv("Macro rekaman", 14f, Th.ACC2, true))
        val c2 = ctx.card()
        c2.addView(ctx.slider("Ulangi (0 = tanpa henti)", 0, 100, "mc_loops", 1, "x") {})
        c2.addView(ctx.slider("Kecepatan", 50, 300, "mc_speed", 100, "%") {})
        c2.addView(ctx.slider("Tunda sebelum mulai", 0, 10, "mc_start", 2, " dtk") {})
        body.addView(c2)

        add(ctx.rowOf(
            ctx.btn("⏺ Rekam") { svc.startRecord() },
            ctx.btn("▶ Putar") {
                if (macroState() == 3) {
                    svc.playMacro()
                } else {
                    Toast.makeText(ctx, "Siapkan Shizuku dulu (lihat status di halaman Macro)", Toast.LENGTH_LONG).show()
                }
            }
        ))
        add(ctx.rowOf(
            ctx.btn("■ Stop", Th.OFF) {
                svc.stopMacro()
                render(true)
            },
            ctx.btn("🗑 Hapus", Th.RED) {
                MacroService.steps.clear()
                MacroService.save()
                render(true)
            }
        ))
        body.addView(ctx.note("Tip auto-tap: rekam 1 ketukan, lalu atur Ulangi ke 0. Hentikan macro dengan mengetuk bubble ⚡. Setiap ketukan punya jeda sekitar 0,1 sampai 0,3 detik."))
    }

    private fun pgSpeed() {
        val c = ctx.card()
        c.addView(ctx.item("🧹", "Bersihkan memori", "Tutup aplikasi latar belakang",
            ctx.toggle(S.b("sp_mem", true)) { S.put("sp_mem", it) }, null))
        c.addView(ctx.divider())
        c.addView(ctx.item("🔕", "Jangan Ganggu", "Blokir notifikasi (butuh izin)",
            ctx.toggle(S.b("sp_dnd", false)) { S.put("sp_dnd", it) }, null))
        c.addView(ctx.divider())
        c.addView(ctx.item("📈", "Tampilkan monitor", "",
            ctx.toggle(S.b("sp_mon", true)) { S.put("sp_mon", it) }, null))
        body.addView(c)
        add(ctx.btn("🚀 Percepat Sekarang") {
            var n = 0
            if (S.b("sp_mem", true)) n = svc.cleanRam()
            if (S.b("sp_dnd", false)) {
                S.put("dnd", true)
                svc.setDnd(true)
            }
            if (S.b("sp_mon", true)) {
                S.put("mon_on", true)
                svc.applyMonitor()
            }
            Toast.makeText(ctx, "Game dipercepat (" + n + " app ditutup)", Toast.LENGTH_SHORT).show()
            svc.closePanel()
        })
        body.addView(ctx.note("Ini menutup aplikasi latar belakang dan mengurangi gangguan. Tidak menaikkan FPS secara langsung."))
    }

    private fun pgMemory() {
        val c = ctx.card()
        val info = ctx.tv(svc.ramInfo(), 15f, Th.TXT, true)
        info.setPadding(0, ctx.dp(8), 0, ctx.dp(8))
        c.addView(info)
        c.addView(ctx.slider("Bersihkan otomatis tiap (0 = mati)", 0, 30, "mem_auto", 0, " mnt") {})
        body.addView(c)
        add(ctx.btn("🧹 Bersihkan Sekarang") {
            val n = svc.cleanRam()
            Toast.makeText(ctx, "Selesai: " + n + " app ditutup", Toast.LENGTH_SHORT).show()
            render()
        })
        body.addView(ctx.note("Hanya aplikasi yang ada di latar belakang yang ditutup. Aplikasi sistem tidak disentuh."))
    }

    private fun pgTimer() {
        val c = ctx.card()
        val st = ctx.tv(timerText(), 22f, Th.ACC2, true)
        st.gravity = Gravity.CENTER
        st.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
        timerStatus = st
        c.addView(st, LinearLayout.LayoutParams(-1, -2))
        c.addView(ctx.slider("Durasi", 1, 120, "tm_min", 30, " mnt") {})
        c.addView(ctx.item("📳", "Getar saat selesai", "",
            ctx.toggle(S.b("tm_vib", true)) { S.put("tm_vib", it) }, null))
        body.addView(c)
        add(ctx.rowOf(
            ctx.btn("▶ Mulai") {
                svc.startTimer(S.i("tm_min", 30))
                render()
            },
            ctx.btn("■ Berhenti", Th.OFF) {
                svc.stopTimer()
                render()
            }
        ))
    }

    private fun timerText(): String {
        val t = svc.timerLeft()
        if (t <= 0) return "Timer tidak aktif"
        val m = t / 60
        val s = t % 60
        return "Sisa " + (if (m < 10) "0" else "") + m + ":" + (if (s < 10) "0" else "") + s
    }

    private fun pgSettings() {
        val c = ctx.card()
        c.addView(ctx.slider("Ukuran bubble ⚡", 36, 100, "bub_size", 44, " dp") { svc.applyOptions() })
        c.addView(ctx.slider("Transparansi bubble", 30, 100, "bub_alpha", 85, "%") { svc.applyOptions() })
        c.addView(ctx.slider("Transparansi panel", 60, 100, "panel_alpha", 100, "%") {
            root.alpha = S.i("panel_alpha", 100) / 100f
        })
        body.addView(c)
        add(ctx.btn("Reset semua pengaturan", Th.OFF) {
            val mt = S.s("mt_data", "")
            S.reset()
            S.put("mt_data", mt)
            svc.applyCrosshair()
            svc.applyMonitor()
            svc.applyOptions()
            Triggers.reload()
            root.alpha = 1f
            render()
        })
        add(ctx.btn("Keluar dari Turbo", Th.RED) {
            Triggers.destroy()
            svc.quit()
        })
    }
}
