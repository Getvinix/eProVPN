package com.example.eprovpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerMode: Spinner
    private lateinit var etConfigName: EditText
    private lateinit var etCreator: EditText
    private lateinit var etServer: EditText
    private lateinit var etPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var spinnerPayload: Spinner
    private lateinit var etPayload: EditText
    private lateinit var etSni: EditText
    private lateinit var etRemoteProxy: EditText
    private lateinit var etRemotePort: EditText
    private lateinit var etUuid: EditText
    private lateinit var etVlessLink: EditText
    private lateinit var spinnerSsMethod: Spinner
    private lateinit var etSsMethod: EditText
    private lateinit var etNotes: EditText
    private lateinit var btnExport: Button
    private lateinit var btnImport: Button
    private lateinit var btnConnect: Button
    private lateinit var tvLog: TextView

    private lateinit var layoutUserPass: LinearLayout
    private lateinit var layoutPayload: LinearLayout
    private lateinit var layoutSni: LinearLayout
    private lateinit var layoutRemote: LinearLayout
    private lateinit var layoutUuid: LinearLayout
    private lateinit var layoutVless: LinearLayout
    private lateinit var layoutSsMethod: LinearLayout

    private var isConnected = false
    private var exportedJson = ""

    private val modes = listOf("SSH + SSL", "SSH + WS", "SSL / TLS", "VLESS", "VMESS", "Shadowsocks", "Trojan", "HTTP Custom")
    private val payloads = listOf("Safaricom - Youtube", "Safaricom - WhatsApp", "Airtel - TikTok", "Halotel - Free", "Custom")
    private val payloadMap = mapOf(
        "Safaricom - Youtube" to "GET / HTTP/1.1[crlf]Host: www.youtube.com[crlf][crlf]",
        "Safaricom - WhatsApp" to "GET / HTTP/1.1[crlf]Host: web.whatsapp.com[crlf][crlf]",
        "Airtel - TikTok" to "GET / HTTP/1.1[crlf]Host: www.tiktok.com[crlf][crlf]",
        "Halotel - Free" to "GET / HTTP/1.1[crlf]Host: www.google.com[crlf][crlf]",
        "Custom" to ""
    )
    private val ssMethods = listOf("aes-256-gcm", "aes-128-gcm", "chacha20-ietf-poly1305", "aes-256-cfb")

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    val text = BufferedReader(InputStreamReader(contentResolver.openInputStream(uri))).readText()
                    loadFromJson(text)
                    log("✅ Config Imported!")
                } catch (e: Exception) { log("❌ Import failed: ${e.message}") }
            }
        }
    }

    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpn()
        else log("❌ VPN permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupSpinners()
        setupListeners()
        log("eProVPN ready. Chagua MODE kuanza.")
    }

    private fun bindViews() {
        spinnerMode = findViewById(R.id.spinnerMode)
        etConfigName = findViewById(R.id.etConfigName)
        etCreator = findViewById(R.id.etCreator)
        etServer = findViewById(R.id.etServer)
        etPort = findViewById(R.id.etPort)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        spinnerPayload = findViewById(R.id.spinnerPayload)
        etPayload = findViewById(R.id.etPayload)
        etSni = findViewById(R.id.etSni)
        etRemoteProxy = findViewById(R.id.etRemoteProxy)
        etRemotePort = findViewById(R.id.etRemotePort)
        etUuid = findViewById(R.id.etUuid)
        etVlessLink = findViewById(R.id.etVlessLink)
        spinnerSsMethod = findViewById(R.id.spinnerSsMethod)
        etSsMethod = findViewById(R.id.etSsMethod)
        etNotes = findViewById(R.id.etNotes)
        btnExport = findViewById(R.id.btnExport)
        btnImport = findViewById(R.id.btnImport)
        btnConnect = findViewById(R.id.btnConnect)
        tvLog = findViewById(R.id.tvLog)
        layoutUserPass = findViewById(R.id.layoutUserPass)
        layoutPayload = findViewById(R.id.layoutPayload)
        layoutSni = findViewById(R.id.layoutSni)
        layoutRemote = findViewById(R.id.layoutRemote)
        layoutUuid = findViewById(R.id.layoutUuid)
        layoutVless = findViewById(R.id.layoutVless)
        layoutSsMethod = findViewById(R.id.layoutSsMethod)
    }

    private fun setupSpinners() {
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modes)
        spinnerPayload.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, payloads)
        spinnerSsMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, ssMethods)

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { updateUiForMode(modes[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerPayload.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val preset = payloadMap[payloads[pos]] ?: ""
                if (preset.isNotEmpty()) etPayload.setText(preset)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        spinnerSsMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { etSsMethod.setText(ssMethods[pos]) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        updateUiForMode(modes[0])
    }

    private fun updateUiForMode(mode: String) {
        layoutUserPass.visibility = if (mode.contains("SSH") || mode.contains("HTTP")) View.VISIBLE else View.GONE
        layoutPayload.visibility = if (mode.contains("SSH") || mode.contains("HTTP")) View.VISIBLE else View.GONE
        layoutSni.visibility = if (mode.contains("SSL") || mode.contains("Trojan") || mode.contains("SSH")) View.VISIBLE else View.GONE
        layoutRemote.visibility = if (mode.contains("SSH") || mode.contains("HTTP")) View.VISIBLE else View.GONE
        layoutUuid.visibility = if (mode == "VLESS" || mode == "VMESS" || mode == "Trojan") View.VISIBLE else View.GONE
        layoutVless.visibility = if (mode == "VLESS" || mode == "VMESS") View.VISIBLE else View.GONE
        layoutSsMethod.visibility = if (mode == "Shadowsocks") View.VISIBLE else View.GONE
        log("Mode: $mode")
    }

    private fun setupListeners() {
        btnExport.setOnClickListener { exportConfig() }
        btnImport.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }
            importLauncher.launch(intent)
        }
        btnConnect.setOnClickListener {
            if (isConnected) disconnect() else connect()
        }
    }

    private fun buildJson(): JSONObject {
        return JSONObject().apply {
            put("configName", etConfigName.text.toString())
            put("creator", etCreator.text.toString())
            put("mode", spinnerMode.selectedItem.toString())
            put("server", etServer.text.toString())
            put("port", etPort.text.toString())
            put("username", etUsername.text.toString())
            put("password", etPassword.text.toString())
            put("payload", etPayload.text.toString())
            put("sni", etSni.text.toString())
            put("remoteProxy", etRemoteProxy.text.toString())
            put("remotePort", etRemotePort.text.toString())
            put("uuid", etUuid.text.toString())
            put("vlessLink", etVlessLink.text.toString())
            put("ssMethod", etSsMethod.text.toString())
            put("notes", etNotes.text.toString())
            put("app", "eProVPN")
        }
    }

    private fun loadFromJson(text: String) {
        val j = JSONObject(text)
        etConfigName.setText(j.optString("configName"))
        etCreator.setText(j.optString("creator"))
        val mode = j.optString("mode")
        val idx = modes.indexOf(mode); if (idx >= 0) spinnerMode.setSelection(idx)
        etServer.setText(j.optString("server"))
        etPort.setText(j.optString("port"))
        etUsername.setText(j.optString("username"))
        etPassword.setText(j.optString("password"))
        etPayload.setText(j.optString("payload"))
        etSni.setText(j.optString("sni"))
        etRemoteProxy.setText(j.optString("remoteProxy"))
        etRemotePort.setText(j.optString("remotePort"))
        etUuid.setText(j.optString("uuid"))
        etVlessLink.setText(j.optString("vlessLink"))
        etSsMethod.setText(j.optString("ssMethod"))
        etNotes.setText(j.optString("notes"))
    }

    private fun exportConfig() {
        try {
            exportedJson = buildJson().toString(2)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, exportedJson)
                putExtra(Intent.EXTRA_SUBJECT, "${etConfigName.text}.epro")
            }
            startActivity(Intent.createChooser(intent, "Export ePro Config"))
            log("✅ Exported: ${etConfigName.text}")
        } catch (e: Exception) { log("❌ Export failed: ${e.message}") }
    }

    private fun connect() {
        if (etServer.text.isNullOrBlank()) { Toast.makeText(this, "Weka Server Host", Toast.LENGTH_SHORT).show(); return }
        val intent = VpnService.prepare(this)
        if (intent != null) vpnLauncher.launch(intent) else startVpn()
    }

    private fun startVpn() {
        isConnected = true
        btnConnect.text = "DISCONNECT"
        log("🔌 Connecting to ${etServer.text}:${etPort.text} [${spinnerMode.selectedItem}]...")
        log("⏳ Kazi ya VPN Service bado inaendelea - hapa ndipo EproVpnService itakapoanza.")
        Toast.makeText(this, "Connected (Demo Mode) - VPN Service inakuja", Toast.LENGTH_LONG).show()
    }

    private fun disconnect() {
        isConnected = false
        btnConnect.text = "CONNECT"
        log("🔌 Disconnected")
    }

    private fun log(msg: String) {
        tvLog.append("\n$msg")
    }
}
