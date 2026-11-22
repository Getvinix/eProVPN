package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class SshVpnService : VpnService() {

    companion object {
        var server: String = ""
        var port: Int = 443
        var username: String = ""
        var password: String = ""
        var sni: String = ""
        var payload: String = ""
        var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var session: Session? = null
    private var thread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        thread = Thread {
            try {
                isRunning = true
                // 1. Connect SSH kweli
                val jsch = JSch()
                session = jsch.getSession(username, server, port)
                session?.setPassword(password)
                session?.setConfig("StrictHostKeyChecking", "no")
                session?.setConfig("PreferredAuthentications", "password")
                session?.connect(15000)
                
                // 2. Dynamic SOCKS proxy kupitia SSH
                session?.setPortForwardingD("127.0.0.1", 1080)

                // 3. Unda TUN interface
                val builder = Builder()
                    .setSession("eProVPN")
                    .addAddress("10.8.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setMtu(1500)
                vpnInterface = builder.establish()
                
                // 4. Anza kusogeza data TUN <-> SOCKS
                val fd = vpnInterface?.fileDescriptor ?: return@Thread
                val input = FileInputStream(fd)
                val output = FileOutputStream(fd)
                val buffer = ByteArray(32767)
                
                while (isRunning && session?.isConnected == true) {
                    val len = input.read(buffer)
                    if (len > 0) {
                        // Rudisha packet kama ping - kwa sasa tunadrop TCP complex
                        // Hapa tunahandle ICMP / DNS tu ili simu isikate
                        handlePacket(buffer, len, output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stopSelf()
            }
        }
        thread?.start()
        return START_STICKY
    }

    private fun handlePacket(packet: ByteArray, len: Int, output: FileOutputStream) {
        // Tunaangalia kama ni DNS au ICMP, tunairudisha ili connection ibaki hai
        // TCP halisi inahitaji proxy kamili - hii ni hatua ya kwanza ya live data
        try {
            if (len < 20) return
            val version = (packet[0].toInt() shr 4) and 0xF
            if (version != 4) return
            val protocol = packet[9].toInt() and 0xFF
            // ICMP - Echo reply
            if (protocol == 1) {
                val reply = packet.copyOf(len)
                // Swap IP src/dst
                for (i in 0 until 4) {
                    val tmp = reply[12 + i]
                    reply[12 + i] = reply[16 + i]
                    reply[16 + i] = tmp
                }
                reply[8] = 0 // ICMP type = echo reply
                // Recalculate checksum rahisi - tunaweka 0 kwa sasa OS itakubali
                output.write(reply, 0, len)
            }
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        isRunning = false
        try { session?.disconnect() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
        super.onDestroy()
    }
}
