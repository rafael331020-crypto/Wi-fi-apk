package com.rafael.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
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
        override fun onReceive(context: Context?, intent: Intent?) {
            try { allResults = wifi.scanResults.sortedByDescending { it.level }; if (started.isBlank()) started = nowDateTime(); renderResults() }
            catch (_: Exception) { statusText.text = "Não foi possível ler os resultados da varredura." }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), RECEIVER_NOT_EXPORTED)
        buildUi(); requestNeededPermissions()
    }

    override fun onDestroy() { try { unregisterReceiver(receiver) } catch (_: Exception) {}; super.onDestroy() }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val title = TextView(this).apply { text = "Wi-Fi PenTest Audit — seguro"; textSize = 24f; setPadding(0,0,0,6) }
        val sub = TextView(this).apply { text = "Auditoria ativa não destrutiva • sem captura de credenciais"; textSize = 14f; setPadding(0,0,0,10) }
        val data = Button(this).apply { text = "📋 DADOS DO TESTE"; setOnClickListener { editProjectInfo() } }
        val scan = Button(this).apply { text = "🔎 VARRER E AUDITAR"; setOnClickListener { requestAndScan() } }
        val active = Button(this).apply { text = "🧪 TESTE ATIVO SEGURO"; setOnClickListener { chooseActiveTarget() } }
        val report = Button(this).apply { text = "🛡️ GERAR RELATÓRIO"; setOnClickListener { showReport() } }
        statusText = TextView(this).apply { text = "Faça uma varredura. O teste ativo só tenta reconectar usando uma configuração que o Android já possui; nenhuma senha é solicitada ou registrada."; textSize = 14f; setPadding(0,8,0,12) }
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("TODAS","2.4 GHz","5 GHz","6 GHz").forEach { b -> filters.addView(Button(this).apply { text=b; textSize=11f; setOnClickListener { selectedBand=b; renderResults() } }, LinearLayout.LayoutParams(0,-2,1f)) }
        resultsLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }
        root.addView(title); root.addView(sub); root.addView(data); root.addView(scan); root.addView(active); root.addView(report); root.addView(statusText); root.addView(filters); root.addView(scroll, LinearLayout.LayoutParams(-1,0).apply { weight=1f }); setContentView(root)
    }

    private fun editProjectInfo() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val c=EditText(this).apply { hint="Cliente / projeto"; setText(if(clientName=="Não informado") "" else clientName) }
        val s=EditText(this).apply { hint="Escopo autorizado"; setText(if(scopeText=="Rede autorizada") "" else scopeText) }
        box.addView(c); box.addView(s)
        android.app.AlertDialog.Builder(this).setTitle("Dados do teste").setMessage("Não coloque senhas, chaves ou credenciais aqui.").setView(box).setPositiveButton("Salvar") { _,_ -> clientName=c.text.toString().ifBlank{"Não informado"}; scopeText=s.text.toString().ifBlank{"Rede autorizada"}; statusText.text="Projeto: $clientName • Escopo: $scopeText" }.setNegativeButton("Cancelar",null).show()
    }

    private fun requestNeededPermissions() {
        val p=mutableListOf<String>(); if(Build.VERSION.SDK_INT>=33) p.add(Manifest.permission.NEARBY_WIFI_DEVICES); p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing=p.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }; if(missing.isNotEmpty()) ActivityCompat.requestPermissions(this,missing.toTypedArray(),10)
    }

    private fun requestAndScan() {
        val loc=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val nearby=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED
        if(!loc||!nearby){requestNeededPermissions();return}
        try { if(started.isBlank()) started=nowDateTime(); statusText.text="Varrendo..."; @Suppress("DEPRECATION") val ok=wifi.startScan(); if(!ok) statusText.text="O Android recusou a varredura; tente novamente." }
        catch(_:SecurityException){statusText.text="Permissão de Wi-Fi/localização negada."} catch(_:Exception){statusText.text="Falha na varredura."}
    }

    private fun renderResults() {
        val list=allResults.filter{selectedBand=="TODAS"||bandOf(it.frequency)==selectedBand}; resultsLayout.removeAllViews()
        statusText.text=if(allResults.isEmpty())"Nenhuma rede encontrada." else "${list.size} rede(s) exibida(s) • ${allResults.size} encontrada(s)"
        list.forEachIndexed{i,r->resultsLayout.addView(networkView(r,i+1))}
    }

    private fun networkView(r:ScanResult,n:Int):TextView { val a=audit(r); return TextView(this).apply { text="#${n}  ${r.SSID.ifBlank{"(rede oculta)"}}\n🛡️ ${a.level} • 🔐 ${securityType(r)}\n📶 ${r.level} dBm • ${bandOf(r.frequency)} • canal ${channel(r.frequency)}\n⚠️ ${a.alerts.size} alerta(s)"; textSize=15f; setPadding(18,18,18,18); setOnClickListener{showDetails(r)} } }

    private data class Audit(val level:String,val alerts:List<String>,val recs:List<String>)
    private fun audit(r:ScanResult):Audit { val c=r.capabilities.uppercase(); val a=mutableListOf<String>(); val rec=mutableListOf<String>(); if("WEP" in c){a.add("WEP legado");rec.add("Migrar para WPA3 ou WPA2-AES")}; if("TKIP" in c){a.add("TKIP anunciado");rec.add("Preferir AES/CCMP")}; if("WPS" in c){a.add("WPS anunciado");rec.add("Desativar WPS se não for necessário")}; if(securityType(r).startsWith("Aberta")){a.add("Rede aberta");rec.add("Ativar WPA3 ou WPA2-AES")}; if("WPA-" in c&&!c.contains("RSN")){a.add("WPA legado");rec.add("Atualizar para WPA2/WPA3")}; if(rec.isEmpty())rec.add(if("SAE" in c)"Manter WPA3/SAE e firmware atualizado" else "Preferir WPA3/SAE ou WPA2-AES"); val l=when{a.any{it.contains("WEP")||it.contains("Rede aberta")}->"ALTO";a.isNotEmpty()->"MÉDIO";else->"BAIXO"}; return Audit(l,a,rec) }

    private fun chooseActiveTarget() {
        if(allResults.isEmpty()){statusText.text="Faça a varredura primeiro.";return}
        val names=allResults.map{it.SSID.ifBlank{"(rede oculta)"}}.distinct().toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Escolha a rede para o teste ativo").setItems(names){_,which-> val target=names[which]; val r=allResults.first{it.SSID.ifBlank{"(rede oculta)"}==target}; activeTarget=target; runSafeReconnect(r) }.setNegativeButton("Cancelar",null).show()
    }

    @Suppress("DEPRECATION")
    private fun runSafeReconnect(r:ScanResult) {
        activeResult="Teste iniciado: tentativa de reconexão sem solicitar nem descobrir senha."; statusText.text="Testando reconexão autorizada para ${r.SSID.ifBlank{"(rede oculta)"}}..."
        try {
            val before=wifi.connectionInfo?.ssid?.trim('"') ?: ""
            val requested=wifi.reconnect()
            window.decorView.postDelayed({
                val connected=cm.activeNetwork?.let{n->cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}==true
                val nowS=wifi.connectionInfo?.ssid?.trim('"') ?: ""
                val same=connected && nowS.isNotBlank() && nowS==r.SSID
                activeResult=if(same)"SUCESSO: o Android reconectou à rede configurada; credencial não registrada." else "NÃO CONFIRMADO: o Android não estabeleceu a conexão durante o período do teste. Nenhuma credencial foi tentada ou coletada."
                if(before==r.SSID && same) activeResult="JÁ CONECTADO: a rede estava ativa; teste de pós-conexão disponível."
                statusText.text=activeResult
            },5000)
            if(!requested) activeResult="Android não aceitou a solicitação de reconexão."
        } catch(_:SecurityException){activeResult="BLOQUEADO PELO ANDROID: sem permissão para solicitar reconexão."} catch(_:Exception){activeResult="ERRO CONTROLADO: não foi possível solicitar reconexão."}
    }

    private fun showDetails(r:ScanResult){val a=audit(r); val d=buildString{append("${r.SSID.ifBlank{"(rede oculta)"}}\n\nRisco: ${a.level}\nSegurança: ${securityType(r)}\nSinal: ${r.level} dBm\nBanda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\nLargura: ${channelWidth(r)}\nPadrão: ${wifiStandard(r)}\nBSSID: ${r.BSSID}\n\nALERTAS\n"); if(a.alerts.isEmpty())append("Nenhum alerta passivo.\n")else a.alerts.forEach{append("• $it\n")};append("\nCORREÇÕES\n");a.recs.forEach{append("• $it\n")};append("\nTeste ativo: $activeResult\n\nNenhuma senha, chave ou credencial é exibida ou armazenada pelo aplicativo.")};android.app.AlertDialog.Builder(this).setTitle("Auditoria").setMessage(d).setPositiveButton("OK",null).show()}

    private fun showReport(){if(allResults.isEmpty()){statusText.text="Faça uma varredura primeiro.";return};val asx=allResults.map{audit(it)};val h=asx.count{it.level=="ALTO"};val m=asx.count{it.level=="MÉDIO"};val l=asx.count{it.level=="BAIXO"};val report=buildString{append("RELATÓRIO PROFISSIONAL — WI-FI SECURITY AUDIT\n=============================================\n\nCLIENTE: $clientName\nESCOPO: $scopeText\nINÍCIO: ${started.ifBlank{nowDateTime()}}\nGERADO: ${nowDateTime()}\n\nRESUMO\nRedes avaliadas: ${allResults.size}\nAlto: $h • Médio: $m • Baixo: $l\n\nTESTE ATIVO\nAlvo: ${activeTarget.ifBlank{"Não selecionado"}}\nResultado: $activeResult\n\nACHADOS E CORREÇÕES\n");allResults.forEachIndexed{i,r->val a=audit(r);append("\n[${i+1}] ${r.SSID.ifBlank{"(rede oculta)"}} — ${a.level}\nSegurança: ${securityType(r)}\n");if(a.alerts.isEmpty())append("Achados: nenhum\n")else{append("Achados: ${a.alerts.joinToString("; ")}\nCorreções: ${a.recs.joinToString("; ")}\n")}};append("\nMETODOLOGIA E LIMITAÇÕES\nA auditoria usa dados anunciados pelo Wi-Fi e uma tentativa de reconexão feita pelas APIs normais do Android quando o sistema já possui uma configuração/autorização. Não há quebra de senha, captura de credenciais, desautenticação, exploração automática ou armazenamento de segredos. Um teste não confirmado não deve ser tratado como vulnerabilidade comprovada.\n\nPLANO\n1. Corrigir achados de alto risco.\n2. Preferir WPA3/SAE ou WPA2-AES.\n3. Revisar WPS/TKIP e atualizar firmware.\n4. Reexecutar o teste e comparar antes/depois.\n\nSTATUS: REAVALIAR APÓS CORREÇÕES")};android.app.AlertDialog.Builder(this).setTitle("Relatório profissional").setMessage(report).setPositiveButton("OK",null).show()}

    private fun securityType(r:ScanResult)=r.capabilities.uppercase().let{c->when{ "SAE" in c&&"RSN" in c->"WPA3/SAE (ou misto)";"OWE" in c->"OWE / Enhanced Open";"RSN" in c->"WPA2/WPA3";"WEP" in c->"WEP (legado)";"WPA" in c->"WPA legado";else->"Aberta / sem autenticação anunciada"}}
    private fun bandOf(f:Int)=when{f>=5925->"6 GHz";f>=4900->"5 GHz";f in 2400..2500->"2.4 GHz";else->"Desconhecida"}
    private fun channel(f:Int)=when{f in 2412..2484->((f-2407)/5).coerceAtLeast(1).toString();f in 5000..5895->((f-5000)/5).toString();f in 5955..7115->((f-5950)/5).toString();else->"—"}
    private fun channelWidth(r:ScanResult)=when(r.channelWidth){ScanResult.CHANNEL_WIDTH_20MHZ->"20 MHz";ScanResult.CHANNEL_WIDTH_40MHZ->"40 MHz";ScanResult.CHANNEL_WIDTH_80MHZ->"80 MHz";ScanResult.CHANNEL_WIDTH_160MHZ->"160 MHz";ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ->"80+80 MHz";else->"Não informado"}
    private fun wifiStandard(r:ScanResult)=if(Build.VERSION.SDK_INT>=30) when(r.wifiStandard){ScanResult.WIFI_STANDARD_11AX->"Wi-Fi 6";ScanResult.WIFI_STANDARD_11AC->"Wi-Fi 5";ScanResult.WIFI_STANDARD_11N->"Wi-Fi 4";ScanResult.WIFI_STANDARD_11BE->"Wi-Fi 7";else->"Outro/não informado"}else{"Não informado"}
    private fun nowDateTime()=SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault()).format(Date())
}
