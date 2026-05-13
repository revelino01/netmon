package com.netmon.core

import com.netmon.domain.AppTrafficStats
import com.netmon.domain.PacketEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory aggregation of per-UID traffic stats.
 * Emits snapshots every second to avoid ViewModel churn.
 */
object FlowTracker {

    private val stats = ConcurrentHashMap<Int, AppStatsInternal>()

    private val _trafficFlow = MutableSharedFlow<List<AppTrafficStats>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val trafficFlow: Flow<List<AppTrafficStats>> = _trafficFlow

    fun ingest(event: PacketEvent) {
        stats.compute(event.uid) { _, existing ->
            val current = existing ?: AppStatsInternal(uid = event.uid)
            when (event.direction) {
                PacketEvent.Direction.OUTGOING -> current.copy(
                    txBytes = current.txBytes + event.payloadSize,
                    txPackets = current.txPackets + 1,
                    connections = current.connections + "${event.dstAddr}:${event.dstPort}",
                    lastActivity = event.timestamp
                )
                PacketEvent.Direction.INCOMING -> current.copy(
                    rxBytes = current.rxBytes + event.payloadSize,
                    rxPackets = current.rxPackets + 1,
                    connections = current.connections + "${event.srcAddr}:${event.srcPort}",
                    lastActivity = event.timestamp
                )
            }
        }
    }

    /** Called periodically (1s) to emit a snapshot to UI. */
    fun emitSnapshot(packageNames: Map<Int, Pair<String, String>>) {
        val snapshot = stats.map { (uid, internal) ->
            val names = packageNames[uid]
            AppTrafficStats(
                uid = uid,
                packageName = names?.first ?: uid.toString(),
                appName = names?.second ?: "App $uid",
                totalRxBytes = internal.rxBytes,
                totalTxBytes = internal.txBytes,
                totalRxPackets = internal.rxPackets,
                totalTxPackets = internal.txPackets,
                connections = internal.connections,
                lastActivity = internal.lastActivity,
            )
        }.sortedByDescending { it.totalRxBytes + it.totalTxBytes }
        _trafficFlow.tryEmit(snapshot)
    }

    fun reset() {
        stats.clear()
    }
}

private data class AppStatsInternal(
    val uid: Int,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val rxPackets: Long = 0L,
    val txPackets: Long = 0L,
    val connections: Set<String> = emptySet(),
    val lastActivity: Long = 0L,
)