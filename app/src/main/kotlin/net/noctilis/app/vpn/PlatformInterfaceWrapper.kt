package net.noctilis.app.vpn

import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.system.OsConstants
import android.util.Log
import io.nekohasekai.libbox.BridgeOptions
import io.nekohasekai.libbox.BridgeSession
import io.nekohasekai.libbox.ConnectionOwner
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.LocalDNSTransport
import io.nekohasekai.libbox.NeighborUpdateListener
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.PlatformUser
import io.nekohasekai.libbox.ShellSession
import io.nekohasekai.libbox.StringIterator
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import net.noctilis.app.App
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import io.nekohasekai.libbox.NetworkInterface as LibboxNetworkInterface

/**
 * Платформенные вызовы ядра sing-box (libbox 1.14). За основу взят официальный клиент
 * sing-box для Android (сентябрь 2026) без root, shell и «моста» — нам они не нужны.
 */
interface PlatformInterfaceWrapper : PlatformInterface {

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {}

    override fun openTun(options: TunOptions): Int = error("invalid argument")

    override fun useProcFS(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int,
        destinationAddress: String, destinationPort: Int,
    ): ConnectionOwner {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) error("android: unsupported")
        val uid = App.connectivity.getConnectionOwnerUid(
            ipProtocol, InetSocketAddress(sourceAddress, sourcePort), InetSocketAddress(destinationAddress, destinationPort),
        )
        if (uid == Process.INVALID_UID) error("android: connection owner not found")
        val packages = App.pm.getPackagesForUid(uid)
        val owner = ConnectionOwner()
        owner.userId = uid
        owner.userName = packages?.firstOrNull() ?: ""
        owner.setAndroidPackageNames(StringArray((packages?.toList() ?: emptyList()).iterator()))
        return owner
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.start()
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        val networks = App.connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val interfaces = mutableListOf<LibboxNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibboxNetworkInterface()
            val linkProperties = App.connectivity.getLinkProperties(network) ?: continue
            val caps = App.connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface = networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer = StringArray(linkProperties.dnsServers.mapNotNull { it.hostAddress }.iterator())
            boxInterface.gateway = StringArray(
                linkProperties.routes
                    .filter { it.destination.prefixLength == 0 }
                    .mapNotNull { it.gateway }
                    .filterNot { it.isAnyLocalAddress }
                    .mapNotNull { it.hostAddress }
                    .iterator(),
            )
            boxInterface.type = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libbox.InterfaceTypeWIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libbox.InterfaceTypeCellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libbox.InterfaceTypeEthernet
                else -> Libbox.InterfaceTypeOther
            }
            boxInterface.index = networkInterface.index
            runCatching { boxInterface.mtu = networkInterface.mtu }
            boxInterface.addresses = StringArray(networkInterface.interfaceAddresses.map { it.toPrefix() }.iterator())
            var flags = 0
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                flags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) flags = flags or OsConstants.IFF_LOOPBACK
            if (networkInterface.isPointToPoint) flags = flags or OsConstants.IFF_POINTOPOINT
            if (networkInterface.supportsMulticast()) flags = flags or OsConstants.IFF_MULTICAST
            boxInterface.flags = flags
            boxInterface.metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator())
    }

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? {
        @Suppress("DEPRECATION")
        val info = try { App.wifiManager.connectionInfo } catch (_: Exception) { null } ?: return null
        var ssid = info.ssid ?: return WIFIState("", "")
        if (ssid == "<unknown ssid>") return WIFIState("", "")
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) ssid = ssid.substring(1, ssid.length - 1)
        return WIFIState(ssid, info.bssid ?: "")
    }

    /** DNS типа "local" в конфигурации не используем — узлы приходят готовыми адресами. */
    override fun localDNSTransport(): LocalDNSTransport? = null

    override fun sendNotification(notification: Notification) {}

    override fun cancelNotification(identifier: String, typeID: Int) {}

    override fun startNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun closeNeighborMonitor(listener: NeighborUpdateListener?) {}

    override fun registerMyInterface(name: String?) {}

    // Shell, SSH и «мост» — возможности официального клиента для root-устройств; у нас их нет.
    override fun usePlatformShell(): Boolean = false

    override fun checkPlatformShell() {
        error("not supported")
    }

    override fun openShellSession(
        user: PlatformUser?, command: String?, environ: StringIterator?, term: String?, rows: Int, cols: Int,
    ): ShellSession = error("not supported")

    override fun lookupUser(username: String?): PlatformUser = error("not supported")

    override fun lookupSFTPServer(): String = error("not supported")

    override fun readSystemSSHHostKey(): String = error("not supported")

    override fun tailscaleHostname(): String =
        Settings.Global.getString(App.instance.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"

    override fun usePlatformBridge(): Boolean = false

    override fun createBridge(options: BridgeOptions?): BridgeSession = error("not supported")

    class InterfaceArray(private val iterator: Iterator<LibboxNetworkInterface>) : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): LibboxNetworkInterface = iterator.next()
    }

    class StringArray(private val iterator: Iterator<String>) : StringIterator {
        override fun len(): Int = 0   // ядро не использует
        override fun hasNext(): Boolean = iterator.hasNext()
        override fun next(): String = iterator.next()
    }

    private fun InterfaceAddress.toPrefix(): String = if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/${networkPrefixLength}"
    } else {
        "${address.hostAddress}/${networkPrefixLength}"
    }
}
