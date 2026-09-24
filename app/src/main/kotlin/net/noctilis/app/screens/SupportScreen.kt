package net.noctilis.app.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.noctilis.app.Api
import net.noctilis.app.Fmt
import net.noctilis.app.Host
import net.noctilis.app.Images
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NField
import net.noctilis.app.ui.NHeader
import net.noctilis.app.ui.NText
import net.noctilis.app.ui.accentGradient
import org.json.JSONObject
import android.util.Base64

private data class Msg(val id: Int, val dir: String, val text: String, val ts: Long, val hasImage: Boolean)

/**
 * Поддержка внутри приложения — без Telegram и MAX. Текст и скриншоты уходят оператору
 * в группу поддержки отдельной темой, ответы (текст и картинки) приходят сюда; опрос каждые 8 с.
 */
@Composable
fun SupportScreen(host: Host, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var attached by remember { mutableStateOf<ByteArray?>(null) }
    var attachedPreview by remember { mutableStateOf<Bitmap?>(null) }
    var sending by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val images = remember { mutableStateMapOf<Int, Bitmap?>() }   // id → картинка (null = грузится/нет)
    val imageOrder = remember { ArrayDeque<Int>() }                  // порядок загрузки — старые выбрасываем из памяти
    val list = rememberLazyListState()
    val ui = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    fun parse(o: JSONObject): List<Msg> {
        val a = o.optJSONArray("messages") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }
            .map { m -> Msg(m.optInt("id"), m.optString("dir"), m.optString("text"), m.optLong("ts"), m.optBoolean("has_image")) }
            .distinctBy { it.id }   // повтор id ронял LazyColumn («key was already used»)
    }
    suspend fun load() {
        val t = host.token ?: return
        try { val l = withContext(Dispatchers.IO) { parse(Api.supportList(t, 0)) }; msgs = l; loaded = true; err = "" }
        catch (_: Exception) { loaded = true; if (msgs.isEmpty()) err = "Нет связи с сервером" }
    }
    fun loadImage(id: Int) {
        if (images.containsKey(id)) return
        images[id] = null
        ui.launch {
            val t = host.token
            val bmp = if (t == null) null else withContext(Dispatchers.IO) { try { Images.decodeScaled(Api.supportImage(t, id), 1200) } catch (_: Exception) { null } }
            images[id] = bmp
            if (bmp != null) {
                // в памяти держим не больше 12 картинок (≈ до 70 МБ): остальные подгрузятся заново при прокрутке
                imageOrder.addLast(id)
                while (imageOrder.size > 12) { val old = imageOrder.removeFirst(); if (old != id) images.remove(old) }
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        ui.launch {
            // файл из галереи не читаем целиком: сразу ужимаем в JPEG ≤ 1600 px и храним только его
            val jpeg = withContext(Dispatchers.IO) { Images.toJpeg(ctx, uri) }
            val prev = jpeg?.let { withContext(Dispatchers.IO) { Images.decodeScaled(it, 600) } }
            if (jpeg != null && prev != null) { attached = jpeg; attachedPreview = prev } else err = "Не удалось открыть картинку"
        }
    }
    // опрос каждые 8 с — только пока экран виден; свёрнутое приложение сеть не дёргает
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { load(); delay(8_000) } } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
        NHeader("Поддержка", onBack)
        NCard {
            NText("Напишите, что не работает: что за телефон, какой сервер выбран, что показывает приложение. Скриншот можно прикрепить. Ответ появится здесь же. Обычно отвечаем в течение часа днём по Москве.", muted = true, size = 12)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loaded && msgs.isEmpty()) item { NText(if (err.isEmpty()) "Сообщений пока нет." else err, muted = true, size = 13) }
            items(msgs, key = { it.id }) { m ->
                val mine = m.dir == "in"
                if (m.hasImage && !images.containsKey(m.id)) LaunchedEffect(m.id) { loadImage(m.id) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp)).background(if (mine) p.accent.copy(alpha = 0.18f) else p.card2).padding(12.dp),
                    ) {
                        NText(if (mine) "Вы" else "Поддержка NOCTILIS", muted = true, size = 11)
                        if (m.hasImage) {
                            val bmp = images[m.id]
                            if (bmp != null) Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
                            else Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(p.card), contentAlignment = Alignment.Center) { NText("картинка…", muted = true, size = 12) }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (m.text.isNotBlank() && !(m.hasImage && m.text.startsWith("📷"))) NText(m.text, size = 14)
                        NText(Fmt.time(m.ts), muted = true, size = 10)
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        attachedPreview?.let { prev ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(prev.asImageBitmap(), null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(10.dp))
                NText("Скриншот прикреплён", muted = true, size = 12)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Close, null, tint = p.muted, modifier = Modifier.size(22.dp).clickable { attached = null; attachedPreview = null })
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(p.card2).border(1.dp, p.line, RoundedCornerShape(14.dp))
                    .clickable(enabled = !sending) { picker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AddPhotoAlternate, null, tint = p.accent, modifier = Modifier.size(24.dp)) }
            NField(text, { text = it }, "Сообщение…", Modifier.weight(1f), singleLine = false)
            val canSend = !sending && (text.trim().length >= 2 || attached != null)
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(if (canSend) accentGradient(p) else androidx.compose.ui.graphics.Brush.linearGradient(listOf(p.card2, p.card2)))
                    .clickable(enabled = canSend) {
                        sending = true
                        val t = text.trim(); val img = attached; val token = host.token
                        ui.launch {
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    val b64 = img?.let { Base64.encodeToString(it, Base64.NO_WRAP) }   // уже JPEG ≤ 1600 px
                                    Api.supportSend(token ?: error("нет аккаунта"), t, b64); true
                                } catch (_: Exception) { false }
                            }
                            sending = false
                            if (ok) { text = ""; attached = null; attachedPreview = null; load() }
                            else err = "Не отправилось — проверьте интернет и попробуйте ещё раз"
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Send, null, tint = if (canSend) p.accentText else p.muted, modifier = Modifier.size(22.dp)) }
        }
        if (sending) NText("Отправляем…", muted = true, size = 12)
        if (err.isNotEmpty() && msgs.isNotEmpty()) NText(err, color = p.warn, size = 12)
        Spacer(Modifier.height(10.dp))
    }
}
