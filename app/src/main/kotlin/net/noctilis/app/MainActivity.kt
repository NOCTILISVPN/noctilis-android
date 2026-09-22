package net.noctilis.app

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.noctilis.app.vpn.VPNService
import net.noctilis.app.vpn.VpnState
import net.noctilis.app.vpn.VpnStatus
import org.json.JSONObject

// Цвета NOCTILIS: ночь, холодный свет глаз персонажа.
private val Night = Color(0xFF0B1020)
private val Panel = Color(0xFF141B33)
private val Moon = Color(0xFF9FB8FF)
private val Fog = Color(0xFF8A93A8)
private val Glow = Color(0xFF4F7CFF)
private val Warn = Color(0xFFFF8A65)

private val NoctilisColors = darkColorScheme(
    primary = Moon, background = Night, surface = Panel,
    onBackground = Color.White, onSurface = Color.White,
)

private enum class Screen { Home, Settings, Exclusions, Pay }

class MainActivity : ComponentActivity() {

    /** Аккаунт и конфигурация — состояние экрана. */
    private var account by mutableStateOf<JSONObject?>(Prefs.me?.let { runCatching { JSONObject(it) }.getOrNull() })
    private var loading by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initExclusions()
        setContent {
            MaterialTheme(colorScheme = NoctilisColors) {
                Root()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // после возврата с оплаты — подтянуть свежий срок
        refresh(silent = true)
    }

    /** Первый запуск: исключения по умолчанию из того, что реально установлено. */
    private fun initExclusions() {
        if (!Prefs.exclusionsInitialized) {
            Prefs.excluded = DefaultExclusions.installed(packageManager)
            Prefs.exclusionsInitialized = true
        }
    }

    /** Регистрация (если надо) + /me + /config. */
    private fun refresh(silent: Boolean = false) {
        if (loading) return
        loading = !silent
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
                if (me.optBoolean("active")) {
                    val cfg = Api.config(token)
                    Prefs.config = cfg.toString()
                }
            } catch (e: ApiException) {
                if (e.code == 401) Prefs.token = null   // токен потерян на сервере — заведём заново
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

    private fun openPay() {
        val url = account?.optString("pay_url").orEmpty()
        if (url.isBlank()) return
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf(Screen.Home) }
        val status by VpnState.status.collectAsState()
        val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) VPNService.start(this)
        }
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            if (account == null || Prefs.config == null) refresh()
        }

        fun toggle() {
            if (VpnState.isRunning) {
                VPNService.stop(this); return
            }
            val acc = account
            if (acc == null) { refresh(); return }
            if (!acc.optBoolean("active")) { screen = Screen.Pay; return }
            if (Prefs.config == null) { refresh(); return }
            val intent = VpnService.prepare(this)
            if (intent != null) vpnPermission.launch(intent) else VPNService.start(this)
        }

        Box(Modifier.fillMaxSize().background(Night).statusBarsPadding().navigationBarsPadding()) {
            when (screen) {
                Screen.Home -> HomeScreen(status, ::toggle,
                    onSettings = { screen = Screen.Settings },
                    onExclusions = { screen = Screen.Exclusions },
                    onPay = { screen = Screen.Pay })
                Screen.Settings -> { BackHandler { screen = Screen.Home }; SettingsScreen { screen = Screen.Home } }
                Screen.Exclusions -> { BackHandler { screen = Screen.Home }; ExclusionsScreen { screen = Screen.Home } }
                Screen.Pay -> { BackHandler { screen = Screen.Home }; PayScreen { screen = Screen.Home } }
            }
        }
    }

    // ── Главный экран: 4 кнопки ──

    @Composable
    private fun HomeScreen(status: VpnStatus, onToggle: () -> Unit, onSettings: () -> Unit, onExclusions: () -> Unit, onPay: () -> Unit) {
        val acc = account
        val days = acc?.optInt("days_left") ?: 0
        val active = acc?.optBoolean("active") ?: false
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text("NOCTILIS", color = Moon, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    acc == null && loading -> "Подключаемся к серверу…"
                    acc == null -> "Аккаунт не создан"
                    !active -> "Подписка закончилась"
                    days <= 1 -> "Остался последний день"
                    else -> "Осталось дней: $days"
                },
                color = if (acc != null && (!active || days <= 3)) Warn else Fog, fontSize = 15.sp,
            )
            Spacer(Modifier.weight(1f))

            val (label, color) = when (status) {
                is VpnStatus.Connected -> "ВЫКЛЮЧИТЬ" to Glow
                is VpnStatus.Starting -> "ВКЛЮЧАЕМ…" to Panel
                is VpnStatus.Stopping -> "ВЫКЛЮЧАЕМ…" to Panel
                else -> "ВКЛЮЧИТЬ" to Panel
            }
            Box(
                Modifier.size(200.dp).clip(CircleShape).background(color)
                    .border(3.dp, if (status is VpnStatus.Connected) Moon else Fog, CircleShape)
                    .clickable(enabled = status !is VpnStatus.Starting && status !is VpnStatus.Stopping) { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (status is VpnStatus.Starting || status is VpnStatus.Stopping) {
                    CircularProgressIndicator(color = Moon)
                } else {
                    Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                when (status) {
                    is VpnStatus.Connected -> "VPN включён · защищённое соединение"
                    is VpnStatus.Error -> status.message
                    else -> "VPN выключен"
                },
                color = if (status is VpnStatus.Error) Warn else Fog, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Warn, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { refresh() })
                Text("нажмите, чтобы повторить", color = Fog, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MenuButton("Настройки", Modifier.weight(1f), onSettings)
                MenuButton("Исключения", Modifier.weight(1f), onExclusions)
                MenuButton("Оплата", Modifier.weight(1f), onPay, accent = !active || days <= 3)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    @Composable
    private fun MenuButton(text: String, modifier: Modifier, onClick: () -> Unit, accent: Boolean = false) {
        Box(
            modifier.height(52.dp).clip(RoundedCornerShape(14.dp))
                .background(if (accent) Glow else Panel).clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
    }

    @Composable
    private fun Header(title: String, onBack: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Moon, fontSize = 34.sp, modifier = Modifier.clickable { onBack() }.padding(end = 14.dp))
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }

    // ── Настройки ──

    @Composable
    private fun SettingsScreen(onBack: () -> Unit) {
        val cfg = Prefs.config
        val servers = remember(cfg) { if (cfg != null) ConfigBuilder.serverTags(cfg) else emptyList() }
        var server by remember { mutableStateOf(Prefs.server) }
        var autoStart by remember { mutableStateOf(Prefs.autoStart) }
        val acc = account
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Header("Настройки", onBack)
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text("Сервер", color = Fog, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                }
                items(listOf("auto") + servers) { tag ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            server = tag; Prefs.server = tag; restartIfRunning()
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = server == tag, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Moon, unselectedColor = Fog))
                        Spacer(Modifier.width(8.dp))
                        Text(if (tag == "auto") "Автовыбор (самый быстрый)" else tag, color = Color.White, fontSize = 15.sp)
                    }
                }
                item {
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Включать после перезагрузки", color = Color.White, fontSize = 15.sp)
                            Text("VPN поднимется сам, если был включён", color = Fog, fontSize = 12.sp)
                        }
                        Switch(checked = autoStart, onCheckedChange = { autoStart = it; Prefs.autoStart = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Moon, checkedTrackColor = Glow))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Аккаунт", color = Fog, fontSize = 13.sp)
                    Text(acc?.optString("username") ?: "—", color = Color.White, fontSize = 15.sp)
                    Text("Устройств на подписке: ${acc?.optInt("device_limit") ?: "—"}", color = Fog, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Обновить список серверов", color = Moon, fontSize = 15.sp,
                        modifier = Modifier.clickable { refresh() }.padding(vertical = 6.dp))
                    Spacer(Modifier.height(24.dp))
                    Text("Версия ${BuildConfig.VERSION_NAME} · ядро sing-box", color = Fog, fontSize = 12.sp)
                }
            }
        }
    }

    /** После смены сервера или исключений — пересоздать туннель, если он включён. */
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

    // ── Исключения приложений ──

    private data class AppItem(val pkg: String, val label: String)

    @Composable
    private fun ExclusionsScreen(onBack: () -> Unit) {
        var apps by remember { mutableStateOf<List<AppItem>?>(null) }
        var excluded by remember { mutableStateOf(Prefs.excluded) }
        var query by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            apps = withContext(Dispatchers.IO) { installedApps() }
        }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Header("Исключения", onBack)
            Text("Отмеченные приложения работают мимо VPN: банки, Госуслуги, MAX, карты — как без VPN.",
                color = Fog, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Поиск", color = Fog) },
                keyboardOptions = KeyboardOptions.Default,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Moon, unfocusedBorderColor = Fog,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Moon,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Moon) }
            } else {
                val q = query.trim().lowercase()
                val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                Text("Исключено: ${excluded.size}", color = Fog, fontSize = 12.sp)
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
                            Checkbox(checked = checked, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = Glow, uncheckedColor = Fog))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(app.label, color = Color.White, fontSize = 15.sp)
                                Text(app.pkg, color = Fog, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Glow)
                        .clickable { scope.launch { restartIfRunning(); onBack() } },
                    contentAlignment = Alignment.Center,
                ) { Text("Готово", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }

    /** Установленные приложения с иконкой в лаунчере + всё из списка по умолчанию; исключённые — сверху. */
    private fun installedApps(): List<AppItem> {
        val pm = packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.activityInfo.packageName }.toSet()
        val excluded = Prefs.excluded
        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .filter { it.packageName in launchable || it.packageName in DefaultExclusions.packages || it.packageName in excluded ||
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { AppItem(it.packageName, pm.getApplicationLabel(it).toString()) }
            .distinctBy { it.pkg }
            .sortedWith(compareBy({ it.pkg !in excluded }, { it.label.lowercase() }))
    }

    // ── Оплата ──

    @Composable
    private fun PayScreen(onBack: () -> Unit) {
        val acc = account
        val days = acc?.optInt("days_left") ?: 0
        val active = acc?.optBoolean("active") ?: false
        val price = acc?.optInt("price_rub") ?: 150
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Header("Оплата", onBack)
            Spacer(Modifier.height(10.dp))
            Text(if (active) "Осталось дней: $days" else "Подписка закончилась", color = if (active && days > 3) Color.White else Warn, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text("$price ₽ за 30 дней · 5 ₽ в день. Оплата картой, откроется страница оплаты. Дни добавятся к текущему сроку.",
                color = Fog, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp)).background(Glow)
                    .clickable { openPay() },
                contentAlignment = Alignment.Center,
            ) { Text("Оплатить $price ₽", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(14.dp))
            Text("После оплаты вернитесь в приложение — срок обновится сам. Если нет, нажмите «Проверить».",
                color = Fog, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Text("Проверить оплату", color = Moon, fontSize = 15.sp, modifier = Modifier.clickable { refresh() }.padding(vertical = 6.dp))
            if (loading) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator(color = Moon) }
        }
    }
}

private fun Modifier.clip(shape: androidx.compose.ui.graphics.Shape): Modifier = androidx.compose.ui.draw.clip(this, shape)
