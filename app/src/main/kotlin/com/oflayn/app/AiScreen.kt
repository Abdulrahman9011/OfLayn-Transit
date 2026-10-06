package com.oflayn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Msg(val user: Boolean, val text: String)

/**
 * The assistant. It is always available: no key, no token, no server, no download.
 * Answers are produced on the phone from the bundled network + the trips stored for today.
 */
@Composable
fun AiScreen(c: AppContainer) {
    val st = c.settings
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun send(text: String) {
        val t = text.trim()
        if (t.isBlank() || busy) return
        messages += Msg(true, t)
        input = ""
        busy = true
        scope.launch {
            val a = withContext(Dispatchers.Default) { c.assistant.answer(t) }
            messages += Msg(false, a.text)
            busy = false
        }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Spark, Brand.Orange, size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("OfLayn AI", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    st.s("مساعد محلي يعمل على هاتفك — بدون مفتاح وبدون إنترنت", "Telefonunda çalışan yerel asistan — anahtarsız, internetsiz", "On-device assistant — no key, works offline"),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(st.s("محلي", "Yerel", "Local"), Brand.Live)
        }
        if (messages.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.Center) {
                Text(st.s("جرّب أحد هذه الأسئلة", "Şunlardan birini dene", "Try one of these"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                val samples = listOf(
                    st.s("موعد 31A القادم", "31A sonraki sefer", "Next 31A"),
                    st.s("محطات 31A", "31A durakları", "31A stops"),
                    st.s("كم عدد رحلات 99 اليوم", "Bugün 99 kaç sefer", "How many 99 trips today"),
                    st.s("شو الخطوط على محطة سيتيلر باركي", "SİTELER PARKI durağındaki hatlar", "Lines at SİTELER PARKI"),
                )
                samples.forEach { s ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface).clickable { send(s) }.padding(14.dp),
                    ) { Text(s, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    st.s("المساعد يقرأ فقط البيانات المحفوظة على هاتفك (الخطوط والمحطات ورحلات اليوم)، ولا يخترع أي رحلة أو وقت.",
                        "Asistan yalnızca telefonundaki kayıtlı veriyi okur, uydurmaz.",
                        "The assistant only reads data stored on your phone and never invents a trip or a time."),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(messages) { m ->
                    if (m.user) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(m.text, Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp)).background(Brand.Orange).padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 15.sp, lineHeight = 21.sp)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Box(Modifier.padding(top = 3.dp).size(26.dp).clip(CircleShape).background(Brand.OrangeSoft), contentAlignment = Alignment.Center) { Icon(Ic.Spark, Brand.Orange, size = 15.dp) }
                            Spacer(Modifier.width(10.dp))
                            Text(m.text, Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp), fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    input, { input = it.take(300) }, Modifier.weight(1f).padding(vertical = 12.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                    maxLines = 3,
                    decorationBox = { inner -> if (input.isEmpty()) Text(st.s("اسأل عن أي خط أو محطة…", "Herhangi bir hat veya durak sor…", "Ask about any line or stop…"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) else inner() },
                )
            }
            Spacer(Modifier.width(8.dp))
            val can = input.isNotBlank() && !busy
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (can) Brand.Orange else MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = can) { send(input) }, contentAlignment = Alignment.Center) {
                Icon(Ic.Send, if (can) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp)
            }
        }
    }
}
