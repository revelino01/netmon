package com.netmon.data

import android.content.pm.PackageManager
import com.netmon.core.FlowTracker
import com.netmon.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrafficRepositoryImpl @Inject constructor(
    private val trafficLogDao: TrafficLogDao,
    private val dnsQueryLogDao: DnsQueryLogDao,
    private val packageManager: PackageManager,
) : TrafficRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val batchBuffer = mutableListOf<PacketEvent>()
    private var lastFlush = System.currentTimeMillis()

    private val uidNameCache = mutableMapOf<Int, Pair<String, String>>()

    /** Map UID → (packageName, appName) */
    fun resolveUid(uid: Int): Pair<String, String> {
        return uidNameCache.getOrPut(uid) {
            try {
                val packages = packageManager.getPackagesForUid(uid) ?: return uid.toString() to "Unknown"
                val pkgName = packages.firstOrNull() ?: uid.toString()
                val appInfo = packageManager.getApplicationInfo(pkgName, 0)
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                pkgName to appName
            } catch (e: Exception) {
                uid.toString() to "App $uid"
            }
        }
    }

    override fun observeLiveTraffic(): Flow<List<AppTrafficStats>> = FlowTracker.trafficFlow

    override fun observeTimeline(): Flow<List<BandwidthPoint>> = flowOf(emptyList())

    override fun observeDnsQueries(): Flow<List<DnsQuery>> =
        dnsQueryLogDao.getRecent(100).map { logs ->
            logs.map { DnsQuery(it.uid, it.packageName, it.timestamp, it.query, it.responseAddresses.split(",")) }
        }

    override suspend fun ingestEvent(event: PacketEvent) {
        FlowTracker.ingest(event)
        batchBuffer.add(event)
        if (System.currentTimeMillis() - lastFlush > 500 && batchBuffer.isNotEmpty()) {
            flushBatch()
        }
    }

    override suspend fun flushBatch() {
        if (batchBuffer.isEmpty()) return
        val batch = batchBuffer.toList()
        batchBuffer.clear()
        lastFlush = System.currentTimeMillis()

        trafficLogDao.insertAll(batch.map { event ->
            TrafficLog(
                uid = event.uid,
                packageName = event.packageName,
                timestamp = event.timestamp,
                protocol = event.protocol,
                srcAddr = event.srcAddr,
                srcPort = event.srcPort,
                dstAddr = event.dstAddr,
                dstPort = event.dstPort,
                payloadSize = event.payloadSize,
                direction = if (event.direction == PacketEvent.Direction.OUTGOING) 1 else 0,
                isDns = event.isDns,
                dnsQuery = event.dnsQuery,
            )
        })

        val dnsEntries = batch.filter { it.isDns && it.dnsQuery != null }.map { event ->
            DnsQueryLog(
                uid = event.uid,
                packageName = event.packageName,
                timestamp = event.timestamp,
                query = event.dnsQuery!!,
                responseAddresses = "",
            )
        }
        if (dnsEntries.isNotEmpty()) {
            dnsQueryLogDao.insertAll(dnsEntries)
        }
    }

    override suspend fun getHistory(uid: Int, since: Long): List<PacketEvent> {
        return emptyList()
    }

    override suspend fun clearHistory() {
        trafficLogDao.deleteAll()
    }
}