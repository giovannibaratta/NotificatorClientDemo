package it.baratta.giovanni.habitat.notificator.clientdemo.service

import android.content.Context
import android.util.Log
import it.baratta.giovanni.habitat.notificator.api.response.StatusResponse
import it.baratta.giovanni.habitat.notificator.clientdemo.MainPresenter
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.PropertiesFile
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.NotificatorService
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.ResponseParser
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Created by Gio on 14/09/2017.
 */

class MqttConnectionThread(private val context : Context,
                           private val properties : PropertiesFile,
                           private val mqttClientOption : MqttConnectOptions,
                           private val onSuccess : (client : MqttAndroidClient) -> Unit,
                           private val messageCallback : (topic : String?, message : MqttMessage?) -> Unit,
                           private val onError : (throwable : Throwable) -> Unit) : Thread(){
    override fun run() {

        val token = properties.getProperty(MainPresenter.TOKEN_PROPERTY)
        if(token == null){
            onError(Exception("token non presente"))
            return
        }

        Log.d("ClientDemo","token presente ${token}")

        val server = properties.getProperty(MainPresenter.SERVER_PROPERTY)

        if(server == null) {
            onError(Exception("server non presente"))
        }

        // ho tutte le informazioni necessarie, verifico che il token sia
        // ancora valido

        val response = Retrofit.Builder()
                .baseUrl(server)
                .addConverterFactory(GsonConverterFactory.create(ResponseParser.customGson))
                .build()
                .create(NotificatorService::class.java)
                .registrationStatus(token)
                .execute()

        if(!response.isSuccessful
                || response.body() == null) {
            onError(Exception("invocazione rest fallita"))
            return
        }


        Log.d("ClientDemo","REST OK")

        val status = response.body() as StatusResponse
        if(!status.registered) {
            onError(Exception("token $token non registrato"))
            return
        }


        Log.d("ClientDemo","token $token registrato")

        // verifico che il token abbia un notificator mqtt associato
        val mqttModule = status.notificatorModule
                .filter { it.moduleName == "mqtt" }
                .firstOrNull()

        if(mqttModule == null) {
            onError(Exception("il token non ha un modulo mqtt"))
            return
        }

        val mqttServer = mqttModule.params.getParam("server")
        val topic = mqttModule.params.getParam("topic")
        var qosLevel = mqttModule.params
                    .getParam("qosLevel")?.toIntOrNull() ?: 0

        if(qosLevel < 0 || qosLevel > 2)
            qosLevel = 0

        if(mqttServer == null || topic == null) {
            onError(Exception("il modulo associato non ha i parametri minimi per la" +
                    "configurazione"))
            return
        }

        Log.d("ClientDemo","moduli OK")

        val mqClient = MqttAndroidClient(context, mqttServer, "AndroidClient - ${token}")

        try{
            mqClient.connect(mqttClientOption, null,
                    MqttConnectionActionListener(topic, qosLevel,
                            { onSuccess(mqClient)}, // onSuccess callback
                            messageCallback, // message callback
                            onError)/* Error callback */)

        } catch (e: MqttException) {
            Log.d("ClientDemo", "Errore durante la connessione con mqttBroker ${server}")
            onError(e)
        }


        Log.d("ClientDemo","Connessione avvenuta correttamente")
    }
}