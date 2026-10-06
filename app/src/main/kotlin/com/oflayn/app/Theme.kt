package com.oflayn.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

object Brand {
    val Orange = Color(0xFFF26B21)
    val OrangeSoft = Color(0x1FF26B21)
    val Ink = Color(0xFF1B2430)
    val Mist = Color(0xFFF2F4F7)
    val Live = Color(0xFF1FB36B)
    val Offline = Color(0xFF8A94A6)
    val Warn = Color(0xFFE8A317)
    private val badge = listOf(
        Color(0xFF2E7CF6), Color(0xFF8E44AD), Color(0xFF0FA3A3), Color(0xFFD94A6B),
        Color(0xFF3D8B37), Color(0xFFE0592A), Color(0xFF5B5FC7), Color(0xFFB8860B),
    )
    fun lineColor(code: String): Color = badge[(code.filter { it.isLetterOrDigit() }.hashCode() and 0x7fffffff) % badge.size]
}

fun appDark() = darkColorScheme(
    primary = Brand.Orange, onPrimary = Color.White,
    background = Color(0xFF0F141B), onBackground = Color(0xFFF1F4F8),
    surface = Color(0xFF1A212B), onSurface = Color(0xFFF1F4F8),
    surfaceVariant = Color(0xFF242D39), onSurfaceVariant = Color(0xFF9FAABA),
    outline = Color(0xFF2E3846), error = Color(0xFFFF6B6B),
)

fun appLight() = lightColorScheme(
    primary = Brand.Orange, onPrimary = Color.White,
    background = Brand.Mist, onBackground = Brand.Ink,
    surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE9EDF2), onSurfaceVariant = Color(0xFF69758A),
    outline = Color(0xFFDDE2EA), error = Color(0xFFD93025),
)

@Composable
fun Card(modifier: Modifier = Modifier, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(pad),
        content = content,
    )
}

@Composable
fun LineBadge(code: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(if (big) 12.dp else 8.dp)).background(Brand.lineColor(code))
            .padding(horizontal = if (big) 14.dp else 9.dp, vertical = if (big) 8.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (big) 22.sp else 14.sp, maxLines = 1) }
}

