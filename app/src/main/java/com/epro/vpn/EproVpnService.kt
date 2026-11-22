package com.epro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class EproVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == "DISCONNECT") {
                stopVpn()
                return START_NOT_STICKY
            }

            val host = intent?.getStringExtra("host") ?: ""
            val mode = intent?.getStringExtra("mode") ?: "HTTP Proxy"

            if (host.isBlank()) {
                Log.e("eProVPN", "Host empty")
                stopSelf()
                return START_NOT_STICKY
            }

            // 1. Notification KWANZA - kabla ya chochote
            createChannel()
            val notification = buildNotification("Connecting to $host...")
            startForeground(1, notification)

            // 2. Anzisha VPN kwenye Thread tofauti
            running = true
            Thread {
                try {
                    val builder = Builder()
                    builder.setSession("eProVPN")
                    builder.addAddress("10.8.0.2", 32)
                    builder.addDnsServer("8.8.8.8")
                    builder.addDnsServer("1.1.1.1")
                    builder.addRoute("0.0.0.0", 0)
                    builder.setMtu(1500)

                    // USIWEKE addDisallowedApplication hapa - ndio ilikuwa inafanya crash kwa simu zingine
                    vpnInterface = builder.establish()

                    if (vpnInterface == null) {
                        Log.e("eProVPN", "establish() returned null")
                        stopVpn()
                        return@Thread
                    }

                    updateNotification("Connected - $mode | $host")
                    Log.d("eProVPN", "VPN Connected to $host")

                    // Keep alive - subiri tu mpaka user a-disconnect
                    while (running) {
                        try { Thread.sleep(1000) } catch (e: Exception) { break }
                    }
                } catch (e: Exception) {
                    Log.e("eProVPN", "VPN Thread crash: ${e.message}")
                    e.printStackTrace()
                    try { updateNotification("Failed: ${e.message}") } catch (ex: Exception) {}
                    try { Thread.sleep(1500) } catch (ex: Exception) {}
                    stopVpn()
                }
            }.start()

        } catch (e: Exception) {
            Log.e("eProVPN", "onStartCommand crash: ${e.message}")
            e.printStackTrace()
            try { stopSelf() } catch (ex: Exception) {}
        }
        return START_STICKY
    }

    private fun stopVpn() {
        running = false
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        try { stopForeground(true) } catch (e: Exception) {}
        try { stopSelf() } catch (e: Exception) {}
    }

    private fun createChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = NotificationChannel("epro_vpn", "eProVPN", NotificationManager.IMPORTANCE_LOW)
                val nm = getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(ch)
            }
        } catch (e: Exception) { Log.e("eProVPN", "Channel: ${e.message}") }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "epro_vpn")
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("eProVPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.notify(1, buildNotification(text))
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        try { stopVpn() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onRevoke() {
        try { stopVpn() } catch (e: Exception) {}
        super.onRevoke()
    }
}
