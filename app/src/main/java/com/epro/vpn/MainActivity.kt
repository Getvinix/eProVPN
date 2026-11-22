package com.epro.vpn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPayload: EditText
    private lateinit var etSni: EditText
    private lateinit var etRemoteProxy: EditText
    private lateinit var etRemotePort: EditText
    private lateinit var etUuid: EditText
    private lateinit var etVlessLink: EditText
    private lateinit var etSsMethod: EditText
    private lateinit var etConfigName: EditText
    private lateinit var etCreator: EditText
    private lateinit var etNotes: EditText
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var layoutPayload: LinearLayout
    private lateinit var layoutRemote: LinearLayout
    private lateinit var layoutSni: LinearLayout
    private lateinit var layoutUuid: LinearLayout
    private lateinit var layoutVless: LinearLayout
    private lateinit var layoutSsMethod: LinearLayout
    private lateinit var layoutUserPass: LinearLayout
    private lateinit var spinnerMode: Spinner
    private lateinit var spinnerPayload: Spinner
    private lateinit var spinnerSsMethod: Spinner

    private var isConnected = false
    private var selectedMode = "HTTP Proxy"

    private val modes = listOf("HTTP Proxy","SSH/TLS","SSL/TLS Proxy","V2Ray/Xray","Hysteria","Psiphon","Shadowsocks")
    private val payloadPresets = listOf("Custom - Andika mwenyewe","GET Standard","GET + Keep Alive","CONNECT Standard","POST Standard")
    private val payloadValues = listOf("","GET / HTTP/1.1[crlf]Host: [sni][crlf][crlf]","GET / HTTP/1.1[crlf]Host: [sni][crlf]Connection: keep-alive[crlf][crlf]","CONNECT [host_port] HTTP/1.1[crlf]Host: [sni][crlf][crlf]","POST / HTTP/1.1[crlf]Host: [sni][crlf]Content-Length: 0[crlf][crlf]")
    private val ssMethods = listOf("aes-256-gcm","aes-128-gcm","chacha20-ietf-poly1305","aes-256-cfb","plain")

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { writeExportFile(it) } }
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { readImportFile(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServer = findViewById(R.id.etServer)
        etPort = findViewById(R.id.etPort)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etPayload = findViewById(R.id.etPayload)
        etSni = findViewById(R.id.etSni)
        etRemoteProxy = findViewById(R.id.etRemoteProxy)
        etRemotePort = findViewById(R.id.etRemotePort)
        etUuid = findViewById(R.id.etUuid)
        etVlessLink = findViewById(R.id.etVlessLink)
        etSsMethod = findViewById(R.id.etSsMethod)
        etConfigName = findViewById(R.id.etConfigName)
        etCreator = findViewById(R.id.etCreator)
        etNotes = findViewById(R.id.etNotes)
        tvLog = findViewById(R.id.tvLog)
        btnConnect = findViewById(R.id.btnConnect)
        layoutPayload = findViewById(R.id.layoutPayload)
        layoutRemote = findViewById(R.id.layoutRemote)
        layoutSni = findViewById(R.id.layoutSni)
        layoutUuid = findViewById(R.id.layoutUuid)
        layoutVless = findViewById(R.id.layoutVless)
        layoutSsMethod = findViewById(R.id.layoutSsMethod)
        layoutUserPass = findViewById(R.id.layoutUserPass)
        spinnerMode = findViewById(R.id.spinnerMode)
        spinnerPayload = findViewById(R.id.spinnerPayload)
        spinnerSsMethod = findViewById(R.id.spinnerSsMethod)

        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { setMode(modes[pos]); saveAll() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerPayload.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payloadPresets)
        spinnerPayload.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (pos > 0) etPayload.setText(payloadValues[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerSsMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ssMethods)
        spinnerSsMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { etSsMethod.setText(ssMethods[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { saveAll(); exportConfig() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch("*/*") }
        btnConnect.setOnClickListener { if (!isConnected) startVpn() else stopVpn() }

        // RESTORE - Rudisha details zilizohifadhiwa
        loadAll()
        // AUTO-SAVE - Kila ukiandika, inahifadhi
        setupAutoSave()
        log("eProVPN ready... Details zako zimerudishwa")
    }

    // ===== SAVE / LOAD - Hii ndio inazuia kupotea =====
    private fun saveAll() {
        try {
            val prefs = getSharedPreferences("epro_config", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString("name", etConfigName.text.toString())
                putString("creator", etCreator.text.toString())
                putString("notes", etNotes.text.toString())
                putString("mode", selectedMode)
                putString("server", etServer.text.toString())
                putString("port", etPort.text.toString())
                putString("username", etUsername.text.toString())
                putString("password", etPassword.text.toString())
                putString("payload", etPayload.text.toString())
                putString("sni", etSni.text.toString())
                putString("remoteProxy", etRemoteProxy.text.toString())
                putString("remotePort", etRemotePort.text.toString())
                putString("uuid", etUuid.text.toString())
                putString("vlessLink", etVlessLink.text.toString())
                putString("ssMethod", etSsMethod.text.toString())
                apply()
            }
        } catch (e: Exception) {}
    }

    private fun loadAll() {
        try {
            val prefs = getSharedPreferences("epro_config", Context.MODE_PRIVATE)
            etConfigName.setText(prefs.getString("name", ""))
            etCreator.setText(prefs.getString("creator", ""))
            etNotes.setText(prefs.getString("notes", ""))
            etServer.setText(prefs.getString("server", "sg1.eprovpn.com"))
            etPort.setText(prefs.getString("port", "443"))
            etUsername.setText(prefs.getString("username", ""))
            etPassword.setText(prefs.getString("password", ""))
            etPayload.setText(prefs.getString("payload", "GET / HTTP/1.1[crlf]Host: [sni][crlf][crlf]"))
            etSni.setText(prefs.getString("sni", ""))
            etRemoteProxy.setText(prefs.getString("remoteProxy", ""))
            etRemotePort.setText(prefs.getString("remotePort", ""))
            etUuid.setText(prefs.getString("uuid", ""))
            etVlessLink.setText(prefs.getString("vlessLink", ""))
            etSsMethod.setText(prefs.getString("ssMethod", "aes-256-gcm"))
            val mode = prefs.getString("mode", "HTTP Proxy") ?: "HTTP Proxy"
            val idx = modes.indexOf(mode)
            if (idx >= 0) spinnerMode.setSelection(idx)
            setMode(mode)
        } catch (e: Exception) { setMode("HTTP Proxy") }
    }

    private fun setupAutoSave() {
        val fields = listOf(etConfigName, etCreator, etNotes, etServer, etPort, etUsername, etPassword, etPayload, etSni, etRemoteProxy, etRemotePort, etUuid, etVlessLink, etSsMethod)
        fields.forEach { it.addTextChangedListener { saveAll() } }
    }

    override fun onPause() { super.onPause(); saveAll() }

    private fun exportConfig() {
        val name = etConfigName.text.toString().ifEmpty { "epro-config" }
        exportLauncher.launch("$name.epro")
    }

    private fun writeExportFile(uri: Uri) {
        try {
            val json = JSONObject()
            json.put("app", "eProVPN"); json.put("version", 1)
            json.put("name", etConfigName.text.toString()); json.put("creator", etCreator.text.toString()); json.put("notes", etNotes.text.toString())
            json.put("mode", selectedMode); json.put("server", etServer.text.toString()); json.put("port", etPort.text.toString())
            json.put("username", etUsername.text.toString()); json.put("password", etPassword.text.toString())
            json.put("payload", etPayload.text.toString()); json.put("sni", etSni.text.toString())
            json.put("remoteProxy", etRemoteProxy.text.toString()); json.put("remotePort", etRemotePort.text.toString())
            json.put("uuid", etUuid.text.toString()); json.put("vlessLink", etVlessLink.text.toString()); json.put("ssMethod", etSsMethod.text.toString())
            contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
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
