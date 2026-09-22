package net.noctilis.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Напоминание об окончании подписки внутри приложения: за день до конца и в день окончания,
 * не чаще раза в сутки (правило Андрея: не надоедать). Проверяется при каждом обновлении
 * данных аккаунта и раз в 6 часов из туннеля.
 */
object Reminder {
    private const val CHANNEL = "billing"
    private const val ID = 2
    private val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun check(ctx: Context, me: JSONObject) {
        val active = me.optBoolean("active")
        val days = me.optInt("days_left")
        val text = when {
            !active -> "Подписка закончилась. Продлите, и доступ вернётся сразу."
            days <= 1 -> "Подписка заканчивается завтра. Продлите заранее, чтобы VPN не выключился."
            else -> return
        }
        val today = day.format(Date())
        if (Prefs.lastReminderDay == today) return
        Prefs.lastReminderDay = today
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Подписка", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).putExtra("screen", "pay"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("NOCTILIS")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try { nm.notify(ID, n) } catch (_: Exception) {}
    }
}
