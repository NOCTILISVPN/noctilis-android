package net.noctilis.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.noctilis.app.R

/**
 * Оформление как в кабинете NOCTILIS (Mini App): те же цвета, скругления 24/18,
 * шрифты Unbounded (заголовки) и Onest (текст), тёмная и светлая темы.
 */
@Immutable
data class NoctilisPalette(
    val bg: Color, val card: Color, val card2: Color, val line: Color,
    val text: Color, val muted: Color, val accent: Color, val accentText: Color,
    val ok: Color, val warn: Color, val danger: Color, val isDark: Boolean,
)

val DarkPalette = NoctilisPalette(
    bg = Color(0xFF0A0D11), card = Color(0xF0161C22), card2 = Color(0xFF1B232C), line = Color(0x247FC4E8),
    text = Color(0xFFEAF0F5), muted = Color(0xFF8796A4), accent = Color(0xFF7FC4E8), accentText = Color(0xFF06111A),
    ok = Color(0xFF6FD79A), warn = Color(0xFFF2C572), danger = Color(0xFFFF7A7A), isDark = true,
)

val LightPalette = NoctilisPalette(
    bg = Color(0xFFF4F6F9), card = Color(0xFFFFFFFF), card2 = Color(0xFFEEF2F6), line = Color(0xFFE3E8EE),
    text = Color(0xFF0F1720), muted = Color(0xFF66727F), accent = Color(0xFF1C7FB8), accentText = Color(0xFFFFFFFF),
    ok = Color(0xFF3FB56F), warn = Color(0xFFC79A2E), danger = Color(0xFFD9534F), isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

val HeadFont = FontFamily(Font(R.font.unbounded))
val BodyFont = FontFamily(Font(R.font.onest))

/** Градиент главной кнопки — как .btn в кабинете. */
fun accentGradient(p: NoctilisPalette) = Brush.linearGradient(
    listOf(Color(0xFFA8DCF4), p.accent, Color(0xFF5FB0DE)),
)

@Composable
fun NCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .border(1.dp, p.line, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun NHeading(text: String, size: Int = 18) {
    Text(text, color = LocalPalette.current.text, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = size.sp)
}

@Composable
fun NLabel(text: String) {
    Text(text.uppercase(), color = LocalPalette.current.muted, fontFamily = BodyFont, fontSize = 11.sp, letterSpacing = 1.2.sp)
}

@Composable
fun NText(text: String, muted: Boolean = false, size: Int = 14, color: Color? = null) {
    val p = LocalPalette.current
    Text(text, color = color ?: if (muted) p.muted else p.text, fontFamily = BodyFont, fontSize = size.sp, lineHeight = (size + 6).sp)
}

/** Главная кнопка с градиентом (.btn). */
@Composable
fun NButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) accentGradient(p) else Brush.linearGradient(listOf(p.card2, p.card2)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = if (enabled) p.accentText else p.muted, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, color = if (enabled) p.accentText else p.muted, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Вторичная кнопка (.btn.ghost). */
@Composable
fun NGhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, danger: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = if (danger) p.danger else p.text
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.card2)
            .border(1.dp, p.line, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = c, fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

/** Строка-пункт списка с иконкой и стрелкой (как пункты меню кабинета). */
@Composable
fun NRow(icon: ImageVector, title: String, subtitle: String? = null, trailing: String? = null, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(p.card2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontFamily = BodyFont, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = p.muted, fontFamily = BodyFont, fontSize = 12.sp)
        }
        if (trailing != null) Text(trailing, color = p.muted, fontFamily = BodyFont, fontSize = 13.sp)
        if (onClick != null) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ChevronRight, null, tint = p.muted, modifier = Modifier.size(18.dp)) }
    }
}
