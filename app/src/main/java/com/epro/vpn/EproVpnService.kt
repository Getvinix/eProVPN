package com.epro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

class EproVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") {
            stopVpn()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra("host") ?: "sg1.eprovpn.com"
        val mode = intent?.getStringExtra("mode") ?: "HTTP Proxy"

        // Anzisha notification mara moja
        startForeground(1, buildNotification("Connected - $mode", "Server: $host • Tap to open"))

        Thread {
            try {
                isRunning = true
                val builder = Builder(this)
                builder.setSession("eProVPN")
                builder.addAddress("10.8.0.2", 32)
                builder.addRoute("0.0.0.0", 0)
                builder.addDnsServer("8.8.8.8")
                builder.addDnsServer("1.1.1.1")
                vpnInterface = builder.establish()
                // Hapa baadaye SSH / Payload / V2Ray itaunganishwa
                // Kwa sasa VPN interface imefunguliwa na notification inaonekana
            } catch (e: Exception) {
                e.printStackTrace()
                stopVpn()
            }
        }.start()

        return START_STICKY
    }

    private fun buildNotification(title: String, text: String): Notification {
        val channelId = "eprovpn_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "eProVPN", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun stopVpn() {
        isRunning = false
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}
