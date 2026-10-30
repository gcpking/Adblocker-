package com.gabriel.adblocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream

class BlockerService : VpnService() {
    companion object {
        const val ACTION_STOP = "com.gabriel.adblocker.STOP"
        private const val TAG = "BlockerService"
        private const val DNS_ADDR = "10.111.222.2"

        /**
         * Apps that bypass the filter entirely and use the normal network + DNS. Messaging apps
         * are sensitive to DNS tampering (custom resolvers, DNS-over-HTTPS, fallback IPs), so they
         * are left alone; YouTube and everything else still go through the filter.
         */
        val BYPASS_APPS = listOf(
            "com.whatsapp", "com.whatsapp.w4b",
            "org.thoughtcrime.securesms", // Signal
            "org.telegram.messenger",
        )

        const val YOUTUBE = "com.google.android.youtube"
        private const val CUT_MS = 2500L
        private const val CUT_COOLDOWN_MS = 8000L

        @Volatile var instance: BlockerService? = null
        @Volatile var running = false
        @Volatile var listener: (() -> Unit)? = null
    }

    private var tun: ParcelFileDescriptor? = null
    private var proxy: DnsProxy? = null
    private var out: FileOutputStream? = null
    private var reader: Thread? = null
    private val main = Handler(Looper.getMainLooper())
    private var cutting = false
    private var lastCut = 0L

    private fun notifyChanged() {
        Handler(Looper.getMainLooper()).post { listener?.invoke() }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopBlocking()
            return START_NOT_STICKY
        }
        if (running) return START_STICKY
        try {
            Blocklist.load(this)
            if (!establishNormal()) { stopSelf(); return START_NOT_STICKY }
            Thread({ try { Blocklist.updateIfStale(this) } catch (e: Exception) { Log.w(TAG, "update failed", e) } }, "blocklist-update").start()
            running = true
            Prefs.setWanted(this, true)
            showNotification()
            notifyChanged()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start", e)
            stopBlocking()
        }
        return START_STICKY
    }

    private fun baseBuilder() = Builder()
        .setSession(getString(R.string.app_name))
        .addAddress("10.111.222.1", 24)
        .setMetered(false)
        .setConfigureIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))

    /** Normal mode: only DNS enters the tunnel and is filtered. */
    private fun establishNormal(): Boolean {
        val b = baseBuilder().addDnsServer(DNS_ADDR).addRoute(DNS_ADDR, 32)
        for (pkg in listOf(packageName) + BYPASS_APPS) {
            try { b.addDisallowedApplication(pkg) } catch (e: Exception) { /* not installed */ }
        }
        val pfd = b.establish() ?: return false
        startTunnel(pfd, filterDns = true)
        return true
    }

    /** Cut mode: all of YouTube's traffic is routed into the tunnel and dropped. */
    private fun establishCut(): Boolean {
        val b = baseBuilder().addRoute("0.0.0.0", 0)
        try { b.addRoute("::", 0) } catch (e: Exception) { /* IPv4 only is fine */ }
        try { b.addAllowedApplication(YOUTUBE) } catch (e: Exception) { return false }
        val pfd = b.establish() ?: return false
        startTunnel(pfd, filterDns = false)
        return true
    }

    /** Called by the ad detector when an ad appears on screen. */
    fun cutYouTube() = main.post {
        val now = System.currentTimeMillis()
        if (!running || cutting || now - lastCut < CUT_COOLDOWN_MS) return@post
        if (!establishCut()) return@post
        cutting = true
        lastCut = now
        main.postDelayed({
            cutting = false
            if (running) establishNormal()
        }, CUT_MS)
    }

    private fun startTunnel(pfd: ParcelFileDescriptor, filterDns: Boolean) {
        val o = FileOutputStream(pfd.fileDescriptor)
        val input = FileInputStream(pfd.fileDescriptor)
        val dns = proxy ?: DnsProxy(this) { pkt ->
            val cur = out
            if (cur != null) synchronized(cur) { try { cur.write(pkt) } catch (_: Exception) {} }
        }.also { proxy = it }
        val oldTun = tun
        val oldReader = reader
        tun = pfd
        out = o
        reader = Thread({
            val buf = ByteArray(32767)
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0 && filterDns) dns.handle(buf, n) // cut mode: packets are dropped
                }
            } catch (e: Exception) {
                if (tun === pfd) Log.w(TAG, "Tunnel error", e)
            }
        }, "dns-tunnel").also { it.start() }
        oldReader?.interrupt()
        try { oldTun?.close() } catch (_: Exception) {}
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("status", "Ad blocker status", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getBroadcast(this, 1, Intent(this, StopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val icon = Icon.createWithResource(this, R.drawable.ic_shield)
        nm.notify(1, Notification.Builder(this, "status")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Blocking ads")
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(icon, "Turn off", stop).build())
            .build())
    }

    private fun teardown() {
        running = false
        main.removeCallbacksAndMessages(null)
        cutting = false
        reader?.interrupt(); reader = null
        proxy?.close(); proxy = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null; out = null
        getSystemService(NotificationManager::class.java).cancel(1)
        notifyChanged()
    }

    private fun stopBlocking() {
        Prefs.setWanted(this, false)
        teardown()
        stopSelf()
    }

    override fun onRevoke() = stopBlocking()

    override fun onDestroy() {
        instance = null
        teardown()
        super.onDestroy()
    }
}
