package com.epro.vpn

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private val payloadPresets = listOf(
        "Custom - Andika mwenyewe",
        "GET Standard",
        "GET + Keep Alive",
        "CONNECT Standard",
        "POST Standard"
    )
    private val payloadValues = listOf(
        "",
        "GET / HTTP/1.1[crlf]Host: [sni][crlf][crlf]",
        "GET / HTTP/1.1[crlf]Host: [sni][crlf]Connection: keep-alive[crlf][crlf]",
        "CONNECT [host_port] HTTP/1.1[crlf]Host: [sni][crlf][crlf]",
        "POST / HTTP/1.1[crlf]Host: [sni][crlf]Content-Length: 0[crlf][crlf]"
    )
    private val ssMethods = listOf("aes-256-gcm","aes-128-gcm","chacha20-ietf-poly1305","aes-256-cfb","plain")

    // EXPORT - Chagua wapi kuhifadhi file
    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeExportFile(it) }
    }
    // IMPORT - Chagua file
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { readImportFile(it) }
    }

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

        // --- DROPDOWN: MODE ---
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { setMode(modes[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // --- DROPDOWN: PAYLOAD ---
        spinnerPayload.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payloadPresets)
        spinnerPayload.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos > 0) etPayload.setText(payloadValues[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        // --- DROPDOWN: SHADOWSOCKS METHOD ---
        spinnerSsMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ssMethods)
        spinnerSsMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { etSsMethod.setText(ssMethods[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnExport).setOnClickListener { exportConfig() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { importLauncher.launch("*/*") }

        btnConnect.setOnClickListener {
            if (!isConnected) startVpn() else stopVpn()
        }

        setMode("HTTP Proxy")
        log("eProVPN ready... Chagua Mode, Export au Import")
    }

    // ===== EXPORT =====
    private fun exportConfig() {
        val name = etConfigName.text.toString().ifEmpty { "epro-config" }
        exportLauncher.launch("$name.epro")
    }

    private fun writeExportFile(uri: Uri) {
        try {
            val json = JSONObject()
            json.put("app", "eProVPN")
            json.put("version", 1)
            json.put("name", etConfigName.text.toString())
            json.put("creator", etCreator.text.toString())
            json.put("notes", etNotes.text.toString())
            json.put("mode", selectedMode)
            json.put("server", etServer.text.toString())
            json.put("port", etPort.text.toString())
            json.put("username", etUsername.text.toString())
            json.put("password", etPassword.text.toString())
            json.put("payload", etPayload.text.toString())
            json.put("sni", etSni.text.toString())
            json.put("remoteProxy", etRemoteProxy.text.toString())
            json.put("remotePort", etRemotePort.text.toString())
            json.put("uuid", etUuid.text.toString())
            json.put("vlessLink", etVlessLink.text.toString())
            json.put("ssMethod", etSsMethod.text.toString())
            json.put("createdAt", System.currentTimeMillis().toString())

            contentResolver.openOutputStream(uri)?.use { it.write(json.toString(2).toByteArray()) }
            log("Exported: ${etConfigName.text}.epro")
            Toast.makeText(this, "Exported! Tuma file kwa mtu mwingine", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            log("Export failed: ${e.message}")
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== IMPORT =====
    private fun readImportFile(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
            val json = JSONObject(text)
            etConfigName.setText(json.optString("name"))
            etCreator.setText(json.optString("creator"))
            etNotes.setText(json.optString("notes"))
            etServer.setText(json.optString("server"))
            etPort.setText(json.optString("port"))
            etUsername.setText(json.optString("username"))
            etPassword.setText(json.optString("password"))
            etPayload.setText(json.optString("payload"))
            etSni.setText(json.optString("sni"))
            etRemoteProxy.setText(json.optString("remoteProxy"))
            etRemotePort.setText(json.optString("remotePort"))
            etUuid.setText(json.optString("uuid"))
            etVlessLink.setText(json.optString("vlessLink"))
            etSsMethod.setText(json.optString("ssMethod"))

            val mode = json.optString("mode", "HTTP Proxy")
            val idx = modes.indexOf(mode)
            if (idx >= 0) spinnerMode.setSelection(idx)
            setMode(mode)

            // Onyesha Notes za muundaji kwa alert
            val notes = json.optString("notes")
            val creator = json.optString("creator")
            if (notes.isNotEmpty()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Config kutoka kwa: $creator")
                    .setMessage("Notes:\n\n$notes")
                    .setPositiveButton("OK, Nimeelewa", null)
                    .show()
            }
            log("Imported: ${json.optString("name")} by $creator")
            Toast.makeText(this, "Imported!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            log("Import failed: ${e.message}")
            Toast.makeText(this, "File sio sahihi ya eProVPN", Toast.LENGTH_LONG).show()
        }
    }

    private fun setMode(mode: String) {
        selectedMode = mode
        layoutPayload.visibility = View.GONE
        layoutRemote.visibility = View.GONE
        layoutSni.visibility = View.GONE
        layoutUuid.visibility = View.GONE
        layoutVless.visibility = View.GONE
        layoutSsMethod.visibility = View.GONE
        layoutUserPass.visibility = View.VISIBLE
        when (mode) {
            "HTTP Proxy" -> { layoutPayload.visibility = View.VISIBLE; layoutRemote.visibility = View.VISIBLE }
            "SSH/TLS" -> { layoutSni.visibility = View.VISIBLE }
            "SSL/TLS Proxy" -> { layoutPayload.visibility = View.VISIBLE; layoutRemote.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE }
            "V2Ray/Xray" -> { layoutVless.visibility = View.VISIBLE; layoutUuid.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE; layoutUserPass.visibility = View.GONE }
            "Hysteria" -> { layoutUuid.visibility = View.VISIBLE; layoutSni.visibility = View.VISIBLE; etUuid.hint = "Auth Password" }
            "Psiphon" -> { }
            "Shadowsocks" -> { layoutSsMethod.visibility = View.VISIBLE; layoutUserPass.visibility = View.GONE }
        }
        log("Mode: $mode")
    }

    private fun log(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("\n[$time] $msg")
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) startActivityForResult(intent, 100) else onActivityResult(100, RESULT_OK, null)
    }

    private fun stopVpn() {
        val i = Intent(this, EproVpnService::class.java)
        i.action = "DISCONNECT"
        ContextCompat.startForegroundService(this, i)
        isConnected = false
        btnConnect.text = "CONNECT"
        log("Disconnected")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val i = Intent(this, EproVpnService::class.java)
            i.putExtra("host", etServer.text.toString())
            i.putExtra("port", etPort.text.toString())
            i.putExtra("username", etUsername.text.toString())
            i.putExtra("password", etPassword.text.toString())
            i.putExtra("payload", etPayload.text.toString())
            i.putExtra("sni", etSni.text.toString())
            i.putExtra("mode", selectedMode)
            i.putExtra("uuid", etUuid.text.toString())
            i.putExtra("vless", etVlessLink.text.toString())
            i.putExtra("ssMethod", etSsMethod.text.toString())
            i.putExtra("remoteProxy", etRemoteProxy.text.toString())
            i.putExtra("remotePort", etRemotePort.text.toString())
            ContextCompat.startForegroundService(this, i)
            isConnected = true
            btnConnect.text = "DISCONNECT"
            log("Connecting [$selectedMode] to ${etServer.text}...")
        } else if (requestCode == 100) {
            log("VPN permission denied")
        }
    }
}
