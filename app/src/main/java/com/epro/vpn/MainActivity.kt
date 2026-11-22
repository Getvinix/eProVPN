private fun setMode(mode: String) {
    selectedMode = mode
    layoutPayload.visibility = View.GONE
    layoutRemote.visibility = View.GONE
    layoutSni.visibility = View.GONE
    layoutUuid.visibility = View.GONE
    layoutVless.visibility = View.GONE
    layoutSsMethod.visibility = View.GONE
    layoutUserPass.visibility = View.VISIBLE

    // PORT - Daima ionekane, usiifiche kamwe
    etPort.visibility = View.VISIBLE
    etServer.visibility = View.VISIBLE

    when (mode) {
        "HTTP Proxy" -> {
            layoutPayload.visibility = View.VISIBLE
            layoutRemote.visibility = View.VISIBLE
            etPort.hint = "Port - mf: 80 / 8080"
            if (etPort.text.toString().isEmpty()) etPort.setText("80")
        }
        "SSH/TLS" -> {
            layoutSni.visibility = View.VISIBLE
            etPort.hint = "SSH Port - mf: 22 / 443"
            if (etPort.text.toString().isEmpty()) etPort.setText("443")
        }
        "SSL/TLS Proxy" -> {
            // HAPA NDIO ULIPOSEMA - Port ya SSL/TLS
            layoutPayload.visibility = View.VISIBLE
            layoutRemote.visibility = View.VISIBLE
            layoutSni.visibility = View.VISIBLE
            layoutUserPass.visibility = View.VISIBLE
            etPort.hint = "SSL/TLS Port - mf: 443"
            etSni.hint = "SNI / Bug Host - mf: www.safaricom.co.ke"
            // Kama port ni tupu, weka 443 moja kwa moja
            if (etPort.text.toString().isEmpty()) etPort.setText("443")
            // Kama Remote Port ni tupu, weka 80
            if (etRemotePort.text.toString().isEmpty()) etRemotePort.setText("80")
        }
        "V2Ray/Xray" -> {
            layoutVless.visibility = View.VISIBLE
            layoutUuid.visibility = View.VISIBLE
            layoutSni.visibility = View.VISIBLE
            layoutUserPass.visibility = View.GONE
            etPort.hint = "V2Ray Port - mf: 443"
        }
        "Hysteria" -> {
            layoutUuid.visibility = View.VISIBLE
            layoutSni.visibility = View.VISIBLE
            etPort.hint = "Hysteria Port - mf: 443"
        }
        "Psiphon" -> {
            etPort.hint = "Port - mf: 443"
        }
        "Shadowsocks" -> {
            layoutSsMethod.visibility = View.VISIBLE
            layoutUserPass.visibility = View.GONE
            etPort.hint = "Shadowsocks Port - mf: 8388"
        }
    }
    log("Mode: $mode | Port: ${etPort.text}")
}enOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            log("Exported: ${etConfigName.text}.epro")
            Toast.makeText(this, "Exported!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) { log("Export failed: ${e.message}") }
    }

    private fun readImportFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val json = JSONObject(text)
            etConfigName.setText(json.optString("name")); etCreator.setText(json.optString("creator")); etNotes.setText(json.optString("notes"))
            etServer.setText(json.optString("server")); etPort.setText(json.optString("port"))
            etUsername.setText(json.optString("username")); etPassword.setText(json.optString("password"))
            etPayload.setText(json.optString("payload")); etSni.setText(json.optString("sni"))
            etRemoteProxy.setText(json.optString("remoteProxy")); etRemotePort.setText(json.optString("remotePort"))
            etUuid.setText(json.optString("uuid")); etVlessLink.setText(json.optString("vlessLink")); etSsMethod.setText(json.optString("ssMethod"))
            val mode = json.optString("mode", "HTTP Proxy")
            val idx = modes.indexOf(mode); if (idx >= 0) spinnerMode.setSelection(idx); setMode(mode)
            saveAll()
            val notes = json.optString("notes"); val creator = json.optString("creator")
            if (notes.isNotEmpty()) { android.app.AlertDialog.Builder(this).setTitle("Config kutoka kwa: $creator").setMessage("Notes:\n\n$notes").setPositiveButton("OK", null).show() }
            log("Imported: ${json.optString("name")} by $creator")
        } catch (e: Exception) { Toast.makeText(this, "File sio sahihi", Toast.LENGTH_LONG).show() }
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        layoutPayload.visibility = View.GONE; layoutRemote.visibility = View.GONE; layoutSni.visibility = View.GONE
        layoutUuid.visibility = View.GONE; layoutVless.visibility = View.GONE; layoutSsMethod.visibility = View.GONE
        layoutUserPass.visibility = View.VISIBLE
        when (mode) {
            "HTTP Proxy" -> { layoutPayload.visibility = View.VISIBLE; layoutRemote.visibility = View.VISIBLE }
            "SSH/TLS" -> { layoutSni.visibility = View.VISIBLE }
            "SSL/TLS Proxy" -> { layoutPayload.visibility = View.VISIBLE; layoutRemote.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE }
            "V2Ray/Xray" -> { layoutVless.visibility = View.VISIBLE; layoutUuid.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE; layoutUserPass.visibility = View.GONE }
            "Hysteria" -> { layoutUuid.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE }
            "Psiphon" -> { }
            "Shadowsocks" -> { layoutSsMethod.visibility = View.VISIBLE; layoutUserPass.visibility = View.GONE }
        }
        log("Mode: $mode")
    }

    private fun log(msg: String) {
        try {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            tvLog.append("\n[$time] $msg")
        } catch (e: Exception) {}
    }

    private fun startVpn() {
        // VALIDATION - Zuia crash kama server tupu
        val host = etServer.text.toString().trim()
        if (host.isEmpty()) { Toast.makeText(this, "Weka Server Host kwanza!", Toast.LENGTH_SHORT).show(); return }
        val port = etPort.text.toString().toIntOrNull()
        if (port == null || port <= 0 || port > 65535) { Toast.makeText(this, "Port sio sahihi! Weka 443", Toast.LENGTH_SHORT).show(); return }
        saveAll()
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) startActivityForResult(intent, 100) else onActivityResult(100, RESULT_OK, null)
        } catch (e: Exception) {
            log("VPN prepare failed: ${e.message}")
            Toast.makeText(this, "VPN Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVpn() {
        try {
            val i = Intent(this, EproVpnService::class.java); i.action = "DISCONNECT"
            ContextCompat.startForegroundService(this, i)
        } catch (e: Exception) {}
        isConnected = false; btnConnect.text = "CONNECT"; log("Disconnected")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            try {
                val i = Intent(this, EproVpnService::class.java)
                i.putExtra("host", etServer.text.toString()); i.putExtra("port", etPort.text.toString())
                i.putExtra("username", etUsername.text.toString()); i.putExtra("password", etPassword.text.toString())
                i.putExtra("payload", etPayload.text.toString()); i.putExtra("sni", etSni.text.toString())
                i.putExtra("mode", selectedMode); i.putExtra("uuid", etUuid.text.toString())
                i.putExtra("vless", etVlessLink.text.toString()); i.putExtra("ssMethod", etSsMethod.text.toString())
                i.putExtra("remoteProxy", etRemoteProxy.text.toString()); i.putExtra("remotePort", etRemotePort.text.toString())
                ContextCompat.startForegroundService(this, i)
                isConnected = true; btnConnect.text = "DISCONNECT"
                log("Connecting [${selectedMode}] to ${etServer.text}...")
            } catch (e: Exception) { log("Start failed: ${e.message}"); Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show() }
        } else if (requestCode == 100) { log("VPN permission denied"); Toast.makeText(this, "Kubali VPN Permission", Toast.LENGTH_SHORT).show() }
    }
}
