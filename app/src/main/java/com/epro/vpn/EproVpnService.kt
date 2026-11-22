package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class EproVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = Builder()
        builder.setSession("eProVPN")
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
        vpnInterface = builder.establish()
        return START_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        super.onDestroy()
    }
}
