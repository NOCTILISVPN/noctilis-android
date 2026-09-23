package net.noctilis.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, message: String) : IOException(message)

/** Серверная часть: xui-api на KZ, наружу через app.noctilis.net (путь /app, Литва → KZ). */
object Api {
    private const val BASE = "https://app.noctilis.net/app"
    private val ua = "noctilis-android/" + BuildConfig.VERSION_NAME

    private fun call(method: String, path: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", ua)
        c.setRequestProperty("Accept", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
        val text = stream.bufferedReader().use { it.readText() }
        val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
        if (code !in 200..299) {
            throw ApiException(code, json.optString("error", "HTTP $code"))
        }
        return json
    }

    /** Первый запуск: аккаунт по устройству, сразу пробный период. Повтор — тот же аккаунт. */
    fun register(ctx: Context): JSONObject {
        val body = JSONObject()
            .put("device_id", Prefs.deviceId(ctx))
            .put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
            .put("os", "Android " + Build.VERSION.RELEASE)
            .put("app_version", BuildConfig.VERSION_NAME)
        return call("POST", "/register", body)
    }

    fun me(token: String): JSONObject = call("GET", "/me", token = token)

    fun config(token: String): JSONObject = call("GET", "/config", token = token)

    /** Привязка действующей подписки по ссылке из кабинета (решение Андрея 22.09). */
    fun link(token: String, sub: String): JSONObject = call("POST", "/link", JSONObject().put("sub", sub), token)

    fun devices(token: String): JSONObject = call("GET", "/devices", token = token)
    fun devicesReset(token: String): JSONObject = call("POST", "/devices/reset", JSONObject(), token)
    fun reissue(token: String): JSONObject = call("POST", "/reissue", JSONObject(), token)

    fun ref(token: String): JSONObject = call("GET", "/ref", token = token)
    fun refApply(token: String, code: String): JSONObject = call("POST", "/ref/apply", JSONObject().put("code", code), token)
    fun refWithdraw(token: String, requisites: String): JSONObject = call("POST", "/ref/withdraw", JSONObject().put("requisites", requisites), token)

    /** Журнал ядра и состояние — на сервер, чтобы разбирать «не подключается» без adb. */
    fun diag(token: String, log: String, server: String, excludedCount: Int, status: String, note: String = ""): JSONObject {
        val body = JSONObject()
            .put("log", log.takeLast(150_000))
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("model", (Build.MANUFACTURER + " " + Build.MODEL).trim())
            .put("os", "Android " + Build.VERSION.RELEASE)
            .put("server", server)
            .put("excluded_count", excludedCount)
            .put("status", status)
            .put("note", note)
        return call("POST", "/diag", body, token)
    }
}
