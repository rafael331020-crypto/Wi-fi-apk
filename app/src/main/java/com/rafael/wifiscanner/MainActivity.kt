package com.rafael.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var wifi: WifiManager
    private lateinit var cm: ConnectivityManager
    private lateinit var resultsLayout: LinearLayout
    private lateinit var statusText: TextView
    private var allResults = emptyList<ScanResult>()
    private var selectedBand = "TODAS"
    private var clientName = "Não informado"
    private var scopeText = "Rede autorizada"
    private var started = ""
    private var activeResult = "Ainda não executado"
    private var activeTarget = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            try {
                allResults = wifi.scanResults.sortedByDescending { it.level }
                if (started.isBlank()) started = nowDateTime()
                renderResults()
            } catch (_: Exception) {
                statusText.text = "Não foi possível ler os resultados da varredura."
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), RECEIVER_NOT_EXPORTED)
        buildUi()
        requestNeededPermissions()
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val title = TextView(this).apply { text = "Wi-Fi PenTest Audit — seguro"; textSize = 24f; setPadding(0, 0, 0, 6) }
        val sub = TextView(this).apply { text = "Suíte de auditoria Wi-Fi • diagnóstico, risco, correção e reteste"; textSize = 14f; setPadding(0, 0, 0, 10) }
        val data = Button(this).apply { text = "📋 DADOS DO TESTE"; setOnClickListener { editProjectInfo() } }
        val scan = Button(this).apply { text = "🔎 VARRER E AUDITAR"; setOnClickListener { requestAndScan() } }
        val active = Button(this).apply { text = "🧪 TESTE ATIVO SEGURO"; setOnClickListener { chooseActiveTarget() } }
        val report = Button(this).apply { text = "🛡️ GERAR RELATÓRIO"; setOnClickListener { showReport() } }
        statusText = TextView(this).apply {
            text = "Faça uma varredura. Toque em uma rede para ver evidências, score, diagnóstico e plano de correção."
            textSize = 14f; setPadding(0, 8, 0, 12)
        }
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("TODAS", "2.4 GHz", "5 GHz", "6 GHz").forEach { b ->
            filters.addView(Button(this).apply {
                text = b; textSize = 11f; setOnClickListener { selectedBand = b; renderResults() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        resultsLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }
        root.addView(title); root.addView(sub); root.addView(data); root.addView(scan); root.addView(active); root.addView(report)
        root.addView(statusText); root.addView(filters)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0).apply { weight = 1f })
        setContentView(root)
    }

    private fun editProjectInfo() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val c = EditText(this).apply { hint = "Cliente / projeto"; setText(if (clientName == "Não informado") "" else clientName) }
        val s = EditText(this).apply { hint = "Escopo autorizado"; setText(if (scopeText == "Rede autorizada") "" else scopeText) }
        box.addView(c); box.addView(s)
        android.app.AlertDialog.Builder(this).setTitle("Dados do teste")
            .setMessage("Não coloque senhas, chaves ou credenciais aqui.").setView(box)
            .setPositiveButton("Salvar") { _, _ ->
                clientName = c.text.toString().ifBlank { "Não informado" }
                scopeText = s.text.toString().ifBlank { "Rede autorizada" }
                statusText.text = "Projeto: $clientName • Escopo: $scopeText"
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun requestNeededPermissions() {
        val p = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = p.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 10)
    }

    private fun requestAndScan() {
        val loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val nearby = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        if (!loc || !nearby) { requestNeededPermissions(); return }
        try {
            if (started.isBlank()) started = nowDateTime()
            statusText.text = "Varrendo e classificando riscos..."
            @Suppress("DEPRECATION") val ok = wifi.startScan()
            if (!ok) statusText.text = "O Android recusou a varredura; tente novamente."
        } catch (_: SecurityException) { statusText.text = "Permissão de Wi-Fi/localização negada." }
          catch (_: Exception) { statusText.text = "Falha controlada na varredura." }
    }

    private fun renderResults() {
        val list = allResults.filter { selectedBand == "TODAS" || bandOf(it.frequency) == selectedBand }
        resultsLayout.removeAllViews()
        statusText.text = if (allResults.isEmpty()) "Nenhuma rede encontrada." else "${list.size} rede(s) exibida(s) • ${allResults.size} encontrada(s) • toque para auditoria detalhada"
        list.forEachIndexed { i, r -> resultsLayout.addView(networkView(r, i + 1)) }
    }

    private fun networkView(r: ScanResult, n: Int): TextView {
        val a = audit(r)
        return TextView(this).apply {
            text = "#${n}  ${r.SSID.ifBlank { "(rede oculta)" }}\n" +
                    "🛡️ ${a.level} • Score ${a.score}/100 • 🔐 ${securityType(r)}\n" +
                    "📶 ${r.level} dBm • ${bandOf(r.frequency)} • canal ${channel(r.frequency)} • ${channelWidth(r)}\n" +
                    "⚠️ ${a.alerts.size} achado(s) • 🛠️ ${a.recs.size} correção(ões)\n" +
                    "👉 Toque para evidências e plano de reteste"
            textSize = 15f; setPadding(18, 18, 18, 18); setOnClickListener { showDetails(r) }
        }
    }

    private data class Audit(
        val level: String,
        val score: Int,
        val alerts: List<String>,
        val recs: List<String>,
        val evidence: List<String>,
        val validation: List<String>
    )

    private fun audit(r: ScanResult): Audit {
        val c = r.capabilities.uppercase(Locale.ROOT)
        val alerts = mutableListOf<String>()
        val rec = mutableListOf<String>()
        val evidence = mutableListOf<String>()
        var score = 0

        evidence.add("Capacidades anunciadas pelo AP: ${r.capabilities.ifBlank { "não informadas" }}")
        evidence.add("Sinal observado: ${r.level} dBm; ${bandOf(r.frequency)}; canal ${channel(r.frequency)}; largura ${channelWidth(r)}")
        evidence.add("Padrão Wi-Fi: ${wifiStandard(r)}")

        if ("WEP" in c) { alerts.add("WEP legado detectado"); rec.add("Migrar para WPA3-SAE ou WPA2-AES/CCMP e remover WEP"); score += 55 }
        if ("TKIP" in c) { alerts.add("TKIP anunciado"); rec.add("Desabilitar TKIP e usar AES/CCMP"); score += 25 }
        if ("WPS" in c) { alerts.add("WPS anunciado"); rec.add("Desativar WPS quando não for necessário e revisar o método de provisionamento"); score += 15 }
        if (securityType(r).startsWith("Aberta")) { alerts.add("Rede aberta sem autenticação anunciada"); rec.add("Ativar WPA3-SAE ou WPA2-AES/CCMP; para convidados, usar rede isolada"); score += 60 }
        if ("WPA-" in c && !c.contains("RSN")) { alerts.add("WPA legado anunciado sem RSN"); rec.add("Atualizar segurança para WPA2/WPA3 e remover WPA legado"); score += 35 }
        if ("CCMP" !in c && "RSN" in c && "SAE" !in c) { alerts.add("Não foi possível confirmar CCMP a partir do anúncio"); rec.add("Confirmar no AP que WPA2 usa AES/CCMP e não TKIP"); score += 10 }
        if (r.SSID.isBlank()) { alerts.add("SSID oculto"); rec.add("Não tratar ocultação de SSID como controle de segurança; usar autenticação forte"); score += 3 }
        if (r.level < -80) evidence.add("Sinal fraco observado; resultado pode variar por posição e interferência")
        if (r.level > -45) evidence.add("Sinal muito forte no ponto de medição; repetir em outros pontos se cobertura for relevante")
        if (alerts.isEmpty()) {
            rec.add(if ("SAE" in c) "Manter WPA3-SAE, firmware atualizado e senha forte" else "Preferir WPA3-SAE ou WPA2-AES/CCMP e manter firmware atualizado")
        }
        val bounded = score.coerceAtMost(100)
        val level = when { bounded >= 60 -> "ALTO"; bounded >= 20 -> "MÉDIO"; else -> "BAIXO" }
        val validation = mutableListOf(
            "Aplicar a correção no ponto de acesso e salvar a configuração.",
            "Executar nova varredura e comparar segurança/score.",
            "Confirmar que WEP/TKIP/WPS ou rede aberta deixaram de ser anunciados, quando aplicável.",
            "Repetir o teste ativo somente em rede autorizada e previamente configurada no Android."
        )
        return Audit(level, bounded, alerts, rec, evidence, validation)
    }

    private fun chooseActiveTarget() {
        if (allResults.isEmpty()) { statusText.text = "Faça a varredura primeiro."; return }
        val names = allResults.map { it.SSID.ifBlank { "(rede oculta)" } }.distinct().toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Escolha a rede para o teste ativo seguro")
            .setItems(names) { _, which ->
                val target = names[which]
                val r = allResults.first { it.SSID.ifBlank { "(rede oculta)" } == target }
                activeTarget = target
                runSafeReconnect(r)
            }.setNegativeButton("Cancelar", null).show()
    }

    @Suppress("DEPRECATION")
    private fun runSafeReconnect(r: ScanResult) {
        activeResult = "TESTE NÃO CONCLUSIVO: tentativa normal de reconexão, sem solicitar, descobrir ou registrar senha."
        statusText.text = "Executando diagnóstico de conectividade para ${r.SSID.ifBlank { "(rede oculta)" }}..."
        try {
            val before = wifi.connectionInfo?.ssid?.trim('"') ?: ""
            val requested = wifi.reconnect()
            window.decorView.postDelayed({
                val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                val nowS = wifi.connectionInfo?.ssid?.trim('"') ?: ""
                val same = connected && nowS.isNotBlank() && nowS == r.SSID
                activeResult = when {
                    same && before == r.SSID -> "JÁ CONECTADO: o Android já estava associado à rede; não houve teste de credencial."
                    same -> "CONEXÃO CONFIRMADA: o Android estabeleceu conexão usando uma configuração/autorização já existente no sistema."
                    !requested -> "NÃO EXECUTADO: o Android não aceitou a solicitação de reconexão."
                    else -> "NÃO CONFIRMADO: o Android não estabeleceu conexão com ${r.SSID.ifBlank { "(rede oculta)" }} no período do teste. Isso não comprova vulnerabilidade de senha."
                }
                val internet = when { validated -> "Internet validada"; connected -> "Wi-Fi conectado, mas Internet não validada"; else -> "Sem conexão Wi-Fi confirmada" }
                statusText.text = "$activeResult\nDiagnóstico: $internet\nPróximo passo: aplique as correções da auditoria e repita o teste."
            }, 5000)
            if (!requested) activeResult = "NÃO EXECUTADO: Android não aceitou a solicitação de reconexão."
        } catch (_: SecurityException) { activeResult = "BLOQUEADO PELO ANDROID: sem permissão para solicitar reconexão." }
          catch (_: Exception) { activeResult = "ERRO CONTROLADO: não foi possível solicitar reconexão." }
    }

    @Suppress("DEPRECATION")
    private fun connectivityDiagnostic(r: ScanResult): String {
        val wifiEnabled = try { wifi.isWifiEnabled } catch (_: Exception) { false }
        val current = try { wifi.connectionInfo?.ssid?.trim('"') ?: "" } catch (_: Exception) { "" }
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return buildString {
            append("Wi-Fi do aparelho: ${if (wifiEnabled) "ATIVADO" else "DESATIVADO"}\n")
            append("Conexão Wi-Fi atual: ${if (connected) "CONECTADO" else "NÃO CONECTADO"}\n")
            append("SSID atual: ${if (current.isBlank()) "não identificado" else current}\n")
            append("Internet validada: ${if (validated) "SIM" else "NÃO CONFIRMADO"}\n")
            append("Alvo: ${r.SSID.ifBlank { "(rede oculta)" }}\n")
        }
    }

    private fun showDetails(r: ScanResult) {
        val a = audit(r)
        val diag = connectivityDiagnostic(r)
        val d = buildString {
            append("DIAGNÓSTICO DE SEGURANÇA\n=========================\n")
            append("Rede: ${r.SSID.ifBlank { "(rede oculta)" }}\n")
            append("Risco: ${a.level}\nScore: ${a.score}/100\nSegurança: ${securityType(r)}\n")
            append("Sinal: ${r.level} dBm\nBanda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\n")
            append("Largura: ${channelWidth(r)}\nPadrão: ${wifiStandard(r)}\n\n")
            append("EVIDÊNCIAS\n")
            a.evidence.forEach { append("• $it\n") }
            append("\nCONECTIVIDADE\n$diag\n")
            append("ACHADOS\n")
            if (a.alerts.isEmpty()) append("• Nenhum alerta passivo identificado pelos dados anunciados.\n") else a.alerts.forEach { append("• $it\n") }
            append("\nPLANO DE CORREÇÃO\n")
            a.recs.forEachIndexed { i, x -> append("${i + 1}. $x\n") }
            append("\nVALIDAÇÃO / RETESTE\n")
            a.validation.forEachIndexed { i, x -> append("${i + 1}. $x\n") }
            append("\nTESTE ATIVO: $activeResult\n")
            append("\nLIMITAÇÕES\nO diagnóstico passivo usa dados anunciados pelo ponto de acesso. Um teste ativo não confirmado não prova senha fraca, ausência de autenticação ou acesso indevido. O aplicativo não quebra, descobre, captura ou armazena senhas, chaves ou credenciais, não desautentica clientes e não cria pontos de acesso maliciosos.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Auditoria • ${r.SSID.ifBlank { "rede oculta" }}").setMessage(d).setPositiveButton("OK", null).show()
    }

    private fun showReport() {
        if (allResults.isEmpty()) { statusText.text = "Faça uma varredura primeiro."; return }
        val audits = allResults.map { audit(it) }
        val h = audits.count { it.level == "ALTO" }
        val m = audits.count { it.level == "MÉDIO" }
        val l = audits.count { it.level == "BAIXO" }
        val report = buildString {
            append("RELATÓRIO PROFISSIONAL — WI-FI SECURITY AUDIT\n=============================================\n\n")
            append("CLIENTE: $clientName\nESCOPO: $scopeText\nINÍCIO: ${started.ifBlank { nowDateTime() }}\nGERADO: ${nowDateTime()}\n\n")
            append("RESUMO EXECUTIVO\nRedes avaliadas: ${allResults.size}\nRisco alto: $h • Médio: $m • Baixo: $l\n\n")
            append("METODOLOGIA\nVarredura passiva dos anúncios Wi-Fi, classificação de segurança, evidências observáveis, diagnóstico de conectividade e recomendações de correção. O teste ativo usa somente reconexão normal disponibilizada pelo Android para configurações/autorização existentes.\n\n")
            allResults.forEachIndexed { i, r ->
                val a = audit(r)
                append("${i + 1}. ${r.SSID.ifBlank { "(rede oculta)" }}\n")
                append("   Risco: ${a.level} • Score: ${a.score}/100\n")
                append("   Segurança: ${securityType(r)} • ${bandOf(r.frequency)} • canal ${channel(r.frequency)} • ${r.level} dBm\n")
                if (a.alerts.isEmpty()) append("   Achados: nenhum alerta passivo\n") else a.alerts.forEach { append("   Achado: $it\n") }
                a.recs.forEach { append("   Correção: $it\n") }
                append("   Reteste: executar nova varredura e comparar o resultado.\n\n")
            }
            append("TESTE ATIVO MAIS RECENTE\nAlvo: ${activeTarget.ifBlank { "não selecionado" }}\nResultado: $activeResult\n\n")
            append("PLANO GERAL\n1. Priorizar riscos ALTO.\n2. Remover protocolos legados e habilitar WPA3-SAE ou WPA2-AES/CCMP.\n3. Desabilitar WPS quando não necessário.\n4. Atualizar firmware do AP e revisar administração remota.\n5. Reexecutar a auditoria após as alterações e documentar antes/depois.\n\n")
            append("STATUS: REAVALIAR APÓS CORREÇÕES\n\n")
            append("LIMITAÇÕES E SEGURANÇA\nNenhuma senha, chave ou credencial é solicitada, descoberta, exibida ou armazenada. Não são executados brute force, captura de handshake/PMKID, deauth, packet injection, Evil Twin, bypass ou exploração automatizada. Resultados devem ser usados somente em redes autorizadas.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Relatório profissional").setMessage(report).setPositiveButton("OK", null).show()
    }

    private fun securityType(r: ScanResult): String {
        val c = r.capabilities.uppercase(Locale.ROOT)
        return when {
            c.isBlank() -> "Aberta / não informada"
            "SAE" in c && "RSN" in c -> "WPA3 / WPA2"
            "SAE" in c -> "WPA3-SAE"
            "RSN" in c -> "WPA2"
            "WPA" in c && "WEP" !in c -> "WPA legado"
            "WEP" in c -> "WEP"
            else -> "Aberta / desconhecida"
        }
    }

    private fun bandOf(freq: Int): String = when (freq) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "Outra"
    }

    private fun channel(freq: Int): String {
        if (freq <= 0) return "?"
        return when {
            freq in 2412..2484 -> if (freq == 2484) "14" else ((freq - 2407) / 5).toString()
            freq in 5000..5900 -> ((freq - 5000) / 5).toString()
            freq in 5925..7125 -> ((freq - 5950) / 5).toString()
            else -> "?"
        }
    }

    private fun channelWidth(r: ScanResult): String {
        return if (Build.VERSION.SDK_INT >= 23) when (r.channelWidth) {
            ScanResult.CHANNEL_WIDTH_20MHZ -> "20 MHz"
            ScanResult.CHANNEL_WIDTH_40MHZ -> "40 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ -> "80 MHz"
            ScanResult.CHANNEL_WIDTH_160MHZ -> "160 MHz"
            ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> "80+80 MHz"
            else -> "não informado"
        } else "não informado"
    }

    private fun wifiStandard(r: ScanResult): String {
        if (Build.VERSION.SDK_INT >= 30) return when (r.wifiStandard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
            ScanResult.WIFI_STANDARD_11N -> "802.11n"
            ScanResult.WIFI_STANDARD_11AC -> "802.11ac"
            ScanResult.WIFI_STANDARD_11AX -> "802.11ax / Wi-Fi 6"
            ScanResult.WIFI_STANDARD_11AD -> "802.11ad"
            ScanResult.WIFI_STANDARD_11BE -> "802.11be / Wi-Fi 7"
            else -> "não informado"
        }
        return "não informado"
    }

    private fun nowDateTime(): String = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
}
