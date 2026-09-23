package net.noctilis.app

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Что экраны просят у активности: аккаунт, токен, открыть ссылку, скопировать, обновить. */
interface Host {
    val token: String?
    val account: JSONObject?
    fun openUrl(url: String)
    fun copy(text: String, toast: String = "Скопировано")
    fun share(text: String)
    fun refresh(silent: Boolean = false)
    fun toast(text: String)
    fun runUi(block: () -> Unit)
}

/** Форматирование как в кабинете NOCTILIS. */
object Fmt {
    fun rub(kop: Int): String = "${kop / 100} ₽"
    fun num(n: Int): String = String.format(Locale("ru"), "%,d", n).replace(',', ' ')
    fun gb(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f ГБ", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.0f МБ", bytes / (1024.0 * 1024))
        bytes > 0 -> "менее 1 МБ"
        else -> "0 МБ"
    }
    fun dw(n: Int): String {
        val a = n % 10; val b = n % 100
        return if (b in 11..14) "дней" else if (a == 1) "день" else if (a in 2..4) "дня" else "дней"
    }
    fun date(ms: Long): String = if (ms <= 0) "—" else SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(ms))
    fun dateShort(sec: Long): String = if (sec <= 0) "" else SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(sec * 1000))
    fun time(sec: Long): String = if (sec <= 0) "" else SimpleDateFormat("dd.MM HH:mm", Locale("ru")).format(Date(sec * 1000))
}
