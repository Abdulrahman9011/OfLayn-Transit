package com.oflayn.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oflayn.domain.trips.Board
import com.oflayn.domain.trips.FreshLevel
import com.oflayn.domain.trips.RefreshReport
import com.oflayn.domain.trips.StopRef
import com.oflayn.domain.trips.Trip
import com.oflayn.domain.trips.TripKind
import com.oflayn.domain.trips.distM
import com.oflayn.domain.trips.minuteOfDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * The first screen. It shows the whole day's bus trips straight from the phone's own storage —
 * no internet needed — with the freshness of the saved day, a refresh button, and the nearest stops.
 */
@Composable
fun TodayScreen(
    c: AppContainer,
    locationGranted: Boolean,
    onAskLocation: () -> Unit,
    openLine: (String) -> Unit,
    openStop: (Int, String) -> Unit,
) {
    val st = c.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<Board?>(null) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<RefreshReport?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var near by remember { mutableStateOf<List<Pair<StopRef, Double>>>(emptyList()) }
    var online by remember { mutableStateOf(c.isOnline()) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); online = c.isOnline(); delay(15_000) } }

    // Instant cache read, then a refresh only when the saved day is missing / not fresh / forced.
    LaunchedEffect(Unit) {
        board = withContext(Dispatchers.Default) { c.engine.board() }
        if (st.autoRefresh && c.isOnline()) {
            busy = true
            val r = withContext(Dispatchers.Default) { c.engine.open(onProgress = { a, b -> progress = a to b }) }
            board = r
            busy = false
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) {
            onAskLocation()
            scope.launch {
                val p = c.location()
                if (p != null) near = withContext(Dispatchers.Default) { c.network.near(p.first, p.second, 6) }
            }
        }
    }
    LaunchedEffect(locationGranted) {
        if (locationGranted) {
            val p = c.location()
            if (p != null) near = withContext(Dispatchers.Default) { c.network.near(p.first, p.second, 6) }
        }
    }

    val b = board
    val trips = remember(b, now, st.showEstimates) {
        val all = b?.trips.orEmpty()
        val base = if (st.showEstimates) all + c.engine.extendEstimates(now) else all
        base.filter { it.departMin >= minuteOfDay(now) - 2 }.sortedWith(compareBy({ it.departMin }, { it.lineCode })).take(300)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.s("رحلات اليوم", "Bugünün seferleri", "Today's trips"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(10.dp))
                    if (online) Pill(st.s("متصل", "Çevrimiçi", "Online"), Brand.Live)
                    else Pill(st.s("بدون إنترنت", "Çevrimdışı", "Offline"), Brand.Offline)
                }
                Spacer(Modifier.height(4.dp))
                val f = b?.freshness
                val freshTxt = when {
                    b == null -> st.s("جارٍ التحضير…", "Hazırlanıyor…", "Preparing…")
                    f == null || f.level == FreshLevel.EMPTY -> st.s("لا توجد رحلات محفوظة", "Kayıtlı sefer yok", "No stored trips")
                    f.level == FreshLevel.FRESH -> st.s("محفوظة على الهاتف · عمرها ${f.ageLabel}", "Telefonda kayıtlı · ${f.ageLabel}", "Saved on phone · ${f.ageLabel} old")
                    f.level == FreshLevel.STALE -> st.s("بيانات قديمة · ${f.ageLabel}", "Eski veri · ${f.ageLabel}", "Stale data · ${f.ageLabel}")
                    else -> st.s("بيانات منتهية · ${f.ageLabel}", "Süresi geçti · ${f.ageLabel}", "Expired · ${f.ageLabel}")
                }
                Text(freshTxt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b?.message?.isNotBlank() == true) Text(b.message, fontSize = 12.sp, color = Brand.Warn)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Brand.Orange).clickable(enabled = !busy && online) {
                            scope.launch {
                                busy = true
                                val r = withContext(Dispatchers.Default) { c.engine.open(force = true, onProgress = { a, bb -> progress = a to bb }) }
                                board = r
                                st.updateLastRefresh(System.currentTimeMillis())
                                busy = false
                            }
                        }.padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Ic.Refresh, androidx.compose.ui.graphics.Color.White, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (busy) st.s("يحدّث… ${progress.first}/${progress.second}", "Güncelleniyor…", "Refreshing…")
                                else st.s("تحديث رحلات اليوم", "Bugünü güncelle", "Refresh today"),
                                color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface)
                            .clickable { if (!locationGranted) perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(st.s("أقرب محطة", "En yakın durak", "Nearest stop"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
                if (!online) Text(
                    st.s("الرحلات المحفوظة تعمل بدون إنترنت. التحديث يحتاج اتصالاً.", "Kayıtlı seferler internet olmadan çalışır.", "Stored trips work without internet; refreshing needs a connection."),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (near.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(st.s("محطات قريبة منك", "Yakınındaki duraklar", "Stops near you"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    near.take(4).forEach { hit ->
                        val s = hit.first
                        val d = hit.second
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surface).clickable { openStop(s.id, s.name) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Ic.Pin, Brand.Orange, size = 18.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${d.toInt()} " + st.s("م", "m", "m") + " · " + c.network.linesAtStop(s.id).take(4).joinToString("، ") { it.code }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Icon(Ic.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                st.s("الرحلات القادمة (${trips.size})", "Yaklaşan seferler (${trips.size})", "Upcoming trips (${trips.size})"),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        items(trips, key = { "${it.routeId}-${it.dir}-${it.stopId}-${it.departMin}-${it.plate}" }) { t ->
            TripRow(c, t, now, openLine, openStop)
        }
        if (trips.isEmpty() && b != null) {
            item {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(st.s("لا توجد رحلات قادمة محفوظة الآن.", "Şu an kayıtlı yaklaşan sefer yok.", "No stored upcoming trips right now."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        st.s("شغّل التحديث مع إنترنت لحفظ رحلات اليوم كاملة على الهاتف.", "Bugünün tüm seferlerini telefona kaydetmek için internetle güncelle.", "Refresh with internet to save the whole day on the phone."),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TripRow(c: AppContainer, t: Trip, now: Long, openLine: (String) -> Unit, openStop: (Int, String) -> Unit) {
    val st = c.settings
    val left = (t.departMin - minuteOfDay(now)).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).clickable { openLine(t.lineCode) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineBadge(t.lineCode)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.lineTitle.ifBlank { t.lineCode }, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                titleTr(t.stopName) +
                    (if (t.plate.isNotBlank()) " · ${t.plate}" else ""),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val kind = when (t.kind) {
                    TripKind.OFFICIAL -> st.s("جدول رسمي", "Resmî tarife", "Official timetable")
                    TripKind.OBSERVED -> st.s("رحلة حقيقية اليوم", "Bugünün gerçek seferi", "Real trip today")
                    TripKind.ESTIMATED -> st.s("تقديري (من التواتر)", "Tahmini (sıklıktan)", "Estimated (from headway)")
                }
                val col = when (t.kind) {
                    TripKind.OFFICIAL -> Brand.Live
                    TripKind.OBSERVED -> Brand.Orange
                    TripKind.ESTIMATED -> Brand.Warn
                }
                Pill(kind, col)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(t.hhmm, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Brand.Orange)
            Text(
                if (left <= 0) st.s("الآن", "şimdi", "now") else "$left " + st.s("دقيقة", "dk", "min"),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
