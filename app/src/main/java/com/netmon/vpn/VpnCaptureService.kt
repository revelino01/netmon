package com.netmon.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.netmon.core.FlowTracker
import com.netmon.core.PacketParser
import com.netmon.domain.PacketEvent
import com.netmon.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

class VpnCaptureService : VpnService() {

    companion object {
        const val CHANNEL_ID = "netmon_vpn"
        const val NOTIFICATION_ID = 1
        const val MAX_PACKET_SIZE = 32768

        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isStopping = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Monitoring network traffic"))

        vpnInterface = establishVpn()
        if (vpnInterface == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch { readLoop() }

        // Periodic snapshot emission
        scope.launch {
            while (isActive) {
                delay(1000)
                FlowTracker.emitSnapshot(emptyMap()) // UID→name mapping injected later
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        isStopping = true
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        isRunning = false
        FlowTracker.reset()
        super.onDestroy()
    }

    override fun onRevoke() {
        isStopping = true
        vpnInterface?.close()
        vpnInterface = null
        stopSelf()
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        return Builder()
            .setSession("NetMon")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setMtu(1500)
            .setBlocking(true)
            .establish()
    }

    private suspend fun readLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val buffer = ByteBuffer.allocateDirect(MAX_PACKET_SIZE)

        try {
            while (coroutineContext.isActive && !isStopping) {
                buffer.clear()
                val bytesRead = withContext(Dispatchers.IO) { input.read(buffer.array()) }
                if (bytesRead <= 0) continue

                buffer.position(0)
                buffer.limit(bytesRead)

                val parsed = PacketParser.parse(buffer, bytesRead) ?: continue

                // Determine direction: if dst is our VPN address, it's incoming
                val direction = if (parsed.dstAddr == "10.8.0.2") {
                    PacketEvent.Direction.INCOMING
                } else {
                    PacketEvent.Direction.OUTGOING
                }

                val event = PacketEvent(
                    uid = 0, // Will be filled by UID mapping
                    packageName = "",
                    timestamp = System.currentTimeMillis(),
                    protocol = parsed.protocol,
                    srcAddr = parsed.srcAddr,
                    srcPort = parsed.srcPort,
                    dstAddr = parsed.dstAddr,
                    dstPort = parsed.dstPort,
                    payloadSize = parsed.payloadSize.coerceAtLeast(0),
                    direction = direction,
                    isDns = parsed.isDns,
                    dnsQuery = parsed.dnsQuery,
                )

                FlowTracker.ingest(event)
            }
        } catch (e: Exception) {
            if (!isStopping) {
                // Log error but don't crash
                e.printStackTrace()
            }
        } finally {
            input.close()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NetMon VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Network monitoring active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VpnCaptureService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NetMon")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}