package com.adblock.browser

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Màn hình chính: nút BẬT/TẮT chặn quảng cáo cho toàn bộ điện thoại. */
class MainActivity : Activity() {

    private lateinit var toggle: Button
    private lateinit var status: TextView
    private lateinit var stats: TextView
    private lateinit var recent: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var wantStart = false

    private val refresher = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toggle = findViewById(R.id.toggle)
        status = findViewById(R.id.status)
        stats = findViewById(R.id.stats)
        recent = findViewById(R.id.recent)

        toggle.setOnClickListener {
            if (AdBlockVpnService.running) {
                AdBlockVpnService.stop(this)
            } else {
                requestStart()
            }
            handler.postDelayed({ render() }, 400)
        }
        findViewById<Button>(R.id.btn_update).setOnClickListener { updateList() }
        findViewById<Button>(R.id.btn_browser).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java))
        }

        io.execute { AdBlocker.ensureLoaded(applicationContext) }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun requestStart() {
        val consent = VpnService.prepare(this)
        if (consent != null) {
            wantStart = true
            startActivityForResult(consent, REQ_VPN)
        } else {
            AdBlockVpnService.start(this)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN && wantStart) {
            wantStart = false
            if (resultCode == RESULT_OK) {
                AdBlockVpnService.start(this)
            } else {
                Toast.makeText(this, R.string.vpn_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun render() {
        val on = AdBlockVpnService.running
        toggle.text = getString(if (on) R.string.btn_on else R.string.btn_off)
        toggle.setBackgroundResource(if (on) R.drawable.toggle_on else R.drawable.toggle_off)
        status.text = getString(if (on) R.string.vpn_on else R.string.vpn_off)
        stats.text = getString(R.string.stats, AdBlocker.blockedCount.get(), AdBlocker.size())
        recent.text = AdBlocker.recentBlocked.take(30).joinToString("\n").ifEmpty { "—" }
    }

    private fun updateList() {
        Toast.makeText(this, R.string.updating, Toast.LENGTH_SHORT).show()
        io.execute {
            val msg = try {
                getString(R.string.update_ok, AdBlocker.updateFromInternet(applicationContext))
            } catch (e: Exception) {
                getString(R.string.update_fail, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                render()
            }
        }
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_VPN = 1
    }
}
