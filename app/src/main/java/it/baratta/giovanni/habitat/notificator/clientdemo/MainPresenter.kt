package it.baratta.giovanni.habitat.notificator.clientdemo

import android.util.Log
import com.google.gson.GsonBuilder
import it.baratta.giovanni.habitat.notificator.api.request.ConfigurationParams
import it.baratta.giovanni.habitat.notificator.api.request.ModuleRequest
import it.baratta.giovanni.habitat.notificator.api.request.RegistrationRequest
import it.baratta.giovanni.habitat.notificator.api.response.ErrorResponse
import it.baratta.giovanni.habitat.notificator.api.response.IResponse
import it.baratta.giovanni.habitat.notificator.api.response.RegistrationResponse
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.NotificatorService
import it.baratta.giovanni.habitat.notificator.clientdemo.retrofit.ResponseParser
import it.baratta.giovanni.habitat.notificator.clientdemo.utils.RetrofitCallback
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Presenter per registarsi presso il server centrale tramite richieste REST
 */
class MainPresenter(private val view : IMainView) : IMainPresenter {

    private var retrofitCall : Call<IResponse>? = null
    private var currentToken: String? = null

    val gson = GsonBuilder()
            .registerTypeAdapter(IResponse::class.java, ResponseParser())
            .create()

    init {
        showOnlyUI()
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registered =false
    }

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
                { call, reponse -> onSuccessRegistrationCall(reponse?.body())}, // onSuccess
                { call, throwable -> onErrorRegistrationCall(throwable)})) // onError

    }

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
                { call, reponse -> onSuccessDeregistrationCall(reponse?.body())}, // onSuccess
                { call, throwable -> onErrorDeregistrationCall(throwable)})) // onError

    }

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

    private fun onErrorDeregistrationCall(throwable: Throwable?){
        retrofitCall = null
        view.showError(throwable?.localizedMessage ?: "")
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
        showOnlyUI()
    }


    private fun onSuccessRegistrationCall(response : IResponse?){
        retrofitCall = null

        if(response == null)
            return

        when(response.error){
           false ->{
               currentToken = (response as RegistrationResponse).token
               view.showMessagge(currentToken ?: "")
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

    private fun onErrorRegistrationCall(throwable: Throwable?){
        retrofitCall = null
        view.showError(throwable?.localizedMessage ?: "")
        view.registered = false
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        showOnlyUI()
    }

    private fun showOnlyUI(){
        view.showUI(true)
        view.showProgress(false)
    }

    private fun showOnlyProgress(){
        view.showUI(false)
        view.showProgress(true)
    }

    companion object {
        private val mqttServerRegex = Regex("^tcp://\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}:\\d{4,5}$")
        private val serverRegex = Regex("^\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}$")
    }
}