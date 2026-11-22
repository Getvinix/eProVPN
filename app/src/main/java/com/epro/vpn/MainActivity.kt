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

    private val
