package net.noctilis.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val version: String, val url: String, val size: Long)

/**
 * Обновление из приложения: noctilis.net/noctilis-version.json (пишет noctilis-apk-sync.sh на KZ),
 * скачивание системным DownloadManager и установка поверх — ключ подписи постоянный.
 */
object Updater {
    private const val VERSION_URL = "https://noctilis.net/noctilis-version.json"
    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available
    private val _state = MutableStateFlow("")   // текст состояния: «Скачиваем…», ошибка
    val state: StateFlow<String> = _state

    private fun parse(v: String): List<Int> = v.trim().removePrefix("v").split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote); val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Вызывать в фоне. */
    fun check() {
        try {
            val c = URL(VERSION_URL).openConnection() as HttpURLConnection
            c.connectTimeout = 10_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "noctilis-android/" + BuildConfig.VERSION_NAME)
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val v = j.getString("version")
            _available.value = if (isNewer(v, BuildConfig.VERSION_NAME)) UpdateInfo(v, j.getString("url"), j.optLong("size")) else null
        } catch (_: Exception) {
        }
    }

    fun download(ctx: Context, info: UpdateInfo) {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        val file = File(dir, "noctilis-update.apk")
        if (file.exists()) file.delete()
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val req = DownloadManager.Request(Uri.parse(info.url))
            .setTitle("NOCTILIS ${info.version}")
            .setDescription("Обновление приложения")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
        val id = dm.enqueue(req)
        _state.value = "Скачиваем ${info.version}…"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try { c.unregisterReceiver(this) } catch (_: Exception) {}
                if (!file.exists() || (info.size > 0 && file.length() != info.size)) {
                    _state.value = "Файл скачался не полностью, попробуйте ещё раз"
                    return
                }
                _state.value = ""
                install(c, file)
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun install(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
