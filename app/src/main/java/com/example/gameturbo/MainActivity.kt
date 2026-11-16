package com.example.gameturbo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        root.addView(TextView(this).apply {
            text = "Game Turbo\nPanel Turbo + Crosshair + Macro"
            textSize = 20f
            gravity = Gravity.CENTER
        })
        root.addView(Button(this).apply {
            text = "Aktifkan Game Turbo"
            setOnClickListener { start() }
        })
        root.addView(Button(this).apply {
            text = "Matikan"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        })
        setContentView(root)
        val perms = ArrayList<String>()
        perms.add("android.permission.RECORD_AUDIO")
        if (android.os.Build.VERSION.SDK_INT >= 33) perms.add("android.permission.POST_NOTIFICATIONS")
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampil di atas aplikasi lain'", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        moveTaskToBack(true)
    }
}
