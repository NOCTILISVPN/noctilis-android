package net.noctilis.app.screens

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.noctilis.app.Api
import net.noctilis.app.ApiException
import net.noctilis.app.Fmt
import net.noctilis.app.Host
import net.noctilis.app.Images
import net.noctilis.app.ui.BodyFont
import net.noctilis.app.ui.HeadFont
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NBadge
import net.noctilis.app.ui.NButton
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NField
import net.noctilis.app.ui.NGhostButton
import net.noctilis.app.ui.NHeader
import net.noctilis.app.ui.NHeading
import net.noctilis.app.ui.NKv
import net.noctilis.app.ui.NLabel
import net.noctilis.app.ui.NPromoCard
import net.noctilis.app.ui.NRow
import net.noctilis.app.ui.NText
import net.noctilis.app.ui.NTile
import org.json.JSONArray
import org.json.JSONObject

/** Данные вкладки «Бонусы»: рефералка + акции, одна база с ботом NOCTILIS. */
class BonusState {
    var data by mutableStateOf<JSONObject?>(null)
    var error by mutableStateOf("")
    /** Запрос живёт в области активности, а не экрана: ответ дойдёт и после перехода между вкладками бонусов. */
    fun load(host: Host) {
        host.scope.launch {
            try { val t = host.token ?: error("нет аккаунта"); val r = withContext(Dispatchers.IO) { Api.bonus(t) }; data = r; error = "" }
            catch (_: Exception) { error = "Бонусы сейчас недоступны — попробуйте позже" }
        }
    }
}

/** Сетевой вызов с экрана: в фоне, результат — в состояние экрана; при уходе с экрана корутина отменяется. */
private fun CoroutineScope.io(block: suspend () -> String, then: (String) -> Unit) = launch { then(withContext(Dispatchers.IO) { block() }) }

private fun JSONObject?.ref(): JSONObject = this?.optJSONObject("ref") ?: JSONObject()
private fun JSONObject?.promos(): JSONObject = this?.optJSONObject("promos") ?: JSONObject()

/** Секция «Ещё способы заработать» — три акции, как в кабинете. */
@Composable
fun PromoCards(bonus: JSONObject?, title: String, onOpen: (String) -> Unit) {
    val pr = bonus.promos()
    val tiers = pr.optJSONArray("tiers")
    val top = tiers?.optJSONArray(2)?.optInt(1) ?: 100_000
    NLabel(title)
    Spacer(Modifier.height(6.dp))
    NPromoCard("🎬", "Ролик про NOCTILIS", "до ${Fmt.num(top)} ₽ за просмотры + промокод зрителям") { onOpen("guide") }
    Spacer(Modifier.height(8.dp))
    NPromoCard("📺", "Заработок на коротких видео", "${pr.optInt("banner_rate", 300) / 100} ₽ за 1 000 просмотров с нашей плашкой") { onOpen("banner") }
    Spacer(Modifier.height(8.dp))
    NPromoCard("📸", "${pr.optInt("story_days", 7)} дней за сторис", "Опубликуйте историю — получите неделю") { onOpen("story") }
}

