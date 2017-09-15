package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Created by Gio on 14/09/2017.
 *
 * NOTE :
 * Per testare il broadcastReciever senza riavviare
 * adb -s device-or-emulator-id shell am broadcast -a android.intent.action.BOOT_COMPLETED
 */
class BootServiceInitializer : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent?) {
        if(ctx == null || intent == null)
            return

        if(intent.action != Intent.ACTION_BOOT_COMPLETED)
            return

        Log.d("ClientDemo", "Avvio servizio on BOOT")
        // avvio il foreground service
        val intent : Intent = Intent(ctx, MqttForegroundService::class.java)
        intent.setAction(MqttForegroundService.RESTORE_ACTION)
        ctx.startService(intent)
    }

}