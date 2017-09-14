package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.R
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.gson.Gson
import it.baratta.giovanni.habitat.notificator.api.Message
import it.baratta.giovanni.habitat.notificator.clientdemo.MainActivity
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.PropertiesFile
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import java.io.File
import java.util.*


/**
 * Created by Gio on 14/09/2017.
 */
class MqttForegroundService(server : String,
                            port : Int) : Service() {

    constructor() : this("192.168.0.5",1883)

    private val client: MqttAndroidClient

    companion object {
        private val gson = Gson()
    }

    init {

        Log.d("gioTAG", "MqttForegroundService - onCreate : init")
        // inizializzazione client mqtt
        client = MqttAndroidClient(this,
                "tcp://${server}:${port}",
                "AndroidClient${Random().nextLong()}")

        client.setCallback(MqttServiceCallback(this))
    }

    override fun onCreate() {
        super.onCreate()



        Log.d("gioTAG", "MqttForegroundService - onCreate : created")

        val options = MqttConnectOptions()
        options.isAutomaticReconnect = true
        options.keepAliveInterval = 60

        try {
            client.connect(options, null, HabitatMqttAction(client))
        } catch (e: MqttException) {
            Log.d("gioTAG", "MainActivity - onCreate : Eccezione connesione")
        }

        //val notification = Notification.Builder(this)

        val notification = Notification(R.drawable.arrow_down_float, "MyService",
                System.currentTimeMillis())
        val notificationIntent = Intent(this, MqttForegroundService::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        //notification. .setLatestEventInfo(this, "connessione",
        //        "non lo so", pendingIntent)

        startForeground(10, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("gioTAG", "MqttService - onStartCommand : ")

        val file = File(filesDir, "tracking")
        if(file.exists()){
            Log.d("gioTAg","il file esiste")
            Log.d("gioTAG", "" + PropertiesFile(file, true, true).getProperty("server"))
        }else{
            Log.d("gioTAg","il file non esiste")
        }

        // con START_STICKY il servizio viene riavviato anche
        // se non ci sono richieste da parte di altri componenti
        return Service.START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder {
        Log.d("gioTAG", "MqttService - onBind : Binding")
        TODO("not implemented")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    private class MqttServiceCallback(private val context : Context) : MqttCallback {

        override fun messageArrived(topic: String?, message: MqttMessage?) {


            val sb = StringBuilder()
            for (i in 0.until(message?.payload?.size ?: 0)) {
                sb.append(message?.payload?.elementAt(i)?.toChar())
            }

            val msg = gson.fromJson(sb.toString(), Message::class.java)

            Log.d("gioTAG", "MqttServiceCallback - messageArrived : ${msg.id} - ${msg.jsonData}")

            val builder = NotificationCompat.Builder(context, "MqttMessage")
                    .setSmallIcon(R.drawable.sym_def_app_icon)
                    .setContentTitle("MqttNotification")
                    .setContentText(sb.toString())

            val NOTIFICATION_ID = Random().nextInt(10000)

            val targetIntent = Intent(context, MainActivity::class.java)
            val contentIntent = PendingIntent.getActivity(context, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(contentIntent)
            val nManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nManager.notify(NOTIFICATION_ID, builder.build())
        }

        override fun connectionLost(cause: Throwable?) {
            Log.d("gioTAG", "MqttServiceCallback - connectionLost : ")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            Log.d("gioTAG", "MqttServiceCallback - deliveryComplete : ")
        }
    }

    private class HabitatMqttAction(private val client: MqttAndroidClient) : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d("gioTAG", "HabitatMqttAction - onSuccess : ")

            try {
                Log.d("gioTAG", "MainActivity - onCreate : porva il subscrive")
                client.subscribe("HabitatDevice", 2, null, HabitatSubscribe())
            } catch (e: Exception) {
                Log.d("gioTAG", "MainActivity - onCreate : Eccezione subscribe")
            }
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.d("gioTAG", "HabitatMqttAction - onFailure : ${exception}")
        }
    }

    private class HabitatSubscribe() : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d("gioTAG", "HabitatSubscribe - onSuccess : Sottoscritto")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.d("gioTAG", "HabitatSubscribe - onFailure : Non sottoscritto")
        }
    }
}