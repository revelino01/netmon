package com.netmon.domain

/** A single parsed network packet event. */
data class PacketEvent(
    val uid: Int,
    val packageName: String,
    val timestamp: Long,
    val protocol: Int,        // 6=TCP, 17=UDP
    val srcAddr: String,
    val srcPort: Int,
    val dstAddr: String,
    val dstPort: Int,
    val payloadSize: Int,
    val direction: Direction,
    val isDns: Boolean = false,
    val dnsQuery: String? = null,
) {
    enum class Direction { INCOMING, OUTGOING }
}

/** Aggregated traffic stats for a single app. */
data class AppTrafficStats(
    val uid: Int,
    val packageName: String,
    val appName: String,
    val totalRxBytes: Long,
    val totalTxBytes: Long,
    val totalRxPackets: Long,
    val totalTxPackets: Long,
    val connections: Set<String>,  // dstAddr:dstPort
    val lastActivity: Long,
    val isDns: Boolean = false,
)

/** DNS query log entry. */
data class DnsQuery(
    val uid: Int,
    val packageName: String,
    val timestamp: Long,
    val query: String,
    val responseAddresses: List<String>,
)

/** Dashboard UI state. */
data class DashboardUiState(
    val isMonitoring: Boolean = false,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val activeAppCount: Int = 0,
    val apps: List<AppTrafficStats> = emptyList(),
    val timeline: List<BandwidthPoint> = emptyList(),
    val dnsQueries: List<DnsQuery> = emptyList(),
    val error: String? = null,
)

data class BandwidthPoint(
    val timestamp: Long,
    val rxBytes: Long,
    val txBytes: Long,
)