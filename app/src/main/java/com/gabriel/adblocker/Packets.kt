package com.gabriel.adblocker

/** Minimal IPv4 helpers for the DNS-only tunnel. */
object Packets {
    fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xff) shl 8) or (b[i + 1].toInt() and 0xff)
    fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v shr 8).toByte(); b[i + 1] = v.toByte() }

    private fun sum(b: ByteArray, off: Int, len: Int, init: Long = 0): Long {
        var s = init
        var i = off
        while (i + 1 < off + len) { s += u16(b, i); i += 2 }
        if (len % 2 == 1) s += (b[off + len - 1].toInt() and 0xff) shl 8
        return s
    }

    private fun fold(s0: Long): Int {
        var s = s0
        while (s shr 16 != 0L) s = (s and 0xffff) + (s shr 16)
        return (s.inv() and 0xffff).toInt()
    }

    /** UDP reply to [req] (an IPv4/UDP packet) carrying [payload]; addresses and ports swapped. */
    fun udpReply(req: ByteArray, ihl: Int, payload: ByteArray): ByteArray {
        val total = ihl + 8 + payload.size
        val out = ByteArray(total)
        System.arraycopy(req, 0, out, 0, ihl)
        put16(out, 2, total)
        out[8] = 64
        System.arraycopy(req, 12, out, 16, 4) // dst = old src
        System.arraycopy(req, 16, out, 12, 4) // src = old dst
        put16(out, 10, 0)
        put16(out, 10, fold(sum(out, 0, ihl)))
        System.arraycopy(req, ihl + 2, out, ihl, 2)     // src port = old dst port
        System.arraycopy(req, ihl, out, ihl + 2, 2)     // dst port = old src port
        put16(out, ihl + 4, 8 + payload.size)
        put16(out, ihl + 6, 0) // UDP checksum optional over IPv4
        System.arraycopy(payload, 0, out, ihl + 8, payload.size)
        return out
    }

    /** TCP RST/ACK answering a SYN, so a resolver falling back to TCP fails fast instead of hanging. */
    fun tcpReset(req: ByteArray, ihl: Int): ByteArray {
        val total = ihl + 20
        val out = ByteArray(total)
        System.arraycopy(req, 0, out, 0, 12)
        out[0] = 0x45
        put16(out, 2, total)
        out[8] = 64
        out[9] = 6
        System.arraycopy(req, 16, out, 12, 4)
        System.arraycopy(req, 12, out, 16, 4)
        put16(out, 10, 0)
        put16(out, 10, fold(sum(out, 0, 20)))
        System.arraycopy(req, ihl + 2, out, 20, 2)
        System.arraycopy(req, ihl, out, 22, 2)
        val seq = ((req[ihl + 4].toLong() and 0xff) shl 24) or ((req[ihl + 5].toLong() and 0xff) shl 16) or
            ((req[ihl + 6].toLong() and 0xff) shl 8) or (req[ihl + 7].toLong() and 0xff)
        val ack = (seq + 1) and 0xffffffffL
        for (k in 0..3) out[28 + k] = (ack shr (24 - 8 * k)).toByte()
        out[32] = 0x50
        out[33] = 0x14 // RST + ACK
        var s = sum(out, 12, 8) + 6 + 20
        s = sum(out, 20, 20, s)
        put16(out, 36, fold(s))
        return out
    }
}
