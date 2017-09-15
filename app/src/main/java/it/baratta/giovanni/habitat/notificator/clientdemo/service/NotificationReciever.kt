package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log


/**
 * Created by Gio on 14/09/2017.
 */
class NotificationReciever() : NotificationListenerService() {

    private var counter = 0

    override fun onCreate() {
        super.onCreate()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if(sbn == null || sbn.packageName != this.packageName)
            return

        Log.d("gioTAG", "Ho rilevato una notifica - ${counter++}-  ${sbn?.packageName}")
        /*if(sbn.id < 5000){
            Log.d("gioTAG", "rimuovo")
            /* Eliminare una notifica */
            val notificationManager = this
                    .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(sbn.id)
        }*/
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}