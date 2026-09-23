package net.noctilis.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.noctilis.app.ui.BodyFont
import net.noctilis.app.ui.DarkPalette
import net.noctilis.app.ui.HeadFont
import net.noctilis.app.ui.LightPalette
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NButton
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NGhostButton
import net.noctilis.app.ui.NHeading
import net.noctilis.app.ui.NLabel
import net.noctilis.app.ui.NRow
import net.noctilis.app.ui.NText
import net.noctilis.app.ui.accentGradient
import net.noctilis.app.vpn.CoreClient
import net.noctilis.app.vpn.LogBuffer
import net.noctilis.app.vpn.ProbeService
import net.noctilis.app.vpn.VPNService
import net.noctilis.app.vpn.VpnState
import net.noctilis.app.vpn.VpnStatus
import org.json.JSONObject

private enum class Screen { Home, Settings, Exclusions, Pay }

class MainActivity : ComponentActivity() {

    /** Аккаунт и конфигурация — состояние экрана. */
    private var account by mutableStateOf<JSONObject?>(Prefs.me?.let { runCatching { JSONObject(it) }.getOrNull() })
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var themeName by mutableStateOf(Prefs.theme)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initExclusions()
        setContent {
            val palette = if (themeName == "light") LightPalette else DarkPalette
            val scheme = if (palette.isDark) darkColorScheme(primary = palette.accent, background = palette.bg, surface = palette.card, onBackground = palette.text, onSurface = palette.text)
                         else lightColorScheme(primary = palette.accent, background = palette.bg, surface = palette.card, onBackground = palette.text, onSurface = palette.text)
            CompositionLocalProvider(LocalPalette provides palette) {
                MaterialTheme(colorScheme = scheme) { Root() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh(silent = true)   // после возврата с оплаты — свежий срок
    }

    private fun initExclusions() {
        if (!Prefs.exclusionsInitialized) {
            Prefs.excluded = DefaultExclusions.installed(packageManager)
            Prefs.exclusionsInitialized = true
        }
    }

    /** Регистрация (если надо) + /me + /config + проверка обновления. */
    private fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = !silent
        Thread { Updater.check() }.start()
        Thread {
            try {
                var token = Prefs.token
                if (token == null) {
                    val r = Api.register(this)
                    token = r.getString("token")
                    Prefs.token = token
                    Prefs.me = r.toString()
                }
                val me = Api.me(token)
                Prefs.me = me.toString()
                runOnUiThread { account = me; error = null }
                Reminder.check(this, me)
                if (me.optBoolean("active")) Prefs.config = Api.config(token).toString()
            } catch (e: ApiException) {
                if (e.code == 401) Prefs.token = null
                runOnUiThread { if (!silent || account == null) error = describe(e) }
            } catch (e: Exception) {
                runOnUiThread { if (!silent || account == null) error = describe(e) }
            } finally {
                runOnUiThread { loading = false }
            }
        }.start()
    }

    private fun describe(e: Exception): String = when {
        e is ApiException && e.code == 402 -> "Подписка закончилась — продлите её"
        e is ApiException -> "Сервер ответил: ${e.message}"
        else -> "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз"
    }

    private fun openUrl(url: String) {
        if (url.isBlank()) return
        try { CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url)) }
        catch (_: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun openPay(days: Int? = null) {
        val base = account?.optString("pay_url").orEmpty()
        if (base.isBlank()) return
        openUrl(if (days != null) "$base&days=$days" else base)
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("NOCTILIS", text))
    }

    /** После смены исключений — пересоздать туннель, если он включён. */
    private fun restartIfRunning() {
        if (VpnState.isRunning) {
            VPNService.stop(this)
            Thread {
                var waited = 0
                while (VpnState.isRunning && waited < 5000) { Thread.sleep(100); waited += 100 }
                Thread.sleep(300)
                if (VpnService.prepare(this) == null) VPNService.start(this)
            }.start()
        }
    }

