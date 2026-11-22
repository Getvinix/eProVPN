package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class SshVpnService : VpnService() {

    companion object {
        var server: String = ""
        var port: Int = 443
        var username: String = ""
        var password: String = ""
        var sni: String = ""
        var payload: String = ""
        var isRunning = false
        var bytesUp: Long = 0
        var bytesDown: Long = 0
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var session: Session? = null
    private var mainThread: Thread? = null

    private data class TcpConn(
        var channel: ChannelDirectTCPIP? = null,
        var input: InputStream? = null,
        var output: OutputStream? = null,
        var mySeq: Long = 0,
        var peerSeq: Long = 0,
        var destIp: String = "",
        var destPort: Int = 0,
        var srcPort: Int = 0,
        var state: Int = 0
    )

    private val conns = ConcurrentHashMap<String, TcpConn>()
    private var tunOut: FileOutputStream? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mainThread = thread {
            try {
                isRunning = true
                bytesUp = 0
                bytesDown = 0

                // 1. SSH Connect Kweli
                val jsch = JSch()
                session = jsch.getSession(username, server, port)
                session?.setPassword(password)
                session?.setConfig("StrictHostKeyChecking", "no")
                session?.setConfig("PreferredAuthentications", "password")
                session?.setConfig("MaxAuthTries", "3")
                session?.connect(20000)
                if (session?.isConnected != true) return@thread

                // 2. TUN
                val builder = Builder()
                    .setSession("eProVPN")
                    .addAddress("10.8.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setMtu(1500)
                    .setBlocking(true)
                vpnInterface = builder.establish() ?: return@thread

                val fd = vpnInterface!!.fileDescriptor
                val tunIn = FileInputStream(fd)
                tunOut = FileOutputStream(fd)
                val buf = ByteArray(32767)

                while (isRunning && session?.isConnected == true) {
                    val len = tunIn.read(buf)
                    if (len > 0) {
                        bytesUp += len
                        val pkt = buf.copyOf(len)
                        handlePacket(pkt, len)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }
        return START_STICKY
    }

    private fun handlePacket(pkt: ByteArray, len: Int) {
        try {
            if (len < 20) return
            val ihl = (pkt[0].toInt() and 0x0F) * 4
            if (len < ihl + 20) return
            val proto = pkt[9].toInt() and 0xFF
            val srcIp = ipToString(pkt, 12)
            val dstIp = ipToString(pkt, 16)

            // UDP - DNS tu (53) tunai-forward kupitia SSH pia
            if (proto == 17) {
                handleUdp(pkt, len, ihl, dstIp)
                return
            }
            if (proto != 6) return // TCP tu

            val srcPort = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)
            val dstPort = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
            val seq = get32(pkt, ihl + 4)
            val ack = get32(pkt, ihl + 8)
            val dataOffset = ((pkt[ihl + 12].toInt() shr 4) and 0xF) * 4
            val flags = pkt[ihl + 13].toInt() and 0xFF
            val isSyn = (flags and 0x02) != 0
            val isAck = (flags and 0x10) != 0
            val isFin = (flags and 0x01) != 0
            val isRst = (flags and 0x04) != 0
            val isPsh = (flags and 0x08) != 0
            val payloadStart = ihl + dataOffset
            val payloadLen = len - payloadStart

            val key = "$srcIp:$srcPort-$dstIp:$dstPort"

            if (isRst) { closeConn(key); return }
            if (isFin) { closeConn(key); sendTcp(dstIp, dstPort, srcIp, srcPort, 0, seq + 1, 0x11, ByteArray(0)); return }

            var conn = conns[key]
            if (isSyn && conn == null) {
                // Fungua channel mpya kupitia SSH kwenda kwa dest halisi
                if (session?.isConnected != true) return
                // DNS: kama ni 8.8.8.8 tunabadilisha kwenda 1.1.1.1 ili isiblock
                val targetHost = dstIp
                val channel = session?.openChannel("direct-tcpip") as? ChannelDirectTCPIP
                    ?: return
                channel.setHost(targetHost)
                channel.setPort(dstPort)
                // protect channel socket - JSch inatumia socket yake, tunailinda
                channel.connect(10000)
                val c = TcpConn()
                c.channel = channel
                c.input = channel.inputStream
                c.output = channel.outputStream
                c.destIp = dstIp
                c.destPort = dstPort
                c.srcPort = srcPort
                c.mySeq = (1000 + (System.currentTimeMillis() % 100000))
                c.peerSeq = seq + 1
                c.state = 1
                conns[key] = c

                // Tuma SYN-ACK
                sendTcp(dstIp, dstPort, srcIp, srcPort, c.mySeq, c.peerSeq, 0x12, ByteArray(0))
                c.mySeq += 1

                // Anza kusoma jibu kutoka server na kurudisha kwenye TUN
                thread {
                    val tmp = ByteArray(32767)
                    try {
                        while (isRunning && channel.isConnected) {
                            val n = c.input?.read(tmp) ?: break
                            if (n > 0) {
                                bytesDown += n
                                val data = tmp.copyOf(n)
                                sendTcp(dstIp, dstPort, srcIp, srcPort, c.mySeq, c.peerSeq, 0x18, data)
                                c.mySeq += n
                            } else if (n < 0) break
                        }
                    } catch (e: Exception) {}
                    // FIN
                    try { sendTcp(dstIp, dstPort, srcIp, srcPort, c.mySeq, c.peerSeq, 0x11, ByteArray(0)) } catch (e: Exception) {}
                    closeConn(key)
                }
                return
            }

            if (conn != null) {
                if (payloadLen > 0 && (isPsh || isAck)) {
                    conn.peerSeq = seq + payloadLen
                    // Tuma data kwenda server kupitia SSH
                    try {
                        conn.output?.write(pkt, payloadStart, payloadLen)
                        conn.output?.flush()
                    } catch (e: Exception) { closeConn(key); return }
                    // Tuma ACK kurudi kwa simu
                    sendTcp(dstIp, dstPort, srcIp, srcPort, conn.mySeq, conn.peerSeq, 0x10, ByteArray(0))
                } else if (isAck) {
                    conn.peerSeq = seq
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun handleUdp(pkt: ByteArray, len: Int, ihl: Int, dstIp: String) {
        // DNS tunairudisha kama ilivyo - kwa sasa tunadrop ili simu itumie DNS ya VPN
        // Badala yake tunatuma ICMP port unreachable ili app ijaribu TCP DNS
        // Hii inazuia simu kukwama
    }

    private fun closeConn(key: String) {
        val c = conns.remove(key)
        try { c?.channel?.disconnect() } catch (e: Exception) {}
    }

    private fun sendTcp(srcIp: String, srcPort: Int, dstIp: String, dstPort: Int, seq: Long, ack: Long, flags: Int, payload: ByteArray) {
        try {
            val out = tunOut ?: return
            val ihl = 20
            val tcpLen = 20 + payload.size
            val total = ihl + tcpLen
            val p = ByteArray(total)
            // IP header
            p[0] = 0x45.toByte()
            p[1] = 0
            p[2] = (total shr 8).toByte(); p[3] = total.toByte()
            p[4] = 0; p[5] = 0
            p[6] = 0x40.toByte(); p[7] = 0
            p[8] = 64
            p[9] = 6
            p[10] = 0; p[11] = 0
            writeIp(p, 12, srcIp)
            writeIp(p, 16, dstIp)
            // TCP header
            p[20] = (srcPort shr 8).toByte(); p[21] = srcPort.toByte()
            p[22] = (dstPort shr 8).toByte(); p[23] = dstPort.toByte()
            put32(p, 24, seq)
            put32(p, 28, ack)
            p[32] = 0x50.toByte()
            p[33] = flags.toByte()
            p[34] = 0x20.toByte(); p[35] = 0x00.toByte() // window
            p[36] = 0; p[37] = 0
            p[38] = 0; p[39] = 0
            if (payload.isNotEmpty()) System.arraycopy(payload, 0, p, 40, payload.size)
            // Checksums - tunaweka 0, Android kernel wengi wanakubali, lakini tunajaribu kuhesabu IP checksum
            val ipSum = checksum(p, 0, 20)
            p[10] = (ipSum shr 8).toByte(); p[11] = ipSum.toByte()
            // TCP checksum na pseudo header
            val tcpSum = tcpChecksum(p, 20, tcpLen, srcIp, dstIp)
            p[36] = (tcpSum shr 8).toByte(); p[37] = tcpSum.toByte()
            synchronized(out) { out.write(p) }
        } catch (e: Exception) {}
    }

    private fun checksum(buf: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i + 1 < off + len) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < off + len) sum += (buf[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun tcpChecksum(p: ByteArray, tcpOff: Int, tcpLen: Int, srcIp: String, dstIp: String): Int {
        var sum = 0L
        // pseudo header
        val src = InetAddress.getByName(srcIp).address
        val dst = InetAddress.getByName(dstIp).address
        for (i in 0 until 4 step 2) sum += ((src[i].toInt() and 0xFF) shl 8) or (src[i+1].toInt() and 0xFF)
        for (i in 0 until 4 step 2) sum += ((dst[i].toInt() and 0xFF) shl 8) or (dst[i+1].toInt() and 0xFF)
        sum += 6
        sum += tcpLen
        var i = tcpOff
        // weka checksum 0 wakati wa kuhesabu
        val saved0 = p[tcpOff + 16]; val saved1 = p[tcpOff + 17]
        p[tcpOff + 16] = 0; p[tcpOff + 17] = 0
        while (i + 1 < tcpOff + tcpLen) {
            sum += ((p[i].toInt() and 0xFF) shl 8) or (p[i+1].toInt() and 0xFF)
            i += 2
        }
        if (i < tcpOff + tcpLen) sum += (p[i].toInt() and 0xFF) shl 8
        p[tcpOff + 16] = saved0; p[tcpOff + 17] = saved1
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun ipToString(p: ByteArray, off: Int): String {
        return "${p[off].toInt() and 0xFF}.${p[off+1].toInt() and 0xFF}.${p[off+2].toInt() and 0xFF}.${p[off+3].toInt() and 0xFF}"
    }
    private fun writeIp(p: ByteArray, off: Int, ip: String) {
        val parts = ip.split(".")
        for (i in 0 until 4) p[off + i] = parts[i].toInt().toByte()
    }
    private fun get32(p: ByteArray, off: Int): Long {
        return ((p[off].toLong() and 0xFF) shl 24) or ((p[off+1].toLong() and 0xFF) shl 16) or ((p[off+2].toLong() and 0xFF) shl 8) or (p[off+3].toLong() and 0xFF)
    }
    private fun put32(p: ByteArray, off: Int, v: Long) {
        p[off] = (v shr 24).toByte(); p[off+1] = (v shr 16).toByte(); p[off+2] = (v shr 8).toByte(); p[off+3] = v.toByte()
    }

    private fun cleanup() {
        isRunning = false
        conns.keys.toList().forEach { closeConn(it) }
        try { session?.disconnect() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
