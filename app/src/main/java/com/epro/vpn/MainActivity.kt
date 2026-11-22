package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etServer: EditText
    private lateinit var etPort: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etPayload: EditText
    private lateinit var etSni: EditText
    private lateinit var etRemoteProxy: EditText
    private lateinit var etRemotePort: EditText
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnPayload: Button
    private lateinit var btnTls: Button
    private lateinit var btnSlowDns: Button

    private var isConnected = false
    private var selectedMode = "Payload"
    private var selectedServer = "sg1.eprovpn.com"

    private val servers = listOf(
        Triple("Singapore", "sg1.eprovpn.com", "42ms • Low latency"),
        Triple("USA", "us2.eprovpn.com", "128ms • Medium"),
        Triple("Germany", "de3.eprovpn.com", "95ms • Low latency"),
        Triple("Kenya", "ke1.eprovpn.com", "67ms • Low latency"),
        Triple("UK", "uk1.eprovpn.com", "54ms • Low latency")
    )

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
        tvLog = findViewById(R.id.tvLog)
        btnConnect = findViewById(R.id.btnConnect)
        btnPayload = findViewById(R.id.btnPayload)
        btnTls = findViewById(R.id.btnTls)
        btnSlowDns = findViewById(R.id.btnSlowDns)

        // Server List clicks
        findViewById<LinearLayout>(R.id.serverSingapore)?.setOnClickListener { selectServer(0) }
        findViewById<LinearLayout>(R.id.serverUSA)?.setOnClickListener { selectServer(1) }
        findViewById<LinearLayout>(R.id.serverGermany)?.setOnClickListener { selectServer(2) }

        // Tabs
        btnPayload.setOnClickListener { setMode("Payload", btnPayload) }
        btnTls.setOnClickListener { setMode("Enhanced TLS", btnTls) }
        btnSlowDns.setOnClickListener { setMode("SlowDNS", btnSlowDns) }

        // Premium / Servers buttons
        findViewById<TextView>(R.id.btnServers)?.setOnClickListener {
            startActivity(Intent(this, ServersActivity::class.java))
        }
        findViewById<TextView>(R.id.btnPremium)?.setOnClickListener {
            startActivity(Intent(this, PremiumActivity::class.java))
        }

        btnConnect.setOnClickListener {
            if (!isConnected) startVpn() else stopVpn()
        }

        log("Initializing SSH client...")
        log("Payload mode active")
        log("Server selected: Singapore (sg1.eprovpn.com)")
        log("Ready to connect..")
    }

    private fun selectServer(index: Int) {
        val s = servers[index]
        selectedServer = s.second
        etServer.setText(s.second)
        log("Server selected: ${s.first} (${s.second})")
        Toast.makeText(this, "${s.first} selected", Toast.LENGTH_SHORT).show()
    }

    private fun setMode(mode: String, btn: Button) {
        selectedMode = mode
        // reset colors
        val inactive = ContextCompat.getColor(this, android.R.color.darker_gray)
        btnPayload.setBackgroundResource(R.drawable.chip_bg)
        btnTls.setBackgroundResource(R.drawable.chip_bg)
        btnSlowDns.setBackgroundResource(R.drawable.chip_bg)
        btn.setBackgroundResource(R.drawable.chip_bg_active)
        log("Method: $mode selected")
        if (mode == "Payload") etPayload.setText("GET / HTTP/1.1[crlf]Host: [sni][crlf][crlf]")
        if (mode == "Enhanced TLS") etPayload.setText("GET / HTTP/1.1[crlf]Host: [sni]")
        if (mode == "SlowDNS") etPayload.setText("DNS Tunnel")
    }

    private fun log(msg: String) {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        tvLog.append("\n[$time] $msg")
    }

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 100)
        } else {
            onActivityResult(100, RESULT_OK, null)
        }
    }

    private fun stopVpn() {
        startService(Intent(this, EproVpnService::class.java).setAction("DISCONNECT"))
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
            startService(i)
            isConnected = true
            btnConnect.text = "DISCONNECT"
            log("Connecting to $selectedServer...")
            log("Loading config: $selectedMode mode active")
        }
    }
}
