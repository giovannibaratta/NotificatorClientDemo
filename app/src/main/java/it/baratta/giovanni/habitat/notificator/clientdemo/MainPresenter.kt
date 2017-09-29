package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
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

        try {
            restInteractor =
                    RestAsyncInteractor(view.registrationServer)
            view.registerButtonEnabled = true
        }catch (exception : Exception){
            view.showError("Registration server non valido")
            view.registerButtonEnabled = false
            restInteractor = RestAsyncInteractor("http://localhost")
        }

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

        view.registerButtonEnabled = true
        // se sono dati validi creo un nuovo interactor
        restInteractor.cancelTask()
        try {
            restInteractor =
                    RestAsyncInteractor(serverAddress)
            view.registerButtonEnabled = true
        }catch (exception : Exception){
            view.showError("Registration server non valido")
            view.registerButtonEnabled = false
            restInteractor = RestAsyncInteractor("http://localhost")
        }
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
        if(!isConnected()){
            view.showError("Non sei connesso alla rete")
            return
        }

        // recupero i dati e preparo la richiesta
        val eventSourceModule = ArrayList<ModuleRequest>()
        val notificatorModule = ArrayList<ModuleRequest>()

        showOnlyProgress()

        try {
            if (view.pingEnabled)
                eventSourceModule.add(readPingSourceInformation())

            if (view.sepaEnabled)
                eventSourceModule.add(readSepaSourceInformation())

            if (view.fcmEnabled)
                notificatorModule.add(readFcmNotificationInformation())

            if (view.mqttEnabled)
                notificatorModule.add(readMqttNotificationInformation())
        }catch (exception : Exception){
            showOnlyUI()
            view.showError(exception.localizedMessage)
            return
        }

        if(eventSourceModule.size == 0){
            showOnlyUI()
            view.showError(view.context.getString(R.string.event_source_size_error))
            return
        }

        if(notificatorModule.size == 0){
            showOnlyUI()
            view.showError(view.context.getString(R.string.notificator_size_error))
            return
        }
        
        restInteractor.registration(RegistrationRequest(eventSourceModule, notificatorModule),
                                    this::onSuccessRegistrationCall,
                                    this::onErrorRegistrationCall, true)

    }

    private fun readFcmNotificationInformation() : ModuleRequest {

        val server = view.fcmServer
        val firebaseToken = FirebaseInstanceId.getInstance().token

        if(!isValidFCMServer(server))
            throw IllegalStateException()

        if(firebaseToken == null)
            throw IllegalStateException(view.context.getString(R.string.firebase_token_error))

        val parms = ConfigurationParams()
        parms.setParam("fcmToken", firebaseToken)
        parms.setParam("fcmProxy", server)
        return ModuleRequest("fcm", parms)
    }

    private fun readSepaSourceInformation() : ModuleRequest{
        val server = view.sepaServer
        val query = view.sepaQuery

        if(!isValidSEPAServer(server))
            throw IllegalStateException(view.context.getString(R.string.sepa_server_error))

        if(!isValidSEPAQuery(query))
            throw  IllegalStateException(view.context.getString(R.string.sepa_query_error))

        val parms = ConfigurationParams()
        parms.setParam("server",server)
        parms.setParam("query",query)
        return ModuleRequest("sepa", parms)
    }

    private fun readPingSourceInformation() : ModuleRequest
        = ModuleRequest("ping", ConfigurationParams())

    private fun readMqttNotificationInformation() : ModuleRequest{
        val server = view.mqttServer
        val topic = view.mqttTopic

        if(!isValidMqttServer(server))
            throw IllegalStateException(view.context.getString(R.string.mqtt_server_error))

        if(topic.isBlank())
            throw  IllegalStateException(view.context.getString(R.string.mqtt_topic_error))

        val parms = ConfigurationParams()
        parms.setParam("server",server)
        parms.setParam("topic",topic)
        return ModuleRequest("mqtt", parms)
    }

    private fun isValidFCMServer(server: String) : Boolean
        = !server.isBlank()

    private fun isValidMqttServer(server : String) : Boolean
        = !server.isBlank()

    private fun isValidSEPAServer(server: String) : Boolean
        = !server.isBlank()

    private fun isValidSEPAQuery(query : String) : Boolean
        = !query.isBlank()

    /**
     * effettua una richiesta di deregitrazione tramite invocazione REST
     */
    override fun deregister() {


        val token = currentToken

        if(token == null){
            view.showError("Non sei ancora registrato")
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

    private fun isConnected() : Boolean
        = wifiManager.connectionInfo.networkId != -1
            || telephonyManager.dataState == TelephonyManager.DATA_CONNECTED

    override fun onDesroy() { subscription.dispose() }
    override fun onPause() { }
    override fun onRestart() { }

    companion object {
        private const val debounceTime = 300L
        const val TOKEN_PROPERTY = "token"
        const val SERVER_PROPERTY = "server"
        const val MQTT_SERVER = "mqttserver"
        const val MQTT_TOPIC = "mqtttopic"
        const val MQTT_QOS = "qoslevel"
    }
}