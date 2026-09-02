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
import android.widget.EditText
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
    private var clientName = "Não informado"
    private var scopeText = "Rede autorizada"
    private var assessmentStarted = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                allResults = wifi.scanResults.sortedByDescending { it.level }
                if (assessmentStarted.isBlank()) assessmentStarted = nowDateTime()
                renderResults()
            } catch (_: SecurityException) { statusText.text = "Permissão não disponível para ler a varredura." }
            catch (_: Exception) { statusText.text = "Não foi possível ler os resultados." }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), RECEIVER_NOT_EXPORTED)
        buildUi(); requestNeededPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val title = TextView(this).apply { text = "Wi-Fi PenTest Audit — autorizado"; textSize = 25f; setPadding(0, 0, 0, 6) }
        val subtitle = TextView(this).apply { text = "Avaliação defensiva, evidências não sensíveis e relatório profissional"; textSize = 14f; setPadding(0, 0, 0, 12) }
        val projectButton = Button(this).apply { text = "📋 DADOS DO TESTE"; setOnClickListener { editProjectInfo() } }
        val scanButton = Button(this).apply { text = "🔎 VARRER E AUDITAR"; setOnClickListener { requestAndScan() } }
        val reportButton = Button(this).apply { text = "🛡️ GERAR RELATÓRIO"; setOnClickListener { showProfessionalReport() } }
        statusText = TextView(this).apply { text = "Defina o cliente/escopo e faça uma varredura. Use somente em redes autorizadas."; textSize = 14f; setPadding(0, 8, 0, 12) }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("TODAS", "2.4 GHz", "5 GHz", "6 GHz").forEach { band ->
            val b = Button(this).apply { text = band; textSize = 11f; setOnClickListener { selectedBand = band; renderResults() } }
            filterRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }
        root.addView(title); root.addView(subtitle); root.addView(projectButton); root.addView(scanButton); root.addView(reportButton)
        root.addView(statusText); root.addView(filterRow)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f })
        setContentView(root)
    }

    private fun editProjectInfo() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 8, 32, 0) }
        val client = EditText(this).apply { hint = "Cliente / projeto"; setText(if (clientName == "Não informado") "" else clientName) }
        val scope = EditText(this).apply { hint = "Escopo autorizado (ex.: SSID/rede)"; setText(if (scopeText == "Rede autorizada") "" else scopeText) }
        box.addView(client); box.addView(scope)
        android.app.AlertDialog.Builder(this).setTitle("Dados do teste autorizado").setView(box)
            .setMessage("Não informe senhas ou credenciais neste formulário.")
            .setPositiveButton("Salvar") { _, _ -> clientName = client.text.toString().ifBlank { "Não informado" }; scopeText = scope.text.toString().ifBlank { "Rede autorizada" }; statusText.text = "Projeto: $clientName • Escopo: $scopeText" }
            .setNegativeButton("Cancelar", null).show()
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
        if (requestCode == 10 && grantResults.any { it != PackageManager.PERMISSION_GRANTED }) statusText.text = "Conceda as permissões de Wi-Fi/localização para auditar."
    }

    private fun requestAndScan() {
        val locationOk = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearbyOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        if (!locationOk || !nearbyOk) { statusText.text = "Solicitando permissões..."; requestNeededPermissions(); return }
        try {
            if (assessmentStarted.isBlank()) assessmentStarted = nowDateTime()
            statusText.text = "Varrendo e avaliando configurações anunciadas..."
            @Suppress("DEPRECATION") val started = wifi.startScan()
            if (!started) statusText.text = "O Android recusou a nova varredura. Tente novamente em alguns segundos."
        } catch (_: SecurityException) { statusText.text = "Permissão de Wi-Fi negada pelo Android. Verifique as permissões do app." }
        catch (_: Exception) { statusText.text = "Erro ao iniciar a varredura. Tente novamente." }
    }

    private fun renderResults() {
        val filtered = allResults.filter { selectedBand == "TODAS" || bandOf(it.frequency) == selectedBand }
        resultsLayout.removeAllViews()
        statusText.text = if (allResults.isEmpty()) "Nenhuma rede encontrada." else "${filtered.size} rede(s) exibida(s) • ${allResults.size} encontrada(s) • ${now()}"
        filtered.forEachIndexed { index, r -> resultsLayout.addView(networkView(r, index + 1)) }
    }

    private fun networkView(r: ScanResult, position: Int): TextView {
        val a = audit(r); val ssid = r.SSID.ifBlank { "(rede oculta)" }
        val text = buildString {
            append("#$position  $ssid\n────────────────────────\n")
            append("🛡️ Risco: ${a.level}\n🔐 Segurança: ${securityType(r)}\n⚠️ Alertas: ${a.alerts.size}\n")
            append("📶 Sinal: ${r.level} dBm (${signalQuality(r.level)}%)\n📻 ${r.frequency} MHz • ${bandOf(r.frequency)} • canal ${channel(r.frequency)}\n")
            append("↔️ Largura: ${channelWidth(r)} • ⚙️ ${wifiStandard(r)}\n🆔 BSSID: ${r.BSSID}\n🕒 ${now()}")
        }
        return TextView(this).apply { this.text = text; textSize = 15f; setPadding(18, 18, 18, 18); setOnClickListener { showDetails(r) } }
    }

    private data class Audit(val level: String, val alerts: List<String>, val recommendations: List<String>)
    private fun audit(r: ScanResult): Audit {
        val caps = r.capabilities.uppercase(); val alerts = mutableListOf<String>(); val rec = mutableListOf<String>()
        if ("WEP" in caps) { alerts.add("WEP legado"); rec.add("Migrar para WPA3 ou WPA2-AES") }
        if ("TKIP" in caps) { alerts.add("TKIP anunciado"); rec.add("Preferir AES/CCMP") }
        if ("WPS" in caps) { alerts.add("WPS anunciado"); rec.add("Desativar WPS se não for necessário") }
        if (securityType(r).startsWith("Aberta")) { alerts.add("Rede aberta"); rec.add("Usar WPA2-AES ou WPA3") }
        if ("WPA-" in caps && !caps.contains("RSN")) { alerts.add("WPA legado"); rec.add("Atualizar para WPA2/WPA3") }
        if (alerts.isEmpty()) rec.add(if ("SAE" in caps) "Manter WPA3/SAE e firmware atualizado" else "Preferir WPA3/SAE ou WPA2-AES")
        val level = when { alerts.any { it.contains("WEP") || it.contains("Rede aberta") } -> "ALTO"; alerts.isNotEmpty() -> "MÉDIO"; else -> "BAIXO" }
        return Audit(level, alerts, rec)
    }

    private fun showDetails(r: ScanResult) {
        val a = audit(r)
        val details = buildString {
            append("${r.SSID.ifBlank { "(rede oculta)" }}\n\nNÍVEL DE RISCO: ${a.level}\n\n")
            append("Segurança: ${securityType(r)}\nCapabilities anunciadas: ${r.capabilities.ifBlank { "—" }}\n")
            append("Sinal: ${r.level} dBm (${signalQuality(r.level)}%)\nFrequência: ${r.frequency} MHz\nBanda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\n")
            append("Largura: ${channelWidth(r)}\nPadrão: ${wifiStandard(r)}\nBSSID: ${r.BSSID}\n\nALERTAS\n")
            if (a.alerts.isEmpty()) append("Nenhum alerta passivo identificado.\n") else a.alerts.forEach { append("• $it\n") }
            append("\nRECOMENDAÇÕES\n"); a.recommendations.forEach { append("• $it\n") }
            append("\nA auditoria usa informações anunciadas no scan. Não testa senhas, não coleta credenciais, não desautentica clientes e não explora dispositivos.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Auditoria defensiva").setMessage(details).setPositiveButton("OK", null).show()
    }

    private fun showProfessionalReport() {
        if (allResults.isEmpty()) { android.app.AlertDialog.Builder(this).setTitle("Relatório").setMessage("Faça uma varredura primeiro.").setPositiveButton("OK", null).show(); return }
        val audits = allResults.map { audit(it) }; val high = audits.count { it.level == "ALTO" }; val med = audits.count { it.level == "MÉDIO" }; val low = audits.count { it.level == "BAIXO" }
        val findings = audits.flatMapIndexed { i, a -> a.alerts.map { "${i + 1}. $it" } }.distinct()
        val report = buildString {
            append("RELATÓRIO DE PENTEST — WI-FI SECURITY AUDIT\n")
            append("================================================\n\n")
            append("CLIENTE/PROJETO: $clientName\nESCOPO: $scopeText\nINÍCIO: ${assessmentStarted.ifBlank { nowDateTime() }}\nGERADO: ${nowDateTime()}\n\n")
            append("DECLARAÇÃO DE ESCOPO\nO teste foi estruturado para avaliação defensiva de ambiente autorizado. Não foram realizadas tentativas de quebra de senha, coleta de credenciais, desautenticação ou exploração automática.\n\n")
            append("RESUMO EXECUTIVO\nRedes avaliadas: ${allResults.size}\nRisco alto: $high\nRisco médio: $med\nRisco baixo: $low\n\n")
            append("ACHADOS\n")
            if (findings.isEmpty()) append("Nenhum achado passivo relevante.\n") else findings.forEach { append("• $it\n") }
            append("\nDETALHAMENTO POR REDE\n")
            allResults.forEachIndexed { i, r ->
                val a = audit(r)
                append("\n[${i + 1}] ${r.SSID.ifBlank { "(rede oculta)" }}\n")
                append("Risco: ${a.level} | Segurança: ${securityType(r)} | Sinal: ${r.level} dBm\n")
                append("Banda: ${bandOf(r.frequency)} | Canal: ${channel(r.frequency)} | Largura: ${channelWidth(r)} | Padrão: ${wifiStandard(r)}\n")
                append("BSSID: ${r.BSSID}\n")
                if (a.alerts.isEmpty()) append("Achados: nenhum\n") else { append("Achados: ${a.alerts.joinToString(", ")}\n"); append("Correções: ${a.recommendations.joinToString("; ")}\n") }
            }
            append("\nPLANO DE CORREÇÃO\n1. Corrigir redes abertas e WEP prioritariamente.\n2. Revisar WPS e TKIP.\n3. Preferir WPA3/SAE ou WPA2-AES.\n4. Atualizar firmware do roteador e revisar configurações de administração.\n5. Reexecutar a auditoria e comparar os resultados.\n\nLIMITAÇÕES\nOs achados são baseados em informações disponíveis ao aplicativo e não representam confirmação de exploração de vulnerabilidades. Credenciais e segredos não são registrados pelo relatório.\n\nSTATUS: REAVALIAR APÓS CORREÇÕES")
        }
        android.app.AlertDialog.Builder(this).setTitle("Relatório profissional").setMessage(report).setPositiveButton("OK", null).show()
    }

    private fun securityType(r: ScanResult): String { val c = r.capabilities.uppercase(); return when { "SAE" in c && "RSN" in c -> "WPA3/SAE (ou modo misto)"; "OWE" in c -> "OWE / Enhanced Open"; "RSN" in c -> "WPA2/WPA3"; "WEP" in c -> "WEP (legado)"; "WPA" in c -> "WPA legado"; else -> "Aberta / sem autenticação anunciada" } }
    private fun bandOf(freq: Int): String = when { freq >= 5925 -> "6 GHz"; freq >= 4900 -> "5 GHz"; freq in 2400..2500 -> "2.4 GHz"; else -> "Desconhecida" }
    private fun channel(freq: Int): String = when { freq in 2412..2484 -> (((freq - 2407) / 5).coerceAtLeast(1)).toString(); freq in 5000..5895 -> ((freq - 5000) / 5).toString(); freq in 5955..7115 -> ((freq - 5950) / 5).toString(); else -> "—" }
    private fun signalQuality(dbm: Int): Int = ((dbm + 100) * 2).coerceIn(0, 100)
    private fun channelWidth(r: ScanResult): String = when (r.channelWidth) { ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"; ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"; ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"; ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"; ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"; else -> "Não informado" }
    private fun wifiStandard(r: ScanResult): String = if (Build.VERSION.SDK_INT >= 30) when (r.wifiStandard) { ScanResult.WIFI_STANDARD_LEGACY -> "Legado"; ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"; ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"; ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6/6E"; ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"; else -> "Desconhecido" } else "Não disponível"
    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    private fun nowDateTime(): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) { }; super.onDestroy() }
}
