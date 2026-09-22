package net.noctilis.app.vpn

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.nekohasekai.libbox.InterfaceUpdateListener
import net.noctilis.app.App
import java.net.NetworkInterface

/**
 * Сообщает ядру, какой сетевой интерфейс сейчас основной (Wi-Fi / мобильный).
 *
 * 🔴 Нельзя брать registerDefaultNetworkCallback: с Android 9 «сеть по умолчанию» — это наш же
 * VPN-туннель, как только он поднят. Ядро получало tun как основной интерфейс и писало
 * «no available network interface», ни одно соединение не выходило (22.09.2026, первый запуск
 * у Андрея). Запрашиваем сеть с условиями INTERNET + NOT_RESTRICTED — у NetworkRequest по
 * умолчанию ещё и NOT_VPN, поэтому туннель в ответ не попадает. Так делает официальный клиент.
 */
object DefaultNetworkMonitor {
    @Volatile
    private var listener: InterfaceUpdateListener? = null
    @Volatile
    var defaultNetwork: Network? = null
        private set
    private var registered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            LogBuffer.add("app", "основная сеть: ${describe(network)}")
            notifyListener(network)
        }
        override fun onLost(network: Network) {
            if (defaultNetwork == network) {
                defaultNetwork = null
                LogBuffer.add("app", "основная сеть потеряна")
                notifyListener(null)
            }
        }
    }

    private fun describe(network: Network): String {
        val lp = try { App.connectivity.getLinkProperties(network) } catch (_: Exception) { null }
        val caps = try { App.connectivity.getNetworkCapabilities(network) } catch (_: Exception) { null }
        val kind = when {
            caps == null -> "?"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        return "${lp?.interfaceName} ($kind)"
    }

    @Synchronized
    fun start() {
        if (registered) return
        try {
            val cm = App.connectivity
            when {
                Build.VERSION.SDK_INT >= 31 -> cm.registerBestMatchingNetworkCallback(request, callback, mainHandler)
                Build.VERSION.SDK_INT >= 28 -> cm.requestNetwork(request, callback, mainHandler)   // нужен CHANGE_NETWORK_STATE
                else -> cm.registerDefaultNetworkCallback(callback, mainHandler)
            }
            registered = true
        } catch (e: Exception) {
            LogBuffer.add("app", "не удалось подписаться на сеть: $e")
        }
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        try { App.connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        registered = false
        defaultNetwork = null
    }

    fun setListener(l: InterfaceUpdateListener?) {
        listener = l
        if (l != null) notifyListener(defaultNetwork)
    }

    private fun notifyListener(network: Network?) {
        val l = listener ?: return
        if (network == null) {
            l.updateDefaultInterface("", -1, false, false)
            return
        }
        val name = App.connectivity.getLinkProperties(network)?.interfaceName ?: return
        for (attempt in 0 until 10) {
            val index = try { NetworkInterface.getByName(name)?.index } catch (_: Exception) { null }
            if (index != null) {
                l.updateDefaultInterface(name, index, false, false)
                return
            }
            Thread.sleep(100)
        }
        LogBuffer.add("app", "интерфейс $name не найден по имени")
    }
}
