package com.adblock.browser

/** Phân tích / dựng gói IPv4 + UDP + DNS tối giản cho bộ lọc DNS. */
object Packets {

    class DnsQuery(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayload: ByteArray,
        val domain: String?,
    )

    private fun u16(b: ByteArray, off: Int) = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun parseDnsQuery(p: ByteArray): DnsQuery? {
        if (p.size < 28) return null
        val version = (p[0].toInt() and 0xF0) shr 4
        if (version != 4) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return null // chỉ UDP
        val total = minOf(u16(p, 2), p.size)
        if (total < ihl + 8) return null
        val srcIp = p.copyOfRange(12, 16)
        val dstIp = p.copyOfRange(16, 20)
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return null
        val payload = p.copyOfRange(ihl + 8, total)
        if (payload.size < 12) return null
        return DnsQuery(srcIp, dstIp, srcPort, dstPort, payload, parseQuestionName(payload))
    }

    /** Trả về tên miền trong câu hỏi đầu tiên, vd "ads.example.com". */
    fun parseQuestionName(dns: ByteArray): String? {
        if (u16(dns, 4) < 1) return null
        val sb = StringBuilder()
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) return sb.toString()
            if (len and 0xC0 != 0) return null // không có nén trong câu hỏi
            if (i + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (k in 1..len) sb.append((dns[i + k].toInt() and 0xFF).toChar())
            i += len + 1
        }
        return null
    }

    /** Vị trí kết thúc phần câu hỏi đầu tiên (sau QTYPE/QCLASS). */
    private fun questionEnd(dns: ByteArray): Int? {
        var i = 12
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) {
                val end = i + 1 + 4
                return if (end <= dns.size) end else null
            }
            i += len + 1
        }
        return null
    }

    /** Dựng phản hồi NXDOMAIN (tên miền không tồn tại) cho truy vấn bị chặn. */
    fun blockedResponse(query: ByteArray): ByteArray? {
        val end = questionEnd(query) ?: return null
        val r = query.copyOf(end)
        r[2] = (0x80 or (query[2].toInt() and 0x79)).toByte() // QR=1, giữ OPCODE + RD
        r[3] = (0x80 or 3).toByte()                           // RA=1, RCODE=3 (NXDOMAIN)
        r[4] = 0; r[5] = 1                                     // QDCOUNT=1
        for (k in 6 until 12) r[k] = 0                         // AN/NS/AR = 0
        return r
    }

    /** Dựng gói IPv4/UDP trả lời: đảo nguồn/đích của truy vấn. */
    fun buildUdpReply(q: DnsQuery, payload: ByteArray): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40 // Don't fragment
        p[8] = 64   // TTL
        p[9] = 17   // UDP
        System.arraycopy(q.dstIp, 0, p, 12, 4)
        System.arraycopy(q.srcIp, 0, p, 16, 4)
        val sum = ipChecksum(p, 0, 20)
        p[10] = (sum shr 8).toByte(); p[11] = sum.toByte()

        p[20] = (q.dstPort shr 8).toByte(); p[21] = q.dstPort.toByte()
        p[22] = (q.srcPort shr 8).toByte(); p[23] = q.srcPort.toByte()
        val udpLen = 8 + payload.size
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        // UDP checksum = 0 (hợp lệ với IPv4)
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun ipChecksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            sum += u16(b, i)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
