package net.noctilis.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class VpnStatus {
    data object Disconnected : VpnStatus()
    data object Starting : VpnStatus()
    data object Connected : VpnStatus()
    data object Stopping : VpnStatus()
    data class Error(val message: String) : VpnStatus()
}

object VpnState {
    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    val status: StateFlow<VpnStatus> = _status

    fun set(s: VpnStatus) { _status.value = s }
    val isRunning: Boolean get() = _status.value is VpnStatus.Connected || _status.value is VpnStatus.Starting
}
