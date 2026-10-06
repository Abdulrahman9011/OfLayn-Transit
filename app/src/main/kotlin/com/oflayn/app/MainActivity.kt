package com.oflayn.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OfLaynApp).container
        setContent { AppRoot(container) }
    }
}

enum class AppTab { TODAY, LINES, AI, MORE }

sealed interface Detail {
    data class Line(val code: String) : Detail
    data class Stop(val id: Int, val name: String) : Detail
}

@Composable
private fun AppRoot(c: AppContainer) {
    val st = c.settings
    val dark = when (st.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val direction = if (st.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    var tab by remember { mutableStateOf(AppTab.TODAY) }
    val stack = remember { mutableStateListOf<Detail>() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    BackHandler(enabled = stack.isNotEmpty()) { stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.isEmpty() && tab != AppTab.TODAY) { tab = AppTab.TODAY }

    MaterialTheme(colorScheme = if (dark) appDark() else appLight()) {
        CompositionLocalProvider(LocalLayoutDirection provides direction, LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val top = stack.lastOrNull()) {
                        is Detail.Line -> LineDetailScreen(c, top.code, onBack = { stack.removeAt(stack.lastIndex) }, openStop = { id, name -> stack.add(Detail.Stop(id, name)) })
                        is Detail.Stop -> StopScreen(c, top.id, top.name, onBack = { stack.removeAt(stack.lastIndex) }, openLine = { stack.add(Detail.Line(it)) })
                        null -> when (tab) {
                            AppTab.TODAY -> TodayScreen(
                                c,
                                locationGranted = locationGranted,
                                onAskLocation = { locationGranted = true },
                                openLine = { stack.add(Detail.Line(it)) },
                                openStop = { id, name -> stack.add(Detail.Stop(id, name)) },
                            )
                            AppTab.LINES -> LinesScreen(c, openLine = { stack.add(Detail.Line(it)) }, openStop = { id, name -> stack.add(Detail.Stop(id, name)) })
                            AppTab.AI -> AiScreen(c)
                            AppTab.MORE -> MoreScreen(c)
                        }
                    }
                }
                if (!imeOpen) Box(Modifier.background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
                    BottomBar(
                        items = listOf(
                            Triple(Ic.Home, st.s("اليوم", "Bugün", "Today"), tab == AppTab.TODAY && stack.isEmpty()),
                            Triple(Ic.Bus, st.s("الخطوط", "Hatlar", "Lines"), tab == AppTab.LINES && stack.isEmpty()),
                            Triple(Ic.Spark, "AI", tab == AppTab.AI && stack.isEmpty()),
                            Triple(Ic.More, st.s("المزيد", "Daha", "More"), tab == AppTab.MORE && stack.isEmpty()),
                        ),
                        onSelect = { stack.clear(); tab = AppTab.entries[it] },
                    )
                }
            }
        }
    }
}
