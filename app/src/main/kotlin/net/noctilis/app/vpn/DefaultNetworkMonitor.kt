package net.noctilis.app.vpn

import android.net.ConnectivityManager
import android.net.Network
import io.nekohasekai.libbox.InterfaceUpdateListener
import net.noctilis.app.App
import java.net.NetworkInterface

/** Сообщает ядру, какой сетевой интерфейс сейчас основной (Wi-Fi / мобильный). */
object DefaultNetworkMonitor {
    @Volatile
    private var listener: InterfaceUpdateListener? = null
    @Volatile
    var defaultNetwork: Network? = null
        private set
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNetwork = network
            notifyListener(network)
        }
        override fun onLost(network: Network) {
            if (defaultNetwork == network) {
                defaultNetwork = null
                notifyListener(null)
            }
        }
    }

    @Synchronized
    fun start() {
        if (registered) return
        try {
            App.connectivity.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (_: Exception) {
        }
        defaultNetwork = App.connectivity.activeNetwork
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        try { App.connectivity.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        registered = false
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
    }
}
