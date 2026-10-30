package com.gabriel.adblocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Clicks YouTube's "Skip ad" button as soon as it shows up. */
class YouTubeSkipService : AccessibilityService() {
    private val skipIds = listOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button_container",
    )
    private val skipTexts = listOf("skip ads", "skip ad", "skip")

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
}
