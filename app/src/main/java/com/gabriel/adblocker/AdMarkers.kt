package com.gabriel.adblocker

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Helpers for reading YouTube's seek bar: video times from its labels, ad breaks from its yellow markers. */
object AdMarkers {
    private val timeRe = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})")
    private const val CUR_ID = "com.google.android.youtube:id/time_bar_current_time"
    private const val TOTAL_ID = "com.google.android.youtube:id/time_bar_total_time"
    private const val BAR_ID = "com.google.android.youtube:id/time_bar"

    fun parseTimes(text: String): List<Int> = timeRe.findAll(text).map {
        (it.groupValues[1].toIntOrNull() ?: 0) * 3600 + it.groupValues[2].toInt() * 60 + it.groupValues[3].toInt()
    }.toList()

    private fun labelOf(n: AccessibilityNodeInfo) = (n.text ?: n.contentDescription)?.toString().orEmpty()

    /** (currentSeconds, totalSeconds) if the player's time labels can be read. */
    fun readPosition(root: AccessibilityNodeInfo): Pair<Int, Int>? {
        val cur = root.findAccessibilityNodeInfosByViewId(CUR_ID).firstOrNull()?.let { parseTimes(labelOf(it)).firstOrNull() }
        val total = root.findAccessibilityNodeInfosByViewId(TOTAL_ID).firstOrNull()?.let { parseTimes(labelOf(it)).firstOrNull() }
        if (cur != null && total != null && total > 0) return cur to total
        // Fallback: first two clock-like strings anywhere on screen ("0:12 / 3:45").
        val found = ArrayList<Int>()
        fun walk(n: AccessibilityNodeInfo, budget: IntArray) {
            if (budget[0]-- <= 0 || found.size >= 2) return
            found += parseTimes(labelOf(n).take(30))
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, budget) }
        }
        walk(root, intArrayOf(400))
        return if (found.size >= 2 && found[1] > 0) found[0] to found[1] else null
    }

    /** Screen bounds of the seek bar, if it is on screen right now. */
    fun barBounds(root: AccessibilityNodeInfo): Rect? {
        val node = root.findAccessibilityNodeInfosByViewId(BAR_ID).firstOrNull { it.isVisibleToUser } ?: return null
        val r = Rect()
        node.getBoundsInScreen(r)
        return if (r.width() > 200 && r.height() > 0) r else null
    }

    private fun isYellow(c: Int): Boolean {
        val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
        return r > 215 && g in 160..235 && b < 90
    }

    /** Positions (0..1 along the bar) of yellow ad markers. */
    fun findMarkers(shot: Bitmap, bar: Rect): List<Double> {
        val left = bar.left.coerceAtLeast(0)
        val right = bar.right.coerceAtMost(shot.width - 1)
        if (right - left < 100) return emptyList()
        val rows = (bar.top..bar.bottom).filter { it in 0 until shot.height }
        val hit = BooleanArray(right - left + 1)
        for (y in rows) for (x in left..right) if (isYellow(shot.getPixel(x, y))) hit[x - left] = true
        val out = ArrayList<Double>()
        var i = 0
        while (i < hit.size) {
            if (!hit[i]) { i++; continue }
            var j = i
            while (j + 1 < hit.size && hit[j + 1]) j++
            if (j - i < 60) out += ((i + j) / 2.0) / (hit.size - 1) // wider runs are not markers
            i = j + 1
        }
        return out
    }
}
