package net.noctilis.app.ui

import android.media.AudioManager
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.noctilis.app.R

/**
 * Страж на главной — то же видео, что в кабинете NOCTILIS (крылья с верхней точки, петля).
 * Видео 960×536: показываем во всю ширину без обрезки; если плеер не смог — статичный кадр.
 */
@Composable
fun HeroVideo(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var failed by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().aspectRatio(960f / 536f).background(Color(0xFF05080C))) {
        if (!failed) {
            // Плеер: без аудиофокуса (иначе открытие приложения ставит на паузу музыку пользователя),
            // при уходе с экрана освобождается сразу (onRelease), при сворачивании VideoView сам
            // отпускает MediaPlayer вместе с поверхностью и заново готовит видео при возврате —
            // onPrepared запускает его снова.
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
                        setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.hero}"))
                        setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0f, 0f); start() }
                        setOnErrorListener { _, _, _ -> failed = true; true }
                        setZOrderMediaOverlay(false)
                    }
                },
                update = { v -> if (!v.isPlaying) runCatching { v.start() } },
                onRelease = { v -> runCatching { v.stopPlayback() } },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(painterResource(R.drawable.hero), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        // плавный уход видео в фон экрана, как в кабинете
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.75f to Color.Transparent, 1f to p.bg)))
    }
}

/** Бейдж статуса подписки — как .badge в кабинете: фон card2, рамка, цветная точка и текст в одну строку. */
@Composable
fun NBadge(text: String, color: Color) {
    val p = LocalPalette.current
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).background(p.card2).border(1.dp, p.line, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontFamily = BodyFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

/** Крупная цифра с подписью (.big в кабинете). */
@Composable
fun NBig(value: String, small: String, size: Int = 44) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.Bold, fontSize = size.sp, lineHeight = (size + 4).sp)
        Spacer(Modifier.width(8.dp))
        Text(small, color = p.muted, fontFamily = BodyFont, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
    }
}

/** Плитка «показатель» (.tile в кабинете): заголовок, значение, подпись; по нажатию — экран. */
@Composable
fun NTile(title: String, value: String, sub: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(p.card2).border(1.dp, p.line, RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(14.dp),
    ) {
        Text(title, color = p.muted, fontFamily = BodyFont, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text(sub, color = p.muted, fontFamily = BodyFont, fontSize = 12.sp)
    }
}

/** Карточка-акция (.promo-card): иконка-эмодзи, заголовок, подпись, стрелка. */
@Composable
fun NPromoCard(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(18.dp))
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(12.dp)).background(p.card2), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(sub, color = p.muted, fontFamily = BodyFont, fontSize = 12.sp)
        }
        Text("›", color = p.muted, fontSize = 22.sp)
    }
}
