package com.rafael.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var wifi: WifiManager
    private lateinit var resultsText: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            showResults(wifi.scanResults.sortedByDescending { it.level })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val title = TextView(this).apply {
            text = "Wi-Fi Scanner — estudo"
            textSize = 24f
        }
        val scanButton = Button(this).apply {
            text = "🔎 Varrer redes Wi-Fi"
            setOnClickListener { requestAndScan() }
        }
        resultsText = TextView(this).apply {
            text = "Toque em Varrer para começar.\n\nO Android pode exigir que a Localização esteja ativada para disponibilizar resultados de Wi-Fi."
            textSize = 16f
        }
        layout.addView(title)
        layout.addView(scanButton)
        layout.addView(resultsText)
        setContentView(layout)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10)
        }
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), RECEIVER_NOT_EXPORTED)
    }

    private fun requestAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 10)
            return
        }
        resultsText.text = "Varrendo..."
        @Suppress("DEPRECATION")
        wifi.startScan()
    }

    private fun showResults(results: List<ScanResult>) {
        if (results.isEmpty()) {
            resultsText.text = "Nenhuma rede encontrada. Verifique Wi-Fi e Localização."
            return
        }
        resultsText.text = results.joinToString("\n\n") { r ->
            val ssid = r.SSID.ifBlank { "(rede oculta)" }
            val security = securityType(r)
            val band = if (r.frequency >= 5925) "6 GHz" else if (r.frequency >= 4900) "5 GHz" else "2.4 GHz"
            "$ssid\nSegurança: $security\nSinal: ${r.level} dBm\nFrequência: ${r.frequency} MHz ($band)\nCanal: ${channel(r.frequency)}"
        }
    }

    private fun securityType(r: ScanResult): String {
        val caps = r.capabilities.uppercase()
        return when {
            "SAE" in caps && ("WPA3" in caps || "WPA2" in caps || "WPA" in caps) -> "WPA3/SAE (ou modo misto)"
            "WPA3" in caps -> "WPA3"
            "WPA2" in caps || "RSN" in caps -> "WPA2/WPA"
            "WEP" in caps -> "WEP (legado)"
            "WPA" in caps -> "WPA"
            else -> "Aberta / sem autenticação anunciada"
        }
    }

    private fun channel(freq: Int): String {
        return when {
            freq in 2412..2484 -> (((freq - 2407) / 5).coerceAtLeast(1)).toString()
            freq in 5000..5895 -> ((freq - 5000) / 5).toString()
            freq in 5955..7115 -> ((freq - 5950) / 5).toString()
            else -> "—"
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
