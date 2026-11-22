package com.epro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class EproVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var socket: Socket? = null
    private var running = AtomicBoolean(false)
    private var workerThread: Thread? = null

    companion object {
        const val TAG = "EproVpnService"
        const val CHANNEL_ID = "epro_vpn_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") {
            stopVpn()
            return START_NOT_STICKY
        }

        if (running.get()) return START_STICKY

        val server = intent?.getStringExtra("server") ?: ""
        val port = intent?.getStringExtra("port")?.toIntOrNull() ?: 443
        val username = intent?.getStringExtra("username") ?: ""
        val password = intent?.getStringExtra("password") ?: ""
        val mode = intent?.getStringExtra("mode") ?: "SSH + SSL"
        val payload = intent?.getStringExtra("payload") ?: ""
        val sni = intent?.getStringExtra("sni") ?: ""
        val remoteProxy = intent?.getStringExtra("remoteProxy") ?: ""
        val remotePort = intent?.getStringExtra("remotePort")?.toIntOrNull() ?: 80
        val uuid = intent?.getStringExtra("uuid") ?: ""
        val configName = intent?.getStringExtra("configName") ?: "eProVPN"

        Log.d(TAG, "Start: $mode $server:$port user=$username sni=$sni payload=$payload")

        startForeground(NOTIF_ID, buildNotification("Connecting to $server..."))
        startVpn(server, port, mode, payload, sni, remoteProxy, remotePort)

        return START_STICKY
    }

    private fun startVpn(server: String, port: Int, mode: String, payload: String, sni: String, remoteProxy: String, remotePort: Int) {
        running.set(true)
        workerThread = Thread {
            try {
                // 1. Tengeneza VPN Interface
                val builder = Builder()
                    .setSession("eProVPN")
                    .addAddress("10.8.0.2", 32)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .addRoute("0.0.0.0", 0)

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    Log.e(TAG, "Failed to establish VPN interface")
                    stopVpn()
                    return@Thread
                }

                updateNotification("Connected: $server:$port [$mode]")

                // 2. Unganisha Socket kwa server
                val targetHost = if (remoteProxy.isNotBlank()) remoteProxy else server
                val targetPort = if (remoteProxy.isNotBlank()) remotePort else port

                socket = Socket()
                socket?.connect(InetSocketAddress(targetHost, targetPort), 10000)
                Log.d(TAG, "Socket connected to $targetHost:$targetPort")

                // 3. Tuma Payload kama upo - badilisha [crlf] kuwa \r\n halisi
                if (payload.isNotBlank()) {
                    val realPayload = payload
                        .replace("[crlf]", "\r\n")
                        .replace("[lf]", "\n")
                        .replace("[cr]", "\r")
                    val out: OutputStream? = socket?.getOutputStream()
                    out?.write(realPayload.toByteArray(Charsets.UTF_8))
                    out?.flush()
                    Log.d(TAG, "Payload sent: $realPayload")
                }

                // 4. Keep alive - soma data ili connection isikatike
                val input: InputStream? = socket?.getInputStream()
                val buffer = ByteArray(32767)
                val vpnInput = ParcelFileDescriptor.AutoCloseInputStream(vpnInterface).read(buffer) // dummy to keep ref

                while (running.get() && socket?.isConnected == true) {
                    try {
                        val read = input?.read(buffer) ?: -1
                        if (read == -1) break
                        // Hapa ndipo tunge-forward data kwenda VPN interface
                        // Kwa sasa tunai-keep alive
                        Log.d(TAG, "Received $read bytes")
                    } catch (e: Exception) {
                        Log.e(TAG, "Read error: ${e.message}")
                        break
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "VPN error: ${e.message}", e)
            } finally {
                if (running.get()) stopVpn()
            }
        }
        workerThread?.start()
    }

    private fun stopVpn() {
        running.set(false)
        try { socket?.close() } catch (_: Exception) {}
        try { vpnInterface?.close() } catch (_: Exception) {}
        socket = null
        vpnInterface = null
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "VPN stopped")
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "eProVPN", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("eProVPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification(text))
    }
}
