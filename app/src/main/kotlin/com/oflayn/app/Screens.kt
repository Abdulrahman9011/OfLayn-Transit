package com.oflayn.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.oflayn.app.ai.GemmaUi
import com.oflayn.app.ai.Verdict
import com.oflayn.app.data.ImportResult
import com.oflayn.core.model.BursaCardType
import com.oflayn.core.model.Route
import com.oflayn.core.model.Stop
import com.oflayn.domain.ai.AiMessage
import com.oflayn.domain.ai.AiRequest
import com.oflayn.domain.ai.Lang
import com.oflayn.domain.ai.LanguageDetector
import com.oflayn.domain.ai.ToolResult
import com.oflayn.domain.ai.norm
import com.oflayn.domain.voice.VoiceAction
import com.oflayn.domain.voice.VoiceError
import com.oflayn.domain.voice.VoiceEvent
import com.oflayn.domain.voice.VoiceState
import com.oflayn.domain.voice.VoiceStateMachine
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Arabic/Turkish are written inline; German/Russian are looked up from [L10n] by the English text. */
fun Settings.s(ar: String, tr: String, en: String): String = when (language) {
    "ar" -> ar
    "tr" -> tr
    "de" -> L10n.de[en] ?: en
    "ru" -> L10n.ru[en] ?: en
    else -> en
}

private fun fmt(r: ToolResult) = r.data.removePrefix("NO_DATA: ") + (r.error?.let { "\n$it" } ?: "")

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun <T> Picker(label: String, items: List<T>, selected: T?, text: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${selected?.let(text) ?: "—"}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item -> DropdownMenuItem(text = { Text(text(item)) }, onClick = { onPick(item); open = false }) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onChange)
    }
}

/** Shows the installed dataset's provenance, or a download prompt when none is installed. */
@Composable
private fun DataCard(c: AppContainer, onChanged: () -> Unit = {}) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<com.oflayn.domain.transit.DatasetInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(busy) { info = c.repo.info() }
    GlassCard {
        Text(st.s("بيانات المحطات والخطوط", "Durak ve hat verisi", "Stops and routes data"), style = MaterialTheme.typography.titleMedium)
        val i = info
        if (i == null) {
            Text(st.s(
                "غير متوفرة. لم أجد GTFS رسميًا؛ يمكن تنزيل بيانات مشتقة من OpenStreetMap (DERIVED / UNOFFICIAL).",
                "Yok. Resmî GTFS bulunamadı; OpenStreetMap'ten türetilmiş veri indirilebilir (DERIVED / UNOFFICIAL).",
                "Not installed. No official GTFS was found; you can download data derived from OpenStreetMap (DERIVED / UNOFFICIAL).",
            ))
        } else {
            Text("${i.label} — ${i.source}")
            Text("${i.stopCount} ${st.s("محطة", "durak", "stops")} · ${i.routeCount} ${st.s("خط", "hat", "routes")}")
            Text("${i.freshness(System.currentTimeMillis())} · ${DateFormat.getDateInstance().format(Date(i.fetchedAtMs))}", style = MaterialTheme.typography.bodySmall)
            Text(i.attribution, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = {
            busy = true; msg = ""
            scope.launch {
                msg = when (val r = c.repo.importFromOverpass()) {
                    is ImportResult.Success -> st.s("تم التحديث", "Güncellendi", "Updated")
                    is ImportResult.Failed -> r.reason
                }
                busy = false; onChanged()
            }
        }, enabled = !busy && c.isOnline()) { Text(if (busy) st.s("جارٍ التنزيل…", "İndiriliyor…", "Downloading…") else if (i == null) st.s("تنزيل البيانات", "Veriyi indir", "Download data") else st.s("تحديث البيانات", "Veriyi güncelle", "Refresh data")) }
        if (!c.isOnline()) Text(st.s("يتطلب إنترنت", "İnternet gerekli", "Requires internet"), style = MaterialTheme.typography.bodySmall)
        if (msg.isNotBlank()) Text(msg)
    }
}

