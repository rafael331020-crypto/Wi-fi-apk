package com.rafael.wifiscanner

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        override fun onReceive(context: Context?, intent: Intent?) {
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
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
        val title = TextView(this).apply { text = "Wi-Fi PenTest Audit — seguro"; textSize = 24f; setPadding(0,0,0,6) }
        val sub = TextView(this).apply { text = "Auditoria ativa não destrutiva • diagnóstico e correção por rede"; textSize = 14f; setPadding(0,0,0,10) }
        val data = Button(this).apply { text = "📋 DADOS DO TESTE"; setOnClickListener { editProjectInfo() } }
        val scan = Button(this).apply { text = "🔎 VARRER E AUDITAR"; setOnClickListener { requestAndScan() } }
        val active = Button(this).apply { text = "🧪 TESTE ATIVO SEGURO"; setOnClickListener { chooseActiveTarget() } }
        val report = Button(this).apply { text = "🛡️ GERAR RELATÓRIO"; setOnClickListener { showReport() } }
        statusText = TextView(this).apply {
            text = "Faça uma varredura. Toque em uma rede para ver risco, diagnóstico, evidência e plano de correção."
            textSize = 14f; setPadding(0,8,0,12)
        }
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        listOf("TODAS","2.4 GHz","5 GHz","6 GHz").forEach { b ->
            filters.addView(Button(this).apply {
                text=b; textSize=11f; setOnClickListener { selectedBand=b; renderResults() }
            }, LinearLayout.LayoutParams(0,-2,1f))
        }
        resultsLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(resultsLayout) }
        root.addView(title); root.addView(sub); root.addView(data); root.addView(scan); root.addView(active); root.addView(report)
        root.addView(statusText); root.addView(filters)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0).apply { weight=1f })
        setContentView(root)
    }

    private fun editProjectInfo() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,0) }
        val c=EditText(this).apply { hint="Cliente / projeto"; setText(if(clientName=="Não informado") "" else clientName) }
        val s=EditText(this).apply { hint="Escopo autorizado"; setText(if(scopeText=="Rede autorizada") "" else scopeText) }
        box.addView(c); box.addView(s)
        android.app.AlertDialog.Builder(this).setTitle("Dados do teste")
            .setMessage("Não coloque senhas, chaves ou credenciais aqui.").setView(box)
            .setPositiveButton("Salvar") { _,_ ->
                clientName=c.text.toString().ifBlank{"Não informado"}
                scopeText=s.text.toString().ifBlank{"Rede autorizada"}
                statusText.text="Projeto: $clientName • Escopo: $scopeText"
            }.setNegativeButton("Cancelar",null).show()
    }

    private fun requestNeededPermissions() {
        val p=mutableListOf<String>()
        if(Build.VERSION.SDK_INT>=33) p.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing=p.filter { ContextCompat.checkSelfPermission(this,it)!=PackageManager.PERMISSION_GRANTED }
        if(missing.isNotEmpty()) ActivityCompat.requestPermissions(this,missing.toTypedArray(),10)
    }

    private fun requestAndScan() {
        val loc=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val nearby=Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.NEARBY_WIFI_DEVICES)==PackageManager.PERMISSION_GRANTED
        if(!loc||!nearby){requestNeededPermissions();return}
        try {
            if(started.isBlank()) started=nowDateTime()
            statusText.text="Varrendo e classificando riscos..."
            @Suppress("DEPRECATION") val ok=wifi.startScan()
            if(!ok) statusText.text="O Android recusou a varredura; tente novamente."
        } catch(_:SecurityException){statusText.text="Permissão de Wi-Fi/localização negada."}
          catch(_:Exception){statusText.text="Falha controlada na varredura."}
    }

    private fun renderResults() {
        val list=allResults.filter{selectedBand=="TODAS"||bandOf(it.frequency)==selectedBand}
        resultsLayout.removeAllViews()
        statusText.text=if(allResults.isEmpty())"Nenhuma rede encontrada." else "${list.size} rede(s) exibida(s) • ${allResults.size} encontrada(s) • toque em uma rede para diagnóstico"
        list.forEachIndexed{i,r->resultsLayout.addView(networkView(r,i+1))}
    }

    private fun networkView(r:ScanResult,n:Int):TextView {
        val a=audit(r)
        return TextView(this).apply {
            text="#${n}  ${r.SSID.ifBlank{"(rede oculta)"}}\n🛡️ Risco ${a.level} • 🔐 ${securityType(r)}\n📶 ${r.level} dBm • ${bandOf(r.frequency)} • canal ${channel(r.frequency)}\n⚠️ ${a.alerts.size} achado(s) • 🛠️ ${a.recs.size} recomendação(ões)\n👉 Toque para ver diagnóstico e plano de correção"
            textSize=15f; setPadding(18,18,18,18); setOnClickListener{showDetails(r)}
        }
    }

    private data class Audit(val level:String,val alerts:List<String>,val recs:List<String>)

    private fun audit(r:ScanResult):Audit {
        val c=r.capabilities.uppercase()
        val a=mutableListOf<String>(); val rec=mutableListOf<String>()
        if("WEP" in c){a.add("WEP legado detectado");rec.add("Migrar a WPA3-SAE ou WPA2-AES/CCMP e remover WEP")}
        if("TKIP" in c){a.add("TKIP anunciado");rec.add("Desabilitar TKIP e usar AES/CCMP")}
        if("WPS" in c){a.add("WPS anunciado");rec.add("Desativar WPS quando não for necessário")}
        if(securityType(r).startsWith("Aberta")){a.add("Rede aberta sem autenticação anunciada");rec.add("Ativar WPA3-SAE ou WPA2-AES/CCMP; para convidados, usar rede isolada")}
        if("WPA-" in c&&!c.contains("RSN")){a.add("WPA legado anunciado");rec.add("Atualizar segurança para WPA2/WPA3 e remover WPA legado")}
        if(rec.isEmpty()) rec.add(if("SAE" in c) "Manter WPA3/SAE, firmware atualizado e senha forte" else "Preferir WPA3/SAE ou WPA2-AES/CCMP e manter firmware atualizado")
        val l=when{
            a.any{it.contains("WEP")||it.contains("Rede aberta")}->"ALTO"
            a.isNotEmpty()->"MÉDIO"
            else->"BAIXO"
        }
        return Audit(l,a,rec)
    }

    private fun chooseActiveTarget() {
        if(allResults.isEmpty()){statusText.text="Faça a varredura primeiro.";return}
        val names=allResults.map{it.SSID.ifBlank{"(rede oculta)"}}.distinct().toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("Escolha a rede para o teste ativo")
            .setItems(names){_,which->
                val target=names[which]
                val r=allResults.first{it.SSID.ifBlank{"(rede oculta)"}==target}
                activeTarget=target; runSafeReconnect(r)
            }.setNegativeButton("Cancelar",null).show()
    }

    @Suppress("DEPRECATION")
    private fun runSafeReconnect(r:ScanResult) {
        activeResult="TESTE NÃO CONCLUSIVO: tentativa de reconexão iniciada sem solicitar, descobrir ou registrar senha."
        statusText.text="Executando diagnóstico de conectividade para ${r.SSID.ifBlank{"(rede oculta)"}}..."
        try {
            val before=wifi.connectionInfo?.ssid?.trim('"') ?: ""
            val requested=wifi.reconnect()
            window.decorView.postDelayed({
                val connected=cm.activeNetwork?.let{n->cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}==true
                val caps=cm.activeNetwork?.let{cm.getNetworkCapabilities(it)}
                val validated=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
                val nowS=wifi.connectionInfo?.ssid?.trim('"') ?: ""
                val same=connected && nowS.isNotBlank() && nowS==r.SSID
                activeResult=when {
                    same && before==r.SSID -> "JÁ CONECTADO: o Android já estava associado à rede; o teste de credencial não foi executado."
                    same -> "CONEXÃO CONFIRMADA: o Android estabeleceu conexão usando uma configuração já autorizada pelo sistema."
                    !requested -> "NÃO EXECUTADO: o Android não aceitou a solicitação de reconexão."
                    else -> "NÃO CONFIRMADO: o Android não estabeleceu conexão com ${r.SSID.ifBlank{"(rede oculta)"}} no período do teste. Isso não comprova vulnerabilidade."
                }
                val validation=if(validated) "Internet validada" else if(connected) "Wi-Fi conectado, mas validação de Internet não confirmada" else "Sem conexão Wi-Fi confirmada"
                statusText.text="$activeResult\nDiagnóstico: $validation\nPróximo passo: verifique a configuração salva no Android e reexecute após aplicar as correções recomendadas."
            },5000)
            if(!requested) activeResult="NÃO EXECUTADO: Android não aceitou a solicitação de reconexão."
        } catch(_:SecurityException){activeResult="BLOQUEADO PELO ANDROID: sem permissão para solicitar reconexão."}
          catch(_:Exception){activeResult="ERRO CONTROLADO: não foi possível solicitar reconexão."}
    }

    @Suppress("DEPRECATION")
    private fun connectivityDiagnostic(r:ScanResult):String {
        val wifiEnabled=try{wifi.isWifiEnabled}catch(_:Exception){false}
        val current=try{wifi.connectionInfo?.ssid?.trim('"') ?: ""}catch(_:Exception){""}
        val network=cm.activeNetwork
        val caps=network?.let{cm.getNetworkCapabilities(it)}
        val connected=caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true
        val validated=caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        return buildString {
            append("Wi-Fi do aparelho: ${if(wifiEnabled)"ATIVADO" else "DESATIVADO"}\n")
            append("Conexão Wi-Fi atual: ${if(connected)"CONECTADO" else "NÃO CONECTADO"}\n")
            append("SSID atual: ${if(current.isBlank())"não identificado" else current}\n")
            append("Rede validada para Internet: ${if(validated)"SIM" else "NÃO CONFIRMADO"}\n")
            append("Alvo do diagnóstico: ${r.SSID.ifBlank{"(rede oculta)"}}\n")
        }
    }

    private fun showDetails(r:ScanResult){
        val a=audit(r)
        val diag=connectivityDiagnostic(r)
        val d=buildString{
            append("DIAGNÓSTICO DE SEGURANÇA\n=========================\n")
            append("Rede: ${r.SSID.ifBlank{"(rede oculta)"}}\n")
            append("Risco: ${a.level}\nSegurança detectada: ${securityType(r)}\n")
            append("Sinal: ${r.level} dBm\nBanda: ${bandOf(r.frequency)}\nCanal: ${channel(r.frequency)}\n")
            append("Largura: ${channelWidth(r)}\nPadrão: ${wifiStandard(r)}\n\n")
            append("DIAGNÓSTICO DE CONECTIVIDADE\n$diag\n")
            append("ACHADOS\n")
            if(a.alerts.isEmpty()) append("• Nenhum alerta passivo identificado pelos dados anunciados.\n") else a.alerts.forEach{append("• $it\n")}
            append("\nPLANO DE CORREÇÃO\n")
            a.recs.forEachIndexed{i,x->append("${i+1}. $x\n")}
            append("\nVALIDAÇÃO PÓS-CORREÇÃO\n• Salvar a nova configuração no ponto de acesso.\n• Reexecutar a varredura.\n• Repetir o teste ativo somente em rede autorizada e já configurada no Android.\n• Comparar o risco antes/depois.\n")
            append("\nTESTE ATIVO: $activeResult\n\nIMPORTANTE\nUm teste ativo não confirmado não é evidência de senha fraca nem de acesso sem autenticação. O aplicativo não quebra, descobre, captura ou armazena senhas, chaves ou credenciais.")
        }
        android.app.AlertDialog.Builder(this).setTitle("Auditoria • ${r.SSID.ifBlank{"rede oculta"}}").setMessage(d).setPositiveButton("OK",null).show()
    }

    private fun showReport(){
        if(allResults.isEmpty()){statusText.text="Faça uma varredura primeiro.";return}
        val asx=allResults.map{audit(it)}
        val h=asx.count{it.level=="ALTO"}; val m=asx.count{it.level=="MÉDIO"}; val l=asx.count{it.level=="BAIXO"}
        val report=buildString{
            append("RELATÓRIO PROFISSIONAL — WI-FI SECURITY AUDIT\n=============================================\n\n")
            append("CLIENTE: $clientName\nESCOPO: $scopeText\nINÍCIO: ${started.ifBlank{nowDateTime()}}\nGERADO: ${nowDateTime()}\n\n")
            append("RESUMO EXECUTIVO\nRedes avaliadas: ${allResults.size}\nRisco alto: $h • Médio: $m • Baixo: $l\n")
            append("Objetivo: identificar configurações Wi-Fi anunciadas que merecem correção e fornecer orientação de remediação.\n\n")
            append("TESTE ATIVO\nAlvo: ${activeTarget.ifBlank{"Não selecionado"}}\nResultado: $activeResult\n\n")
            append("ACHADOS, DIAGNÓSTICO E CORREÇÕES POR REDE\n")
            allResults.forEachIndexed{i,r->
                val a=audit(r)
                append("\n[${i+1}] ${r.SSID.ifBlank{"(rede oculta)"}}\n")
                append("Risco: ${a.level}\nSegurança: ${securityType(r)}\nBanda/canal: ${bandOf(r.frequency)} / ${channel(r.frequency)}\n")
                if(a.alerts.isEmpty()) append("Achados: nenhum alerta passivo identificado\n") else append("Achados: ${a.alerts.joinToString("; ")}\n")
                append("Plano de correção:\n")
                a.recs.forEachIndexed{idx,rec->append("${idx+1}. $rec\n")}
                append("Validação: executar nova varredura após as alterações e comparar o risco.\n")
            }
            append("\nPLANO GERAL DE REMEDIAÇÃO\n1. Corrigir primeiro redes classificadas como ALTO.\n2. Priorizar WPA3-SAE; quando necessário, usar WPA2-AES/CCMP.\n3. Remover WEP, WPA legado e TKIP.\n4. Desativar WPS quando não for necessário.\n5. Atualizar firmware dos APs/roteadores.\n6. Usar senhas fortes e exclusivas, sem registrá-las neste aplicativo.\n7. Reexecutar a auditoria e documentar o antes/depois.\n\nMETODOLOGIA E LIMITAÇÕES\nA análise passiva usa informações anunciadas pelos pontos de acesso. O teste ativo utiliza somente APIs normais do Android para solicitar reconexão quando o sistema já possui uma configuração/autorização. O aplicativo não tenta descobrir ou quebrar senhas, capturar credenciais, realizar desautenticação, criar pontos de acesso maliciosos ou explorar redes. Um resultado não confirmado não deve ser tratado como vulnerabilidade comprovada.\n\nSTATUS: REAVALIAR APÓS CORREÇÕES")
        }
        android.app.AlertDialog.Builder(this).setTitle("Relatório profissional").setMessage(report).setPositiveButton("OK",null).show()
    }

    private fun securityType(r:ScanResult)=r.capabilities.uppercase().let{c->when{
        "SAE" in c&&"RSN" in c->"WPA3/SAE (ou misto)"
        "OWE" in c->"OWE / Enhanced Open"
        "RSN" in c->"WPA2/WPA3"
        "WEP" in c->"WEP (legado)"
        "WPA" in c->"WPA legado"
        else->"Aberta / sem autenticação anunciada"
    }}
    private fun bandOf(f:Int)=when{f>=5925->"6 GHz";f>=4900->"5 GHz";f in 2400..2500->"2.4 GHz";else->"Desconhecida"}
    private fun channel(f:Int)=when{f in 2412..2484->((f-2407)/5).coerceAtLeast(1).toString();f in 5000..5895->((f-5000)/5).toString();f in 5955..7115->((f-5950)/5).toString();else->"—"}
    private fun channelWidth(r:ScanResult)=when(r.channelWidth){
        ScanResult.CHANNEL_WIDTH_20MHZ->"20 MHz"; ScanResult.CHANNEL_WIDTH_40MHZ->"40 MHz"; ScanResult.CHANNEL_WIDTH_80MHZ->"80 MHz"; ScanResult.CHANNEL_WIDTH_160MHZ->"160 MHz"; ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ->"80+80 MHz"; else->"Não informado"
    }
    private fun wifiStandard(r:ScanResult)=if(Build.VERSION.SDK_INT>=30) when(r.wifiStandard){
        ScanResult.WIFI_STANDARD_11AX->"Wi-Fi 6"; ScanResult.WIFI_STANDARD_11AC->"Wi-Fi 5"; ScanResult.WIFI_STANDARD_11N->"Wi-Fi 4"; ScanResult.WIFI_STANDARD_11BE->"Wi-Fi 7"; else->"Outro/não informado"
    }else{"Não informado"}
    private fun nowDateTime()=SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.getDefault()).format(Date())
}
