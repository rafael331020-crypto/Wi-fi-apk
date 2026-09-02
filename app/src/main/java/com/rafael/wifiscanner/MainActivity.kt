package com.rafael.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var wifi: WifiManager
    private lateinit var resultsLayout: LinearLayout
    private lateinit var statusText: TextView
    private var allResults: List<ScanResult> = emptyList()
    private var selectedBand = "TODAS"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            allResults = wifi.scanResults.sortedByDescending { it.level }
            renderResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), RECEIVER_NOT_EXPORTED)
        buildUi()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val title = TextView(this).apply {
            text = "Wi-Fi Scanner — estudo"
            textSize = 26f
            setPadding(0, 0, 0, 8)
        }
        val subtitle = TextView(this).apply {
            text = "Análise passiva das redes Wi-Fi anunciadas ao redor"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }
        val scanButton = Button(this).apply {
            text = "🔎 VARRER REDES WI-FI"
            setOnClickListener { requestAndScan() }
        }
        statusText = TextView(this).apply {
            text = "Pronto para varrer."
            textSize = 14f
            setPadding(0, 8, 0, 12)
        }
        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("TODAS", "2.4 GHz", "5 GHz", "6 GHz").forEach { band ->
            val b = Button(this).apply {
                text = band
                textSize = 11f
                setOnClickListener {
                    selectedBand = band
                    renderResults()
                }
            }
            filterRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }

        root.addView(title)
        root.addView(subtitle)
        root.addView(scanButton)
        root.addView(statusText)
        root.addView(filterRow)
        val scrollParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0
        ).apply { weight = 1f }
        root.addView(scroll, scrollParams)
        setContentView(root)
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
        }
    }

    private fun requestAndScan() {
        val locationOk = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val nearbyOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            this, Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
        if (!locationOk || !nearbyOk) {
            requestNeededPermissions()
            return
        }
        statusText.text = "Varrendo redes..."
        @Suppress("DEPRECATION")
        val started = wifi.startScan()
        if (!started) {
            statusText.text = "O Android recusou a nova varredura. Tente novamente em alguns segundos."
        }
    }

    private fun renderResults() {
        val filtered = allResults.filter {
            selectedBand == "TODAS" || bandOf(it.frequency) == selectedBand
        }
        resultsLayout.removeAllViews()
        statusText.text = if (allResults.isEmpty()) {
            "Nenhuma rede encontrada."
        } else {
            "${filtered.size} rede(s) exibida(s) • ${allResults.size} encontrada(s) • ${now()}"
        }
        filtered.forEachIndexed { index, r ->
            resultsLayout.addView(networkView(r, index + 1))
        }
    }

    private fun networkView(r: ScanResult, position: Int): TextView {
        val ssid = r.SSID.ifBlank { "(rede oculta)" }
        val band = bandOf(r.frequency)
        val quality = signalQuality(r.level)
        val width = channelWidth(r)
        val standard = wifiStandard(r)
        val bssid = r.BSSID.ifBlank { "—" }
        val security = securityType(r)
        val text = buildString {
            append("#$position  $ssid\n")
            append("────────────────────────\n")
            append("🔐 Segurança: $security\n")
            append("📶 Sinal: ${r.level} dBm ($quality%) — ${signalLabel(r.level)}\n")
            append("📻 Frequência: ${r.frequency} MHz ($band)\n")
            append("📡 Canal: ${channel(r.frequency)}\n")
            append("↔️ Largura do canal: $width\n")
            append("⚙️ Padrão Wi-Fi: $standard\n")
            append("🆔 BSSID: $bssid\n")
            append("🏷️ OUI: ${bssid.take(8).ifBlank { "—" }}\n")
            append("🕒 Leitura: ${now()}")
        }
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(18, 18, 18, 18)
            setOnClickListener { showDetails(r) }
        }
    }

    private fun showDetails(r: ScanResult) {
        val details = buildString {
            append("${r.SSID.ifBlank { "(rede oculta)" }}\n\n")
            append("BSSID: ${r.BSSID}\nSegurança: ${securityType(r)}\nSinal: ${r.level} dBm\n")
            append("Qualidade estimada: ${signalQuality(r.level)}%\nFrequência: ${r.frequency} MHz\n")
            append("Banda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\n")
            append("Largura do canal: ${channelWidth(r)}\nPadrão Wi-Fi: ${wifiStandard(r)}\n")
            append("Capabilities: ${r.capabilities.ifBlank { "—" }}\n\n")
            append("Os dados são obtidos apenas do anúncio público da rede pelo Android. O aplicativo não tenta conectar, descobrir senhas ou acessar a rede.")
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Detalhes da rede")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun securityType(r: ScanResult): String {
        val caps = r.capabilities.uppercase()
        return when {
            "SAE" in caps && ("RSN" in caps || "WPA" in caps) -> "WPA3/SAE (ou modo misto)"
            "OWE" in caps -> "OWE / Enhanced Open"
            "WPA3" in caps -> "WPA3"
            "RSN" in caps -> "WPA2/WPA"
            "WEP" in caps -> "WEP (legado)"
            "WPA" in caps -> "WPA"
            else -> "Aberta / sem autenticação anunciada"
        }
    }

    private fun bandOf(freq: Int): String = when {
        freq >= 5925 -> "6 GHz"
        freq >= 4900 -> "5 GHz"
        freq in 2400..2500 -> "2.4 GHz"
        else -> "Desconhecida"
    }

    private fun channel(freq: Int): String = when {
        freq in 2412..2484 -> (((freq - 2407) / 5).coerceAtLeast(1)).toString()
        freq in 5000..5895 -> ((freq - 5000) / 5).toString()
        freq in 5955..7115 -> ((freq - 5950) / 5).toString()
        else -> "—"
    }

    private fun signalQuality(dbm: Int): Int = ((dbm + 100) * 2).coerceIn(0, 100)

    private fun signalLabel(dbm: Int): String = when {
        dbm >= -50 -> "Excelente"
        dbm >= -60 -> "Muito bom"
        dbm >= -67 -> "Bom"
        dbm >= -75 -> "Regular"
        else -> "Fraco"
    }

    private fun channelWidth(r: ScanResult): String = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
        else -> "Não informado"
    }

    private fun wifiStandard(r: ScanResult): String = if (Build.VERSION.SDK_INT >= 30) {
        when (r.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Legado"
            ScanResult.WIFI_STANDARD_11N -> "802.11n (Wi-Fi 4)"
            ScanResult.WIFI_STANDARD_11AC -> "802.11ac (Wi-Fi 5)"
            ScanResult.WIFI_STANDARD_11AX -> "802.11ax (Wi-Fi 6/6E)"
            ScanResult.WIFI_STANDARD_11BE -> "802.11be (Wi-Fi 7)"
            else -> "Desconhecido"
        }
    } else {
        "Não disponível nesta versão do Android"
    }

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