@Composable
fun HomeScreen(c: AppContainer, onNavigate: (AppTab) -> Unit) {
    val st = c.settings
    var reload by remember { mutableIntStateOf(0) }
    var alerts by remember { mutableStateOf("…") }
    LaunchedEffect(reload) { alerts = c.tools.call("getServiceAlerts").data }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(st.s("مواصلات OfLayn", "Oflayn Ulaşım", "Offline Transit"), style = MaterialTheme.typography.headlineSmall)
        key(reload) { DataCard(c) { reload++ } }
        GlassCard {
            Text(st.s("التتبع المباشر", "Canlı takip", "Live tracking"), style = MaterialTheme.typography.titleMedium)
            Text("LIVE VEHICLES = UNAVAILABLE")
            Text(st.s("لا يوجد مصدر موثّق للمركبات الحية.", "Doğrulanmış canlı araç kaynağı yok.", "No verified live-vehicle source."), style = MaterialTheme.typography.bodySmall)
        }
        GlassCard {
            Text(st.s("جدول الأجرة", "Ücret tarifesi", "Fare table"), style = MaterialTheme.typography.titleMedium)
            val m = c.fareManifest
            Text(if (m == null) "UNAVAILABLE" else "${m.manifestId}\n${m.source.verification}\n${m.validFrom}")
        }
        GlassCard {
            Text(st.s("التنبيهات", "Duyurular", "Alerts"), style = MaterialTheme.typography.titleMedium)
            Text(alerts.removePrefix("NO_DATA: "))
        }
        Row {
            Button(onClick = { onNavigate(AppAppTab.MAP) }, Modifier.padding(end = 8.dp)) { Text(st.s("الخريطة", "Harita", "Map")) }
            Button(onClick = { onNavigate(AppTab.TRANSIT) }) { Text(st.s("المواصلات", "Ulaşım", "Transit")) }
        }
    }
}

@Composable
private fun StopPicker(c: AppContainer, label: String, value: String, onValue: (String) -> Unit, onPick: (Stop) -> Unit) {
    val st = c.settings
    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    LaunchedEffect(Unit) { stops = c.repo.stops() }
    val n = norm(value)
    val matches = if (n.length < 2) emptyList() else stops.filter { norm(it.name).contains(n) }.take(5)
    OutlinedTextField(value, onValue, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
    matches.forEach { s -> TextButton(onClick = { onPick(s) }) { Text(s.name) } }
    if (n.length >= 2 && matches.isEmpty() && stops.isNotEmpty()) Text(st.s("لا نتائج", "Sonuç yok", "No matches"), style = MaterialTheme.typography.bodySmall)
}

private enum class TransitTab { STOPS, ROUTES, PLAN, FARE }

@Composable
fun TransitScreen(c: AppContainer, onNavigate: (AppTab) -> Unit) {
    val st = c.settings
    var tab by remember { mutableStateOf(TransitTab.STOPS) }
    var reload by remember { mutableIntStateOf(0) }
    var stops by remember { mutableStateOf<List<Stop>>(emptyList()) }
    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var planFrom by remember { mutableStateOf("") }
    LaunchedEffect(reload) { stops = c.repo.stops(); routes = c.repo.routes() }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        TabRow(selectedTabIndex = tab.ordinal) {
            TransitTab.entries.forEach { t ->
                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(when (t) {
                    TransitTab.STOPS -> st.s("المحطات", "Duraklar", "Stops")
                    TransitTab.ROUTES -> st.s("الخطوط", "Hatlar", "Routes")
                    TransitTab.PLAN -> st.s("رحلة", "Yolculuk", "Plan")
                    TransitTab.FARE -> st.s("الأجرة", "Ücret", "Fare")
                }, maxLines = 1) })
            }
        }
        if (stops.isEmpty() && tab != TransitTab.FARE) {
            Column(Modifier.verticalScroll(rememberScrollState())) { key(reload) { DataCard(c) { reload++ } } }
        } else when (tab) {
            TransitTab.STOPS -> StopsTab(c, stops, routes, onShowOnMap = { c.mapFocusStopId = it.id; c.mapFocusRouteId = null; onNavigate(AppTab.MAP) }, onPlanFrom = { planFrom = it.name; tab = TransitTab.PLAN })
            TransitTab.ROUTES -> RoutesTab(c, routes, stops, onShowOnMap = { c.mapFocusRouteId = it.id; c.mapFocusStopId = null; onNavigate(AppTab.MAP) })
            TransitTab.PLAN -> PlanTab(c, planFrom)
            TransitTab.FARE -> FareTab(c)
        }
    }
}

