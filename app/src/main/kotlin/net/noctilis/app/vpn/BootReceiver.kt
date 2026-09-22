package net.noctilis.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import net.noctilis.app.Prefs

/** «Всегда включён»: после перезагрузки поднимаем туннель, если он был включён и разрешение есть. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.autoStart || !Prefs.wantConnected || Prefs.token == null || Prefs.config == null) return
        if (VpnService.prepare(context) != null) return   // разрешение отозвано — без экрана не поднять
        VPNService.start(context)
    }
}
