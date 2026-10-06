package com.oflayn.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oflayn.domain.trips.FreshLevel

@Composable
fun MoreScreen(c: AppContainer) {
    val st = c.settings
    var online by remember { mutableStateOf(c.isOnline()) }
    var board by remember { mutableStateOf(c.engine.board()) }
    LaunchedEffect(Unit) { while (true) { online = c.isOnline(); kotlinx.coroutines.delay(10_000) } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(st.s("المزيد", "Daha fazla", "More"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.Bus, Brand.Orange, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.s("حالة البيانات", "Veri durumu", "Data status"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            st.s("${c.network.lineCount} خط · ${c.network.stopCount} محطة داخل التطبيق · ${board.trips.size} رحلة محفوظة",
                                "${c.network.lineCount} hat · ${c.network.stopCount} durak uygulamada · ${board.trips.size} kayıtlı sefer",
                                "${c.network.lineCount} lines · ${c.network.stopCount} stops bundled · ${board.trips.size} trips stored"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            when (board.freshness.level) {
                                FreshLevel.FRESH -> st.s("البيانات حديثة (${board.freshness.ageLabel})", "Veri taze (${board.freshness.ageLabel})", "Data fresh (${board.freshness.ageLabel})")
                                FreshLevel.STALE -> st.s("البيانات قديمة (${board.freshness.ageLabel})", "Veri eski (${board.freshness.ageLabel})", "Data stale (${board.freshness.ageLabel})")
                                FreshLevel.EXPIRED -> st.s("البيانات منتهية (${board.freshness.ageLabel})", "Veri süresi geçti (${board.freshness.ageLabel})", "Data expired (${board.freshness.ageLabel})")
                                FreshLevel.EMPTY -> st.s("لا توجد بيانات محفوظة", "Kayıtlı veri yok", "Nothing stored")
                            },
                            fontSize = 12.sp, color = if (board.freshness.level == FreshLevel.FRESH) Brand.Live else Brand.Warn,
                        )
                        Text("${st.s("المصدر", "Kaynak", "Source")}: ${c.network.source}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Pill(if (online) st.s("متصل", "Çevrimiçi", "Online") else st.s("بدون إنترنت", "Çevrimdışı", "Offline"), if (online) Brand.Live else Brand.Offline)
                }
            }
        }
        item { SectionTitle(st.s("اللغة", "Dil", "Language")) }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ar" to "العربية", "tr" to "Türkçe", "en" to "English").forEach { (k, label) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (st.language == k) Brand.Orange else MaterialTheme.colorScheme.surface)
                            .clickable { st.updateLanguage(k) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) { Text(label, color = if (st.language == k) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item { SectionTitle(st.s("المظهر", "Görünüm", "Theme")) }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to st.s("تلقائي", "Sistem", "System"), "light" to st.s("فاتح", "Açık", "Light"), "dark" to st.s("داكن", "Koyu", "Dark")).forEach { (k, label) ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (st.theme == k) Brand.Orange else MaterialTheme.colorScheme.surface)
                            .clickable { st.updateTheme(k) }.padding(horizontal = 16.dp, vertical = 9.dp),
                    ) { Text(label, color = if (st.theme == k) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item { SectionTitle(st.s("صلاحية الرحلات المحفوظة (حسب المدة)", "Kayıtlı seferlerin geçerliliği", "Validity of stored trips")) }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 3, 6, 12, 24).forEach { h ->
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (st.ttlHours == h) Brand.Orange else MaterialTheme.colorScheme.surface)
                            .clickable { st.updateTtlHours(h) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { Text("$h ${st.s("س", "sa", "h")}", color = if (st.ttlHours == h) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item { SectionTitle(st.s("خيارات", "Seçenekler", "Options")) }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchRow(st.s("تحديث تلقائي عند فتح التطبيق", "Açılışta otomatik güncelle", "Auto refresh on open"), st.autoRefresh) { st.updateAutoRefresh(it) }
                SwitchRow(st.s("إظهار الرحلات التقديرية", "Tahmini seferleri göster", "Show estimated trips"), st.showEstimates) { st.updateShowEstimates(it) }
            }
        }
        item { SectionTitle(st.s("حول التطبيق", "Uygulama hakkında", "About")) }
        item {
            Card(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    st.s("OfLayn مواصلات بورصة — نسخة بدون إنترنت.\n\n• كل الخطوط والمحطات محفوظة داخل التطبيق وتعمل من أول فتحة وبدون إنترنت.\n• رحلات اليوم تُحفظ على الهاتف عند أول اتصال وتُعرض حسب المدة المحددة أعلاه.\n• المساعد مدمج في التطبيق ويعمل محلياً: لا مفتاح، لا رابط، لا تحميل نموذج.\n• الرحلات التقديرية تُوسم دائماً بأنها تقديرية، ولا تُقدَّم كجدول رسمي.\n• المصدر: خدمة BursaKart الرسمية «أين حافلتي».",
                        "OfLayn Bursa ulaşım — çevrimdışı sürüm.\n\n• Tüm hatlar ve duraklar uygulamada gömülü, ilk açılışta internetsiz çalışır.\n• Bugünün seferleri ilk bağlantıda telefona kaydedilir ve yukarıdaki süreye göre gösterilir.\n• Asistan uygulamaya gömülüdür ve yerel çalışır: anahtar yok, adres yok, model indirme yok.\n• Tahmini seferler her zaman tahmini olarak işaretlenir.\n• Kaynak: BKK BursaKart «Otobüsüm Nerede» servisi.",
                        "OfLayn Bursa transit — offline edition.\n\n• All lines and stops are bundled and work on the very first launch with no internet.\n• Today's trips are saved on the phone at the first connection and shown per the validity window above.\n• The assistant is built in and runs locally: no key, no URL, no model download.\n• Estimated trips are always labelled as estimates, never as an official timetable.\n• Source: the official BursaKart \"Otobüsüm Nerede\" service."),
                    fontSize = 12.sp, lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).clickable { onChange(!checked) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(if (checked) Brand.Live else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Ic.Check, Color.White, size = 16.dp) }
    }
}
