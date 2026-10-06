package com.oflayn.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oflayn.domain.trips.LineInfo
import com.oflayn.domain.trips.StopRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Line search over the data bundled in the APK: instant, offline, no request. */
@Composable
fun LinesScreen(c: AppContainer, openLine: (String) -> Unit, openStop: (Int, String) -> Unit) {
    val st = c.settings
    var q by remember { mutableStateOf("") }
    var lines by remember { mutableStateOf<List<LineInfo>>(emptyList()) }
    var stops by remember { mutableStateOf<List<StopRef>>(emptyList()) }

    LaunchedEffect(q) {
        val query = q
        val r = withContext(Dispatchers.Default) {
            if (query.isBlank()) c.network.searchLines("", 60) to emptyList<StopRef>()
            else c.network.searchLines(query, 40) to c.network.searchStops(query, 20)
        }
        lines = r.first; stops = r.second
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(st.s("الخطوط والمحطات", "Hatlar ve duraklar", "Lines and stops"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Search, Brand.Orange, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    q, { q = it.take(40) }, Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    singleLine = true,
                    decorationBox = { inner -> if (q.isEmpty()) Text(st.s("ابحث عن خط (31A) أو محطة", "Hat (31A) veya durak ara", "Search a line (31A) or a stop"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) else inner() },
                )
                if (q.isNotEmpty()) Box(Modifier.clickable { q = "" }.padding(4.dp)) { Icon(Ic.Close, MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp) }
            }
            Text(
                st.s("${c.network.lineCount} خط · ${c.network.stopCount} محطة محفوظة داخل التطبيق", "${c.network.lineCount} hat · ${c.network.stopCount} durak uygulamada kayıtlı", "${c.network.lineCount} lines · ${c.network.stopCount} stops bundled in the app"),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (stops.isNotEmpty()) {
                item { Text(st.s("محطات", "Duraklar", "Stops"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)) }
                items(stops.take(12), key = { "s${it.id}" }) { s ->
                    val codes = c.network.linesAtStop(s.id).take(5).joinToString("، ") { it.code }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable { openStop(s.id, s.name) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Pin, Brand.Orange, size = 18.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (codes.isNotBlank()) Text(codes, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(Ic.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                    }
                }
            }
            item { Text(st.s("خطوط", "Hatlar", "Lines"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
            items(lines, key = { "l${it.routeId}" }) { l ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).clickable { openLine(l.code) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(l.code)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            st.s("${l.stopCount} محطة · ${c.tripsForLine(l.code).size} رحلة محفوظة اليوم", "${l.stopCount} durak · bugün ${c.tripsForLine(l.code).size} kayıtlı sefer", "${l.stopCount} stops · ${c.tripsForLine(l.code).size} trips stored today"),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        )
                    }
                    Icon(Ic.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                }
            }
        }
    }
}

/** One page for every line: directions, the stop timeline and today's stored trips. */
@Composable
fun LineDetailScreen(c: AppContainer, code: String, onBack: () -> Unit, openStop: (Int, String) -> Unit) {
    val st = c.settings
    val line = c.network.line(code)
    var dirIdx by remember { mutableStateOf(0) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(20_000) } }
    val trips = remember(now) {
        val t = c.engine.forLine(code)
        if (st.showEstimates) (t + c.engine.extendEstimates(now).filter { it.routeId == line?.routeId }) else t
    }.sortedBy { it.departMin }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { onBack() }, contentAlignment = Alignment.Center) { Icon(Ic.Back, MaterialTheme.colorScheme.onBackground, size = 22.dp) }
            Spacer(Modifier.width(6.dp))
            if (line != null) LineBadge(line.code, big = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(line?.title ?: code, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(st.s("${line?.stopCount ?: 0} محطة · ${trips.size} رحلة اليوم", "${line?.stopCount ?: 0} durak · bugün ${trips.size} sefer", "${line?.stopCount ?: 0} stops · ${trips.size} trips today"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (line == null) {
            Text(st.s("هذا الخط غير موجود في البيانات المحفوظة.", "Bu hat kayıtlı veride yok.", "This line is not in the stored data."), Modifier.padding(16.dp))
            return@Column
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            line.dirs.forEachIndexed { i, d ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (i == dirIdx) Brand.Orange else MaterialTheme.colorScheme.surface)
                        .clickable { dirIdx = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        (if (d.dir == "G") st.s("ذهاب", "Gidiş", "Outbound") else st.s("عودة", "Dönüş", "Return")) + " · ${d.stops.size}",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = if (i == dirIdx) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        val dir = line.dirs.getOrNull(dirIdx) ?: line.dirs.first()
        val nextByStop = trips.groupBy { it.stopId }.mapValues { it.value.first() }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)) {
            items(dir.stops) { s ->
                val t = nextByStop[s.id]
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { openStop(s.id, s.name) }.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(Brand.OrangeSoft), contentAlignment = Alignment.Center) {
                        Text("${s.seq}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand.Orange)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                    if (t != null) Text(t.hhmm, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Brand.Orange)
                }
            }
        }
        Text(
            st.s("كل الأوقات محفوظة على هاتفك وتعرض بدون إنترنت.", "Tüm saatler telefonunda kayıtlı, internetsiz gösterilir.", "All times are stored on your phone and shown offline."),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
        )
    }
}

/** Every line that passes this stop, plus the next stored trips from it. */
@Composable
fun StopScreen(c: AppContainer, stopId: Int, name: String, onBack: () -> Unit, openLine: (String) -> Unit) {
    val st = c.settings
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(20_000) } }
    val stop = c.network.stop(stopId)
    val lines = remember { c.network.linesAtStop(stopId) }
    val trips = remember(now) { c.engine.forStop(stopId) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable { onBack() }, contentAlignment = Alignment.Center) { Icon(Ic.Back, MaterialTheme.colorScheme.onBackground, size = 22.dp) }
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(stop?.name ?: name, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                Text(st.s("${lines.size} خط يمر من هنا", "${lines.size} hat buradan geçiyor", "${lines.size} lines pass here"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(st.s("رحلات قادمة محفوظة", "Kayıtlı yaklaşan seferler", "Stored upcoming trips"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp)) }
            if (trips.isEmpty()) item {
                Text(st.s("لا توجد رحلات محفوظة من هذه المحطة اليوم.", "Bugün bu duraktan kayıtlı sefer yok.", "No trips stored from this stop today."), Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(trips.take(40), key = { "${it.routeId}-${it.departMin}-${it.plate}" }) { t ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).clickable { openLine(t.lineCode) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(t.lineCode); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.lineTitle.ifBlank { t.lineCode }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        if (t.plate.isNotBlank()) Text(t.plate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(t.hhmm, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Brand.Orange)
                }
            }
            item { Text(st.s("كل الخطوط التي تخدم المحطة", "Bu durağın tüm hatları", "All lines serving this stop"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)) }
            items(lines, key = { "l${it.routeId}" }) { l ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface).clickable { openLine(l.code) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(l.code); Spacer(Modifier.width(10.dp))
                    Text(l.title, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1)
                    Icon(Ic.Chevron, MaterialTheme.colorScheme.onSurfaceVariant, size = 16.dp)
                }
            }
        }
    }
}
