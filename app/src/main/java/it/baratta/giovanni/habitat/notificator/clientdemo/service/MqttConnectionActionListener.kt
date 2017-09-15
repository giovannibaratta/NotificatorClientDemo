package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * Created by Gio on 14/09/2017.
 */
class MqttConnectionActionListener(private val topic : String,
                                   private val qosLevel : Int = 0,
                                   private val onSuccess : () -> Unit,
                                   private val messageCallback : (topic : String?, message : MqttMessage?) -> Unit,
                                   private val failCallback : (throwable: Throwable) -> Unit) : IMqttActionListener {

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        if(asyncActionToken == null || asyncActionToken?.client == null)
            return
        val client = asyncActionToken.client
        try {
            client.subscribe(topic, qosLevel, messageCallback)
        }catch (mqttException : MqttException){
            Log.d("ClientDemo", "Errore durante l'iscrizione al topic ${topic} avvenuta correttamente")
            client.disconnect()
            client.close()
            failCallback(mqttException)
        }
        Log.d("ClientDemo", "Iscrizione al topic ${topic} avvenuta correttamente")
        onSuccess()
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?)
            = failCallback(exception ?: Exception("Errore non indicato"))
}