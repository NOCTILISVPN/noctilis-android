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
            Libbox.setup(SetupOptions().also {
                it.basePath = filesDir.path
                it.workingPath = work.path
                it.tempPath = cacheDir.path
                it.fixAndroidStack = false
            })
        } catch (e: Exception) {
            Log.e("NOCTILIS", "libbox setup", e)
        }
    }
}
