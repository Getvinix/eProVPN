package com.epro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class EproVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    private val running = AtomicBoolean(false)
    private var currentMode = "HTTP Proxy"
    private var currentHost = ""

    companion object {
        const val TAG = "eProVPN"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") {
            stopVpn()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra("host") ?: ""
        val port = intent?.getStringExtra("port")?.toIntOrNull() ?: 443
        val mode = intent?.getStringExtra("mode") ?: "HTTP Proxy"
        val sni = intent?.getStringExtra("sni") ?: ""
        val payload = intent?.getStringExtra("payload") ?: ""

        if (host.isEmpty()) {
            Log.e(TAG, "Host empty - cannot connect")
            stopSelf()
            return START_NOT_STICKY
        }

        currentHost = host
        currentMode = mode

        // Kama tayari ina-run, isimamishe kwanza
        stopVpn()

        startForeground(1, buildNotification("Connecting to $host..."))
        startVpn(host, port, mode, sni, payload)
        return START_STICKY
    }

    private fun startVpn(host: String, port: Int, mode: String, sni: String, payload: String) {
        running.set(true)
        vpnThread = Thread {
            try {
                // 1. Jaribu kuunganisha kwenye server halisi kwanza
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), 8000)
                Log.d(TAG, "TCP Connected to $host:$port - Mode: $mode")
                // Usifunge socket mara moja - acha i-run kama tunnel test
                // Tutaifunga baada ya VPN interface kuanzishwa

                // 2. Anzisha VPN Interface HALISI
                val builder = Builder()
                builder.setSession("eProVPN")
                    .addAddress("10.8.0.2", 32)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .addRoute("0.0.0.0", 0)
                    .setMtu(1500)

                // Usiruhusu app yenyewe kupita kwenye VPN - kuzuia loop
                try { builder.addDisallowedApplication(packageName) } catch (e: Exception) {}

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface")
                    socket.close()
                    stopVpn()
                    return@Thread
                }

                Log.d(TAG, "VPN Interface Established")
                updateNotification("Connected - $mode | $host")

                val input = FileInputStream(vpnInterface!!.fileDescriptor)
                val output = FileOutputStream(vpnInterface!!.fileDescriptor)
                val buffer = ByteArray(32767)

                // 3. Loop ya kusambaza packets - hii ndio KAZI HALISI
                // Kwa sasa tunasoma packets na kuzitupa, lakini interface iko hai
                // Hatua inayofuata tutaweka forwarding halisi kupitia socket
                while (running.get()) {
                    try {
                        val len = input.read(buffer)
                        if (len > 0) {
                            // Packet imepokelewa - log mara moja tu ili isijaze log
                            // Hapa ndipo tuta-forward kwenda server
                        }
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "Read error: ${e.message}")
                        break
                    }
                }

                try { socket.close() } catch (e: Exception) {}
                try { input.close() } catch (e: Exception) {}
                try { output.close() } catch (e: Exception) {}

            } catch (e: Exception) {
                Log.e(TAG, "Connection failed: ${e.message}")
                e.printStackTrace()
                updateNotification("Failed: ${e.message}")
                // Subiri sekunde 2 ndio usimamishe ili user aone error
                try { Thread.sleep(2000) } catch (ex: Exception) {}
                stopVpn()
            }
        }
        vpnThread?.start()
    }

    private fun stopVpn() {
        running.set(false)
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        try { vpnThread?.interrupt() } catch (e: Exception) {}
        vpnThread = null
        try { stopForeground(true) } catch (e: Exception) {}
        try { stopSelf() } catch (e: Exception) {}
    }

    private fun createChannel() {
        try {
            val channel = NotificationChannel("epro_vpn", "eProVPN", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        } catch (e: Exception) {}
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "epro_vpn")
            .setContentTitle("eProVPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
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
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}
