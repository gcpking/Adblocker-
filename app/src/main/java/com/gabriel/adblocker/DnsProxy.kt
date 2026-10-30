package com.gabriel.adblocker

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Answers DNS queries arriving through the tunnel: blocked names get NXDOMAIN, everything else is
 * forwarded to the network's own DNS servers (falling back to public resolvers).
 */
class DnsProxy(private val vpn: VpnService, private val write: (ByteArray) -> Unit) {
    private val pool = Executors.newFixedThreadPool(16)
    private val fallback = listOf("1.1.1.1", "8.8.8.8").map { InetAddress.getByName(it) }
    @Volatile private var upstream: List<InetAddress> = fallback
    @Volatile private var upstreamAt = 0L

    fun close() = pool.shutdownNow()

    fun handle(pkt: ByteArray, len: Int) {
        if (len < 20 || pkt[0].toInt() shr 4 != 4) return
        val ihl = (pkt[0].toInt() and 15) * 4
        if (len < ihl + 8) return
        when (pkt[9].toInt()) {
            17 -> {
                if (Packets.u16(pkt, ihl + 2) != 53) return
                val end = minOf(len, ihl + Packets.u16(pkt, ihl + 4))
                if (end <= ihl + 8) return
                val query = pkt.copyOfRange(ihl + 8, end)
                val full = pkt.copyOf(ihl + 8)
                val name = qname(query)
                if (name != null && Blocklist.isBlocked(name)) {
                    write(Packets.udpReply(full, ihl, nxdomain(query)))
                } else {
                    pool.execute { forward(full, ihl, query) }
                }
            }
            6 -> if (len >= ihl + 20 && pkt[ihl + 13].toInt() and 0x02 != 0) write(Packets.tcpReset(pkt, ihl))
        }
    }

    private fun forward(req: ByteArray, ihl: Int, query: ByteArray) {
        try {
            DatagramSocket().use { s ->
                vpn.protect(s)
                s.soTimeout = 2000
                for (server in servers()) {
                    try {
                        s.send(DatagramPacket(query, query.size, server, 53))
                        val buf = ByteArray(4096)
                        val p = DatagramPacket(buf, buf.size)
                        // Ignore stray datagrams: only accept a reply with our query id.
                        while (true) {
                            s.receive(p)
                            if (p.length >= 2 && buf[0] == query[0] && buf[1] == query[1]) break
                        }
                        write(Packets.udpReply(req, ihl, buf.copyOf(p.length)))
                        return
                    } catch (e: java.net.SocketTimeoutException) {
                        // try next server
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BlockerService", "Forward failed", e)
        }
    }

    /** The underlying network's DNS servers first (needed on DNS64/IPv6-only and filtered networks). */
    private fun servers(): List<InetAddress> {
        val now = System.currentTimeMillis()
        if (now - upstreamAt > 30_000) {
            upstreamAt = now
            try {
                val cm = vpn.getSystemService(ConnectivityManager::class.java)
                val system = cm.allNetworks
                    .filter { n ->
                        val c = cm.getNetworkCapabilities(n)
                        c != null && !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                            c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    }
                    .flatMap { cm.getLinkProperties(it)?.dnsServers ?: emptyList() }
                upstream = (system + fallback).distinct()
            } catch (e: Exception) {
                upstream = fallback
            }
        }
        return upstream
    }

    private fun qname(q: ByteArray): String? {
        if (q.size < 12 || Packets.u16(q, 4) < 1) return null
        val sb = StringBuilder()
        var i = 12
        while (i < q.size) {
            val l = q[i].toInt() and 0xff
            if (l == 0) return if (sb.isEmpty()) null else sb.toString()
            if (l and 0xc0 != 0 || i + 1 + l > q.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..l) sb.append((q[i + k].toInt() and 0xff).toChar().lowercaseChar())
            i += l + 1
        }
        return null
    }

    private fun nxdomain(q: ByteArray): ByteArray {
        // Echo header + question only (drop any EDNS record), set QR/RA and RCODE=3.
        var i = 12
        while (i < q.size && q[i].toInt() != 0) i += (q[i].toInt() and 0xff) + 1
        val end = minOf(q.size, i + 5)
        val r = q.copyOf(end)
        r[2] = ((q[2].toInt() and 0x79) or 0x80).toByte()
        r[3] = 0x83.toByte()
        Packets.put16(r, 6, 0); Packets.put16(r, 8, 0); Packets.put16(r, 10, 0)
        return r
    }
}
