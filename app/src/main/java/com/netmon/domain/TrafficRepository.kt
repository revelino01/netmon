package com.netmon.domain

import kotlinx.coroutines.flow.Flow

interface TrafficRepository {
    fun observeLiveTraffic(): Flow<List<AppTrafficStats>>
    fun observeTimeline(): Flow<List<BandwidthPoint>>
    fun observeDnsQueries(): Flow<List<DnsQuery>>
    suspend fun ingestEvent(event: PacketEvent)
    suspend fun flushBatch()
    suspend fun getHistory(uid: Int, since: Long): List<PacketEvent>
    suspend fun clearHistory()
}