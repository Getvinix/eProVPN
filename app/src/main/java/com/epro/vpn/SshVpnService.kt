package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

class SshVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var session: Session? = null
    private var running = false

    companion object {
        var server = ""
        var port = 443
        var username = ""
        var password = ""
        var sni = ""
        var payload = ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_STICKY
        running = true
        Thread { runVpn() }.start()
        return START_STICKY
    }

    private fun runVpn() {
        try {
            val builder = Builder()
                .setSession("eProVPN")
                .addAddress("10.8.0.2", 32)
                .addDnsServer("8.8.8.8")
                .addRoute("0.0.0.0", 0)
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopSelf()
                return
            }

            val jsch = JSch()
            session = jsch.getSession(username, server, port)
            session?.setPassword(password)
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session?.setConfig(config)
            session?.connect(15000)

            session?.setPortForwardingL(1080, "127.0.0.1", 1080)

            while (running && session?.isConnected == true) {
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            stopVpn()
        }
    }

    private fun stopVpn() {
        running = false
        try { session?.disconnect() } catch (e: Exception) {}
        try { vpnInterface?.close() } catch (e: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
