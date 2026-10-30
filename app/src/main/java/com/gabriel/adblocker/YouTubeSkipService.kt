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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName != "com.google.android.youtube") return
        try {
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
