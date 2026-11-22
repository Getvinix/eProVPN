package com.epro.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.View
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
    private lateinit var etUuid: EditText
    private lateinit var etVlessLink: EditText
    private lateinit var etSsMethod: EditText
    private lateinit var tvLog: TextView
    private lateinit var btnConnect: Button
    private lateinit var layoutPayload: LinearLayout
    private lateinit var layoutRemote: LinearLayout
    private lateinit var layoutSni: LinearLayout
    private lateinit var layoutUuid: LinearLayout
    private lateinit var layoutVless: LinearLayout
    private lateinit var layoutSsMethod: LinearLayout
    private lateinit var layoutUserPass: LinearLayout

    private var isConnected = false
    private var selectedMode = "HTTP Proxy"

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
        tvLog = findViewById(R.id.tvLog)
        btnConnect = findViewById(R.id.btnConnect)
        layoutPayload = findViewById(R.id.layoutPayload)
        layoutRemote = findViewById(R.id.layoutRemote)
        layoutSni = findViewById(R.id.layoutSni)
        layoutUuid = findViewById(R.id.layoutUuid)
        layoutVless = findViewById(R.id.layoutVless)
        layoutSsMethod = findViewById(R.id.layoutSsMethod)
        layoutUserPass = findViewById(R.id.layoutUserPass)

        findViewById<Button>(R.id.btnHttpProxy).setOnClickListener { setMode("HTTP Proxy") }
        findViewById<Button>(R.id.btnSshTls).setOnClickListener { setMode("SSH/TLS") }
        findViewById<Button>(R.id.btnSslProxy).setOnClickListener { setMode("SSL/TLS Proxy") }
        findViewById<Button>(R.id.btnV2ray).setOnClickListener { setMode("V2Ray/Xray") }
        findViewById<Button>(R.id.btnHysteria).setOnClickListener { setMode("Hysteria") }
        findViewById<Button>(R.id.btnPsiphon).setOnClickListener { setMode("Psiphon") }
        findViewById<Button>(R.id.btnShadowsocks).setOnClickListener { setMode("Shadowsocks") }

        btnConnect.setOnClickListener {
            if (!isConnected) startVpn() else stopVpn()
        }

        setMode("HTTP Proxy")
        log("eProVPN ready...")
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
            "HTTP Proxy" -> {
                layoutPayload.visibility = View.VISIBLE
                layoutRemote.visibility = View.VISIBLE
                etPayload.setText("GET / HTTP/1.1[crlf]Host: [sni][crlf][crlf]")
            }
            "SSH/TLS" -> {
                layoutSni.visibility = View.VISIBLE
                etSni.setText("example.com")
            }
            "SSL/TLS Proxy" -> {
                layoutPayload.visibility = View.VISIBLE
                layoutRemote.visibility = View.VISIBLE
                layoutSni.visibility = View.VISIBLE
                etPayload.setText("GET / HTTP/1.1[crlf]Host: [sni][crlf]Connection: keep-alive[crlf][crlf]")
            }
            "V2Ray/Xray" -> {
                layoutVless.visibility = View.VISIBLE
                layoutUuid.visibility = View.VISIBLE
                layoutSni.visibility = View.VISIBLE
                layoutUserPass.visibility = View.GONE
            }
            "Hysteria" -> {
                layoutUuid.visibility = View.VISIBLE
                layoutSni.visibility = View.VISIBLE
                etUuid.hint = "Auth Password"
            }
            "Psiphon" -> { }
            "Shadowsocks" -> {
                layoutSsMethod.visibility = View.VISIBLE
                layoutUserPass.visibility = View.GONE
            }
        }
        log("Mode: $mode")
        Toast.makeText(this, mode, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
