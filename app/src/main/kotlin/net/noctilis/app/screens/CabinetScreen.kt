package net.noctilis.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.noctilis.app.Api
import net.noctilis.app.ApiException
import net.noctilis.app.Fmt
import net.noctilis.app.Host
import net.noctilis.app.ui.BodyFont
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NBadge
import net.noctilis.app.ui.NBig
import net.noctilis.app.ui.NButton
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NField
import net.noctilis.app.ui.NGhostButton
import net.noctilis.app.ui.NHeader
import net.noctilis.app.ui.NHeading
import net.noctilis.app.ui.NKv
import net.noctilis.app.ui.NLabel
import net.noctilis.app.ui.NText
import net.noctilis.app.ui.NTile
import org.json.JSONObject

/** Личный кабинет: подписка, трафик, устройства по одному, ссылка подписки, привязка. */
@Composable
fun CabinetScreen(host: Host, onBack: () -> Unit, onSubscriptionChanged: () -> Unit, onPay: () -> Unit) {
    val p = LocalPalette.current
    val acc = host.account
    val active = acc?.optBoolean("active") ?: false
    val paid = acc?.optBoolean("paid") ?: false
    val days = acc?.optInt("days_left") ?: 0
    var devices by remember { mutableStateOf<JSONObject?>(null) }
    var msg by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Triple<String, String, String>?>(null) }   // kind, id, name
    var reissueAsk by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    var linkText by remember { mutableStateOf("") }
    var linkState by remember { mutableStateOf("") }

    fun loadDevices() {
        Thread {
            try { val t = host.token ?: return@Thread; val d = Api.devices(t); host.runUi { devices = d } } catch (_: Exception) {}
        }.start()
    }
    LaunchedEffect(Unit) { loadDevices() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NHeader("Кабинет", onBack)
        NCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NHeading("Тариф «Полный»", 16)
                    NText(acc?.optString("username")?.takeIf { it.isNotBlank() } ?: "—", muted = true, size = 12)
                }
                NBadge(
                    if (!active) "Не активна" else if (paid) "Активна" else "Пробный период",
                    if (!active) p.danger else if (paid) p.ok else p.warn,
                )
            }
            Spacer(Modifier.height(8.dp))
            NBig(if (active) "$days" else "0", if (active) Fmt.dw(days) else "дней")
            NText((if (paid) "Подписка" else "Пробный период") + " до " + Fmt.date(acc?.optLong("expiry_ms") ?: 0L), muted = true, size = 13)
            if (active) NText("Баланс: ${acc?.optInt("balance_rub") ?: 0} ₽ — оплаченные дни × 5 ₽", muted = true, size = 13)
            Spacer(Modifier.height(10.dp))
            NButton(if (paid) "Продлить" else "Оформить", onClick = onPay)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val lim = acc?.optInt("device_limit") ?: 0
            NTile("Устройства", "${devices?.optInt("count") ?: "…"}", "из ${if (lim > 0) lim else "—"}", Modifier.weight(1f))
            NTile("Трафик", Fmt.gb(acc?.optLong("traffic_bytes") ?: 0L), "без лимита", Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
        NLabel("Устройства на подписке")
        Spacer(Modifier.height(6.dp))
        NCard {
            val arr = devices?.optJSONArray("devices")
            val lim = acc?.optInt("device_limit") ?: 0
            NText(
                if (paid) "На подписке $lim места: телефон, планшет, компьютер — на одной подписке."
                else "На пробном периоде место одно. Подписка открывает 3 места.",
                muted = true, size = 12,
            )
            Spacer(Modifier.height(6.dp))
            if (devices == null) NText("Загружаем…", muted = true, size = 13)
            else if (arr == null || arr.length() == 0) NText("Пока ни одного устройства.", muted = true, size = 13)
            else (0 until arr.length()).forEach { i ->
                val d = arr.getJSONObject(i)
                val self = d.optBoolean("this")
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        NText(d.optString("model").ifBlank { "устройство" } + if (self) " · это устройство" else "", size = 15)
                        NText(listOf(d.optString("platform"), d.optString("app")).filter { it.isNotBlank() }.joinToString(" · "), muted = true, size = 12)
                    }
                    if (!self) Text("Отвязать", color = p.danger, fontFamily = BodyFont,
                        modifier = Modifier.padding(start = 8.dp).clickable { confirm = Triple(d.optString("kind"), d.optString("id"), d.optString("model").ifBlank { "устройство" }) })
                }
            }
            val cnt = devices?.optInt("count") ?: 0
            if (lim > 0 && cnt >= lim) NText("Свободных мест нет — отвяжите ненужное устройство, потом подключайте нужное.", color = p.warn, size = 12)
        }

        Spacer(Modifier.height(18.dp))
        NLabel("Ссылка подписки")
        Spacer(Modifier.height(6.dp))
        NCard {
            NText("Для Happ, Hiddify или другого телефона. Кто получит ссылку — получит и ваш VPN, не давайте её лишним.", muted = true, size = 12)
            Spacer(Modifier.height(8.dp))
            NGhostButton("Скопировать ссылку", icon = Icons.Rounded.Link) {
                val s = acc?.optString("sub_url").orEmpty()
                if (s.isNotBlank()) host.copy(s, "Ссылка скопирована") else host.toast("Ссылка появится, когда аккаунт создан")
            }
            Spacer(Modifier.height(8.dp))
            NGhostButton("Перевыпустить ссылку", icon = Icons.Rounded.Refresh, danger = true) { reissueAsk = true }
        }

        Spacer(Modifier.height(18.dp))
        NLabel("Уже есть подписка?")
        Spacer(Modifier.height(6.dp))
        NCard {
            NText("Если вы оплачивали подписку в боте или на другом телефоне — привяжите её сюда по ссылке подписки. Пробный аккаунт этого телефона заменится вашей подпиской.", muted = true, size = 12)
            Spacer(Modifier.height(8.dp))
            if (!linkOpen) NGhostButton("Привязать подписку", icon = Icons.Rounded.Link) { linkOpen = true }
            else {
                NField(linkText, { linkText = it }, "https://…/s/…", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                NButton("Привязать", enabled = linkText.isNotBlank() && linkState != "Проверяем…") {
                    linkState = "Проверяем…"
                    Thread {
                        val r = try {
                            val me = Api.link(host.token ?: error("нет аккаунта"), linkText.trim())
                            host.runUi { onSubscriptionChanged() }
                            if (me.optBoolean("already")) "Эта подписка уже привязана" else "Готово: подписка привязана"
                        } catch (e: ApiException) {
                            when (e.message) {
                                "not_found" -> "Подписка по этой ссылке не найдена"
                                "bad_link" -> "Это не похоже на ссылку подписки"
                                "device_limit" -> "На этой подписке нет свободных мест — отвяжите устройство там, где подписка оформлена"
                                else -> "Сервер ответил: ${e.message}"
                            }
                        } catch (_: Exception) { "Нет связи с сервером" }
                        host.runUi { linkState = r; if (r.startsWith("Готово")) { linkText = ""; host.refresh(true); loadDevices() } }
                    }.start()
                }
                if (linkState.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(linkState, color = if (linkState.startsWith("Готово")) p.ok else p.warn, size = 13) }
            }
        }
        if (msg.isNotEmpty()) { Spacer(Modifier.height(8.dp)); NText(msg, color = if (msg.startsWith("Не")) p.warn else p.ok, size = 13) }
        Spacer(Modifier.height(20.dp))
    }

    confirm?.let { (kind, id, name) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = p.card, titleContentColor = p.text, textContentColor = p.muted,
            title = { Text("Отвязать «$name»?", fontFamily = BodyFont) },
            text = { Text("На нём VPN перестанет подключаться, пока не включите снова.", fontFamily = BodyFont) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    Thread {
                        val r = try { Api.deviceDelete(host.token!!, kind, id); "Устройство отвязано" } catch (e: Exception) { "Не удалось: ${e.message}" }
                        host.runUi { msg = r; loadDevices() }
                    }.start()
                }) { Text("Отвязать", color = p.danger, fontFamily = BodyFont) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена", color = p.muted, fontFamily = BodyFont) } },
        )
    }
    if (reissueAsk) {
        AlertDialog(
            onDismissRequest = { reissueAsk = false },
            containerColor = p.card, titleContentColor = p.text, textContentColor = p.muted,
            title = { Text("Перевыпустить ссылку", fontFamily = BodyFont) },
            text = { Text("Нужно, если ссылка утекла. Старая перестанет работать на всех устройствах — на каждом придётся добавить новую. Это приложение переподключится само.", fontFamily = BodyFont) },
            confirmButton = {
                TextButton(onClick = {
                    reissueAsk = false
                    Thread {
                        val r = try { Api.reissue(host.token!!); "Ссылка перевыпущена — старая больше не работает" } catch (e: Exception) { "Не удалось: ${e.message}" }
                        host.runUi { msg = r; if (r.startsWith("Ссылка")) onSubscriptionChanged() }
                    }.start()
                }) { Text("Да, выпустить новую", color = p.danger, fontFamily = BodyFont) }
            },
            dismissButton = { TextButton(onClick = { reissueAsk = false }) { Text("Отмена", color = p.muted, fontFamily = BodyFont) } },
        )
    }
}
