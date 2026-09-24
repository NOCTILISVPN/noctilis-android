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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AppSettingsAlt
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.noctilis.app.screens.BannerScreen
import net.noctilis.app.screens.BonusScreen
import net.noctilis.app.screens.BonusState
import net.noctilis.app.screens.CabinetScreen
import net.noctilis.app.screens.GuideScreen
import net.noctilis.app.screens.StoryScreen
import net.noctilis.app.screens.SupportScreen
import net.noctilis.app.ui.BodyFont
import net.noctilis.app.ui.DarkPalette
import net.noctilis.app.ui.HeadFont
import net.noctilis.app.ui.HeroVideo
import net.noctilis.app.ui.LightPalette
import net.noctilis.app.ui.LocalPalette
import net.noctilis.app.ui.NBadge
import net.noctilis.app.ui.NBig
import net.noctilis.app.ui.NButton
import net.noctilis.app.ui.NCard
import net.noctilis.app.ui.NField
import net.noctilis.app.ui.NGhostButton
import net.noctilis.app.ui.NHeader
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

private enum class Screen { Home, Settings, Exclusions, Pay, Servers, Bonus, Cabinet, Support, Guide, Banner, Story }

class MainActivity : ComponentActivity(), Host {

    /** Аккаунт и конфигурация — состояние экрана. */
    override var account by mutableStateOf<JSONObject?>(Prefs.me?.let { runCatching { JSONObject(it) }.getOrNull() })
    override val token: String? get() = Prefs.token
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var themeName by mutableStateOf(Prefs.theme)
    private val bonus = BonusState()
    private var refreshJob: Job? = null
    override val scope: CoroutineScope get() = lifecycleScope
    /** Уведомление «продлите подписку» при уже открытом приложении (singleTask) приходит в onNewIntent — открываем «Оплату». */
    private var openPayTick by mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra("screen") == "pay") openPayTick++
    }

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

    /**
     * Регистрация (если надо) + /me + /config + проверка обновления.
     * Корутина в lifecycleScope: при уничтожении активности (поворот) отменяется, а не пишет
     * в состояние мёртвой активности. Одновременно идёт один запрос: раньше onResume и первый
     * запуск экрана стартовали два потока сразу, и на первом запуске оба вызывали /register.
     */
    override fun refresh(silent: Boolean) {
        if (refreshJob?.isActive == true) { if (!silent) loading = true; return }
        loading = !silent
        lifecycleScope.launch(Dispatchers.IO) { Updater.check() }
        refreshJob = lifecycleScope.launch {
            try {
                val me = withContext(Dispatchers.IO) {
                    var token = Prefs.token
                    if (token == null) {
                        val r = Api.register(applicationContext)
                        token = r.getString("token")
                        Prefs.token = token
                        Prefs.me = r.toString()
                    }
                    val me = Api.me(token)
                    Prefs.me = me.toString()
                    me
                }
                account = me; error = null
                withContext(Dispatchers.IO) {
                    Reminder.check(applicationContext, me)
                    if (me.optBoolean("active")) Prefs.config = Api.config(Prefs.token ?: return@withContext).toString()
                }
            } catch (e: ApiException) {
                if (e.code == 401) Prefs.token = null
                if (!silent || account == null) error = describe(e)
            } catch (e: Exception) {
                if (!silent || account == null) error = describe(e)
            } finally {
                loading = false
            }
        }
    }

    private fun describe(e: Exception): String = when {
        e is ApiException && e.code == 402 -> "Подписка закончилась — продлите её"
        e is ApiException -> "Сервер ответил: ${e.message}"
        else -> "Нет связи с сервером. Проверьте интернет и попробуйте ещё раз"
    }

    override fun openUrl(url: String) {
        if (url.isBlank()) return
        try { CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url)) }
        catch (_: Exception) { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    }

    private fun openPay(days: Int? = null) {
        val base = account?.optString("pay_url").orEmpty()
        if (base.isBlank()) { refresh(); return }
        // выбор автопродления из кабинета приложения уезжает на страницу оплаты (?autopay=0/1)
        val ap = account?.optJSONObject("autopay")?.optBoolean("on") ?: true
        openUrl(base + (if (days != null) "&days=$days" else "") + "&autopay=" + (if (ap) "1" else "0"))
    }

    override fun copy(text: String, toast: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("NOCTILIS", text))
        toast(toast)
    }

    override fun share(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Поделиться"))
    }

    override fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    override fun runUi(block: () -> Unit) { runOnUiThread(block) }

    /**
     * После смены исключений или подписки — пересоздать туннель, если он включён.
     * Сервис ставит команды в очередь, поэтому START можно слать сразу за STOP; контекст —
     * Application, чтобы не держать активность.
     */
    private fun restartIfRunning() {
        if (VpnState.isRunning) {
            val app = applicationContext
            VPNService.stop(app)
            if (VpnService.prepare(app) == null) VPNService.start(app)
        }
    }

    @Composable
    private fun Root() {
        val p = LocalPalette.current
        // rememberSaveable: поворот экрана не выбрасывает на главный
        var screen by rememberSaveable { mutableStateOf(if (intent?.getStringExtra("screen") == "pay") Screen.Pay else Screen.Home) }
        LaunchedEffect(openPayTick) { if (openPayTick > 0) screen = Screen.Pay }
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
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {   // обновление: не только при возврате на экран, но и раз в 15 минут (24.09);
            // только пока приложение на экране — свёрнутое не ходит в сеть и не тратит батарею
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) { delay(15 * 60_000L); withContext(Dispatchers.IO) { Updater.check() } }
            }
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
        val home = { screen = Screen.Home }
        val subChanged = { refresh(silent = true); restartIfRunning() }

        Box(Modifier.fillMaxSize().background(p.bg)) {
            when (screen) {
                Screen.Home -> HomeScreen(status, ::toggle) { screen = it }
                Screen.Servers -> { BackHandler(onBack = home); ServersScreen(home) }
                Screen.Settings -> { BackHandler(onBack = home); SettingsScreen(home) { screen = Screen.Support } }
                Screen.Exclusions -> { BackHandler(onBack = home); ExclusionsScreen(home) }
                Screen.Pay -> { BackHandler(onBack = home); PayScreen(home) }
                Screen.Cabinet -> { BackHandler(onBack = home); CabinetScreen(this@MainActivity, home, subChanged) { screen = Screen.Pay } }
                Screen.Support -> { BackHandler(onBack = home); SupportScreen(this@MainActivity, home) }
                Screen.Bonus -> { BackHandler(onBack = home); BonusScreen(this@MainActivity, bonus, home) { screen = when (it) { "guide" -> Screen.Guide; "banner" -> Screen.Banner; else -> Screen.Story } } }
                Screen.Guide -> { BackHandler { screen = Screen.Bonus }; GuideScreen(this@MainActivity, bonus) { screen = Screen.Bonus } }
                Screen.Banner -> { BackHandler { screen = Screen.Bonus }; BannerScreen(this@MainActivity, bonus) { screen = Screen.Bonus } }
                Screen.Story -> { BackHandler { screen = Screen.Bonus }; StoryScreen(this@MainActivity, bonus) { screen = Screen.Bonus } }
            }
        }
    }

    // ── Главный экран: страж, сервер сверху по центру, срок, большая кнопка, шесть кнопок ──

    @Composable
    private fun HomeScreen(status: VpnStatus, onToggle: () -> Unit, go: (Screen) -> Unit) {
        val p = LocalPalette.current
        val acc = account
        val days = acc?.optInt("days_left") ?: 0
        val active = acc?.optBoolean("active") ?: false
        val paid = acc?.optBoolean("paid") ?: false
        val update by Updater.available.collectAsState()
        val updState by Updater.state.collectAsState()
        val pings by CoreClient.pings.collectAsState()
        val connected = status is VpnStatus.Connected
        val server = Prefs.server
        val serverLabel = if (server == "auto") "Автовыбор" else server
        val ping = if (server == "auto") pings.values.filter { it.delayMs > 0 }.minOfOrNull { it.delayMs } else pings[server]?.delayMs?.takeIf { it > 0 }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val small = maxHeight < 620.dp   // очень маленький экран — прокрутка, иначе всё влезает без неё (Андрей 24.09)
        Column(Modifier.fillMaxSize().then(if (small) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
            Box(Modifier.fillMaxWidth()) {
                HeroVideo()
                Text("NOCTILIS", color = p.text, fontFamily = HeadFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 5.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 6.dp))
            }
            Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                NCard(padding = 16.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NHeading("Тариф «Полный»", 15)
                        Spacer(Modifier.weight(1f))
                        when {
                            acc == null && loading -> NBadge("Подключаемся…", p.muted)
                            acc == null -> NBadge("Нет аккаунта", p.danger)
                            !active -> NBadge("Не активна", p.danger)
                            paid -> NBadge("Активна", p.ok)
                            else -> NBadge("Пробный период", p.warn)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    NBig(if (active) "$days" else "0", if (active) Fmt.dw(days) else "дней", 36)
                    NText(if (active) "до " + Fmt.date(acc?.optLong("expiry_ms") ?: 0L) else "Подписка закончилась — продлите в разделе «Оплата»", muted = active, color = if (active) null else p.warn, size = 13)
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        NText(it, color = p.warn, size = 12)
                        Text("нажмите, чтобы повторить", color = p.muted, fontFamily = BodyFont, fontSize = 12.sp, modifier = Modifier.clickable { refresh() })
                    }
                }
                if (update != null) {
                    Spacer(Modifier.height(6.dp))
                    NCard(Modifier.clickable(enabled = !Updater.isDownloading) { Updater.download(this@MainActivity, update!!) }) {
                        NText(if (updState.isEmpty()) "Доступна версия ${update!!.version} · нажмите, чтобы обновить" else updState, color = p.accent, size = 14)
                    }
                }
                if (small) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
                val busy = status is VpnStatus.Starting || status is VpnStatus.Stopping
                Box(
                    Modifier.size(172.dp).clip(CircleShape)
                        .background(if (connected) accentGradient(p) else Brush.linearGradient(listOf(p.card2, p.card2)))
                        .border(2.dp, if (connected) p.accent else p.line, CircleShape)
                        .clickable(enabled = !busy) { onToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = p.accent)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Shield, null, tint = if (connected) p.accentText else p.accent, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(if (connected) "ВЫКЛЮЧИТЬ" else "ВКЛЮЧИТЬ", color = if (connected) p.accentText else p.text,
                            fontFamily = HeadFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                    // сервер — под кнопкой включения (Андрей 24.09), нажатие открывает список
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(p.card).border(1.dp, p.line, RoundedCornerShape(999.dp))
                            .clickable { go(Screen.Servers) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Language, null, tint = p.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(serverLabel, color = p.text, fontFamily = BodyFont, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (ping != null) { Spacer(Modifier.width(6.dp)); Text("· $ping мс", color = if (ping < 400) p.ok else p.warn, fontFamily = BodyFont, fontSize = 12.sp) }
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = p.muted, fontSize = 16.sp)
                    }
                Spacer(Modifier.height(6.dp))
                NText(
                    when (status) {
                        is VpnStatus.Connected -> "VPN включён · защищённое соединение"
                        is VpnStatus.Error -> status.message
                        else -> "VPN выключен"
                    },
                    muted = status !is VpnStatus.Error, color = if (status is VpnStatus.Error) p.warn else null, size = 13,
                )
                if (small) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuTile(Icons.Rounded.AccountCircle, "Кабинет", Modifier.weight(1f)) { go(Screen.Cabinet) }
                    MenuTile(Icons.Rounded.AppSettingsAlt, "Исключения", Modifier.weight(1f)) { go(Screen.Exclusions) }
                    MenuTile(Icons.Rounded.CreditCard, "Оплата", Modifier.weight(1f), accent = !active || days <= 3) { go(Screen.Pay) }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MenuTile(Icons.Rounded.Redeem, "Бонусы", Modifier.weight(1f)) { go(Screen.Bonus) }
                    MenuTile(Icons.Rounded.Settings, "Настройки", Modifier.weight(1f)) { go(Screen.Settings) }
                    MenuTile(Icons.Rounded.SupportAgent, "Поддержка", Modifier.weight(1f)) { go(Screen.Support) }
                }
                if (small) Spacer(Modifier.height(12.dp)) else Spacer(Modifier.weight(0.7f))
            }
        }
        }
    }

    @Composable
    private fun MenuTile(icon: ImageVector, text: String, modifier: Modifier, accent: Boolean = false, onClick: () -> Unit) {
        val p = LocalPalette.current
        Column(
            modifier.height(72.dp).clip(RoundedCornerShape(18.dp))
                .background(if (accent) accentGradient(p) else Brush.linearGradient(listOf(p.card, p.card)))
                .border(1.dp, if (accent) Color.Transparent else p.line, RoundedCornerShape(20.dp))
                .clickable { onClick() }.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = if (accent) p.accentText else p.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(5.dp))
            Text(text, color = if (accent) p.accentText else p.text, fontFamily = BodyFont, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, letterSpacing = 0.sp)
        }
    }

    // ── Серверы ──

    @Composable
    private fun ServersScreen(onBack: () -> Unit) {
        val p = LocalPalette.current
        val cfg = Prefs.config
        val servers = remember(cfg) { if (cfg != null) ConfigBuilder.serverTags(cfg) else emptyList() }
        var server by remember { mutableStateOf(Prefs.server) }
        val status by VpnState.status.collectAsState()
        val pings by CoreClient.pings.collectAsState()
        val testing by CoreClient.testing.collectAsState()
        val ui = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            NHeader("Серверы", onBack)
            NCard {
                NText("Автовыбор берёт самый быстрый. Переключение работает на лету, без разрыва.", muted = true, size = 12)
                Spacer(Modifier.height(8.dp))
                val canTest = !testing && (status is VpnStatus.Connected || status is VpnStatus.Disconnected || status is VpnStatus.Error)
                NGhostButton(if (testing) "Проверяем…" else "Проверить пинг", icon = Icons.Rounded.Refresh) {
                    // разбор конфига и команда ядру — не в главном потоке
                    if (canTest) ui.launch(Dispatchers.IO) { if (VpnState.status.value is VpnStatus.Connected) CoreClient.testAll() else ProbeService.probe() }
                }
                Spacer(Modifier.height(8.dp))
                if (servers.isEmpty()) NText("Список серверов появится после первого обмена с сервером NOCTILIS.", muted = true, size = 13)
                (listOf("auto") + servers).forEach { tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            server = tag; Prefs.server = tag
                            // выбор сервера — обращение к ядру по сокету, в главном потоке подвешивало интерфейс
                            ui.launch { if (!withContext(Dispatchers.IO) { CoreClient.select(tag) }) restartIfRunning() }
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = server == tag, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = p.accent, unselectedColor = p.muted))
                        Spacer(Modifier.width(8.dp))
                        // у «Автовыбора» ядро отдаёт пинг прошлого выбора группы — показываем лучший из списка и какой это сервер
                        val best = if (tag == "auto") servers.mapNotNull { pings[it] }.filter { it.delayMs > 0 }.minByOrNull { it.delayMs } else null
                        if (tag == "auto") Column(Modifier.weight(1f)) {
                            NText("Автовыбор", size = 15)
                            NText(if (best != null) "сейчас самый быстрый — ${best.tag}" else "берёт самый быстрый сервер", muted = true, size = 12)
                        } else { NText(tag, size = 15); Spacer(Modifier.weight(1f)) }
                        val ping = if (tag == "auto") best else pings[tag]
                        if (ping != null) NText(if (ping.delayMs > 0) "${ping.delayMs} мс" else "нет ответа", size = 13,
                            color = when { ping.delayMs <= 0 -> p.danger; ping.delayMs < 400 -> p.ok; else -> p.warn })
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    // ── Настройки: оформление, автозапуск, помощь ──

    @Composable
    private fun SettingsScreen(onBack: () -> Unit, onSupport: () -> Unit) {
        val p = LocalPalette.current
        var autoStart by remember { mutableStateOf(Prefs.autoStart) }
        var faqOpen by remember { mutableStateOf(-1) }
        val acc = account
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            NHeader("Настройки", onBack)
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
            Spacer(Modifier.height(12.dp))
            NCard {
                NHeading("Помощь")
                Spacer(Modifier.height(4.dp))
                NRow(Icons.Rounded.SupportAgent, "Написать в поддержку", "чат прямо здесь, без мессенджеров", onClick = onSupport)
                NRow(Icons.Rounded.Campaign, "Новости", "канал NOCTILIS в Telegram") { openUrl(acc?.optString("news_url").orEmpty().ifBlank { "https://t.me/noctilis_vpn_channel" }) }
                DiagRow()
                NRow(Icons.Rounded.Description, "Оферта") { openUrl(acc?.optString("offer_url").orEmpty().ifBlank { "https://noctilis.net/offer" }) }
                NRow(Icons.Rounded.Description, "Конфиденциальность") { openUrl(acc?.optString("privacy_url").orEmpty().ifBlank { "https://noctilis.net/privacy" }) }
                Spacer(Modifier.height(6.dp))
                NText("Версия ${BuildConfig.VERSION_NAME} · ядро sing-box", muted = true, size = 12)
            }
            Spacer(Modifier.height(12.dp))
            NCard {
                NHeading("Частые вопросы")
                Spacer(Modifier.height(4.dp))
                FAQ.forEachIndexed { i, (q, a) ->
                    Column(Modifier.fillMaxWidth().clickable { faqOpen = if (faqOpen == i) -1 else i }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.HelpOutline, null, tint = p.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
                            NText(q, size = 14)
                        }
                        if (faqOpen == i) { Spacer(Modifier.height(4.dp)); NText(a, muted = true, size = 13) }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    @Composable
    private fun DiagRow() {
        var diagState by remember { mutableStateOf("") }
        val ui = rememberCoroutineScope()
        NRow(Icons.Rounded.BugReport, if (diagState.isEmpty()) "Отправить диагностику" else diagState, "журнал ядра уйдёт на наш сервер") {
            if (diagState == "Отправляем…") return@NRow
            diagState = "Отправляем…"
            ui.launch {
                diagState = withContext(Dispatchers.IO) {
                    try {
                        val t = Prefs.token
                        if (t == null) "Нет аккаунта" else { Api.diag(t, LogBuffer.dump(), Prefs.server, Prefs.excluded.size, VpnState.status.value.toString()); "Диагностика отправлена" }
                    } catch (e: Exception) { "Не удалось отправить: ${e.message}" }
                }
            }
        }
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
            NHeader("Исключения", onBack)
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
            NHeader("Оплата", onBack)
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
                        val pl = plans.optJSONObject(i) ?: return@forEach
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
                val ap = acc?.optJSONObject("autopay")
                NText(
                    if (ap?.optBoolean("on") == true) "Автопродление включено: карта сохранится при оплате, дальше списание само в последний день. Выключить — в «Кабинете»."
                    else "Автопродление выключено — включить можно в «Кабинете» или на странице оплаты.",
                    muted = true, size = 12,
                )
                Spacer(Modifier.height(4.dp))
                NText("Есть промокод? Введите его в разделе «Бонусы» до оплаты. После оплаты вернитесь в приложение — срок обновится сам.", muted = true, size = 12)
                Spacer(Modifier.height(8.dp))
                NGhostButton("Проверить оплату", icon = Icons.Rounded.Refresh) { refresh() }
                if (loading) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator(color = p.accent) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    companion object {
        private val FAQ = listOf(
            "Не открываются сайты после включения" to "Проверьте, что сверху выбран сервер с пингом, и попробуйте другой в списке серверов. Если не помогло — «Отправить диагностику» и напишите в поддержку.",
            "Банк или Госуслуги не работают" to "Откройте «Исключения» и отметьте это приложение — оно пойдёт мимо VPN, как без него. Банки, Госуслуги, MAX, карты и VK отмечены по умолчанию.",
            "Почему подписка «Пробный период»" to "Первые 3 дня бесплатно. Дальше 5 ₽ в день: раздел «Оплата», карта, 30/90/180 дней. Дни добавляются к текущему сроку.",
            "Как подключить второй телефон или компьютер" to "На подписке 3 места. На втором телефоне поставьте NOCTILIS и в «Кабинете» привяжите подписку по ссылке. На компьютере — Happ по той же ссылке подписки.",
            "Как заработать" to "Раздел «Бонусы»: 35 % с оплат приглашённых и 15 % со второго уровня, премии за ролики и сторис. Вывод от 500 ₽ на карту или по СБП.",
        )
    }
}
