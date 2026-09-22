package net.noctilis.app

import android.app.Application
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions

class App : Application() {

    companion object {
        lateinit var instance: App
            private set

        val connectivity: ConnectivityManager
            get() = instance.getSystemService(ConnectivityManager::class.java)
        val wifiManager: WifiManager
            get() = instance.applicationContext.getSystemService(WifiManager::class.java)
        val notifications: NotificationManager
            get() = instance.getSystemService(NotificationManager::class.java)
        val pm: PackageManager
            get() = instance.packageManager
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Prefs.init(this)
        try {
            val work = filesDir.resolve("work").also { it.mkdirs() }
            // libbox 1.14: пути + версия приложения; командный сервер слушает unix-сокет в basePath
            Libbox.setup(SetupOptions().also {
                it.basePath = filesDir.path
                it.workingPath = work.path
                it.tempPath = cacheDir.path
                it.fixAndroidStack = false
                it.logMaxLines = 300
                it.appVersion = BuildConfig.VERSION_NAME
                it.appMarketingVersion = BuildConfig.VERSION_NAME
            })
        } catch (e: Exception) {
            Log.e("NOCTILIS", "libbox setup", e)
        }
    }
}
