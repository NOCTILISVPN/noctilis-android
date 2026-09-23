package net.noctilis.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import java.util.UUID

/** Локальное состояние приложения. Секрет здесь один — токен аккаунта. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("noctilis", Context.MODE_PRIVATE)
    }

    /** Идентификатор устройства: ANDROID_ID стабилен для приложения до сброса телефона,
     *  переустановка аккаунт не теряет. Если система его не дала — случайный, один раз. */
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        sp.getString("device_id", null)?.let { return it }
        val sys = try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) { null }
        val id = if (!sys.isNullOrBlank() && sys.length >= 8) "a-$sys" else "r-" + UUID.randomUUID().toString()
        sp.edit().putString("device_id", id).apply()
        return id
    }

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    /** Последний ответ /app/me (JSON) — чтобы показывать дни и без сети. */
    var me: String?
        get() = sp.getString("me", null)
        set(v) = sp.edit().putString("me", v).apply()

    /** Последняя конфигурация sing-box с сервера (JSON). */
    var config: String?
        get() = sp.getString("config", null)
        set(v) = sp.edit().putString("config", v).apply()

    /** Приложения, идущие МИМО туннеля. Заполняется при первом запуске из списка по умолчанию. */
    var excluded: Set<String>
        get() = sp.getStringSet("excluded", null) ?: emptySet()
        set(v) = sp.edit().putStringSet("excluded", v.toSet()).apply()

    var exclusionsInitialized: Boolean
        get() = sp.getBoolean("excl_init", false)
        set(v) = sp.edit().putBoolean("excl_init", v).apply()

    /** "auto" или тег сервера из конфигурации. */
    var server: String
        get() = sp.getString("server", "auto") ?: "auto"
        set(v) = sp.edit().putString("server", v).apply()

    /** Оформление: "dark" | "light" (как в кабинете NOCTILIS). */
    var theme: String
        get() = sp.getString("theme", "dark") ?: "dark"
        set(v) = sp.edit().putString("theme", v).apply()

    /** Поднимать VPN при загрузке телефона. */
    var autoStart: Boolean
        get() = sp.getBoolean("autostart", true)
        set(v) = sp.edit().putBoolean("autostart", v).apply()

    /** Дата последнего напоминания об окончании (yyyy-MM-dd) — не чаще раза в сутки. */
    var lastReminderDay: String
        get() = sp.getString("reminder_day", "") ?: ""
        set(v) = sp.edit().putString("reminder_day", v).apply()

    /** Пользователь хотел VPN включённым (для автозапуска после перезагрузки). */
    var wantConnected: Boolean
        get() = sp.getBoolean("want", false)
        set(v) = sp.edit().putBoolean("want", v).apply()
}
