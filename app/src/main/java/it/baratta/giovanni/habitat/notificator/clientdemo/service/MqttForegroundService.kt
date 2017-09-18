package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.R
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.gson.Gson
import it.baratta.giovanni.habitat.notificator.api.Message
import it.baratta.giovanni.habitat.notificator.clientdemo.MainPresenter
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.FilePersistence
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.PropertiesFile
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.File


/**
 * Created by Gio on 14/09/2017.
 */
class MqttForegroundService : Service() {

    private var mqttClient: MqttAndroidClient? = null
    private val mqttOptions = MqttConnectOptions()
    private lateinit var wakeLock : PowerManager.WakeLock

    private fun updateStatusMessage(msg : String){
        val builder = NotificationCompat.Builder(this, "MqttForegroundService")
                .setSmallIcon(R.drawable.sym_def_app_icon)
                .setContentTitle("MqttNotificationService")
                .setContentText(msg)

        (this.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(1, builder.build())
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"MqttServiceLock")
        Log.d("ClientDemo" , "Avvio MqttForeground service")
        val builder = NotificationCompat.Builder(this, "MqttForegroundService")
                .setSmallIcon(R.drawable.sym_def_app_icon)
                .setContentTitle("MqttNotificationService")

        startForeground(1, builder.build())
        updateStatusMessage("Non connesso")

        mqttOptions.isAutomaticReconnect = true
        mqttOptions.keepAliveInterval = 60
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        wakeLock.acquire()

        if(intent  != null)
            when(intent.action){
                RESTORE_ACTION -> tryRestoreConnection()
                START_NEW_CONNECTION_ACTION -> initializeFromIntent(intent)
                CLOSE_CONNECTION_ACTION -> closeMqttClient()
            }
        // con START_STICKY il servizio viene riavviato anche
        // se non ci sono richieste da parte di altri componenti
        return Service.START_STICKY
    }

    private fun closeMqttClient(){
        Log.d("ClientDemo","Disconnetto il client")
        mqttClient?.close()
        mqttClient = null
        updateStatusMessage("Non connesso")
        wakeLock.release()
        stopForeground(false)

    }

    private fun tryRestoreConnection(){
        Log.d("ClientDemo","Tentativo ripristino connessione")
        val file = File(this.filesDir, FilePersistence.SERVICE_CONFIGURATION_FILE)
        val properties : PropertiesFile

        try{
            properties = PropertiesFile(file, false, false)
        } catch (exception : Exception){
            return
        }

        MqttConnectionThread(this, properties, mqttOptions,
                {   mqttClient?.close()
                    mqttClient = it
                    updateStatusMessage("Connesso con il server ${it.serverURI}")
                    Log.d("ClientDemo","Procedura conclusa con successo")
                } /* sucess callback */,
                this::messageArrived /*message callback */,
                this::onError /* onError */).start()
    }

    private fun onError(throwable: Throwable){
        Log.d("ClientDemo","Errore nel clientMqtt.")
        Log.d("ClientDemo",throwable.localizedMessage)
    }

    private fun initializeFromIntent(intent : Intent){
        Log.d("ClientDemo","Inizializza da activity")
        val mqttServer = intent.getStringExtra(MainPresenter.MQTT_SERVER)
        val mqttTopic = intent.getStringExtra(MainPresenter.MQTT_TOPIC)
        val token = intent.getStringExtra(MainPresenter.TOKEN_PROPERTY)

        if(token == null){
            Log.d("ClientDemo", "token mancante")
            return
        }

        if(mqttServer == null || mqttTopic == null){
            Log.d("ClientDemo", "server/topic mancante")
            return
        }

        val mqttQos = intent.getStringExtra(MainPresenter.MQTT_QOS)
        var qosLevel = mqttQos?.toIntOrNull() ?: 0
        if(qosLevel < 0 || qosLevel > 2)
            qosLevel = 0

        Thread{
            val mqClient = MqttAndroidClient(this, mqttServer, "AndroidClient - ${token}")

            try{
                mqClient.connect(mqttOptions, null,
                        MqttConnectionActionListener(mqttTopic, qosLevel,
                                {   mqttClient?.disconnect()
                                    mqttClient?.close()
                                    mqttClient = mqClient
                                    updateStatusMessage("Connesso con il server ${mqClient.serverURI}")
                                    Log.d("ClientDemo","Procedura conclusa con successo")}, // onSuccess callback
                                this::messageArrived, // message callback
                                this::onError)/* Error callback */)

            } catch (e: MqttException) {
                Log.d("ClientDemo", "Errore durante la connessione con mqttBroker ${mqttServer}")
                onError(e)
            }
            Log.d("ClientDemo","Connessione avvenuta correttamente")
        }.start()
    }

    override fun onBind(p0: Intent?): IBinder?{
        return null
    }

    fun messageArrived(topic: String?, message: MqttMessage?) {

        val sb = StringBuilder()
        for (i in 0.until(message?.payload?.size ?: 0)) {
            sb.append(message?.payload?.elementAt(i)?.toChar())
        }

        val msg = gson.fromJson(sb.toString(), Message::class.java)

        Log.d("ClientDemo", "MqttServiceCallback - messageArrived : ${msg.id} - ${msg.jsonData}")

        val builder = NotificationCompat.Builder(this, "MqttMessage")
                .setSmallIcon(R.drawable.btn_star)
                .setContentTitle("MqttNotification")
                .setContentText("${msg.source} - ${msg.id}")

        builder.extras.putString(NotificationReciever.PAYLOAD, sb.toString())

        val nManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nManager.notify(msg.id.toInt(), builder.build())
    }

    companion object {
        private val gson = Gson()
        val RESTORE_ACTION = "TRY_RESTORE"
        val START_NEW_CONNECTION_ACTION = "NEW_CONNECTION"
        val CLOSE_CONNECTION_ACTION = "CLOSE_CONNECTION"
    }
}