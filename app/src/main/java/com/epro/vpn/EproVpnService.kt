package com.epro.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket

class EproVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var running = false
    private var remoteSocket: Socket? = null

    private fun sendLog(msg: String) {
        Log.d("eProVPN", msg)
        try { val i = Intent("com.epro.vpn.LOG"); i.putExtra("log", msg); sendBroadcast(i) } catch (e: Exception) {}
        try { getSystemService(NotificationManager::class.java)?.notify(1, buildNotification(msg)) } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "DISCONNECT") { stopVpnSafe(); return START_NOT_STICKY }
        try {
            val host = intent?.getStringExtra("host") ?: ""
            val port = intent?.getStringExtra("port")?.toIntOrNull() ?: 443
            val mode = intent?.getStringExtra("mode") ?: "HTTP Proxy"
            val payloadRaw = intent?.getStringExtra("payload") ?: ""
            val sni = intent?.getStringExtra("sni") ?: ""
            val user = intent?.getStringExtra("username") ?: ""
            val pass = intent?.getStringExtra("password") ?: ""
            if (host.isBlank()) { stopSelf(); return START_NOT_STICKY }
            createChannel()
            startForeground(1, buildNotification("Starting..."))
            sendLog("eProVPN v1.0")
            sendLog("Running on ${Build.MANUFACTURER} (${Build.MODEL})")
            sendLog("Android ${Build.VERSION.RELEASE} API-${Build.VERSION.SDK_INT}")
            sendLog("Starting $mode...")
            sendLog("Connecting to $host:$port, timeout 10 seconds")
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
                    vpnInterface = builder.establish()
                    if (vpnInterface == null) { sendLog("Failed: establish() null"); stopVpnSafe(); return@Thread }
                    val tunIn = FileInputStream(vpnInterface!!.fileDescriptor)
                    val tunOut = FileOutputStream(vpnInterface!!.fileDescriptor)
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 10000)
                    remoteSocket = socket
                    sendLog("TCP Connected to $host:$port")
                    if (payloadRaw.isNotEmpty()) {
                        var payload = payloadRaw.replace("[host]", host).replace("[host_port]", "$host:$port").replace("[sni]", sni.ifEmpty { host }).replace("[crlf]", "\r\n").replace("[lf]", "\n")
                        socket.getOutputStream().write(payload.toByteArray()); socket.getOutputStream().flush()
                        sendLog("Payload sent: ${payload.take(80)}")
                    } else if (mode.contains("SSL") || mode.contains("TLS")) {
                        val req = "CONNECT $host:$port HTTP/1.1\r\nHost: ${sni.ifEmpty { host }}\r\n\r\n"
                        socket.getOutputStream().write(req.toByteArray()); socket.getOutputStream().flush()
                        sendLog("CONNECT sent to $host:$port")
                    }
                    socket.soTimeout = 10000
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val firstLine = try { reader.readLine() } catch (e: Exception) { null }
                    if (firstLine != null) {
                        sendLog(firstLine)
                        var line: String?
                        do { line = try { reader.readLine() } catch (e: Exception) { null }; if (!line.isNullOrEmpty()) sendLog(line) } while (!line.isNullOrEmpty())
                        if (firstLine.contains("200") || firstLine.contains("101")) {
                            sendLog("HTTP/1.0 200 Connection established")
                            sendLog("Connected - Tunnel Active! Internet inafanya kazi")
                        } else { sendLog("Failed: Server alikataa: $firstLine"); stopVpnSafe(); return@Thread }
                    } else sendLog("Connected (No HTTP response - Direct TCP)")
                    socket.soTimeout = 0
                    sendLog("Forwarding traffic... Browse sasa")
                    val tunToSocket = Thread {
                        val buf = ByteArray(32767)
                        try { while (running) { val len = tunIn.read(buf); if (len > 0) { try { socket.getOutputStream().write(buf, 0, len); socket.getOutputStream().flush() } catch (e: Exception) { break } } } } catch (e: Exception) {}
                    }
                    val socketToTun = Thread {
                        val buf = ByteArray(32767); val input = socket.getInputStream()
                        try { while (running) { val len = input.read(buf); if (len > 0) { try { tunOut.write(buf, 0, len); tunOut.flush() } catch (e: Exception) { break } } else if (len < 0) break } } catch (e: Exception) {}
                    }
                    tunToSocket.start(); socketToTun.start(); tunToSocket.join(); socketToTun.join()
                    sendLog("Tunnel closed by server")
                } catch (e: Exception) { sendLog("Error: ${e.message}") } finally { stopVpnSafe() }
            }.start()
        } catch (e: Exception) { sendLog("Start failed: ${e.message}"); stopVpnSafe() }
        return START_STICKY
    }

    private fun stopVpnSafe() {
        running = false
        try { remoteSocket?.close() } catch (e: Exception) {}
        remoteSocket = null
        try { vpnInterface?.close() } catch (e: Exception) {}
        vpnInterface = null
        try { stopForeground(true) } catch (e: Exception) {}
        try { stopSelf() } catch (e: Exception) {}
    }
    private fun createChannel() {
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val ch = NotificationChannel("epro_vpn", "eProVPN", NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch) } } catch (e: Exception) {}
    }
    private fun buildNotification(text: String): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, "epro_vpn") else Notification.Builder(this)
        return b.setContentTitle("eProVPN").setContentText(text).setSmallIcon(android.R.drawable.ic_lock_lock).setOngoing(true).build()
    }
    override fun onDestroy() {
        running = false
        try { remoteSocket?.close() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
        try { stopForeground(true) } catch (e: Exception) {}
        super.onDestroy()
    }
    override fun onRevoke() {
        running = false
        try { remoteSocket?.close() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
        super.onRevoke()
    }
}
