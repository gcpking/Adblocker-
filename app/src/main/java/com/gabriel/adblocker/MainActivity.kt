package com.gabriel.adblocker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var button: Button
    private lateinit var status: TextView
    private val bg = GradientDrawable().apply { cornerRadius = 400f }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 20f; gravity = Gravity.CENTER; setPadding(0, 0, 0, 48) }
        val note = TextView(this).apply {
            text = "Blocks ad and tracker domains via a local DNS filter.\n" +
                "WhatsApp, Signal and Telegram are excluded so messaging keeps working."
            textSize = 13f; gravity = Gravity.CENTER; setPadding(48, 48, 48, 0)
        }
        button = Button(this).apply {
            textSize = 28f; setTextColor(Color.WHITE); background = bg
            layoutParams = LinearLayout.LayoutParams(480, 480)
            setOnClickListener { if (BlockerService.running) stopBlocking() else startBlocking() }
        }
        val skip = Button(this).apply {
            text = "Enable YouTube ad skipper"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val browse = Button(this).apply {
            text = "Open ad-free browser"
            setOnClickListener { startActivity(Intent(this@MainActivity, BrowserActivity::class.java)) }
        }
        val cut = Button(this).apply {
            fun label() { text = "Experimental: cut YouTube during ads: " + if (Prefs.cutEnabled(this@MainActivity)) "ON" else "OFF" }
            label()
            setOnClickListener { Prefs.setCutEnabled(this@MainActivity, !Prefs.cutEnabled(this@MainActivity)); label() }
        }
        val markerCut = Button(this).apply {
            fun label() { text = "Experimental: cut 5s before ad markers: " + if (Prefs.markerCutEnabled(this@MainActivity)) "ON" else "OFF" }
            label()
            setOnClickListener { Prefs.setMarkerCutEnabled(this@MainActivity, !Prefs.markerCutEnabled(this@MainActivity)); label() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(status); addView(button); addView(note); addView(skip); addView(browse); addView(cut); addView(markerCut)
        })
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        if (intent.getBooleanExtra("turn_on", false)) startBlocking()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("turn_on", false)) startBlocking()
    }

    private fun startBlocking() {
        val consent = VpnService.prepare(this)
        if (consent != null) startActivityForResult(consent, 1)
        else startService(Intent(this, BlockerService::class.java))
    }

    private fun stopBlocking() {
        startService(Intent(this, BlockerService::class.java).setAction(BlockerService.ACTION_STOP))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == RESULT_OK) startService(Intent(this, BlockerService::class.java))
    }

    override fun onResume() {
        super.onResume()
        BlockerService.listener = ::render
        render()
    }

    override fun onPause() {
        BlockerService.listener = null
        super.onPause()
    }

    private fun render() {
        val on = BlockerService.running
        button.text = if (on) "ON" else "OFF"
        bg.setColor(if (on) Color.parseColor("#2E7D32") else Color.GRAY)
        status.text = if (on) "Blocking ads" else "Not blocking"
    }
}
