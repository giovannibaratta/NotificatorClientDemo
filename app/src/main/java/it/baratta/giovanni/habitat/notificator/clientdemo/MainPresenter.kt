package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import it.baratta.giovanni.habitat.notificator.api.request.ConfigurationParams
import it.baratta.giovanni.habitat.notificator.api.request.ModuleRequest
import it.baratta.giovanni.habitat.notificator.api.request.RegistrationRequest
import it.baratta.giovanni.habitat.notificator.api.response.ErrorResponse
import it.baratta.giovanni.habitat.notificator.api.response.IResponse
import it.baratta.giovanni.habitat.notificator.api.response.RegistrationResponse
import it.baratta.giovanni.habitat.notificator.api.response.StatusResponse
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.FilePersistence
import it.baratta.giovanni.habitat.notificator.clientdemo.persistence.PropertiesFile
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.RestAsyncInteractor
import it.baratta.giovanni.habitat.notificator.clientdemo.service.MqttForegroundService
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Presenter per registarsi presso il server centrale tramite richieste REST
 */
class MainPresenter(private val view : IMainView) : IMainPresenter {

    /* All'avvio non so se il mio token di registrazione è valido */
    private var unkownRegisteredStatus = true

    private var restInteractor : RestAsyncInteractor
    /* se != da null -> sono registrato con il seguente token */
    private var currentToken: String? = null
    /* file utilizzato per salvare le informazioni sullo stato della
        registrazione. Viene utilizzato dal ForegroundService per
        configurarsi correttamente
     */
    private val propertiesFile : PropertiesFile
    private var subscription : Disposable

    private val wifiManager : WifiManager
    private val telephonyManager : TelephonyManager

    init {
        showOnlyUI()
        view.showRegistrationStatusProgress(false)

        subscription =  view.registrationServerChanged
                        .mergeWith(view.registrationPortChanged
                                    .map{value -> value.toString()})
                        .debounce(debounceTime, TimeUnit.MILLISECONDS)
                        .map { value -> }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::serverChanged)


        view.showRegistrationStatusProgress(false)
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registered =false

        wifiManager = view.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        telephonyManager = view.context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        propertiesFile= PropertiesFile(File(view.context.filesDir,FilePersistence.SERVICE_CONFIGURATION_FILE),
                                    true, false)

        currentToken = propertiesFile.getProperty(TOKEN_PROPERTY)
        view.registrationServer = propertiesFile.getProperty(SERVER_PROPERTY) ?: "192.168.0.5"
        view.registrationPort = propertiesFile.getProperty(PORT_PROPERTY)?.toIntOrNull() ?: 8080

        restInteractor =
                RestAsyncInteractor("http://${view.registrationServer}:${view.registrationPort}/core_main_Web_exploded/")

