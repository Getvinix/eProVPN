package com.epro.vpn

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.TextView

class ServersActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_servers)

        findViewById<TextView>(R.id.btnBack)?.setOnClickListener { finish() }

        val servers = listOf(
            R.id.rowSingapore to "Singapore",
            R.id.rowUSA to "USA",
            R.id.rowGermany to "Germany",
            R.id.rowKenya to "Kenya",
            R.id.rowUK to "UK",
            R.id.rowNetherlands to "Netherlands"
        )
        for ((id, name) in servers) {
            findViewById<LinearLayout>(id)?.setOnClickListener {
                Toast.makeText(this, "$name selected - Go back and CONNECT", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
