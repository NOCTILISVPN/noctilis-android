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

    @Synchronized
    fun isRunning(): Boolean = server != null

    /** Запускает ядро-пробник, меряет, через ~8 с останавливает. Возвращает false, если нельзя. */
    @Synchronized
    fun probe(): Boolean {
        if (server != null || VpnState.isRunning) return false
        val base = Prefs.config ?: return false
        val cfg = try { probeConfig(ConfigBuilder.build(base, emptySet(), "auto")) } catch (_: Exception) { return false }
        Thread {
            try {
                DefaultNetworkMonitor.start()
                val s = CommandServer(this, this)
                s.start()
                synchronized(this) { server = s }
                s.startOrReloadService(cfg, OverrideOptions())
                CoreClient.start()
                Thread.sleep(800)
                CoreClient.testAll()
                Thread.sleep(8000)
            } catch (e: Exception) {
                LogBuffer.add("app", "пробник: $e")
            } finally {
                stop()
            }
        }.start()
        return true
    }

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
