package net.noctilis.app.vpn

import android.util.Log
import io.nekohasekai.libbox.CommandClient
import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.CommandClientOptions
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Задержка до сервера по замеру ядра (мс); 0 — не мерили, -1 — не отвечает. */
data class ServerPing(val tag: String, val delayMs: Int, val testedAt: Long)

/**
 * Клиент командного сервера libbox (1.14): журнал ядра в LogBuffer, группы серверов
 * с задержками для экрана настроек, живой выбор сервера и замер пинга без перезапуска.
 */
object CoreClient : CommandClientHandler {
    private var client: CommandClient? = null

    private val _pings = MutableStateFlow<Map<String, ServerPing>>(emptyMap())
    val pings: StateFlow<Map<String, ServerPing>> = _pings
    private val _selected = MutableStateFlow("auto")
    val selected: StateFlow<String> = _selected
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    @Synchronized
    fun start() {
        if (client != null) return
        val options = CommandClientOptions().also {
            it.addCommand(Libbox.CommandLog)
            it.addCommand(Libbox.CommandGroup)
        }
        val c = CommandClient(this, options)
        client = c
        Thread {
            try { c.connect() } catch (e: Exception) { LogBuffer.add("app", "командный клиент: $e") }
        }.start()
    }

    @Synchronized
    fun stop() {
        val c = client ?: return
        client = null
        try { c.disconnect() } catch (_: Exception) {}
        _testing.value = false   // замеры оставляем — их показываем и после выключения
    }

    val isConnected: Boolean get() = client != null

    /** Замер задержки до всех серверов группы «auto» (ядро само пингует каждый). */
    fun testAll() {
        val c = client ?: return
        if (!_testing.compareAndSet(expect = false, update = true)) return   // замер уже идёт
        Thread {
            try { c.urlTest("auto") } catch (e: Exception) { LogBuffer.add("app", "пинг: $e") }
            try { Thread.sleep(6000) } catch (_: InterruptedException) {}
            _testing.value = false
        }.start()
    }

    /** Переключить сервер без перезапуска туннеля. */
    fun select(tag: String): Boolean {
        val c = client ?: return false
        return try {
            c.selectOutbound("proxy", tag)
            _selected.value = tag
            true
        } catch (e: Exception) {
            LogBuffer.add("app", "выбор сервера: $e"); false
        }
    }

    override fun connected() { LogBuffer.add("app", "командный клиент подключён") }
    override fun disconnected(message: String?) { LogBuffer.add("app", "командный клиент отключён: $message") }
    override fun setDefaultLogLevel(level: Int) {}
    override fun clearLogs() {}
    override fun writeLogs(messageList: LogIterator?) {
        val it = messageList ?: return
        while (it.hasNext()) {
            val e = it.next() ?: continue
            LogBuffer.add("core", e.message ?: "")
            Log.d("sing-box", e.message ?: "")
        }
    }
    override fun writeStatus(message: StatusMessage?) {}
    override fun writeGroups(message: OutboundGroupIterator?) {
        val groups = message ?: return
        val map = _pings.value.toMutableMap()
        while (groups.hasNext()) {
            val g = groups.next() ?: continue
            if (g.tag == "proxy") _selected.value = g.selected ?: "auto"
            val items = g.items
            while (items.hasNext()) {
                val i = items.next() ?: continue
                if (i.urlTestTime > 0) map[i.tag] = ServerPing(i.tag, if (i.urlTestDelay > 0) i.urlTestDelay else -1, i.urlTestTime)
            }
        }
        _pings.value = map
    }
    override fun writeOutbounds(message: OutboundGroupItemIterator?) {}
    override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
    override fun updateClashMode(newMode: String?) {}
    override fun writeConnectionEvents(events: ConnectionEvents?) {}
}