/** Small status pill: online / offline / local. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LiveDot(color: Color = Brand.Live) {
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(9.dp).clip(CircleShape).background(color.copy(alpha = a)))
}

enum class Ic { Home, Bus, Spark, More, Search, Pin, Back, Close, Locate, Chevron, Refresh, Clock, Walk, Settings, Check, Send }

@Composable
fun Icon(ic: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirror = rtl && (ic == Ic.Back || ic == Ic.Chevron)
    Canvas(modifier.size(size).graphicsLayer { scaleX = if (mirror) -1f else 1f }) { drawIc(ic, tint) }
}

private fun DrawScope.drawIc(ic: Ic, c: Color) {
    val w = size.width
    val sw = w * 0.085f
    val st = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(x: Float, y: Float) = Offset(w * x, w * y)
    when (ic) {
        Ic.Home -> {
            val path = Path().apply { moveTo(w * .14f, w * .48f); lineTo(w * .5f, w * .16f); lineTo(w * .86f, w * .48f); moveTo(w * .24f, w * .42f); lineTo(w * .24f, w * .84f); lineTo(w * .76f, w * .84f); lineTo(w * .76f, w * .42f) }
            drawPath(path, c, style = st)
        }
        Ic.Bus -> {
            drawRoundRect(c, p(.2f, .12f), Size(w * .6f, w * .66f), CornerRadius(w * .12f), style = st)
            drawLine(c, p(.2f, .45f), p(.8f, .45f), sw, StrokeCap.Round)
            drawCircle(c, w * .045f, p(.34f, .62f)); drawCircle(c, w * .045f, p(.66f, .62f))
            drawLine(c, p(.3f, .78f), p(.3f, .9f), sw, StrokeCap.Round); drawLine(c, p(.7f, .78f), p(.7f, .9f), sw, StrokeCap.Round)
        }
        Ic.Spark -> {
            val path = Path().apply { moveTo(w * .5f, w * .1f); quadraticBezierTo(w * .56f, w * .44f, w * .9f, w * .5f); quadraticBezierTo(w * .56f, w * .56f, w * .5f, w * .9f); quadraticBezierTo(w * .44f, w * .56f, w * .1f, w * .5f); quadraticBezierTo(w * .44f, w * .44f, w * .5f, w * .1f) }
            drawPath(path, c, style = st)
        }
        Ic.More -> { for (i in 0..2) drawCircle(c, w * .065f, p(.2f + i * .3f, .5f)) }
        Ic.Search -> { drawCircle(c, w * .26f, p(.45f, .45f), style = st); drawLine(c, p(.65f, .65f), p(.86f, .86f), sw, StrokeCap.Round) }
        Ic.Pin -> {
            val path = Path().apply { moveTo(w * .5f, w * .9f); cubicTo(w * .16f, w * .55f, w * .2f, w * .12f, w * .5f, w * .12f); cubicTo(w * .8f, w * .12f, w * .84f, w * .55f, w * .5f, w * .9f) }
            drawPath(path, c, style = st); drawCircle(c, w * .1f, p(.5f, .4f))
        }
        Ic.Back -> { val path = Path().apply { moveTo(w * .62f, w * .2f); lineTo(w * .3f, w * .5f); lineTo(w * .62f, w * .8f) }; drawPath(path, c, style = st) }
        Ic.Chevron -> { val path = Path().apply { moveTo(w * .38f, w * .2f); lineTo(w * .7f, w * .5f); lineTo(w * .38f, w * .8f) }; drawPath(path, c, style = st) }
        Ic.Close -> { drawLine(c, p(.25f, .25f), p(.75f, .75f), sw, StrokeCap.Round); drawLine(c, p(.75f, .25f), p(.25f, .75f), sw, StrokeCap.Round) }
        Ic.Locate -> {
            drawCircle(c, w * .22f, p(.5f, .5f), style = st); drawCircle(c, w * .06f, p(.5f, .5f))
            drawLine(c, p(.5f, .08f), p(.5f, .2f), sw, StrokeCap.Round); drawLine(c, p(.5f, .8f), p(.5f, .92f), sw, StrokeCap.Round)
            drawLine(c, p(.08f, .5f), p(.2f, .5f), sw, StrokeCap.Round); drawLine(c, p(.8f, .5f), p(.92f, .5f), sw, StrokeCap.Round)
        }
        Ic.Refresh -> {
            val path = Path().apply { moveTo(w * .82f, w * .5f); cubicTo(w * .82f, w * .72f, w * .68f, w * .86f, w * .5f, w * .86f); cubicTo(w * .28f, w * .86f, w * .14f, w * .7f, w * .14f, w * .5f); cubicTo(w * .14f, w * .3f, w * .3f, w * .14f, w * .5f, w * .14f); cubicTo(w * .66f, w * .14f, w * .78f, w * .22f, w * .84f, w * .34f) }
            drawPath(path, c, style = st)
            drawLine(c, p(.84f, .34f), p(.84f, .14f), sw, StrokeCap.Round); drawLine(c, p(.84f, .34f), p(.64f, .34f), sw, StrokeCap.Round)
        }
        Ic.Clock -> { drawCircle(c, w * .38f, p(.5f, .5f), style = st); drawLine(c, p(.5f, .28f), p(.5f, .52f), sw, StrokeCap.Round); drawLine(c, p(.5f, .52f), p(.68f, .62f), sw, StrokeCap.Round) }
        Ic.Walk -> { drawCircle(c, w * .08f, p(.52f, .15f)); val path = Path().apply { moveTo(w * .52f, w * .3f); lineTo(w * .46f, w * .56f); lineTo(w * .3f, w * .88f); moveTo(w * .46f, w * .56f); lineTo(w * .66f, w * .88f); moveTo(w * .3f, w * .42f); lineTo(w * .52f, w * .32f); lineTo(w * .72f, w * .46f) }; drawPath(path, c, style = st) }
        Ic.Settings -> {
            drawCircle(c, w * .16f, p(.5f, .5f), style = st)
            for (i in 0 until 6) {
                val a = Math.toRadians(i * 60.0)
                drawLine(c, p((.5f + .3f * Math.cos(a)).toFloat(), (.5f + .3f * Math.sin(a)).toFloat()), p((.5f + .4f * Math.cos(a)).toFloat(), (.5f + .4f * Math.sin(a)).toFloat()), sw, StrokeCap.Round)
            }
        }
        Ic.Check -> { drawLine(c, p(.22f, .54f), p(.42f, .74f), sw, StrokeCap.Round); drawLine(c, p(.42f, .74f), p(.8f, .28f), sw, StrokeCap.Round) }
        Ic.Send -> { val path = Path().apply { moveTo(w * .5f, w * .82f); lineTo(w * .5f, w * .2f); moveTo(w * .26f, w * .44f); lineTo(w * .5f, w * .2f); lineTo(w * .74f, w * .44f) }; drawPath(path, c, style = st) }
    }
}

@Composable
fun BottomBar(items: List<Triple<Ic, String, Boolean>>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().shadow(14.dp).background(MaterialTheme.colorScheme.surface).padding(top = 6.dp, bottom = 6.dp)) {
        items.forEachIndexed { i, (ic, label, sel) ->
            val tint = if (sel) Brand.Orange else MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ic, tint, size = 26.dp)
                Spacer(Modifier.height(2.dp))
                Text(label, fontSize = 11.sp, color = tint, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}
