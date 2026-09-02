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
            try {
                allResults = wifi.scanResults.sortedByDescending { it.level }
                renderResults()
            } catch (_: SecurityException) {
                statusText.text = "Permissão não disponível para ler a varredura."
            } catch (_: Exception) {
                statusText.text = "Não foi possível ler os resultados."
            }
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
            text = "Wi-Fi Security Audit — estudo"
            textSize = 25f
            setPadding(0, 0, 0, 6)
        }
        val subtitle = TextView(this).apply {
            text = "Auditoria defensiva e passiva das redes anunciadas"
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }
        val scanButton = Button(this).apply {
            text = "🔎 VARRER E AUDITAR"
            setOnClickListener { requestAndScan() }
        }
        val reportButton = Button(this).apply {
            text = "🛡️ RESUMO DE SEGURANÇA"
            setOnClickListener { showAuditSummary() }
        }
        statusText = TextView(this).apply {
            text = "Pronto. Use somente em redes que você possui ou está autorizado a avaliar."
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
                setOnClickListener { selectedBand = band; renderResults() }
            }
            filterRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }
        root.addView(title)
        root.addView(subtitle)
        root.addView(scanButton)
        root.addView(reportButton)
        root.addView(statusText)
        root.addView(filterRow)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        setContentView(root)
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
            statusText.text = "Conceda as permissões de Wi-Fi/localização para auditar."
        }
    }

    private fun requestAndScan() {
        val locationOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearbyOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        if (!locationOk || !nearbyOk) { statusText.text = "Solicitando permissões..."; requestNeededPermissions(); return }
        try {
            statusText.text = "Varrendo e avaliando configurações anunciadas..."
            @Suppress("DEPRECATION")
            val started = wifi.startScan()
            if (!started) statusText.text = "O Android recusou a nova varredura. Tente novamente em alguns segundos."
        } catch (_: SecurityException) {
            statusText.text = "Permissão de Wi-Fi negada pelo Android. Verifique as permissões do app."
        } catch (_: Exception) {
            statusText.text = "Erro ao iniciar a varredura. Tente novamente."
        }
    }

    private fun renderResults() {
        val filtered = allResults.filter { selectedBand == "TODAS" || bandOf(it.frequency) == selectedBand }
        resultsLayout.removeAllViews()
        statusText.text = if (allResults.isEmpty()) "Nenhuma rede encontrada." else "${filtered.size} rede(s) exibida(s) • ${allResults.size} encontrada(s) • ${now()}"
        filtered.forEachIndexed { index, r -> resultsLayout.addView(networkView(r, index + 1)) }
    }

    private fun networkView(r: ScanResult, position: Int): TextView {
        val audit = audit(r)
        val ssid = r.SSID.ifBlank { "(rede oculta)" }
        val text = buildString {
            append("#$position  $ssid\n")
            append("────────────────────────\n")
            append("🛡️ Risco: ${audit.level}\n")
            append("🔐 Segurança: ${securityType(r)}\n")
            append("⚠️ Alertas: ${audit.alerts.size}\n")
            append("📶 Sinal: ${r.level} dBm (${signalQuality(r.level)}%)\n")
            append("📻 ${r.frequency} MHz • ${bandOf(r.frequency)} • canal ${channel(r.frequency)}\n")
            append("↔️ Largura: ${channelWidth(r)} • ⚙️ ${wifiStandard(r)}\n")
            append("🆔 BSSID: ${r.BSSID}\n")
            append("🕒 ${now()}")
        }
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(18, 18, 18, 18)
            setOnClickListener { showDetails(r) }
        }
    }

    private data class Audit(val level: String, val alerts: List<String>, val recommendations: List<String>)

    private fun audit(r: ScanResult): Audit {
        val caps = r.capabilities.uppercase()
        val alerts = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        if ("WEP" in caps) { alerts.add("WEP legado"); recommendations.add("Migrar para WPA3 ou WPA2-AES") }
        if (caps.contains("TKIP")) { alerts.add("TKIP anunciado"); recommendations.add("Preferir AES/CCMP") }
        if ("WPS" in caps) { alerts.add("WPS anunciado"); recommendations.add("Desativar WPS se não for necessário") }
        if (securityType(r).startsWith("Aberta")) { alerts.add("Rede aberta"); recommendations.add("Usar WPA2-AES ou WPA3") }
        if ("WPA-" in caps && !caps.contains("RSN")) { alerts.add("WPA legado"); recommendations.add("Atualizar para WPA2/WPA3") }
        if (alerts.isEmpty()) {
            recommendations.add(if ("SAE" in caps) "Manter WPA3/SAE e firmware atualizado" else "Preferir WPA3/SAE ou WPA2-AES")
        }
        val level = when {
            alerts.any { it.contains("WEP") || it.contains("Rede aberta") } -> "ALTO"
            alerts.isNotEmpty() -> "MÉDIO"
            else -> "BAIXO"
        }
        return Audit(level, alerts, recommendations)
    }

    private fun showDetails(r: ScanResult) {
        val a = audit(r)
        val details = buildString {
            append("${r.SSID.ifBlank { "(rede oculta)" }}\n\n")
            append("NÍVEL DE RISCO: ${a.level}\n\n")
            append("Segurança: ${securityType(r)}\n")
            append("Capabilities anunciadas: ${r.capabilities.ifBlank { "—" }}\n")
            append("Sinal: ${r.level} dBm (${signalQuality(r.level)}%)\n")
            append("Frequência: ${r.frequency} MHz\nBanda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\n")
            append("Largura: ${channelWidth(r)}\nPadrão: ${wifiStandard(r)}\nBSSID: ${r.BSSID}\n\n")
            append("ALERTAS\n")
            if (a.alerts.isEmpty()) append("Nenhum alerta passivo identificado.\n") else a.alerts.forEach { append("• $it\n") }
            append("\nRECOMENDAÇÕES\n")
            a.recommendations.forEach { append("• $it\n") }
            append("\nLimitação: a auditoria usa somente informações públicas anunciadas no scan do Android. Ela não testa senhas, não tenta entrar na rede, não explora falhas e não confirma vulnerabilidades do roteador.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Auditoria defensiva").setMessage(details).setPositiveButton("OK", null).show()
    }

    private fun showAuditSummary() {
        if (allResults.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("Resumo de segurança").setMessage("Faça uma varredura primeiro.").setPositiveButton("OK", null).show(); return
        }
        val audits = allResults.map { audit(it) }
        val high = audits.count { it.level == "ALTO" }
        val medium = audits.count { it.level == "MÉDIO" }
        val low = audits.count { it.level == "BAIXO" }
        val text = buildString {
            append("Redes avaliadas: ${allResults.size}\n\n")
            append("🔴 Alto: $high\n🟠 Médio: $medium\n🟢 Baixo: $low\n\n")
            append("PRIORIDADES\n")
            if (high > 0) append("• Corrija primeiro redes abertas ou com WEP.\n")
            if (medium > 0) append("• Revise WPS, TKIP e protocolos legados.\n")
            if (high == 0 && medium == 0) append("• Nenhum alerta passivo relevante foi identificado.\n")
            append("\nPara sua própria rede, confirme as configurações diretamente no roteador e mantenha firmware atualizado.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Resumo de segurança").setMessage(text).setPositiveButton("OK", null).show()
    }

    private fun securityType(r: ScanResult): String {
        val caps = r.capabilities.uppercase()
        return when {
            "SAE" in caps && "RSN" in caps -> "WPA3/SAE (ou modo misto)"
            "OWE" in caps -> "OWE / Enhanced Open"
            "RSN" in caps -> "WPA2/WPA3"
            "WEP" in caps -> "WEP (legado)"
            "WPA" in caps -> "WPA legado"
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
    private fun channelWidth(r: ScanResult): String = when (r.channelWidth) {
        ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
        ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
        ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
        ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
        else -> "Não informado"
    }
    private fun wifiStandard(r: ScanResult): String = if (Build.VERSION.SDK_INT >= 30) when (r.wifiStandard) {
        ScanResult.WIFI_STANDARD_LEGACY -> "Legado"
        ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
        ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
        ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6/6E"
        ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
        else -> "Desconhecido"
    } else "Não disponível"
    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        super.onDestroy()
    }
}
