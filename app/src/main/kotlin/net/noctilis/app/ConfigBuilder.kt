package net.noctilis.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Серверная конфигурация sing-box + выбор пользователя:
 *  - приложения-исключения кладём в tun-инбаунд (exclude_package),
 *  - выбранный сервер делаем default у селектора "proxy" (или оставляем автовыбор).
 */
object ConfigBuilder {

    fun serverTags(configJson: String): List<String> = try {
        val outs = JSONObject(configJson).getJSONArray("outbounds")
        (0 until outs.length()).map { outs.getJSONObject(it) }
            .filter { it.optString("type") in setOf("vless", "hysteria2", "trojan", "shadowsocks", "vmess", "tuic", "wireguard") }
            .map { it.getString("tag") }
    } catch (_: Exception) { emptyList() }

    fun build(configJson: String, excluded: Set<String>, server: String): String {
        val cfg = JSONObject(configJson)
        // исключения приложений
        val inbounds = cfg.optJSONArray("inbounds") ?: JSONArray()
        for (i in 0 until inbounds.length()) {
            val inb = inbounds.getJSONObject(i)
            if (inb.optString("type") == "tun") {
                val arr = JSONArray()
                excluded.sorted().forEach { arr.put(it) }
                inb.put("exclude_package", arr)
            }
        }
        // выбор сервера
        val outs = cfg.optJSONArray("outbounds") ?: JSONArray()
        val tags = serverTags(configJson)
        for (i in 0 until outs.length()) {
            val o = outs.getJSONObject(i)
            if (o.optString("type") == "selector" && o.optString("tag") == "proxy") {
                o.put("default", if (server != "auto" && server in tags) server else "auto")
            }
        }
        return cfg.toString()
    }
}