        val token = currentToken
        // se ancora non ho effettuao operazioni ed è presente un token di esecuzioni precedenti
        // non terminate correttamente provo a verificare lo stato
        if(unkownRegisteredStatus && token != null) {
            view.showRegistrationStatusProgress(true)
            restInteractor.status(token, this :: onSuccessStatusCall,
                    {view.showRegistrationStatusProgress(false)}) //onError
        }
    }

    /**
     * Invocato quando l'indirizo del server o la sua porta cambiano,
     * aggiorna il rest interactor con i nuovi dati
     */
    private fun serverChanged(value : Unit){

        val serverAddress = view.registrationServer
        val serverPort = view.registrationPort

        if(serverPort == null || serverPort < 1024 || serverPort > 65536
            || !serverRegex.matches(serverAddress)){
            view.registerButtonEnabled = false
            return
        }
        view.registerButtonEnabled = true
        // se sono dati validi creo un nuovo interactor
        restInteractor.cancelTask()
        restInteractor =
                RestAsyncInteractor("http://${serverAddress}:${serverPort}/core_main_Web_exploded/")
    }

    private fun onSuccessStatusCall(response : IResponse){
        when(response.error){
            false->{
                val castedResponse = response as StatusResponse
                if(response.registered){
                    registerClient(castedResponse.token)
                }else{
                    deregisterClient()
                }
                unkownRegisteredStatus = false
            }
            true -> view.showError("Errore durante il recupero dello stato")
        }
        view.showRegistrationStatusProgress(false)
    }

    /**
     * Effettuo una richiesta di registrazione agli event source e notificator
     * tramite invocazione REST
     */
    override fun register() {

        // verifico la presenza della rete
        if(wifiManager.connectionInfo.networkId == -1 &&
                telephonyManager.dataState != TelephonyManager.DATA_CONNECTED){
            view.showError("Non sei connesso alla rete")
            return
        }

        // recupero i dati e preparo la richiesta
        val eventSourceModule
                = listOf(ModuleRequest("mock", ConfigurationParams(HashMap())))
        val notificatorModule = ArrayList<ModuleRequest>()

        if(view.fcmEnabled){
            val parms = HashMap<String, String>()
            // aggiungi il tokne
            notificatorModule.add(ModuleRequest("fcm", ConfigurationParams(parms)))
        }
        if(view.mqttEnabled){
            val server = view.mqttServer
            if(mqttServerRegex.matches(server)){
                val parms = HashMap<String, String>()
                parms.put("server",server)
                parms.put("topic",view.mqttTopic)
                notificatorModule.add(ModuleRequest("mqtt", ConfigurationParams(parms)))
            }else{
                view.showError("Il server indicato non è valido")
                return
            }
        }
        if(notificatorModule.size == 0){
            view.showError("Devi selezionare almeno un notificator")
            return
        }

        val serverAddress = view.registrationServer
        val serverPort = view.registrationPort

        if(serverPort == null || serverPort < 1024 || serverPort > 65536){
            view.showError("La porta di registrazione non è valida")
            return
        }

        if(!serverRegex.matches(serverAddress)){
            view.showError("Il server di registrazione non è valido")
            return
        }

        showOnlyProgress()

        restInteractor.registration(RegistrationRequest(eventSourceModule, notificatorModule),
                                    this::onSuccessRegistrationCall,
                                    this::onErrorRegistrationCall, true)

    }

    /**
     * effettua una richiesta di deregitrazione tramite invocazione REST
     */
    override fun deregister() {


        val token = currentToken

        if(token == null){
            view.showError("Non sei ancora registrato")
            return
        }

        val serverAddress = view.registrationServer
        val serverPort = view.registrationPort

        if(serverPort == null || serverPort < 1024 || serverPort > 65536){
            view.showError("La porta di registrazione non è valida")
            return
        }

        if(!serverRegex.matches(serverAddress)){
            view.showError("Il server di registrazione non è valido")
            return
        }

        showOnlyProgress()

       restInteractor.deregistration(token,
                    this::onSuccessDeregistrationCall,
                    this::onErrorDeregistrationCall,
               true)

    }

    /**
     * Callback per la richiesta REST di deregitrazione
     */
    private fun onSuccessDeregistrationCall(response: IResponse){

        when(response.error){
            false -> deregisterClient()
            true -> view.showError("Errore durante la deregistrazione : " +
                        "${(response as ErrorResponse).errorMsg}")
        }
        showOnlyUI()
    }

    /**
     * Callback per la richiesta REST di deregistrazione
     */
    private fun onErrorDeregistrationCall(throwable: Throwable?){
        //retrofitCall = null
        view.showError(throwable?.localizedMessage ?: "")
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
        showOnlyUI()
    }

    /**
     * Callback per la richiesta REST di registrazione
     */
    private fun onSuccessRegistrationCall(response : IResponse){
        when(response.error){
           false ->{
               val token = (response as RegistrationResponse).token
               view.showMessagge(token ?: "")
               registerClient(token)
           }
           true -> view.showError("Errore durante la registrazione : " +
                        "${(response as ErrorResponse).errorMsg}")
        }

        showOnlyUI()
    }

    private fun registerClient(token : String){
        Log.d("ClientDemo","Registro il cliente")
        propertiesFile.setProperty(SERVER_PROPERTY, view.registrationServer)
        propertiesFile.setProperty(PORT_PROPERTY, view.registrationPort.toString())
        propertiesFile.setProperty(TOKEN_PROPERTY, token)
        propertiesFile.update()
        currentToken = token
        // avvio mqtt sul servizio di foreground
        val serviceIntent = Intent(view.context, MqttForegroundService::class.java)
        serviceIntent.putExtra(MQTT_SERVER, view.mqttServer)
        serviceIntent.putExtra(MQTT_TOPIC, view.mqttTopic)
        serviceIntent.putExtra(TOKEN_PROPERTY, token)
        serviceIntent.action = MqttForegroundService.START_NEW_CONNECTION_ACTION
        view.context.startService(serviceIntent)

        view.registered = true
        view.lockInteractiveComponents(true)
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
    }

    private fun deregisterClient(){
        propertiesFile.removeProperty(SERVER_PROPERTY)
        propertiesFile.removeProperty(PORT_PROPERTY)
        propertiesFile.removeProperty(TOKEN_PROPERTY)
        propertiesFile.update()
        // avvio mqtt sul servizio di foreground
        val serviceIntent = Intent(view.context, MqttForegroundService::class.java)
        serviceIntent.action = MqttForegroundService.CLOSE_CONNECTION_ACTION
        view.context.startService(serviceIntent)
        currentToken = null
        view.registered = false
        view.lockInteractiveComponents(false)
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
    }

    /**
     * Callback per la richiesta REST di registrazione
     */
    private fun onErrorRegistrationCall(throwable: Throwable?){
        view.showError(throwable?.localizedMessage ?: "")
        view.registered = false
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        showOnlyUI()
    }

    /**
     * Disabilita il carimento e visualizza la UI
     */
    private fun showOnlyUI(){
        view.showUI(true)
        view.showProgress(false)
        view.showRegistrationStatusProgress(false)
    }

    /**
     * Disabilita l'interfaccia e
     *   mostra all'utente un carimaneto
     */
    private fun showOnlyProgress(){
        view.showUI(false)
        view.showProgress(true)
    }

    override fun onDesroy() { subscription.dispose() }
    override fun onPause() { }
    override fun onRestart() { }

    companion object {
        private val mqttServerRegex = Regex("^tcp://\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}:\\d{4,5}$")
        private val serverRegex = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
        private const val debounceTime = 500L
        const val TOKEN_PROPERTY = "token"
        const val SERVER_PROPERTY = "server"
        const val PORT_PROPERTY = "port"
        const val MQTT_SERVER = "mqttserver"
        const val MQTT_TOPIC = "mqtttopic"
        const val MQTT_QOS = "qoslevel"
    }
}