@Composable
private fun StopsTab(c: AppContainer, stops: List<Stop>, routes: List<Route>, onShowOnMap: (Stop) -> Unit, onPlanFrom: (Stop) -> Unit) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var favs by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) { favs = c.dao.favorites("stop").toSet() }
    val n = norm(q)
    val list = stops.filter { n.isBlank() || norm(it.name).contains(n) }.sortedByDescending { it.id in favs }.take(60)
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(st.s("بحث عن محطة", "Durak ara", "Search stops")) }, singleLine = true)
    LazyColumn(Modifier.weight(1f)) {
        items(list, key = { it.id }) { s ->
            GlassCard(Modifier.clickable { open = if (open == s.id) null else s.id }) {
                Text((if (s.id in favs) "★ " else "") + s.name, style = MaterialTheme.typography.titleSmall)
                if (open == s.id) {
                    Text("${"%.5f".format(s.lat)}, ${"%.5f".format(s.lon)}", style = MaterialTheme.typography.bodySmall)
                    val serving = routes.filter { s.id in it.stopIds }.joinToString(", ") { it.shortName }
                    Text(st.s("الخطوط: ", "Hatlar: ", "Routes: ") + serving.ifBlank { st.s("غير معروفة", "bilinmiyor", "none known") })
                    Row {
                        TextButton(onClick = { onShowOnMap(s) }) { Text(st.s("الخريطة", "Harita", "Map")) }
                        TextButton(onClick = { onPlanFrom(s) }) { Text(st.s("ابدأ من هنا", "Buradan başla", "Plan from here")) }
                        TextButton(onClick = { scope.launch {
                            if (s.id in favs) { c.dao.removeFavorite("stop", s.id); favs = favs - s.id } else { c.dao.addFavorite(com.oflayn.app.data.FavoriteEntity("stop", s.id)); favs = favs + s.id }
                        } }) { Text("★") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesTab(c: AppContainer, routes: List<Route>, stops: List<Stop>, onShowOnMap: (Route) -> Unit) {
    val st = c.settings
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val byId = remember(stops) { stops.associateBy { it.id } }
    val n = norm(q)
    val list = routes.filter { n.isBlank() || norm(it.shortName + " " + (it.longName ?: "")).contains(n) }.take(60)
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(st.s("بحث عن خط", "Hat ara", "Search routes")) }, singleLine = true)
    LazyColumn(Modifier.weight(1f)) {
        items(list, key = { it.id }) { r ->
            GlassCard(Modifier.clickable { open = if (open == r.id) null else r.id }) {
                Text("${r.shortName} · ${r.vehicleType}", style = MaterialTheme.typography.titleSmall)
                r.longName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (open == r.id) {
                    Text(r.stopIds.joinToString(" › ") { byId[it]?.name ?: it }, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onShowOnMap(r) }) { Text(st.s("عرض على الخريطة", "Haritada göster", "Show on map")) }
                }
            }
        }
    }
}

@Composable
private fun PlanTab(c: AppContainer, initialFrom: String) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    var fromText by remember { mutableStateOf(initialFrom) }
    var toText by remember { mutableStateOf("") }
    var fromId by remember { mutableStateOf<String?>(null) }
    var toId by remember { mutableStateOf<String?>(null) }
    var useMe by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { useMe = true; fromText = st.s("موقعي الحالي", "Mevcut konumum", "My current location") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        GlassCard {
            Text(st.s("مخطط الرحلة", "Yolculuk planlayıcı", "Trip planner"), style = MaterialTheme.typography.titleMedium)
            StopPicker(c, st.s("من", "Nereden", "From"), fromText, { fromText = it; fromId = null; useMe = false }) { fromText = it.name; fromId = it.id; useMe = false }
            TextButton(onClick = { perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(st.s("استخدم موقعي", "Konumumu kullan", "Use my location")) }
            StopPicker(c, st.s("إلى", "Nereye", "To"), toText, { toText = it; toId = null }) { toText = it.name; toId = it.id }
            Button(onClick = { scope.launch {
                busy = true
                val o = if (useMe) "current" else fromId
                val d = toId
                plan = if (o == null || d == null) st.s("اختر المحطات من القائمة", "Durakları listeden seçin", "Pick the stops from the suggestions")
                else {
                    val r = c.tools.call("planTrip", mapOf("origin" to o, "destination" to d))
                    if (r.ok && !r.data.startsWith("NO_DATA")) c.userData.saveTrip("$fromText → $toText")
                    fmt(r)
                }
                busy = false
            } }, enabled = !busy) { Text(st.s("خطّط", "Planla", "Plan")) }
            if (plan.isNotBlank()) Text(plan, Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun FareTab(c: AppContainer) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf(BursaCardType.entries.firstOrNull { it.name == st.cardType } ?: BursaCardType.UNKNOWN) }
    val tariffs = c.fareManifest?.tariffs.orEmpty()
    var tariff by remember { mutableStateOf<Int?>(null) }
    var fare by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
        GlassCard {
            Text(st.s("حاسبة الأجرة", "Ücret hesaplama", "Fare calculator"), style = MaterialTheme.typography.titleMedium)
            Picker(st.s("البطاقة", "Kart", "Card"), BursaCardType.entries, card, { it.name }) { card = it }
            Picker(st.s("التعرفة", "Tarife", "Tariff"), tariffs, tariffs.firstOrNull { it.tariffNo == tariff }, { "${it.tariffNo} · ${it.name.take(32)}" }) { tariff = it.tariffNo }
            Button(onClick = { scope.launch { fare = fmt(c.tools.call("calculateFare", mapOf("cardType" to card.name, "tariffNo" to tariff.toString()))) } }, enabled = tariff != null) { Text(st.s("احسب", "Hesapla", "Calculate")) }
            if (fare.isNotBlank()) Text(fare)
        }
    }
}

@Composable
fun CardScreen(c: AppContainer, nfc: NfcState) {
    val st = c.settings
    var type by remember { mutableStateOf(BursaCardType.entries.firstOrNull { it.name == st.cardType } ?: BursaCardType.UNKNOWN) }
    var balanceText by remember { mutableStateOf(if (st.manualBalanceKurus >= 0) com.oflayn.core.model.Kurus(st.manualBalanceKurus).toPlainString() else "") }
    var balanceErr by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        GlassCard {
            Text("BursaKart", style = MaterialTheme.typography.titleMedium)
            Text(when (nfc) {
                NfcState.UNSUPPORTED -> st.s("NFC غير مدعوم في الجهاز", "NFC desteklenmiyor", "NFC not supported")
                NfcState.DISABLED -> st.s("NFC معطّل — فعّله من إعدادات الجهاز", "NFC kapalı — cihaz ayarlarından açın", "NFC is off — enable it in system settings")
                NfcState.READY -> st.s("قرّب البطاقة من ظهر الهاتف", "Kartı telefonun arkasına yaklaştırın", "Hold the card to the back of the phone")
                NfcState.DETECTED -> st.s("تم اكتشاف بطاقة وربطها محليًا", "Kart algılandı ve yerel olarak bağlandı", "Card detected and linked locally")
            })
            if (st.linkedCardHash.isNotBlank()) Text("ID: …${st.linkedCardHash.takeLast(6)}", style = MaterialTheme.typography.bodySmall)
        }
        GlassCard {
            Text(st.s("نوع البطاقة (اختيارك)", "Kart türü (sizin seçiminiz)", "Card type (your selection)"), style = MaterialTheme.typography.titleMedium)
            Text(st.s("لا يمكن معرفة النوع من NFC، لذلك يُحدَّد يدويًا.", "Tür NFC'den anlaşılamaz, elle seçilir.", "The type cannot be derived from NFC, so you choose it."), style = MaterialTheme.typography.bodySmall)
            Picker(st.s("النوع", "Tür", "Type"), BursaCardType.entries, type, { it.name }) { type = it; st.setCardType(it.name) }
        }
        GlassCard {
            Text(st.s("الرصيد", "Bakiye", "Balance"), style = MaterialTheme.typography.titleMedium)
            Text("VERIFIED REAL BALANCE: UNAVAILABLE", style = MaterialTheme.typography.labelMedium)
            Text(st.s("لا يوجد API رسمي مدمج للرصيد.", "Entegre resmî bakiye API'si yok.", "No official balance API is integrated."), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(balanceText, { balanceText = it; balanceErr = false }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(st.s("رصيد أدخلته بنفسك (TL)", "Kendi girdiğiniz bakiye (TL)", "Balance you entered (TL)")) }, singleLine = true, isError = balanceErr)
            Row {
                Button(onClick = {
                    val k = runCatching { com.oflayn.core.model.Kurus.ofLira(balanceText.trim().replace(',', '.')).value }.getOrNull()
                    if (k == null || k < 0) balanceErr = true else st.setManualBalance(k)
                }) { Text(st.s("حفظ", "Kaydet", "Save")) }
                TextButton(onClick = { st.setManualBalance(null); balanceText = "" }) { Text(st.s("مسح", "Temizle", "Clear")) }
            }
            if (st.manualBalanceKurus >= 0) Text("MANUAL · ${com.oflayn.core.model.Kurus(st.manualBalanceKurus).toPlainString()} TL · ${DateFormat.getDateTimeInstance().format(Date(st.manualBalanceAtMs))}")
            Text(st.s("المعاملات: لا توجد (لا مصدر رسمي)", "İşlemler: yok (resmî kaynak yok)", "Transactions: none (no official source)"), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private class ChatItem(val fromUser: Boolean, val text: String, val tools: List<String> = emptyList(), val provider: String = "")

@Composable
fun AiScreen(c: AppContainer) {
    val st = c.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatItem>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var voiceState by remember { mutableStateOf(VoiceState.IDLE) }
    var voiceMsg by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val machine = remember { VoiceStateMachine() }
    machine.continuous = st.continuousVoice
    val voiceLocale = when (st.language) { "tr" -> "tr-TR"; "ar" -> "ar-SA"; "de" -> "de-DE"; "ru" -> "ru-RU"; else -> "en-US" }

    lateinit var voice: VoiceController
    fun send(text: String, viaVoice: Boolean) {
        if (text.isBlank() || busy) return
        messages += ChatItem(true, text)
        busy = true
        job = scope.launch {
            val history = messages.dropLast(1).takeLast(6).map { AiMessage(it.fromUser, it.text) }
            val res = try { c.router.generate(AiRequest(text, history)) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
            messages += ChatItem(false, res?.text ?: st.s("حدث خطأ.", "Bir hata oluştu.", "Something went wrong."), res?.toolsUsed.orEmpty(), res?.provider.orEmpty())
            busy = false
            if (viaVoice && res != null) {
                val tr = machine.on(VoiceEvent.ResponseReady)
                voiceState = machine.state
                if (tr.action == VoiceAction.SPEAK_RESPONSE) {
                    voice.speak(res.text, when (LanguageDetector.detect(res.text)) { Lang.TR -> "tr-TR"; Lang.AR -> "ar-SA"; Lang.EN -> if (st.language == "de") "de-DE" else if (st.language == "ru") "ru-RU" else "en-US" })
                }
            }
        }
    }
    fun applyAction(a: VoiceAction) {
        val offline = c.settings.offlineMode || !c.isOnline()
        when (a) {
            VoiceAction.START_LISTENING -> voice.startListening(voiceLocale, offline)
            VoiceAction.STOP_LISTENING -> voice.stopListening()
            VoiceAction.STOP_SPEAKING -> { voice.stopSpeaking(); voice.startListening(voiceLocale, offline) }
            else -> {}
        }
    }
    val deniedMsg = st.s("إذن الميكروفون مرفوض — الكتابة تعمل.", "Mikrofon izni reddedildi — yazı çalışır.", "Microphone permission denied — text still works.")
    voice = remember {
        VoiceController(ctx, object : VoiceController.Callbacks {
            override fun onFinalText(text: String) {
                val t = machine.on(VoiceEvent.SpeechResult(text)); voiceState = machine.state
                if (t.action == VoiceAction.SEND_TO_AI) send(text, true) else voiceMsg = ""
            }
            override fun onError(error: VoiceError) {
                machine.on(VoiceEvent.Failed(error)); voiceState = machine.state
                voiceMsg = when (error) {
                    VoiceError.PERMISSION_DENIED -> deniedMsg
                    VoiceError.NO_MICROPHONE -> st.s("خدمة التعرف على الصوت غير متوفرة.", "Konuşma tanıma yok.", "Speech recognition unavailable.")
                    VoiceError.UNCLEAR_SPEECH -> st.s("لم أفهم الكلام، حاول مجددًا.", "Anlaşılamadı, tekrar deneyin.", "Couldn't understand, try again.")
                    VoiceError.OFFLINE_STT_UNAVAILABLE -> st.s("التعرف على الصوت دون إنترنت غير مدعوم على هذا الجهاز/اللغة.", "Çevrimdışı konuşma tanıma bu cihazda/dilde yok.", "Offline speech recognition isn't available on this device/language.")
                    VoiceError.TTS_FAILURE -> st.s("تعذّر نطق الرد — النص ظاهر.", "Yanıt seslendirilemedi — metin görünür.", "Couldn't speak the reply — text is shown.")
                    VoiceError.NETWORK -> st.s("مشكلة في الشبكة.", "Ağ sorunu.", "Network problem.")
                    else -> st.s("خطأ في الصوت.", "Ses hatası.", "Voice error.")
                }
            }
            override fun onSpeakDone() {
                val t = machine.on(VoiceEvent.SpeakingDone); voiceState = machine.state
                applyAction(t.action)
            }
        })
    }
    // Leaving the screen frees the microphone, TTS and the Gemma model's RAM.
    DisposableEffect(Unit) { onDispose { voice.release(); job?.cancel(); kotlinx.coroutines.MainScope().launch { c.gemma.unload() } } }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { val t = machine.on(VoiceEvent.TapMic); voiceState = machine.state; applyAction(t.action) }
        else { machine.on(VoiceEvent.Failed(VoiceError.PERMISSION_DENIED)); voiceState = machine.state; voiceMsg = deniedMsg }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val quick = listOf(
        st.s("📍 أقرب محطة", "📍 En yakın durak", "📍 Nearest stop") to "nearest stop",
        st.s("💳 رصيد البطاقة", "💳 Kart bakiyesi", "💳 Card balance") to "balance",
        st.s("⚠️ التنبيهات", "⚠️ Duyurular", "⚠️ Alerts") to "alerts",
    )
    val llm = when {
        c.gemma.installed && st.gemmaBenchOk != false -> "Gemma E2B"
        st.proxyUrl.isNotBlank() -> st.modelId
        else -> "Local Data Assistant"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text((if (c.isOnline()) "● online" else "○ offline") + " · " + llm, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(vertical = 6.dp))
        LazyRow { items(quick) { (label, q) -> OutlinedButton(onClick = { send(q, false) }, Modifier.padding(end = 6.dp)) { Text(label) } } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
            items(messages) { m ->
                GlassCard {
                    Text(m.text)
                    if (!m.fromUser) {
                        if (m.tools.isNotEmpty() || m.provider.isNotBlank()) Text("🔧 ${m.tools.joinToString()} · ${m.provider}", style = MaterialTheme.typography.labelSmall)
                        Row {
                            TextButton(onClick = { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("reply", m.text)) }) { Text(st.s("نسخ", "Kopyala", "Copy")) }
                            TextButton(onClick = { val i = messages.indexOf(m); messages.getOrNull(i - 1)?.let { u -> if (u.fromUser) { messages.removeAt(i); messages.removeAt(i - 1); send(u.text, false) } } }) { Text(st.s("إعادة", "Tekrar", "Retry")) }
                        }
                    }
                }
            }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(" " + st.s("جارٍ التفكير…", "Düşünüyor…", "Thinking…"), Modifier.weight(1f))
            TextButton(onClick = { job?.cancel(); busy = false }) { Text(st.s("إيقاف", "Durdur", "Stop")) }
        }
        if (voiceMsg.isNotBlank()) Text(voiceMsg, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true)
            if (st.voiceAi) TextButton(onClick = { voiceMsg = ""; micPermission.launch(Manifest.permission.RECORD_AUDIO) }) {
                Text(when (voiceState) { VoiceState.LISTENING -> "🔴"; VoiceState.PROCESSING -> "⏳"; VoiceState.SPEAKING -> "🔊"; else -> "🎙️" })
            }
            Button(onClick = { val t = input; input = ""; send(t, false) }, enabled = input.isNotBlank() && !busy) { Text("➤") }
        }
    }
}

@Composable
private fun GemmaCard(c: AppContainer) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    val ui by c.gemma.ui.collectAsState()
    var report by remember { mutableStateOf(c.gemma.report()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var installed by remember { mutableStateOf(c.gemma.installed) }
    GlassCard {
        Text("Gemma 4 E2B (" + st.s("على الجهاز", "cihazda", "on device") + ")", style = MaterialTheme.typography.titleMedium)
        Text("RAM ${report.totalRamMb} MB (${report.availRamMb} free) · ${st.s("التخزين", "Depolama", "Storage")} ${report.freeStorageMb} MB · ${report.abis.firstOrNull()} · SDK ${report.sdk}", style = MaterialTheme.typography.bodySmall)
        Text("${st.s("الحكم", "Karar", "Verdict")}: ${report.verdict}" + report.reasons.joinToString("") { "\n• $it" })
        Text(st.s("E4B غير مفعّل (يتطلب Benchmark مثبتًا على الجهاز).", "E4B kapalı (cihazda kanıtlanmış benchmark gerekir).", "E4B is disabled (needs a proven on-device benchmark)."), style = MaterialTheme.typography.bodySmall)
        if (st.gemmaBenchSummary.isNotBlank()) Text("Benchmark: ${st.gemmaBenchSummary}", style = MaterialTheme.typography.bodySmall)
        when (val u = ui) {
            is GemmaUi.Downloading -> {
                val frac = if (u.total > 0) (u.bytes.toFloat() / u.total).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { frac }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                Text("${u.bytes / 1_000_000} / ${u.total / 1_000_000} MB")
                OutlinedButton(onClick = { job?.cancel() }) { Text(st.s("إلغاء", "İptal", "Cancel")) }
            }
            GemmaUi.Working -> Text(st.s("جارٍ التشغيل…", "Çalışıyor…", "Working…"))
            is GemmaUi.Error -> Text(u.message, color = MaterialTheme.colorScheme.error)
            GemmaUi.Idle -> {}
        }
        if (ui !is GemmaUi.Downloading) Row {
            if (!installed) Button(onClick = {
                report = c.gemma.report()
                job = scope.launch { c.gemma.download(); installed = c.gemma.installed; report = c.gemma.report() }
            }, enabled = report.verdict != Verdict.UNSUPPORTED && c.isOnline()) { Text(st.s("تنزيل (~2.6 GB)", "İndir (~2.6 GB)", "Download (~2.6 GB)")) }
            else {
                Button(onClick = { scope.launch { c.gemma.benchmark(); report = c.gemma.report() } }, enabled = ui != GemmaUi.Working, modifier = Modifier.padding(end = 8.dp)) { Text("Benchmark") }
                OutlinedButton(onClick = { scope.launch { c.gemma.delete(); installed = false; report = c.gemma.report() } }) { Text(st.s("حذف النموذج", "Modeli sil", "Delete model")) }
            }
        }
        if (installed) Text("${c.gemma.installedBytes / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SettingsScreen(c: AppContainer) {
    val st = c.settings
    var reload by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        GlassCard {
            Picker(st.s("اللغة", "Dil", "Language"), listOf("ar", "tr", "en", "de", "ru"), st.language, { it }) { st.setLanguage(it) }
            Picker(st.s("المظهر", "Tema", "Theme"), listOf("system", "light", "dark"), st.theme, { it }) { st.setTheme(it) }
        }
        GlassCard {
            SwitchRow(st.s("وضع بدون إنترنت", "Çevrimdışı mod", "Offline mode"), st.offlineMode, st::setOfflineMode)
            SwitchRow(st.s("مزامنة تلقائية", "Otomatik senkron", "Auto sync"), st.autoSync, st::setAutoSync)
            SwitchRow(st.s("بيانات الجوال", "Mobil veri", "Mobile data"), st.mobileData, st::setMobileData)
            SwitchRow(st.s("الإشعارات", "Bildirimler", "Notifications"), st.notifications, st::setNotifications)
            SwitchRow(st.s("صوت NFC", "NFC sesi", "NFC sound"), st.nfcSound, st::setNfcSound)
            SwitchRow(st.s("الاهتزاز", "Titreşim", "Vibration"), st.vibration, st::setVibration)
            SwitchRow(st.s("المساعد الصوتي", "Sesli asistan", "Voice AI"), st.voiceAi, st::setVoiceAi)
            SwitchRow(st.s("محادثة مستمرة", "Sürekli konuşma", "Continuous voice"), st.continuousVoice, st::setContinuousVoice)
            SwitchRow(st.s("صوت الملاحة", "Navigasyon sesi", "Navigation voice"), st.navVoice, st::setNavVoice)
        }
        key(reload) { DataCard(c) { reload++ } }
        GlassCard {
            Text(st.s("الخريطة والبيانات", "Harita ve veri", "Map and data"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(st.mapStyleUrl, { st.setMapStyleUrl(it) }, Modifier.fillMaxWidth(), label = { Text("Map style URL") }, singleLine = true)
            OutlinedTextField(st.overpassUrl, { st.setOverpassUrl(it) }, Modifier.fillMaxWidth(), label = { Text("Overpass URL") }, singleLine = true)
        }
        GlassCard {
            Text(st.s("الذكاء الاصطناعي الأونلاين", "Çevrimiçi yapay zekâ", "Online AI"), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(st.modelId, { st.setModelId(it) }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, singleLine = true)
            OutlinedTextField(st.proxyUrl, { st.setProxyUrl(it) }, Modifier.fillMaxWidth(), label = { Text("Backend proxy URL (https)") }, singleLine = true)
            Text(st.s("مفتاح Gemini يبقى على الخادم ولا يوضع في التطبيق.", "Gemini anahtarı sunucuda kalır, uygulamada değil.", "The Gemini key stays on your server, never in the app."), style = MaterialTheme.typography.bodySmall)
        }
        GemmaCard(c)
        GlassCard {
            Text(st.s("الخصوصية", "Gizlilik", "Privacy"), style = MaterialTheme.typography.titleMedium)
            Text(st.s(
                "يُخزَّن محليًا: الإعدادات، نوع البطاقة الذي اخترته، رصيد أدخلته يدويًا، بصمة SHA-256 لمعرّف البطاقة، المفضلة والرحلات الأخيرة. يُرسل نص أسئلتك فقط إلى خادمك عند تفعيل الذكاء الاصطناعي الأونلاين. الموقع يُطلب عند الحاجة فقط.",
                "Yerel olarak saklanır: ayarlar, seçtiğiniz kart türü, elle girdiğiniz bakiye, kart kimliğinin SHA-256 özeti, favoriler ve son yolculuklar. Çevrimiçi yapay zekâ açıksa yalnızca soru metniniz sunucunuza gider. Konum yalnızca gerektiğinde istenir.",
                "Stored locally: settings, your chosen card type, a balance you typed, a SHA-256 hash of the card ID, favorites and recent trips. Only your question text is sent to your server when online AI is enabled. Location is requested only when needed.",
            ), style = MaterialTheme.typography.bodySmall)
        }
        GlassCard { Text("OfLayn 0.1.0 — " + androidx.compose.ui.res.stringResource(R.string.independent_notice)) }
    }
}
