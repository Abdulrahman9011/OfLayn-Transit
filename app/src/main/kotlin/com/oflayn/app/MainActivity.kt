package com.oflayn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var nfc: NfcController? = null
    private var nfcState by mutableStateOf(NfcState.READY)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // never blocks start-up: no keep-on-screen condition
        super.onCreate(savedInstanceState)
        val container = (application as OfLaynApp).container
        nfc = NfcController(this, container.settings) { nfcState = it }
        nfcState = nfc!!.currentState()
        setContent { AppRoot(container, nfcState) }
    }

    override fun onResume() { super.onResume(); nfc?.start() }
    override fun onPause() { nfc?.stop(); super.onPause() }
}

@Composable
private fun AppRoot(c: AppContainer, nfc: NfcState) {
    val st = c.settings
    val dark = when (st.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val direction = if (st.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr
    var showSplash by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(AppTab.HOME) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Surface(Modifier.fillMaxSize()) {
                if (showSplash) {
                    Splash { showSplash = false }
                } else {
                    Scaffold(bottomBar = {
                        NavigationBar {
                            AppTab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t, onClick = { tab = t },
                                    icon = { Text(t.icon) },
                                    label = { Text(when (t) {
                                        AppTab.HOME -> st.s("الرئيسية", "Ana", "Home")
                                        AppTab.MAP -> st.s("الخريطة", "Harita", "Map")
                                        AppTab.TRANSIT -> st.s("المواصلات", "Ulaşım", "Transit")
                                        AppTab.CARD -> st.s("البطاقة", "Kart", "Card")
                                        AppTab.AI -> "AI"
                                        AppTab.SETTINGS -> st.s("الإعدادات", "Ayarlar", "Settings")
                                    }, maxLines = 1) },
                                )
                            }
                        }
                    }) { pad ->
                        Box(Modifier.padding(pad)) {
                            when (tab) {
                                AppTab.HOME -> HomeScreen(c) { tab = it }
                                AppTab.MAP -> MapScreen(c)
                                AppTab.TRANSIT -> TransitScreen(c) { tab = it }
                                AppTab.CARD -> CardScreen(c, nfc)
                                AppTab.AI -> AiScreen(c)
                                AppTab.SETTINGS -> SettingsScreen(c)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Short fade+scale intro. The dashboard needs no network, so nothing waits on the internet. */
@Composable
private fun Splash(onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(400))
        scale.animateTo(1f, tween(300))
        delay(300)
        onDone()
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.splash_art), null, Modifier.fillMaxWidth().alpha(alpha.value).scale(scale.value))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.independent_notice), style = MaterialTheme.typography.bodySmall)
    }
}
