package net.noctilis.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.noctilis.app.Api
import net.noctilis.app.Host
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NButton
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NField
import net.noctilis.app.ui.NHeader
import net.noctilis.app.ui.NText
import org.json.JSONObject

private data class Msg(val id: Int, val dir: String, val text: String, val ts: Long)

/**
 * Поддержка внутри приложения — без Telegram и MAX. Сообщение уходит оператору
 * в группу поддержки отдельной темой, ответ приходит сюда (опрос каждые 8 с, пока экран открыт).
 */
@Composable
fun SupportScreen(host: Host, onBack: () -> Unit) {
    val p = LocalPalette.current
    var msgs by remember { mutableStateOf<List<Msg>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    fun parse(o: JSONObject): List<Msg> {
        val a = o.optJSONArray("messages") ?: return emptyList()
        return (0 until a.length()).map { i -> val m = a.getJSONObject(i); Msg(m.optInt("id"), m.optString("dir"), m.optString("text"), m.optLong("ts")) }
    }
    fun load() {
        Thread {
            try { val r = Api.supportList(host.token ?: return@Thread, 0); val l = parse(r); host.runUi { msgs = l; loaded = true; err = "" } }
            catch (_: Exception) { host.runUi { loaded = true; if (msgs.isEmpty()) err = "Нет связи с сервером" } }
        }.start()
    }
    LaunchedEffect(Unit) { while (true) { load(); delay(8_000) } }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().imePadding()) {
        NHeader("Поддержка", onBack)
        NCard {
            NText("Напишите, что не работает: что за телефон, какой сервер выбран, что показывает приложение. Ответ появится здесь же, в этом чате. Обычно отвечаем в течение часа днём по Москве.", muted = true, size = 12)
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loaded && msgs.isEmpty()) item { NText(if (err.isEmpty()) "Сообщений пока нет." else err, muted = true, size = 13) }
            items(msgs, key = { it.id }) { m ->
                val mine = m.dir == "in"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.fillMaxWidth(0.85f).clip(RoundedCornerShape(16.dp)).background(if (mine) p.accent.copy(alpha = 0.18f) else p.card2).padding(12.dp),
                    ) {
                        NText(if (mine) "Вы" else "Поддержка NOCTILIS", muted = true, size = 11)
                        NText(m.text, size = 14)
                        NText(net.noctilis.app.Fmt.time(m.ts), muted = true, size = 10)
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NField(text, { text = it }, "Сообщение…", Modifier.weight(1f), singleLine = false)
            NButton(if (sending) "…" else "Отправить", Modifier.width(120.dp), enabled = text.trim().length >= 2 && !sending) {
                sending = true
                val t = text.trim()
                Thread {
                    val ok = try { Api.supportSend(host.token!!, t); true } catch (_: Exception) { false }
                    host.runUi { sending = false; if (ok) { text = ""; load() } else err = "Не отправилось — проверьте интернет и попробуйте ещё раз" }
                }.start()
            }
        }
        if (err.isNotEmpty() && msgs.isNotEmpty()) NText(err, color = p.warn, size = 12)
        Spacer(Modifier.height(10.dp))
    }
}
