package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Intent
import android.util.Log
import com.google.gson.GsonBuilder
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
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.NotificatorService
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.ResponseParser
import it.baratta.giovanni.habitat.notificator.clientdemo.service.MqttForegroundService
import it.baratta.giovanni.habitat.notificator.clientdemo.utils.RetrofitCallback
import okhttp3.HttpUrl
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Presenter per registarsi presso il server centrale tramite richieste REST
 */
class MainPresenter(private val view : IMainView) : IMainPresenter {

    /* se != da null -> invocazione REST in corso */
    private var retrofitCall : Call<IResponse>? = null
    private var retrofitStatusCall : Call<IResponse>? = null
    /* se != da null -> sono registrato con il seguente token */
    private var currentToken: String? = null
    /* file utilizzato per salvare le informazioni sullo stato della
        registrazione. Viene utilizzato dal ForegroundService per
        configurarsi correttamente
     */
    private val propertiesFile : PropertiesFile
    private var subscription : Disposable

    val gson = GsonBuilder()
            .registerTypeAdapter(IResponse::class.java, ResponseParser())
            .create()

    init {
        showOnlyUI()
        view.showRegistrationStatusProgress(false)

        subscription =  view.registrationServerChanged
                        .mergeWith(view.registrationPortChanged
                                    .map{value -> value.toString()})
                        .debounce(debounceTime, TimeUnit.MILLISECONDS)
                        .map { value ->  }
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::serverChanged)

        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registered =false

        propertiesFile= PropertiesFile(File(view.context.filesDir,FilePersistence.SERVICE_CONFIGURATION_FILE),
                                    true, false)
    }

    private fun serverChanged(value : Unit){
        val token = propertiesFile.getProperty(TOKEN_PROPERTY) ?: "das"
        if( token == null) {
            view.showRegistrationStatusProgress(false)
            return
        }

        retrofitStatusCall?.cancel()
        retrofitStatusCall = null

        val serverAddress = view.registrationServer
        val serverPort = view.registrationPort

        if(serverPort == null || serverPort < 1024 || serverPort > 65536
            || !serverRegex.matches(serverAddress)){
            view.showRegistrationStatusProgress(false)
            return
        }

        view.showRegistrationStatusProgress(true)

        val notificatorService = Retrofit.Builder()
                .baseUrl("http://${serverAddress}:${serverPort}/core_main_Web_exploded/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(NotificatorService::class.java)

        retrofitStatusCall = notificatorService.registrationStatus(token)
        retrofitStatusCall?.enqueue(RetrofitCallback<IResponse>(
                { call, response -> onSuccessStatusCall(response?.body(), call?.request()?.url())},
                { call, throwable -> }))
    }

    private fun onSuccessStatusCall(response : IResponse?, server : HttpUrl?){
        retrofitStatusCall = null

        if(response == null || server == null)
            return

        when(response.error){
            false->{
                val castedResponse = response as StatusResponse
                if(response.registered){
                    registerClient(castedResponse.token, server)
                }else{
                    deregisterClient()
                }
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

        if(retrofitCall != null){
            view.showMessagge("Un'altra operazione è in corso")
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
                Log.d("gioTAG","MainPresenter - register : ${server}")
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

        val notificatorService = Retrofit.Builder()
                .baseUrl("http://${serverAddress}:${serverPort}/core_main_Web_exploded/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(NotificatorService::class.java)

        val registrationCall = notificatorService
                .registration(RegistrationRequest(eventSourceModule,notificatorModule))

        retrofitCall = registrationCall

        registrationCall.enqueue(RetrofitCallback<IResponse>(
                { call, reponse -> onSuccessRegistrationCall(reponse?.body(),call?.request()?.url())}, // onSuccess
                { call, throwable -> onErrorRegistrationCall(throwable)})) // onError

    }

    /**
     * effettua una richiesta di deregitrazione tramite invocazione REST
     */
    override fun deregister() {

        if(retrofitCall != null){
            view.showMessagge("c'è un'altra operazione in corso")
            return
        }

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

        val notificatorService = Retrofit.Builder()
                .baseUrl("http://${serverAddress}:${serverPort}/core_main_Web_exploded/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(NotificatorService::class.java)

        val registrationCall = notificatorService
                .deregistration(token)

        retrofitCall = registrationCall

        registrationCall.enqueue(RetrofitCallback<IResponse>(
                { call, response -> onSuccessDeregistrationCall(response?.body())}, // onSuccess
                { call, throwable -> onErrorDeregistrationCall(throwable)})) // onError

    }

    /**
     * Callback per la richiesta REST di deregitrazione
     */
    private fun onSuccessDeregistrationCall(response: IResponse?){
        retrofitCall = null

        if(response == null)
            return

        when(response.error){
            false ->{
                currentToken = null
                view.showMessagge("Deregistrazione avvenuta corretamente")
            }
            true ->
                view.showError("Errore durante la deregistrazione : " +
                        "${(response as ErrorResponse).errorMsg}")
        }

        view.registered = response.error
        view.deregisterButtonEnabled = response.error
        view.registerButtonEnabled = !response.error
        view.registrationServerEnabled = !response.error
        view.registrationServerPortEnabled = !response.error

        showOnlyUI()
    }

    /**
     * Callback per la richiesta REST di deregistrazione
     */
    private fun onErrorDeregistrationCall(throwable: Throwable?){
        retrofitCall = null
        view.showError(throwable?.localizedMessage ?: "")
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
        showOnlyUI()
    }


    /**
     * Callback per la richiesta REST di registrazione
     */
    private fun onSuccessRegistrationCall(response : IResponse?, server: HttpUrl?){
        retrofitCall = null

        if(response == null || server  == null)
            return

        when(response.error){
           false ->{
               currentToken = (response as RegistrationResponse).token
               registerClient((response as RegistrationResponse).token, server)
               view.showMessagge(currentToken ?: "")

               // mi attacco al foreground service
               val intent : Intent = Intent(view.context, MqttForegroundService::class.java)
               view.context.startService(intent)
           }
           true -> view.showError("Errore durante la registrazione : " +
                        "${(response as ErrorResponse).errorMsg}")
        }

        view.registered = !response.error
        view.deregisterButtonEnabled = !response.error
        view.registerButtonEnabled = response.error
        view.registrationServerEnabled = response.error
        view.registrationServerPortEnabled = response.error

        showOnlyUI()
    }

    private fun registerClient(token : String, server: HttpUrl){
        propertiesFile.setProperty(TOKEN_PROPERTY, token)
        propertiesFile.setProperty(SERVER_PROPERTY, server.host())
        propertiesFile.setProperty(PORT_PROPERTY, server.port().toString())
        propertiesFile.update()
        currentToken = token
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
        view.registrationServerEnabled = false
        view.registrationServerPortEnabled = false
    }

    private fun deregisterClient(){
        propertiesFile.removeProperty(TOKEN_PROPERTY)
        propertiesFile.removeProperty(SERVER_PROPERTY)
        propertiesFile.removeProperty(PORT_PROPERTY)
        propertiesFile.update()

        view.registered = false
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registrationServerEnabled = true
        view.registrationServerPortEnabled = true
    }

    /**
     * Callback per la richiesta REST di registrazione
     */
    private fun onErrorRegistrationCall(throwable: Throwable?){
        retrofitCall = null
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
    }
}