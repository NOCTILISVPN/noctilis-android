package net.noctilis.app.vpn

import io.nekohasekai.libbox.CommandServer
import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions
import io.nekohasekai.libbox.SystemProxyStatus
import net.noctilis.app.ConfigBuilder
import net.noctilis.app.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Замер пинга при ВЫКЛЮЧЕННОМ VPN (как в Happ): поднимаем ядро без туннеля — только
 * серверы и urltest, локальный inbound на 127.0.0.1 — меряем и гасим. Пока VPN включён,
 * замер идёт через рабочее ядро (VPNService), сюда не заходим.
 */
object ProbeService : PlatformInterfaceWrapper, CommandServerHandler {
    private var server: CommandServer? = null
    private var starting = false   // поток пробника запущен, ядро ещё не создано

    @Synchronized
    fun isRunning(): Boolean = server != null || starting

    /** Запускает ядро-пробник, меряет, через ~8 с останавливает. Возвращает false, если нельзя. */
    @Synchronized
    fun probe(): Boolean {
        // раньше два быстрых нажатия «Проверить пинг» поднимали два ядра на одном unix-сокете
        if (server != null || starting || VpnState.isRunning) return false
        val base = Prefs.config ?: return false
        val cfg = try { probeConfig(ConfigBuilder.build(base, emptySet(), "auto")) } catch (_: Exception) { return false }
        starting = true
        Thread({
            var s: CommandServer? = null
            try {
                if (VpnState.isRunning) return@Thread   // пока готовили конфиг, пользователь включил VPN
                DefaultNetworkMonitor.start()
                s = CommandServer(this, this)
                s.start()
                synchronized(this) { server = s; starting = false }
                s.startOrReloadService(cfg, OverrideOptions())
                CoreClient.start()
                Thread.sleep(800)
                if (!isMine(s)) return@Thread   // пробник уже погасили (стартовал VPN) — его ядро не трогаем
                CoreClient.testAll()
                Thread.sleep(8000)
            } catch (e: Exception) {
                LogBuffer.add("app", "пробник: $e")
            } finally {
                synchronized(this) { starting = false }
                if (s == null || isMine(s)) stop()
            }
        }, "noctilis-probe").start()
        return true
    }

    @Synchronized
    private fun isMine(s: CommandServer): Boolean = server === s

    @Synchronized
    fun stop() {
        val s = server ?: return
        server = null
        CoreClient.stop()
        try { s.closeService() } catch (_: Exception) {}
        try { s.close() } catch (_: Exception) {}
        DefaultNetworkMonitor.setListener(null)
        DefaultNetworkMonitor.stop()
    }

    /** Из боевого конфига убираем tun, ставим локальный mixed-inbound, без авто-интерфейса. */
    private fun probeConfig(json: String): String {
        val c = JSONObject(json)
        c.put("inbounds", JSONArray().put(JSONObject()
            .put("type", "mixed").put("tag", "probe-in").put("listen", "127.0.0.1").put("listen_port", 0)))
        c.optJSONObject("route")?.put("auto_detect_interface", false)
        return c.toString()
    }

    // ── CommandServerHandler: пробнику команды извне не нужны ──
    override fun serviceStop() { stop() }
    override fun serviceReload() {}
    override fun getSystemProxyStatus(): SystemProxyStatus = SystemProxyStatus().also { it.available = false; it.enabled = false }
    override fun setSystemProxyEnabled(enabled: Boolean) {}
    override fun triggerNativeCrash() {}
    override fun writeDebugMessage(message: String?) { if (message != null) LogBuffer.add("probe", message) }
    override fun connectSSHAgent(): Int = -1
}
