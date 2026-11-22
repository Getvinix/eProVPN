package com.epro.vpn

import com.epro.vpn.R
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

    override fun onCreate
