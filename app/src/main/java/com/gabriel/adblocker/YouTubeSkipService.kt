package com.gabriel.adblocker

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/** Clicks YouTube's "Skip ad" button as soon as it shows up. */
class YouTubeSkipService : AccessibilityService() {
    private val skipIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button_container",
    )
    private val skipTexts = listOf("skip ads", "skip ad", "skip")

    // --- cutting a few seconds before YouTube's yellow ad markers ---
    private val handler = Handler(Looper.getMainLooper())
    private var adTimes: List<Int> = emptyList()   // seconds into the video
    private var videoLength = 0
    private var syncPos = 0
    private var syncAt = 0L
    private var lastScan = 0L
    private val fired = HashSet<Int>()
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            if (adTimes.isEmpty() || !Prefs.markerCutEnabled(this@YouTubeSkipService)) return
            val pos = syncPos + (System.currentTimeMillis() - syncAt) / 1000.0
            for (t in adTimes) {
                if (t !in fired && pos >= t - LEAD_S && pos < t + 1) {
                    fired += t
                    BlockerService.instance?.cutYouTube(CUT_MARKER_MS, force = true)
                }
            }
            ticking = true
            handler.postDelayed(this, 400)
        }
    }

    private fun trackMarkers(root: AccessibilityNodeInfo) {
        if (!Prefs.markerCutEnabled(this)) return
        val now = System.currentTimeMillis()
        AdMarkers.readPosition(root)?.let { (cur, total) ->
            if (total != videoLength) { videoLength = total; adTimes = emptyList(); fired.clear() }
            syncPos = cur; syncAt = now
        }
        if (videoLength == 0 || Build.VERSION.SDK_INT < 30 || now - lastScan < 2000) return
        val bar = AdMarkers.barBounds(root) ?: return
        lastScan = now
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val hw = result.hardwareBuffer
                try {
                    val wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace) ?: return
                    val shot = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    val found = AdMarkers.findMarkers(shot, bar).map { (it * videoLength).toInt() }
                    if (found.isNotEmpty() && found != adTimes) {
                        adTimes = found
                        Toast.makeText(this@YouTubeSkipService,
                            "Ad markers at " + found.joinToString { "%d:%02d".format(it / 60, it % 60) }, Toast.LENGTH_SHORT).show()
                    }
                } finally { hw.close() }
                if (adTimes.isNotEmpty() && !ticking) { ticking = true; handler.post(tick) }
            }
            override fun onFailure(errorCode: Int) {}
        })
    }

    private var adActive = false
    private var lastAdSeen = 0L
    private val adLabel = Regex("(?i)^(ad\\s*(·|•|\\d|of)|visit advertiser|sponsored|why this ad).*")

    /** Looks through the on-screen texts for YouTube's ad markers. */
    private fun adOnScreen(node: AccessibilityNodeInfo, budget: IntArray): Boolean {
        if (budget[0]-- <= 0) return false
        val label = (node.text ?: node.contentDescription)?.toString()?.trim()
        if (label != null && label.length < 80 && adLabel.matches(label)) return true
        if (node.viewIdResourceName == "com.google.android.youtube:id/ad_progress_text") return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (adOnScreen(c, budget)) return true
        }
        return false
    }

    private fun trackAd(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (adOnScreen(root, intArrayOf(500))) {
            lastAdSeen = now
            if (!adActive) {
                adActive = true
                if (Prefs.cutEnabled(this)) BlockerService.instance?.cutYouTube()
            }
        } else if (adActive && now - lastAdSeen > 3000) {
            adActive = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName != "com.google.android.youtube") return
        try {
            trackAd(root)
            trackMarkers(root)
            for (id in skipIds) {
                for (n in root.findAccessibilityNodeInfosByViewId(id)) if (click(n)) return
            }
            for (t in skipTexts) {
                for (n in root.findAccessibilityNodeInfosByText(t)) {
                    val label = (n.text ?: n.contentDescription)?.toString()?.trim()?.lowercase()
                    if (label != null && label in skipTexts && click(n)) return
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    override fun onInterrupt() {}

    private companion object {
        const val LEAD_S = 5      // start the cut this many seconds before the ad break
        const val CUT_MARKER_MS = 7000L
    }
}