@Composable
fun BonusScreen(host: Host, st: BonusState, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val p = LocalPalette.current
    var code by remember { mutableStateOf("") }
    var promo by remember { mutableStateOf("") }
    var reqs by remember { mutableStateOf("") }
    var wdOpen by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val ui = rememberCoroutineScope()
    LaunchedEffect(Unit) { st.load(host) }
    val d = st.data
    val r = d.ref()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NHeader("Бонусы", onBack)
        if (d == null) { NText(if (st.error.isEmpty()) "Загружаем…" else st.error, muted = true, size = 13); Spacer(Modifier.height(20.dp)); return@Column }
        NCard {
            NHeading("Вы получаете долю с оплат тех, кого пригласили", 15)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { NBadge("${r.optInt("l1_pct")}%", p.accent); Spacer(Modifier.width(8.dp)); NText("Кого пригласили вы", size = 14) }
                    NText("Первый уровень · заработано ${Fmt.rub(r.optInt("e1"))}", muted = true, size = 12)
                }
                Text("${r.optInt("l1")}", color = p.text, fontFamily = HeadFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { NBadge("${r.optInt("l2_pct")}%", p.accent); Spacer(Modifier.width(8.dp)); NText("Кого пригласили они", size = 14) }
                    NText("Второй уровень · заработано ${Fmt.rub(r.optInt("e2"))}", muted = true, size = 12)
                }
                Text("${r.optInt("l2")}", color = p.text, fontFamily = HeadFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            NText("Реферальный кошелёк", muted = true, size = 12)
            Text(Fmt.rub(d.optInt("balance")), color = p.text, fontFamily = HeadFont, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NText("Заработано", muted = true, size = 12)
                    Text(Fmt.rub(d.optInt("earned_total")), color = p.text, fontFamily = HeadFont, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    NText("к выводу ${Fmt.rub(d.optInt("withdrawable"))} · от ${r.optInt("withdraw_min")} ₽", muted = true, size = 12)
                }
                val canWd = d.optInt("withdrawable") >= r.optInt("withdraw_min") * 100 && !r.optBoolean("pending_withdraw")
                NButton("Вывести", Modifier.width(120.dp), enabled = canWd) { wdOpen = !wdOpen }
            }
            if (r.optBoolean("pending_withdraw")) { Spacer(Modifier.height(6.dp)); NText("Заявка на вывод в работе — оператор выплатит и отметит.", muted = true, size = 12) }
            if (wdOpen) {
                Spacer(Modifier.height(10.dp))
                NText("Реквизиты: телефон и банк (перевод по СБП) или номер карты. Переводим в течение 3 рабочих дней.", muted = true, size = 12)
                Spacer(Modifier.height(6.dp))
                NField(reqs, { reqs = it }, "+7 900 000-00-00, Сбер", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                NButton("Отправить заявку") {
                    if (reqs.trim().length < 5) { msg = "Укажите реквизиты"; return@NButton }
                    val t = host.token; val rq = reqs.trim()
                    ui.io({
                        try { val x = Api.refWithdraw(t ?: error("нет аккаунта"), rq); "Заявка №${x.optInt("id")} принята" }
                        catch (e: ApiException) { when (e.message) { "pending" -> "Предыдущая заявка ещё в работе"; "min" -> "Меньше минимальной суммы"; else -> "Не удалось: ${e.message}" } }
                        catch (_: Exception) { "Нет связи с сервером" }
                    }) { res -> msg = res; reqs = ""; wdOpen = false; st.load(host) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        NLabel("Ваша ссылка")
        Spacer(Modifier.height(6.dp))
        NCard {
            Text(r.optString("code"), color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            NText(r.optString("link"), color = p.accent, size = 13)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NGhostButton("Копировать", Modifier.weight(1f), icon = Icons.Rounded.Link) { host.copy(r.optString("link"), "Ссылка скопирована") }
                NGhostButton("Пригласить", Modifier.weight(1f), icon = Icons.Rounded.Share) {
                    host.share("Доступ к сайтам, которые не открываются. ${host.account?.optInt("trial_days") ?: 3} дня бесплатно: ${r.optString("link")}")
                }
            }
            if (r.optBoolean("can_apply")) {
                Spacer(Modifier.height(12.dp))
                NText("Вас пригласили? Введите код приглашения — пригласивший получит долю с ваших оплат, вы ничего не теряете.", muted = true, size = 12)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NField(code, { code = it }, "код", Modifier.weight(1f))
                    NGhostButton("Применить", Modifier.width(130.dp)) {
                        val c = code.trim(); if (c.isEmpty()) return@NGhostButton
                        val t = host.token
                        ui.io({
                            try { Api.refApply(t ?: error("нет аккаунта"), c); "Код принят" }
                            catch (e: ApiException) { when (e.message) { "self" -> "Это ваш собственный код"; "bad_code" -> "Код не похож на наш"; else -> "Код не принят: такого приглашающего нет или код уже вводили" } }
                            catch (_: Exception) { "Нет связи с сервером" }
                        }) { res -> msg = res; if (res == "Код принят") { code = ""; st.load(host) } }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        NLabel("Промокод")
        Spacer(Modifier.height(6.dp))
        NCard {
            val pend = d.optString("pending_promo").takeIf { it.isNotBlank() && it != "null" }
            NText(if (pend != null) "Промокод $pend ждёт вашей первой оплаты: +${d.promos().optInt("bonus_days", 30)} дней сверху."
                  else "Промокод может дать: бесплатные дни при первой оплате, бонус в кошелёк.", muted = true, size = 12)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NField(promo, { promo = it.uppercase() }, "NIGHT777", Modifier.weight(1f))
                NGhostButton("Применить", Modifier.width(130.dp)) {
                    val c = promo.trim(); if (c.isEmpty()) return@NGhostButton
                    val t = host.token
                    ui.io({
                        try { val x = Api.promo(t ?: error("нет аккаунта"), c); x.optString("message").ifBlank { if (x.optBoolean("ok")) "Промокод принят" else "Промокод не принят" } }
                        catch (e: ApiException) { "Не удалось: ${e.message}" } catch (_: Exception) { "Нет связи с сервером" }
                    }) { res -> msg = res; promo = ""; st.load(host); host.refresh(true) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        NLabel("История начислений")
        Spacer(Modifier.height(6.dp))
        NCard {
            val h = d.optJSONArray("history")
            if (h == null || h.length() == 0) NText("Пока пусто.", muted = true, size = 13)
            else (0 until minOf(h.length(), 30)).forEach { i ->
                val x = h.optJSONObject(i) ?: return@forEach; val a = x.optInt("amount")
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    NText("${Fmt.dateShort(x.optLong("at"))} · ${x.optString("kind")}", muted = true, size = 13)
                    Spacer(Modifier.weight(1f))
                    NText((if (a > 0) "+" else "") + Fmt.rub(a), color = if (a > 0) p.ok else p.text, size = 13)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        PromoCards(d, "Ещё способы заработать", onOpen)
        if (msg.isNotEmpty()) { Spacer(Modifier.height(10.dp)); NText(msg, color = if (msg.startsWith("Не") || msg.contains("не принят")) p.warn else p.ok, size = 13) }
        Spacer(Modifier.height(20.dp))
    }
}

/** Список материалов (значок, картинки, баннеры) — открываются в браузере, оттуда сохраняются. */
@Composable
private fun MaterialsCard(host: Host, group: String, intro: String) {
    var items by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(group) {
        val t = host.token ?: return@LaunchedEffect
        items = withContext(Dispatchers.IO) { try { Api.materials(t).optJSONArray("items") } catch (_: Exception) { null } }
    }
    NCard {
        NText(intro, muted = true, size = 12)
        Spacer(Modifier.height(4.dp))
        val arr = items
        if (arr == null) NText("Загружаем…", muted = true, size = 12)
        else (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optString("group") == group }.forEach { m ->
            NRow(Icons.Rounded.Download, m.optString("title"), m.optString("note").ifBlank { null }) { host.openUrl(m.optString("url")) }
        }
    }
}

@Composable
private fun VideosCard(host: Host, videos: JSONArray?) {
    val p = LocalPalette.current
    val names = mapOf("new" to "⏳ на проверке", "paid" to "✅ выплачено", "rejected" to "❌ отклонён")
    val reasons = mapOf("rules" to "не по правилам", "fraud" to "накрутка", "below" to "меньше порога")
    NCard {
        if (videos == null) NText("Загружаем…", muted = true, size = 13)
        else if (videos.length() == 0) NText("Пока ни одного ролика.", muted = true, size = 13)
        else (0 until videos.length()).forEach { i ->
            val v = videos.optJSONObject(i) ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                NText("№${v.optInt("id")} · " + v.optString("url").removePrefix("https://").removePrefix("http://").take(30), muted = true, size = 13)
                Spacer(Modifier.weight(1f))
                val st = v.optString("status")
                NText((names[st] ?: st) + (if (st == "paid") " " + Fmt.rub(v.optInt("paid_kop")) else "") +
                      (if (st == "rejected" && v.optString("reason").isNotBlank()) " (${reasons[v.optString("reason")] ?: v.optString("reason")})" else ""),
                      color = if (st == "paid") p.ok else if (st == "rejected") p.danger else p.text, size = 13)
            }
        }
    }
}

@Composable
private fun SubmitVideoCard(host: Host, program: String, tiers: JSONArray?, onDone: () -> Unit) {
    val p = LocalPalette.current
    var url by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ui = rememberCoroutineScope()
    NCard {
        NField(url, { url = it }, if (program == "guide") "https://youtube.com/shorts/…" else "https://www.tiktok.com/@…/video/…", Modifier.fillMaxWidth())
        if (program == "guide" && tiers != null) {
            Spacer(Modifier.height(10.dp))
            NText("Какой порог уже набран?", muted = true, size = 13)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until tiers.length()).forEach { i ->
                    val v = tiers.optJSONArray(i)?.optInt(0) ?: 0
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (tier == i) p.accent else p.card2)
                            .border(1.dp, p.line, RoundedCornerShape(12.dp)).clickable { tier = i }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("${v / 1000}k", color = if (tier == i) p.accentText else p.text, fontFamily = BodyFont, fontSize = 14.sp) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        NButton("Подать ролик", enabled = url.isNotBlank() && !busy) {
            busy = true
            val t = host.token; val u = url.trim(); val tr = tier
            ui.io({
                try { val x = Api.videoSubmit(t ?: error("нет аккаунта"), program, u, tr); "Заявка №${x.optInt("id")} отправлена — проверим и начислим." }
                catch (e: ApiException) { e.message ?: "Не удалось" } catch (_: Exception) { "Нет связи с сервером" }
            }) { r -> busy = false; note = r; if (r.startsWith("Заявка")) { url = ""; onDone() } }
        }
        if (note.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(note, color = if (note.startsWith("Заявка")) p.ok else p.warn, size = 13) }
    }
}

@Composable
fun GuideScreen(host: Host, st: BonusState, onBack: () -> Unit) {
    val p = LocalPalette.current
    var codeIn by remember { mutableStateOf("") }
    var codeNote by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<JSONArray?>(null) }
    val ui = rememberCoroutineScope()
    fun loadVideos() { val t = host.token ?: return; ui.launch { withContext(Dispatchers.IO) { try { Api.videos(t) } catch (_: Exception) { null } }?.let { videos = it } } }
    LaunchedEffect(Unit) { if (st.data == null) st.load(host); loadVideos() }
    val d = st.data; val pr = d.promos(); val a = d?.optJSONObject("author")
    val bonusDays = pr.optInt("bonus_days", 30)
    val tiers = pr.optJSONArray("tiers")
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NHeader("Ролик про NOCTILIS", onBack)
        NCard {
            NText("Снимите короткий ролик-инструкцию: скачали приложение NOCTILIS → нажали «Включить» → сайты открылись. Опубликуйте на YouTube, TikTok или Instagram.", size = 14)
            Spacer(Modifier.height(10.dp))
            NLabel("Премия за просмотры")
            NText("Один раз за ролик, по достигнутому порогу", muted = true, size = 12)
            if (tiers != null) (0 until tiers.length()).forEach { i ->
                val t = tiers.optJSONArray(i) ?: return@forEach
                NKv("от ${Fmt.num(t.optInt(0))} просмотров", "${Fmt.num(t.optInt(1))} ₽")
            }
            NText("Ваш промокод даёт зрителям +$bonusDays ${Fmt.dw(bonusDays)} при первой оплате — называйте его в ролике.", muted = true, size = 12)
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Ваш промокод")
        Spacer(Modifier.height(6.dp))
        NCard {
            if (a != null) {
                Text(a.optString("code"), color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 2.sp)
                NText("Ввели ваш код: ${a.optInt("came")} чел.", muted = true, size = 13)
                Spacer(Modifier.height(6.dp))
                NText(a.optString("link"), color = p.accent, size = 13)
                NText("Ссылка для описания и закрепа — по ней зритель попадёт на страницу приложения с вашим кодом.", muted = true, size = 12)
                Spacer(Modifier.height(8.dp))
                NGhostButton("Скопировать ссылку", icon = Icons.Rounded.Link) { host.copy(a.optString("link"), "Ссылка скопирована") }
            } else {
                NText("Придумайте слово — латиницей, 4–12 букв, в конце можно до трёх цифр. Его будут произносить в ролике, так что покороче.", muted = true, size = 13)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NField(codeIn, { codeIn = it.uppercase() }, "NIGHT777", Modifier.weight(1f))
                    NGhostButton("Создать", Modifier.width(120.dp)) {
                        val t = host.token; val c = codeIn.trim()
                        ui.io({
                            try { val x = Api.codeCreate(t ?: error("нет аккаунта"), c); "Код создан: ${x.optString("code")}" }
                            catch (e: ApiException) { e.message ?: "Не удалось" } catch (_: Exception) { "Нет связи с сервером" }
                        }) { r -> codeNote = r; if (r.startsWith("Код создан")) st.load(host) }
                    }
                }
                if (codeNote.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(codeNote, color = if (codeNote.startsWith("Код создан")) p.ok else p.warn, size = 13) }
            }
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Правила")
        Spacer(Modifier.height(6.dp))
        NCard {
            listOf(
                "Площадки: YouTube, TikTok, Instagram.",
                "Весь ролик в кадре виден значок или название NOCTILIS.",
                "В описании или закрепе — ваша ссылка.",
                "В ролике назван ваш промокод и что он даёт: +$bonusDays ${Fmt.dw(bonusDays)} при оплате.",
                "Ролик опубликован не раньше старта акции" + (pr.optString("ugc_start").takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ".",
                "Накрутка просмотров — отказ; статистику можем запросить.",
                "Один порог на ролик, доплаты при росте нет. Подавайте, когда порог набран.",
                "Премия — в реферальный кошелёк, вывод от ${d.ref().optInt("withdraw_min", 500)} ₽ на карту или по СБП.",
            ).forEach { NText("• $it", size = 13) }
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Материалы")
        Spacer(Modifier.height(6.dp))
        MaterialsCard(host, "kit", "Значок, картинки стража, анимированный баннер, видеобаннер и справка: что говорить, промокод, куда ссылку. Открывается в браузере — там «Сохранить».")
        Spacer(Modifier.height(18.dp))
        NLabel("Подать ролик")
        Spacer(Modifier.height(6.dp))
        SubmitVideoCard(host, "guide", tiers) { loadVideos(); st.load(host) }
        Spacer(Modifier.height(18.dp))
        NLabel("Мои ролики")
        Spacer(Modifier.height(6.dp))
        VideosCard(host, videos)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun BannerScreen(host: Host, st: BonusState, onBack: () -> Unit) {
    val p = LocalPalette.current
    var videos by remember { mutableStateOf<JSONArray?>(null) }
    val ui = rememberCoroutineScope()
    fun loadVideos() { val t = host.token ?: return; ui.launch { withContext(Dispatchers.IO) { try { Api.videos(t) } catch (_: Exception) { null } }?.let { videos = it } } }
    LaunchedEffect(Unit) { if (st.data == null) st.load(host); loadVideos() }
    val d = st.data; val pr = d.promos()
    val rate = pr.optInt("banner_rate", 300) / 100
    val track = d?.optJSONObject("author")?.optString("link")?.takeIf { it.isNotBlank() } ?: d.ref().optString("link")
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NHeader("Короткие видео", onBack)
        NCard {
            NHeading("Заработай на коротких видео", 16)
            Spacer(Modifier.height(6.dp))
            NText("Публикуйте нарезки на YouTube Shorts, TikTok и Instagram Reels с нашей плашкой и получайте вознаграждение за просмотры.", muted = true, size = 13)
            Spacer(Modifier.height(12.dp))
            Text("$rate ₽", color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.Bold, fontSize = 40.sp)
            NText("за 1 000 просмотров", muted = true, size = 13)
            NText("Ролик засчитывается от ${Fmt.num(pr.optInt("banner_min", 30000))} просмотров, подать можно в течение ${pr.optInt("banner_days", 7)} дней", muted = true, size = 12)
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Плашка")
        Spacer(Modifier.height(6.dp))
        MaterialsCard(host, "banner", "Два варианта: простой — класть поверх ролика как есть; с розовым фоном — для хромакея.")
        Spacer(Modifier.height(18.dp))
        NLabel("Условия оплаты")
        Spacer(Modifier.height(6.dp))
        NCard {
            listOf(
                "Оплата за просмотры (охваты): $rate ₽ за 1 000 просмотров видео с плашкой NOCTILIS.",
                "При подозрении на накрутку вправе отказать в выплате или урезать её.",
                "К проверке принимаются ролики от ${Fmt.num(pr.optInt("banner_min", 30000))} просмотров.",
                "Срок подачи — до ${pr.optInt("banner_days", 7)} дней с момента публикации.",
            ).forEach { NText("• $it", size = 13) }
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Оформление")
        Spacer(Modifier.height(6.dp))
        NCard {
            NText("В описании видео обязательно:", size = 14)
            NText("• хештег #noctilis", size = 13)
            NText("• сайт noctilis.net", size = 13)
            Spacer(Modifier.height(6.dp))
            NText("Если площадка позволяет закреплять комментарии — закрепите: «VPN — noctilis.net». В описании профиля — ваша ссылка учёта:", muted = true, size = 12)
            NText(track, color = p.accent, size = 13)
            Spacer(Modifier.height(8.dp))
            NGhostButton("Скопировать ссылку", icon = Icons.Rounded.Link) { host.copy(track, "Ссылка скопирована") }
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Требования к контенту")
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NTile("Язык", "Русский", "только", Modifier.weight(1f)); NTile("Длительность", "от 15 с", "минимум", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        NCard {
            NText("Допускаются: нарезки Twitch- и YouTube-стримеров, фрагменты фильмов, сериалов, мультфильмов, телевизионный контент.", muted = true, size = 13)
            Spacer(Modifier.height(8.dp))
            NLabel("Плашка")
            listOf("Хорошо заметна на протяжении всего видео.", "Не мешает просмотру основного контента.", "60–80 % ширины кадра.",
                "Описание и интерфейс площадки не перекрывают плашку.", "Прозрачность запрещена — плашка полностью видима.").forEach { NText("• $it", size = 13) }
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Подать ролик")
        Spacer(Modifier.height(6.dp))
        SubmitVideoCard(host, "banner", null) { loadVideos(); st.load(host) }
        Spacer(Modifier.height(18.dp))
        NLabel("Мои ролики")
        Spacer(Modifier.height(6.dp))
        VideosCard(host, videos)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun StoryScreen(host: Host, st: BonusState, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val ui = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (st.data == null) st.load(host) }
    val d = st.data; val pr = d.promos(); val r = d.ref()
    val days = pr.optInt("story_days", 7)
    val next = pr.optLong("story_next")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; note = "Отправляем скриншот…"
        val t = host.token
        ui.io({
            try {
                // скриншот → JPEG ≤ 1600 px → base64; файл целиком в память не читаем, нехватка памяти — не падение
                val jpeg = Images.toJpeg(ctx, uri) ?: error("это не картинка")
                val x = Api.storySubmit(t ?: error("нет аккаунта"), Base64.encodeToString(jpeg, Base64.NO_WRAP))
                "Заявка №${x.optInt("id")} отправлена — проверим и начислим $days ${Fmt.dw(days)}."
            } catch (e: ApiException) { e.message ?: "Не удалось" } catch (e: Exception) { "Не удалось: ${e.message}" }
        }) { res -> note = res; busy = false; st.load(host) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
        NHeader("Акция", onBack)
        NCard {
            NHeading("Бесплатные $days дней подписки за сторис", 16)
            Spacer(Modifier.height(6.dp))
            NText("Поделитесь нашей реферальной ссылкой в истории (Telegram, VK, Instagram) и получите неделю подписки в подарок.", muted = true, size = 13)
        }
        Spacer(Modifier.height(18.dp))
        NLabel("Условия акции")
        Spacer(Modifier.height(6.dp))
        NCard {
            NText("1. Выложите в истории наш ролик с вашей реферальной ссылкой в описании.", size = 13)
            NText("2. Через сутки пришлите скриншот истории кнопкой ниже.", size = 13)
            NText("3. Получите $days ${Fmt.dw(days)} подписки бесплатно.", size = 13)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NTile("Видимость", "для всех", "история открыта", Modifier.weight(1f)); NTile("Срок", "24 часа", "минимум", Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        NText("Не чаще раза в ${pr.optInt("story_cooldown_days", 30)} дней. С тех, кто придёт по ссылке, вы получаете ещё и ${r.optInt("l1_pct", 35)}% с их оплат." +
              (if (next > 0) " Следующую историю можно подать после ${Fmt.dateShort(next)}." else ""), muted = true, size = 12)
        Spacer(Modifier.height(18.dp))
        NLabel("Материал")
        Spacer(Modifier.height(6.dp))
        MaterialsCard(host, "stories", "Ролик для истории (6 секунд). Сохраните и выложите, в описании — ваша ссылка.")
        Spacer(Modifier.height(12.dp))
        NGhostButton("Скопировать реф. ссылку", icon = Icons.Rounded.Link) { host.copy(r.optString("link"), "Реферальная ссылка скопирована") }
        Spacer(Modifier.height(8.dp))
        val pending = pr.optBoolean("story_pending")
        NButton(if (pending) "Скриншот на проверке" else "Отправить скриншот", enabled = !pending && next <= 0 && !busy, icon = Icons.Rounded.PhotoCamera) { picker.launch("image/*") }
        if (pending) { Spacer(Modifier.height(6.dp)); NText("Скриншот на проверке — результат появится здесь и в кабинете.", muted = true, size = 12) }
        if (note.isNotEmpty()) { Spacer(Modifier.height(8.dp)); NText(note, color = if (note.startsWith("Заявка")) p.ok else if (note.startsWith("Отправляем")) p.muted else p.warn, size = 13) }
        Spacer(Modifier.height(20.dp))
    }
}
