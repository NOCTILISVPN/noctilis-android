package net.noctilis.app

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, message: String) : IOException(message)

/** Серверная часть: xui-api на KZ, наружу через app.noctilis.net (путь /app, Литва → KZ). */
object Api {
    private const val BASE = "https://app.noctilis.net/app"
    private val ua = "noctilis-android/" + BuildConfig.VERSION_NAME

    private fun raw(method: String, path: String, body: JSONObject? = null, token: String? = null, timeoutMs: Int = 25_000): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 20_000
        c.readTimeout = timeoutMs
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
        if (code !in 200..299) {
            val json = try { JSONObject(text) } catch (_: Exception) { JSONObject() }
            throw ApiException(code, json.optString("error", "HTTP $code"))
        }
        return text
    }

    private fun call(method: String, path: String, body: JSONObject? = null, token: String? = null, timeoutMs: Int = 25_000): JSONObject {
        val text = raw(method, path, body, token, timeoutMs)
        return try { JSONObject(text) } catch (_: Exception) { JSONObject() }
    }

    private fun callArray(method: String, path: String, token: String?): JSONArray {
        val text = raw(method, path, null, token)
        return try { JSONArray(text) } catch (_: Exception) { JSONArray() }
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

    // ── кабинет ──
    fun devices(token: String): JSONObject = call("GET", "/devices", token = token)
    fun deviceDelete(token: String, kind: String, id: String): JSONObject =
        call("POST", "/devices/delete", JSONObject().put("kind", kind).put("id", id), token)
    fun reissue(token: String): JSONObject = call("POST", "/reissue", JSONObject(), token)

    // ── бонусы: рефералка, промокоды, акции (одна база с ботом NOCTILIS) ──
    fun bonus(token: String): JSONObject = call("GET", "/bonus", token = token)
    fun refApply(token: String, code: String): JSONObject = call("POST", "/ref/apply", JSONObject().put("code", code), token)
    fun refWithdraw(token: String, requisites: String): JSONObject = call("POST", "/ref/withdraw", JSONObject().put("requisites", requisites), token)
    fun promo(token: String, code: String): JSONObject = call("POST", "/promo", JSONObject().put("code", code), token)
    fun codeCreate(token: String, code: String): JSONObject = call("POST", "/code", JSONObject().put("code", code), token)
    fun videos(token: String): JSONArray = callArray("GET", "/videos", token)
    fun videoSubmit(token: String, program: String, url: String, tier: Int): JSONObject =
        call("POST", "/videos", JSONObject().put("program", program).put("url", url).put("tier", tier), token)
    fun storySubmit(token: String, imageB64: String): JSONObject =
        call("POST", "/story", JSONObject().put("image", imageB64), token, timeoutMs = 90_000)
    fun materials(token: String): JSONObject = call("GET", "/materials", token = token)

    // ── поддержка внутри приложения: без Telegram и MAX ──
    fun supportList(token: String, after: Int = 0): JSONObject = call("GET", "/support?after=$after", token = token)
    fun supportSend(token: String, text: String): JSONObject = call("POST", "/support", JSONObject().put("text", text), token)

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
