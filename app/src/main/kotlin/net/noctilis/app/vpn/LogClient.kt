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

/**
 * Читает журнал ядра через командный сервер libbox (в 1.14 логи идут только этим путём)
 * и складывает в LogBuffer для кнопки «Отправить диагностику».
 */
class LogClient : CommandClientHandler {
    private var client: CommandClient? = null

    fun start() {
        if (client != null) return
        val options = CommandClientOptions().also { it.addCommand(Libbox.CommandLog) }
        val c = CommandClient(this, options)
        client = c
        Thread {
            try {
                c.connect()
            } catch (e: Exception) {
                LogBuffer.add("app", "журнал ядра недоступен: $e")
            }
        }.start()
    }

    fun stop() {
        val c = client ?: return
        client = null
        try { c.disconnect() } catch (_: Exception) {}
    }

    override fun connected() { LogBuffer.add("app", "журнал ядра подключён") }
    override fun disconnected(message: String?) { LogBuffer.add("app", "журнал ядра отключён: $message") }
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
    override fun writeGroups(message: OutboundGroupIterator?) {}
    override fun writeOutbounds(message: OutboundGroupItemIterator?) {}
    override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) {}
    override fun updateClashMode(newMode: String?) {}
    override fun writeConnectionEvents(events: ConnectionEvents?) {}
}
