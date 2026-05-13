package com.netmon.core

import java.nio.ByteBuffer

/**
 * Parses raw IP packets from VpnService TUN interface.
 * Only extracts headers — no payload inspection beyond DNS queries on port 53.
 */
object PacketParser {

    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
    private const val DNS_PORT = 53

    fun parse(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 20) return null // minimum IPv4 header

        val version = (buffer.get(0).toInt() shr 4) and 0x0F
        return when (version) {
            4 -> parseIPv4(buffer, length)
            6 -> parseIPv6(buffer, length)
            else -> null
        }
    }

    private fun parseIPv4(buffer: ByteBuffer, length: Int): ParsedPacket? {
        val ihl = (buffer.get(0).toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl) return null

        val totalLength = buffer.getShort(2).toInt() and 0xFFFF
        val protocol = buffer.get(9).toInt() and 0xFF
        val srcAddr = intToIp(buffer.getInt(12))
        val dstAddr = intToIp(buffer.getInt(16))

        // Skip IP options if IHL > 20
        buffer.position(ihl)

        return when (protocol) {
            PROTOCOL_TCP -> {
                val srcPort = buffer.getShort(ihl).toInt() and 0xFFFF
                val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF
                ParsedPacket(
                    protocol = PROTOCOL_TCP,
                    srcAddr = srcAddr,
                    srcPort = srcPort,
                    dstAddr = dstAddr,
                    dstPort = dstPort,
                    payloadSize = totalLength - ihl - 20, // approx
                    isDns = dstPort == DNS_PORT || srcPort == DNS_PORT,
                    dnsQuery = if (dstPort == DNS_PORT || srcPort == DNS_PORT) {
                        extractDnsQuery(buffer, ihl + 20, length)
                    } else null
                )
            }
            PROTOCOL_UDP -> {
                val srcPort = buffer.getShort(ihl).toInt() and 0xFFFF
                val dstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF
                val udpLen = buffer.getShort(ihl + 4).toInt() and 0xFFFF
                ParsedPacket(
                    protocol = PROTOCOL_UDP,
                    srcAddr = srcAddr,
                    srcPort = srcPort,
                    dstAddr = dstAddr,
                    dstPort = dstPort,
                    payloadSize = udpLen - 8,
                    isDns = dstPort == DNS_PORT || srcPort == DNS_PORT,
                    dnsQuery = if (dstPort == DNS_PORT || srcPort == DNS_PORT) {
                        extractDnsQuery(buffer, ihl + 8, length)
                    } else null
                )
            }
            else -> ParsedPacket(
                protocol = protocol,
                srcAddr = srcAddr,
                srcPort = 0,
                dstAddr = dstAddr,
                dstPort = 0,
                payloadSize = totalLength - ihl,
                isDns = false,
                dnsQuery = null
            )
        }
    }

    private fun parseIPv6(buffer: ByteBuffer, length: Int): ParsedPacket? {
        if (length < 40) return null

        val nextHeader = buffer.get(6).toInt() and 0xFF
        val payloadLength = buffer.getShort(4).toInt() and 0xFFFF

        // IPv6 source/dest are 16 bytes each
        val srcBytes = ByteArray(16)
        buffer.position(8)
        buffer.get(srcBytes)
        val srcAddr = bytesToIPv6(srcBytes)

        val dstBytes = ByteArray(16)
        buffer.position(24)
        buffer.get(dstBytes)
        val dstAddr = bytesToIPv6(dstBytes)

        // Simplified: only parse TCP/UDP next headers at offset 40
        return when (nextHeader) {
            PROTOCOL_TCP -> {
                val srcPort = buffer.getShort(40).toInt() and 0xFFFF
                val dstPort = buffer.getShort(42).toInt() and 0xFFFF
                ParsedPacket(
                    protocol = PROTOCOL_TCP, srcAddr = srcAddr, srcPort = srcPort,
                    dstAddr = dstAddr, dstPort = dstPort,
                    payloadSize = payloadLength - 20,
                    isDns = dstPort == DNS_PORT || srcPort == DNS_PORT,
                    dnsQuery = null
                )
            }
            PROTOCOL_UDP -> {
                val srcPort = buffer.getShort(40).toInt() and 0xFFFF
                val dstPort = buffer.getShort(42).toInt() and 0xFFFF
                ParsedPacket(
                    protocol = PROTOCOL_UDP, srcAddr = srcAddr, srcPort = srcPort,
                    dstAddr = dstAddr, dstPort = dstPort,
                    payloadSize = payloadLength - 8,
                    isDns = dstPort == DNS_PORT || srcPort == DNS_PORT,
                    dnsQuery = null
                )
            }
            else -> ParsedPacket(
                protocol = nextHeader, srcAddr = srcAddr, srcPort = 0,
                dstAddr = dstAddr, dstPort = 0,
                payloadSize = payloadLength, isDns = false, dnsQuery = null
            )
        }
    }

    /** Extract DNS query name from a DNS message payload. */
    private fun extractDnsQuery(buffer: ByteBuffer, offset: Int, length: Int): String? {
        try {
            // DNS header: 12 bytes, then question section
            if (offset + 12 >= length) return null
            val qdCount = buffer.getShort(offset + 4).toInt() and 0xFFFF
            if (qdCount == 0) return null

            var pos = offset + 12
            val name = StringBuilder()
            while (pos < length) {
                val labelLen = buffer.get(pos).toInt() and 0xFF
                if (labelLen == 0) break
                pos++
                if (pos + labelLen > length) break
                val label = ByteArray(labelLen)
                buffer.position(pos)
                buffer.get(label)
                if (name.isNotEmpty()) name.append('.')
                name.append(String(label))
                pos += labelLen
            }
            return if (name.isNotEmpty()) name.toString() else null
        } catch (e: Exception) {
            return null
        }
    }

    private fun intToIp(i: Int): String {
        return "${(i shr 24) and 0xFF}.${(i shr 16) and 0xFF}.${(i shr 8) and 0xFF}.${i and 0xFF}"
    }

    private fun bytesToIPv6(bytes: ByteArray): String {
        return bytes.chunked(2).joinToString(":") {
            "${((it[0].toInt() and 0xFF) shl 8 or (it[1].toInt() and 0xFF)).toString(16)}"
        }
    }
}

data class ParsedPacket(
    val protocol: Int,
    val srcAddr: String,
    val srcPort: Int,
    val dstAddr: String,
    val dstPort: Int,
    val payloadSize: Int,
    val isDns: Boolean,
    val dnsQuery: String?,
)