package com.epro.vpn

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class PremiumActivity : AppCompatActivity() {
    private var selectedPlan = "1 Month - KSh 150"
    private var selectedPrice = 150
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_premium)
        findViewById<TextView>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<LinearLayout>(R.id.planWeek)?.setOnClickListener { selectPlan("1 Week - KSh 50", 50) }
        findViewById<LinearLayout>(R.id.planMonth)?.setOnClickListener { selectPlan("1 Month - KSh 150", 150) }
        findViewById<LinearLayout>(R.id.plan3Month)?.setOnClickListener { selectPlan("3 Months - KSh 350", 350) }
        findViewById<LinearLayout>(R.id.planYear)?.setOnClickListener { selectPlan("1 Year - KSh 1200", 1200) }

        findViewById<Button>(R.id.btnPay)?.setOnClickListener {
            val phone = findViewById<EditText>(R.id.etPhone).text.toString()
            if (phone.length < 10) {
                Toast.makeText(this, "Weka namba ya M-Pesa sahihi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "STK Push imetumwa kwa $phone - $selectedPlan. Thibitisha kwa PIN yako.", Toast.LENGTH_LONG).show()
            // Hapa baadaye tutaweka Daraja API ya kweli
        }
    }
    private fun selectPlan(name: String, price: Int) {
        selectedPlan = name; selectedPrice = price
        findViewById<TextView>(R.id.tvSelectedPlan)?.text = "Selected: $name"
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
    }
}
