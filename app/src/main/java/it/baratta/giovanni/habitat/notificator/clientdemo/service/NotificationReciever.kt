package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.gson.Gson
import it.baratta.giovanni.habitat.notificator.api.Message


/**
 * Gestisce un elenco delle notifiche arrivate. Elimina le notifiche duplicate se sono già state
 * gestite. Salve le altre in un database locale.
 *
 * TODO:
 * Salvere le notifiche nel database
 * Ottimizzare la lettura delle notifiche aggiungendo source e id direttamente nei Bundle per
 * evitare il parsing ogni volta.
 */
class NotificationReciever() : NotificationListenerService() {
    /* mappa <nomeSorgente,Set<idMessaggio>> */
    private val receivedMessage = HashMap<String, HashSet<Long>>()
    /* mappa <nomeSorgente,idMessaggio>, Boolean */
    private val dismissedMessage = HashMap<Pair<String, Long>, Boolean>()

    private lateinit var notificationManager : NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                                        as NotificationManager
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        Log.d("ClientDemo", "Notifica intercettata")

        if(sbn == null || sbn.packageName != this.packageName)
            return


        // estraggo il payload dalla notifica

        val payload = sbn.notification.extras?.getString(PAYLOAD)
        if( payload == null)
            return

        val msg = gson.fromJson(payload, Message::class.java)
        val arrivedMessage = receivedMessage[msg.source]
        // verifico se il messaggio è già stato ricevuto tramite altra notifica
        if(arrivedMessage != null && arrivedMessage.contains(msg.id)) {
            Log.d("ClientDemo", "Notifica duplicata da ${msg.source} con ID ${msg.id}")
            if(dismissedMessage[Pair(msg.source,msg.id)] ?: false)
                notificationManager.cancel(msg.id.toInt())
            return
        }

        if(!receivedMessage.containsKey(msg.source))
            receivedMessage.put(msg.source, HashSet())

        receivedMessage[msg.source]?.add(msg.id)
        dismissedMessage.put(Pair(msg.source,msg.id),false)
        //salvo il messaggio in un database
        Log.d("ClientDemo", "Ho salvato la notifica da ${msg.source} con ID ${msg.id}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)

        Log.d("ClientDemo", "Notifica intercettata")

        if(sbn == null || sbn.packageName != this.packageName)
            return

        // estraggo il payload dalla notifica
        val payload = sbn.notification.extras?.getString(PAYLOAD)
        if( payload == null)
            return

        val msg = gson.fromJson(payload, Message::class.java)
        dismissedMessage.put(Pair(msg.source, msg.id),true)
    }

    companion object {
        const val PAYLOAD = "payload"
        private val gson = Gson()
    }
}