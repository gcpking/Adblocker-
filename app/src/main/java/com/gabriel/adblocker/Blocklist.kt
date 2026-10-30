package com.gabriel.adblocker

import android.content.Context
import java.io.File
import java.net.URL

/** Domain blocklist with suffix matching and a never-block allowlist for messaging infrastructure. */
object Blocklist {
    private const val SOURCE = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** Never filtered, even if a (parent) entry appears in the blocklist. */
    private val ALLOW = setOf(
        "whatsapp.net", "whatsapp.com", "wa.me",
        "signal.org", "signal.art", "signal.me", "whispersystems.org",
        "telegram.org", "t.me",
        "mtalk.google.com", "android.clients.google.com",
    )

    @Volatile private var blocked: Set<String> = emptySet()

    fun load(context: Context) {
        val local = File(context.filesDir, "blocklist.txt")
        val lines = if (local.exists()) local.readLines()
        else context.assets.open("blocklist.txt").bufferedReader().readLines()
        blocked = parse(lines)
    }

    fun updateIfStale(context: Context) {
        val local = File(context.filesDir, "blocklist.txt")
        if (local.exists() && System.currentTimeMillis() - local.lastModified() < MAX_AGE_MS) return
        val tmp = File(context.filesDir, "blocklist.txt.tmp")
        val fresh = URL(SOURCE).openStream().bufferedReader().use { it.readLines() }
        val domains = parse(fresh)
        if (domains.size < 1000) return // bad download, keep what we have
        tmp.writeText(domains.joinToString("\n"))
        if (tmp.renameTo(local)) blocked = domains
    }

    private fun parse(lines: List<String>): Set<String> {
        val out = HashSet<String>(lines.size * 2)
        for (raw in lines) {
            val line = raw.substringBefore('#').trim().lowercase()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val host = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0]
            if (host == "localhost" || host == "0.0.0.0" || '.' !in host) continue
            out.add(host)
        }
        return out
    }

    fun isBlocked(name: String): Boolean {
        val set = blocked
        var n = name
        while (true) {
            if (n in ALLOW) return false
            val dot = n.indexOf('.')
            if (dot < 0) break
            n = n.substring(dot + 1)
        }
        n = name
        while (true) {
            if (n in set) return true
            val dot = n.indexOf('.')
            if (dot < 0) return false
            n = n.substring(dot + 1)
        }
    }
}
