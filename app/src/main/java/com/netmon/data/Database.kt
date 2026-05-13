package com.netmon.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "traffic_logs")
data class TrafficLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: Int,
    val packageName: String,
    val timestamp: Long,
    val protocol: Int,
    val srcAddr: String,
    val srcPort: Int,
    val dstAddr: String,
    val dstPort: Int,
    val payloadSize: Int,
    val direction: Int, // 0=INCOMING, 1=OUTGOING
    val isDns: Boolean,
    val dnsQuery: String?,
)

@Dao
interface TrafficLogDao {
    @Insert
    suspend fun insertAll(logs: List<TrafficLog>)

    @Query("SELECT * FROM traffic_logs WHERE uid = :uid AND timestamp >= :since ORDER BY timestamp DESC")
    fun getByUid(uid: Int, since: Long): Flow<List<TrafficLog>>

    @Query("SELECT * FROM traffic_logs WHERE isDns = 1 ORDER BY timestamp DESC LIMIT :limit")
    fun getDnsQueries(limit: Int = 100): Flow<List<TrafficLog>>

    @Query("DELETE FROM traffic_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM traffic_logs")
    suspend fun deleteAll()
}

@Entity(tableName = "dns_queries")
data class DnsQueryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: Int,
    val packageName: String,
    val timestamp: Long,
    val query: String,
    val responseAddresses: String, // comma-separated IPs
)

@Dao
interface DnsQueryLogDao {
    @Insert
    suspend fun insertAll(queries: List<DnsQueryLog>)

    @Query("SELECT * FROM dns_queries ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 100): Flow<List<DnsQueryLog>>

    @Query("DELETE FROM dns_queries WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Database(entities = [TrafficLog::class, DnsQueryLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trafficLogDao(): TrafficLogDao
    abstract fun dnsQueryLogDao(): DnsQueryLogDao
}