package net.noctilis.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import io.nekohasekai.libbox.TunOptions
import net.noctilis.app.Api
import net.noctilis.app.ConfigBuilder
import net.noctilis.app.Reminder
import org.json.JSONObject
import net.noctilis.app.MainActivity
import net.noctilis.app.Prefs
import net.noctilis.app.R
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Туннель: VpnService Android + ядро sing-box через CommandServer (libbox 1.14).
 * Управление: ACTION_START / ACTION_STOP. Состояние — VpnState.
 */
class VPNService : VpnService(), PlatformInterfaceWrapper, CommandServerHandler {

    companion object {
        const val ACTION_START = "net.noctilis.app.START"
        const val ACTION_STOP = "net.noctilis.app.STOP"
        private const val CHANNEL = "vpn"
        private const val NOTIFY_ID = 1

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, VPNService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, VPNService::class.java).setAction(ACTION_STOP))
        }
    }

    @Volatile private var commandServer: CommandServer? = null
    @Volatile private var tunFd: ParcelFileDescriptor? = null
    @Volatile private var destroyed = false
    private val pendingStarts = AtomicInteger(0)   // START-команды в очереди: стоп перед ними не снимает уведомление

    /**
     * Старт и стоп идут по очереди в одном потоке: раньше команда, пришедшая во время
     * другой (флаг busy), молча терялась — после смены исключений «стоп → старт» через 300 мс
     * оставлял VPN выключенным, а отзыв VPN системой во время старта не останавливал ядро.
     */
    private val ops = Executors.newSingleThreadExecutor { r -> Thread(r, "noctilis-vpn").apply { isDaemon = true } }

    private fun enqueue(block: () -> Unit) {
        try { ops.execute(block) } catch (_: RejectedExecutionException) {}   // сервис уже уничтожен
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopVpn(startId)
            else -> startVpn(startId)
        }
        return START_STICKY
    }

    private fun startVpn(startId: Int) {
        // startForegroundService требует startForeground в течение 5 с — делаем сразу, в главном потоке
        try { showForeground() } catch (e: Exception) { LogBuffer.add("app", "startForeground: $e") }
        if (commandServer == null) VpnState.set(VpnStatus.Starting)
        pendingStarts.incrementAndGet()
        enqueue {
            pendingStarts.decrementAndGet()
            if (destroyed || commandServer != null) return@enqueue
            VpnState.set(VpnStatus.Starting)
            try {
                ProbeService.stop()   // пробник пинга держит тот же unix-сокет ядра — гасим, иначе старт упадёт
                val serverCfg = Prefs.config ?: error("нет конфигурации — открой приложение")
                val cfg = ConfigBuilder.build(serverCfg, Prefs.excluded, Prefs.server)
                LogBuffer.add("app", "старт: сервер=${Prefs.server}, исключений=${Prefs.excluded.size}, конфиг ${cfg.length} байт")
                DefaultNetworkMonitor.start()
                val server = CommandServer(this, this)
                server.start()
                commandServer = server
                server.startOrReloadService(cfg, OverrideOptions())
                CoreClient.start()
                VpnState.set(VpnStatus.Connected)
                Prefs.wantConnected = true
                startReminderLoop()
            } catch (e: Exception) {
                Log.e("NOCTILIS", "start", e)
                LogBuffer.add("app", "ошибка старта: $e")
                VpnState.set(VpnStatus.Error(e.message ?: "не удалось запустить"))
                cleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    /** startId = -1, когда остановку просит не команда (отзыв системой, ядро). */
    private fun stopVpn(startId: Int = -1) {
        Prefs.wantConnected = false
        if (commandServer != null || VpnState.isRunning) VpnState.set(VpnStatus.Stopping)
        enqueue {
            if (destroyed) return@enqueue
            try {
                cleanup()
            } finally {
                VpnState.set(VpnStatus.Disconnected)
                // если следом уже стоит START (перезапуск после смены исключений) — уведомление и сервис оставляем
                if (pendingStarts.get() == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    // stopSelf(startId): если после STOP уже пришёл новый START, сервис не умрёт и старт выполнится
                    if (startId >= 0) stopSelf(startId) else stopSelf()
                }
            }
        }
    }

    @Volatile private var reminderThread: Thread? = null

    /** Пока туннель включён — раз в 6 часов свежие данные аккаунта и напоминание об окончании. */
    private fun startReminderLoop() {
        if (reminderThread != null) return
        reminderThread = thread(name = "noctilis-reminder", isDaemon = true) {
            val me = Thread.currentThread()
            while (commandServer != null && !me.isInterrupted) {
                try { Thread.sleep(6 * 3600 * 1000L) } catch (_: InterruptedException) { break }
                if (commandServer == null) break
                try {
                    val t = Prefs.token ?: continue
                    val acc = Api.me(t)
                    Prefs.me = acc.toString()
                    Reminder.check(this, acc)
                } catch (_: Exception) {}
            }
            if (reminderThread === me) reminderThread = null
        }
    }

    private fun cleanup() {
        reminderThread?.interrupt()
        reminderThread = null
        CoreClient.stop()
        val server = commandServer
        commandServer = null
        if (server != null) {
            try { server.closeService() } catch (e: Exception) { Log.w("NOCTILIS", "closeService", e) }
            try { server.close() } catch (e: Exception) { Log.w("NOCTILIS", "close", e) }
        }
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        DefaultNetworkMonitor.setListener(null)
        DefaultNetworkMonitor.stop()
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        destroyed = true
        ops.shutdown()
        cleanup()
        if (VpnState.status.value !is VpnStatus.Error) VpnState.set(VpnStatus.Disconnected)
        super.onDestroy()
    }

    // ── CommandServerHandler (ядро просит остановиться/перезагрузиться) ──

    override fun serviceStop() {
        stopVpn()
    }

    override fun serviceReload() {
        val server = commandServer ?: return
        val serverCfg = Prefs.config ?: return
        server.startOrReloadService(ConfigBuilder.build(serverCfg, Prefs.excluded, Prefs.server), OverrideOptions())
    }

    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().also {
        it.available = false
        it.enabled = false
    }

    override fun setSystemProxyEnabled(enabled: Boolean) {}

    override fun triggerNativeCrash() {}

    override fun writeDebugMessage(message: String?) {
        if (message != null) {
            Log.d("sing-box", message)
            LogBuffer.add("core", message)
        }
    }

    override fun connectSSHAgent(): Int = -1

    // ── интерфейс ядра ──

    override fun autoDetectInterfaceControl(fd: Int) {
        if (!protect(fd)) LogBuffer.add("app", "protect($fd) вернул false")
    }

    override fun openTun(options: TunOptions): Int {
        if (prepare(this) != null) error("android: нет разрешения на VPN")
        val builder = Builder()
            .setSession("NOCTILIS")
            .setMtu(options.mtu)
        builder.setMetered(false)

        val inet4 = options.inet4Address
        while (inet4.hasNext()) { val a = inet4.next(); builder.addAddress(a.address(), a.prefix()) }
        val inet6 = options.inet6Address
        while (inet6.hasNext()) { val a = inet6.next(); builder.addAddress(a.address(), a.prefix()) }

        if (options.autoRoute) {
            if (options.dnsMode.value != Libbox.DNSModeDisabled) {
                val dns = options.dnsServerAddress
                while (dns.hasNext()) builder.addDnsServer(dns.next())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val r4 = options.inet4RouteAddress
                if (r4.hasNext()) {
                    while (r4.hasNext()) { val a = r4.next(); builder.addRoute(IpPrefix(InetAddress.getByName(a.address()), a.prefix())) }
                } else if (options.inet4Address.hasNext()) {
                    builder.addRoute("0.0.0.0", 0)
                }
                val r6 = options.inet6RouteAddress
                if (r6.hasNext()) {
                    while (r6.hasNext()) { val a = r6.next(); builder.addRoute(IpPrefix(InetAddress.getByName(a.address()), a.prefix())) }
                } else if (options.inet6Address.hasNext()) {
                    builder.addRoute("::", 0)
                }
                val x4 = options.inet4RouteExcludeAddress
                while (x4.hasNext()) { val a = x4.next(); builder.excludeRoute(IpPrefix(InetAddress.getByName(a.address()), a.prefix())) }
                val x6 = options.inet6RouteExcludeAddress
                while (x6.hasNext()) { val a = x6.next(); builder.excludeRoute(IpPrefix(InetAddress.getByName(a.address()), a.prefix())) }
            } else {
                val r4 = options.inet4RouteRange
                while (r4.hasNext()) { val a = r4.next(); builder.addRoute(a.address(), a.prefix()) }
                val r6 = options.inet6RouteRange
                while (r6.hasNext()) { val a = r6.next(); builder.addRoute(a.address(), a.prefix()) }
            }
            // исключения приложений приходят из конфигурации (exclude_package)
            val inc = options.includePackage
            while (inc.hasNext()) {
                try { builder.addAllowedApplication(inc.next()) } catch (_: PackageManager.NameNotFoundException) {}
            }
            val exc = options.excludePackage
            while (exc.hasNext()) {
                try { builder.addDisallowedApplication(exc.next()) } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        val pfd = builder.establish() ?: error("android: VPN не разрешён или отозван")
        tunFd = pfd
        LogBuffer.add("app", "tun открыт: mtu=${options.mtu}, autoRoute=${options.autoRoute}, fd=${pfd.fd}")
        return pfd.fd
    }

    // ── уведомление ──

    private fun showForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "NOCTILIS VPN", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VPNService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("NOCTILIS")
            .setContentText("VPN включён")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Выключить", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFY_ID, n)
        }
    }
}