    @Composable
    private fun Root() {
        val p = LocalPalette.current
        var screen by remember { mutableStateOf(if (intent?.getStringExtra("screen") == "pay") Screen.Pay else Screen.Home) }
        val status by VpnState.status.collectAsState()
        val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) VPNService.start(this)
        }
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (account == null || Prefs.config == null) refresh()
        }

        fun toggle() {
            if (VpnState.isRunning) { VPNService.stop(this); return }
            val acc = account
            if (acc == null) { refresh(); return }
            if (!acc.optBoolean("active")) { screen = Screen.Pay; return }
            if (Prefs.config == null) { refresh(); return }
            val i = VpnService.prepare(this)
            if (i != null) vpnPermission.launch(i) else VPNService.start(this)
        }

        Box(Modifier.fillMaxSize().background(p.bg)) {
            when (screen) {
                Screen.Home -> HomeScreen(status, ::toggle, { screen = Screen.Settings }, { screen = Screen.Exclusions }, { screen = Screen.Pay })
                Screen.Settings -> { BackHandler { screen = Screen.Home }; SettingsScreen { screen = Screen.Home } }
                Screen.Exclusions -> { BackHandler { screen = Screen.Home }; ExclusionsScreen { screen = Screen.Home } }
                Screen.Pay -> { BackHandler { screen = Screen.Home }; PayScreen { screen = Screen.Home } }
            }
        }
    }

    // ── Главный экран: герой, статус, большая кнопка, 4 кнопки ──

    @Composable
    private fun HomeScreen(status: VpnStatus, onToggle: () -> Unit, onSettings: () -> Unit, onExclusions: () -> Unit, onPay: () -> Unit) {
        val p = LocalPalette.current
        val acc = account
        val days = acc?.optInt("days_left") ?: 0
        val active = acc?.optBoolean("active") ?: false
        val update by Updater.available.collectAsState()
        val updState by Updater.state.collectAsState()
        val connected = status is VpnStatus.Connected
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // герой как в кабинете: картинка с персонажем и плавный уход в фон
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                Image(painterResource(R.drawable.hero), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, p.bg.copy(alpha = 0.35f), p.bg))))
                Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("NOCTILIS", color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = 4.sp)
                    NText("ночной страж свободного интернета", muted = true, size = 13)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                NCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (!active) p.danger else if (days <= 3) p.warn else p.ok))
                        Spacer(Modifier.width(10.dp))
                        NText(
                            when {
                                acc == null && loading -> "Подключаемся к серверу…"
                                acc == null -> "Аккаунт не создан"
                                !active -> "Подписка закончилась"
                                days <= 1 -> "Остался последний день"
                                else -> "Осталось дней: $days"
                            },
                            size = 15,
                        )
                        Spacer(Modifier.weight(1f))
                        NText(acc?.optString("username")?.takeIf { it.isNotBlank() } ?: "", muted = true, size = 12)
                    }
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        NText(it, color = p.warn, size = 13)
                        Text("нажмите, чтобы повторить", color = p.muted, fontFamily = BodyFont, fontSize = 12.sp, modifier = Modifier.clickable { refresh() })
                    }
                }
                if (update != null) {
                    Spacer(Modifier.height(10.dp))
                    NCard(Modifier.clickable(enabled = updState.isEmpty()) { Updater.download(this@MainActivity, update!!) }) {
                        NText(if (updState.isEmpty()) "Доступна версия ${update!!.version} · нажмите, чтобы обновить" else updState, color = p.accent, size = 14)
                    }
                }
                Spacer(Modifier.height(28.dp))
                val busy = status is VpnStatus.Starting || status is VpnStatus.Stopping
                Box(
                    Modifier.size(196.dp).clip(CircleShape)
                        .background(if (connected) accentGradient(p) else Brush.linearGradient(listOf(p.card2, p.card2)))
                        .border(2.dp, if (connected) p.accent else p.line, CircleShape)
                        .clickable(enabled = !busy) { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = p.accent)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Shield, null, tint = if (connected) p.accentText else p.accent, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(if (connected) "ВЫКЛЮЧИТЬ" else "ВКЛЮЧИТЬ", color = if (connected) p.accentText else p.text,
                            fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                NText(
                    when (status) {
                        is VpnStatus.Connected -> "VPN включён · защищённое соединение"
                        is VpnStatus.Error -> status.message
                        else -> "VPN выключен"
                    },
                    muted = status !is VpnStatus.Error, color = if (status is VpnStatus.Error) p.warn else null, size = 13,
                )
                Spacer(Modifier.height(28.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuTile(Icons.Rounded.Settings, "Настройки", Modifier.weight(1f), onSettings)
                    MenuTile(Icons.Rounded.AppSettingsAlt, "Исключения", Modifier.weight(1f), onExclusions)
                    MenuTile(Icons.Rounded.CreditCard, "Оплата", Modifier.weight(1f), onPay, accent = !active || days <= 3)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    @Composable
    private fun MenuTile(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit, accent: Boolean = false) {
        val p = LocalPalette.current
        Column(
            modifier.height(84.dp).clip(RoundedCornerShape(20.dp))
                .background(if (accent) accentGradient(p) else Brush.linearGradient(listOf(p.card, p.card)))
                .border(1.dp, if (accent) Color.Transparent else p.line, RoundedCornerShape(20.dp))
                .clickable { onClick() }.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = if (accent) p.accentText else p.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(text, color = if (accent) p.accentText else p.text, fontFamily = BodyFont, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }

    @Composable
    private fun Header(title: String, onBack: () -> Unit) {
        val p = LocalPalette.current
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = p.accent, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            NHeading(title, 22)
        }
    }

    @Composable
    private fun NField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
        val p = LocalPalette.current
        OutlinedTextField(
            value = value, onValueChange = onChange, singleLine = true,
            placeholder = { Text(placeholder, color = p.muted, fontFamily = BodyFont) },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = p.accent, unfocusedBorderColor = p.line,
                focusedTextColor = p.text, unfocusedTextColor = p.text, cursorColor = p.accent),
            shape = RoundedCornerShape(14.dp), modifier = modifier,
        )
    }

    // ── Настройки ──

    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        val p = LocalPalette.current
        val cfg = Prefs.config
        val servers = remember(cfg) { if (cfg != null) ConfigBuilder.serverTags(cfg) else emptyList() }
        var server by remember { mutableStateOf(Prefs.server) }
        var autoStart by remember { mutableStateOf(Prefs.autoStart) }
        val status by VpnState.status.collectAsState()
        val pings by CoreClient.pings.collectAsState()
        val testing by CoreClient.testing.collectAsState()
        val acc = account
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Header("Настройки", onBack)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    NCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            NHeading("Сервер"); Spacer(Modifier.weight(1f))
                            val canTest = !testing && (status is VpnStatus.Connected || status is VpnStatus.Disconnected || status is VpnStatus.Error)
                            Text(if (testing) "Проверяем…" else "Проверить пинг", color = if (canTest) p.accent else p.muted, fontFamily = BodyFont, fontSize = 13.sp,
                                modifier = Modifier.clickable(enabled = canTest) { if (status is VpnStatus.Connected) CoreClient.testAll() else ProbeService.probe() }.padding(vertical = 6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        (listOf("auto") + servers).forEach { tag ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    server = tag; Prefs.server = tag
                                    if (!CoreClient.select(tag)) restartIfRunning()
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = server == tag, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted))
                                Spacer(Modifier.width(8.dp))
                                NText(if (tag == "auto") "Автовыбор (самый быстрый)" else tag, size = 15)
                                Spacer(Modifier.weight(1f))
                                val ping = pings[tag]
                                if (ping != null) NText(if (ping.delayMs > 0) "${ping.delayMs} мс" else "нет ответа", size = 13,
                                    color = when { ping.delayMs <= 0 -> p.danger; ping.delayMs < 400 -> p.ok; else -> p.warn })
                            }
                        }
                    }
                }
                item { AccountCard(acc) }
                item { NCard { ReferralSection() } }
                item {
                    NCard {
                        NHeading("Оформление")
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Brightness6, null, tint = p.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                            NText("Светлая тема", size = 15); Spacer(Modifier.weight(1f))
                            Switch(checked = themeName == "light", onCheckedChange = { themeName = if (it) "light" else "dark"; Prefs.theme = themeName },
                                colors = SwitchDefaults.colors(checkedThumbColor = p.accentText, checkedTrackColor = p.accent))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bolt, null, tint = p.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { NText("Включать после перезагрузки", size = 15); NText("VPN поднимется сам, если был включён", muted = true, size = 12) }
                            Switch(checked = autoStart, onCheckedChange = { autoStart = it; Prefs.autoStart = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = p.accentText, checkedTrackColor = p.accent))
                        }
                    }
                }
                item {
                    NCard {
                        NHeading("Помощь")
                        Spacer(Modifier.height(4.dp))
                        NRow(Icons.Rounded.SupportAgent, "Поддержка в Telegram", "нужен включённый VPN") { openUrl(acc?.optString("support_tg").orEmpty()) }
                        val mx = acc?.optString("support_max").orEmpty()
                        if (mx.isNotBlank()) NRow(Icons.Rounded.SupportAgent, "Поддержка в MAX", "работает без VPN") { openUrl(mx) }
                        NRow(Icons.Rounded.Description, "Оферта") { openUrl(acc?.optString("offer_url").orEmpty()) }
                        NRow(Icons.Rounded.Description, "Конфиденциальность") { openUrl(acc?.optString("privacy_url").orEmpty()) }
                        DiagRow()
                        Spacer(Modifier.height(6.dp))
                        NText("Версия ${BuildConfig.VERSION_NAME} · ядро sing-box", muted = true, size = 12)
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }

    @Composable
    private fun DiagRow() {
        var diagState by remember { mutableStateOf("") }
        NRow(Icons.Rounded.BugReport, if (diagState.isEmpty()) "Отправить диагностику" else diagState, "журнал ядра уйдёт на наш сервер") {
            diagState = "Отправляем…"
            Thread {
                val r = try {
                    val t = Prefs.token
                    if (t == null) "Нет аккаунта" else { Api.diag(t, LogBuffer.dump(), Prefs.server, Prefs.excluded.size, VpnState.status.value.toString()); "Диагностика отправлена" }
                } catch (e: Exception) { "Не удалось отправить: ${e.message}" }
                runOnUiThread { diagState = r }
            }.start()
        }
    }

    @Composable
    private fun AccountCard(acc: JSONObject?) {
        val p = LocalPalette.current
        var linkOpen by remember { mutableStateOf(false) }
        var linkText by remember { mutableStateOf("") }
        var linkState by remember { mutableStateOf("") }
        var devices by remember { mutableStateOf<JSONObject?>(null) }
        var devOpen by remember { mutableStateOf(false) }
        var msg by remember { mutableStateOf("") }
        fun loadDevices() { Thread { try { val t = Prefs.token ?: return@Thread; val d = Api.devices(t); runOnUiThread { devices = d } } catch (_: Exception) {} }.start() }
        LaunchedEffect(Unit) { loadDevices() }
        NCard {
            NHeading("Аккаунт")
            Spacer(Modifier.height(4.dp))
            NRow(Icons.Rounded.Group, acc?.optString("username")?.takeIf { it.isNotBlank() } ?: "—",
                if (acc?.optBoolean("active") == true) "осталось дней: ${acc.optInt("days_left")}" else "подписка не активна")
            NRow(Icons.Rounded.Devices, "Устройства", "на подписке: ${devices?.optInt("count") ?: "…"} из ${acc?.optInt("device_limit") ?: "—"}",
                onClick = { devOpen = !devOpen; if (devOpen) loadDevices() })
            if (devOpen) {
                val arr = devices?.optJSONArray("devices")
                if (arr == null || arr.length() == 0) NText("Пока ни одного устройства не зарегистрировано", muted = true, size = 12)
                else (0 until arr.length()).forEach { i ->
                    val d = arr.getJSONObject(i)
                    NText("• ${d.optString("platform")} ${d.optString("model")} · ${d.optString("app").take(24)}", muted = true, size = 12)
                }
                Spacer(Modifier.height(6.dp))
                NGhostButton("Отвязать все устройства", icon = Icons.Rounded.Refresh) {
                    Thread {
                        val r = try { val x = Api.devicesReset(Prefs.token!!); "Отвязано: ${x.optInt("removed")}" } catch (e: Exception) { "Не удалось: ${e.message}" }
                        runOnUiThread { msg = r; loadDevices() }
                    }.start()
                }
            }
            NRow(Icons.Rounded.Refresh, "Перевыпустить ссылку подписки", "старая ссылка перестанет работать") {
                Thread {
                    val r = try { Api.reissue(Prefs.token!!); "Ссылка перевыпущена" } catch (e: Exception) { "Не удалось: ${e.message}" }
                    runOnUiThread { msg = r; refresh(silent = true); restartIfRunning() }
                }.start()
            }
            NRow(Icons.Rounded.Link, "У меня уже есть подписка", "привязать по ссылке из кабинета или бота") { linkOpen = !linkOpen }
            if (linkOpen) {
                NText("Вставьте ссылку подписки (ключ): в кабинете и боте она копируется одним нажатием. Пробный аккаунт этого телефона будет заменён вашей подпиской.", muted = true, size = 12)
                Spacer(Modifier.height(6.dp))
                NField(linkText, { linkText = it }, "https://…/s/…", Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                NButton("Привязать", enabled = linkText.isNotBlank() && linkState != "Проверяем…") {
                    linkState = "Проверяем…"
                    Thread {
                        val r = try {
                            val me = Api.link(Prefs.token ?: error("нет аккаунта"), linkText.trim())
                            Prefs.me = me.toString(); runOnUiThread { account = me }; refresh(silent = true)
                            if (me.optBoolean("already")) "Эта подписка уже привязана" else "Готово: подписка привязана"
                        } catch (e: ApiException) {
                            when (e.message) { "not_found" -> "Подписка по этой ссылке не найдена"; "bad_link" -> "Это не похоже на ссылку подписки"; else -> "Сервер ответил: ${e.message}" }
                        } catch (e: Exception) { "Нет связи с сервером" }
                        runOnUiThread { linkState = r; if (r.startsWith("Готово")) { linkText = ""; restartIfRunning() } }
                    }.start()
                }
                if (linkState.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(linkState, color = if (linkState.startsWith("Готово")) p.ok else p.warn, size = 13) }
            }
            if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(msg, color = if (msg.startsWith("Не")) p.warn else p.ok, size = 13) }
        }
    }

    // ── Реферальная программа: та же база и правила, что у бота NOCTILIS ──

    @Composable
    private fun ReferralSection() {
        val p = LocalPalette.current
        var info by remember { mutableStateOf<JSONObject?>(null) }
        var err by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        var reqs by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf("") }
        fun load() {
            Thread {
                try { val t = Prefs.token ?: error("нет аккаунта"); val r = Api.ref(t); runOnUiThread { info = r; err = "" } }
                catch (e: Exception) { runOnUiThread { err = "Реферальная программа сейчас недоступна" } }
            }.start()
        }
        LaunchedEffect(Unit) { load() }
        NHeading("Реферальная программа")
        Spacer(Modifier.height(4.dp))
        val i = info
        if (i == null) { NText(if (err.isEmpty()) "Загружаем…" else err, muted = true, size = 13); return }
        val rub = { kop: Int -> "${kop / 100} ₽" }
        NText("Вы получаете ${i.optInt("l1_pct")}% с каждой оплаты тех, кого пригласили, и ${i.optInt("l2_pct")}% с оплат тех, кого пригласили они.", size = 14)
        Spacer(Modifier.height(8.dp))
        NText("Приглашено: ${i.optInt("l1")} · второй уровень: ${i.optInt("l2")} · оплатили: ${i.optInt("paid")}", muted = true, size = 13)
        NText("Заработано: ${rub(i.optInt("earned_total"))} · к выводу: ${rub(i.optInt("withdrawable"))} (от ${i.optInt("withdraw_min")} ₽)", muted = true, size = 13)
        Spacer(Modifier.height(8.dp))
        val link = i.optString("link")
        NLabel("Ваш код")
        Text(i.optString("code"), color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        NText(link, color = p.accent, size = 13)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NGhostButton("Скопировать", Modifier.weight(1f)) { copy(link); msg = "Ссылка скопирована" }
            NGhostButton("Поделиться", Modifier.weight(1f)) {
                val text = "NOCTILIS — VPN, который работает: банки и Госуслуги мимо туннеля, остальное через VPN. Ставь по ссылке и введи мой код ${i.optString("code")} в настройках: $link"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться"))
            }
        }
        if (i.optBoolean("can_apply")) {
            Spacer(Modifier.height(12.dp))
            NText("Есть код приглашения? Введите его, и пригласивший получит долю с ваших оплат. Вы ничего не теряете.", muted = true, size = 12)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NField(code, { code = it }, "код", Modifier.weight(1f))
                NGhostButton("Применить", Modifier.width(130.dp)) {
                    val c = code.trim(); if (c.isEmpty()) return@NGhostButton
                    Thread {
                        val r = try { Api.refApply(Prefs.token!!, c); "Код принят" }
                        catch (e: ApiException) { when (e.message) { "self" -> "Это ваш собственный код"; "bad_code" -> "Код не похож на наш"; else -> "Код не принят: такого приглашающего нет или код уже вводили" } }
                        catch (e: Exception) { "Нет связи с сервером" }
                        runOnUiThread { msg = r; if (r == "Код принят") { code = ""; load() } }
                    }.start()
                }
            }
        }
        if (i.optInt("withdrawable") >= i.optInt("withdraw_min") * 100 && !i.optBoolean("pending_withdraw")) {
            Spacer(Modifier.height(12.dp))
            NText("Вывод: телефон и банк (СБП) или номер карты одной строкой. Переводим в течение 3 рабочих дней.", muted = true, size = 12)
            Spacer(Modifier.height(6.dp))
            NField(reqs, { reqs = it }, "+7… Сбер / номер карты", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            NButton("Вывести ${rub(i.optInt("withdrawable"))}") {
                if (reqs.trim().length < 5) { msg = "Укажите реквизиты"; return@NButton }
                Thread {
                    val r = try { val x = Api.refWithdraw(Prefs.token!!, reqs.trim()); "Заявка №${x.optInt("id")} принята, сумма зарезервирована" }
                    catch (e: ApiException) { when (e.message) { "pending" -> "Предыдущая заявка ещё в работе"; "min" -> "Меньше минимальной суммы"; else -> "Не удалось: ${e.message}" } }
                    catch (e: Exception) { "Нет связи с сервером" }
                    runOnUiThread { msg = r; reqs = ""; load() }
                }.start()
            }
        } else if (i.optBoolean("pending_withdraw")) {
            Spacer(Modifier.height(8.dp)); NText("Заявка на вывод в работе, оператор выплатит и отметит.", muted = true, size = 12)
        }
        if (msg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); NText(msg, color = if (msg.startsWith("Код принят") || msg.startsWith("Заявка") || msg.startsWith("Ссылка")) p.ok else p.warn, size = 13) }
    }

    // ── Исключения приложений ──

    private data class AppItem(val pkg: String, val label: String)

    @Composable
    private fun ExclusionsScreen(onBack: () -> Unit) {
        val p = LocalPalette.current
        var apps by remember { mutableStateOf<List<AppItem>?>(null) }
        var excluded by remember { mutableStateOf(Prefs.excluded) }
        var query by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { installedApps() } }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Header("Исключения", onBack)
            NText("Отмеченные приложения работают мимо VPN: банки, Госуслуги, MAX, карты — как без VPN.", muted = true, size = 13)
            Spacer(Modifier.height(10.dp))
            NField(query, { query = it }, "Поиск", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = p.accent) }
            } else {
                val q = query.trim().lowercase()
                val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                NText("Исключено: ${excluded.size}", muted = true, size = 12)
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.pkg }) { app ->
                        val checked = app.pkg in excluded
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                excluded = if (checked) excluded - app.pkg else excluded + app.pkg
                                Prefs.excluded = excluded
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = p.accent, uncheckedColor = p.muted, checkmarkColor = p.accentText))
                            Spacer(Modifier.width(8.dp))
                            Column { NText(app.label, size = 15); NText(app.pkg, muted = true, size = 11) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                NButton("Готово") { restartIfRunning(); onBack() }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    private fun installedApps(): List<AppItem> {
        val pm = packageManager
        val launchable = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
        val excluded = Prefs.excluded
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .filter { it.packageName in launchable || it.packageName in DefaultExclusions.packages || it.packageName in excluded || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString()) }
            .distinctBy { it.pkg }
            .sortedWith(compareBy({ it.pkg !in excluded }, { it.label.lowercase() }))
    }

    // ── Оплата ──

    @Composable
    private fun PayScreen(onBack: () -> Unit) {
        val p = LocalPalette.current
        val acc = account
        val days = acc?.optInt("days_left") ?: 0
        val active = acc?.optBoolean("active") ?: false
        val plans = acc?.optJSONArray("plans")
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Header("Оплата", onBack)
            NCard {
                NLabel("Срок")
                Spacer(Modifier.height(4.dp))
                Text(if (active) "Осталось дней: $days" else "Подписка закончилась", color = if (active && days > 3) p.text else p.warn, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                if (active) { Spacer(Modifier.height(4.dp)); NText("Баланс: ${acc?.optInt("balance_rub") ?: 0} ₽ — это оплаченные дни × 5 ₽", muted = true, size = 13) }
            }
            Spacer(Modifier.height(12.dp))
            NCard {
                NHeading("Оформление подписки")
                Spacer(Modifier.height(4.dp))
                NText("5 ₽ в день. Дни добавляются к текущему сроку. Оплата картой, откроется страница оплаты; там же можно включить автопродление.", muted = true, size = 13)
                Spacer(Modifier.height(10.dp))
                if (plans != null) {
                    (0 until plans.length()).forEach { i ->
                        val pl = plans.getJSONObject(i)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.card2).border(1.dp, p.line, RoundedCornerShape(16.dp))
                                .clickable { openPay(pl.optInt("days")) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) { NText(pl.optString("note"), size = 15); NText("на ${pl.optInt("days")} дней", muted = true, size = 12) }
                            Text("${pl.optInt("price")} ₽", color = p.accent, fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    NButton("Оплатить ${acc?.optInt("price_rub") ?: 150} ₽") { openPay() }
                }
                Spacer(Modifier.height(4.dp))
                NText("После оплаты вернитесь в приложение — срок обновится сам.", muted = true, size = 12)
                Spacer(Modifier.height(8.dp))
                NGhostButton("Проверить оплату", icon = Icons.Rounded.Refresh) { refresh() }
                if (loading) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator(color = p.accent) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
