package it.baratta.giovanni.habitat.notificator.clientdemo

import android.os.AsyncTask
import android.util.Log
import it.baratta.giovanni.habitat.notificator.api.ConfigurationParams
import it.baratta.giovanni.habitat.notificator.api.ModuleRequest

/**
 * Created by Gio on 11/09/2017.
 */
class MainPresenter(private val view : IMainView) : IMainPresenter {

    private var runningTask : AsyncTask<*,*,*>? = null
    private var currentToken: String? = null

    override fun register() {

        if(runningTask != null){
            view.showMessagge("c'è un'altra operazione in corso")
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

        val task = RegistrationTask(serverAddress, serverPort,
                        this::onSuccessRegistration,this::onErrorRegistration)
        runningTask = task
        task.execute(Pair(eventSourceModule,notificatorModule))
    }

    companion object {
        private val mqttServerRegex = Regex("^tcp://\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}:\\d{4,5}$")
        private val serverRegex = Regex("^\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}\\.\\d{0,3}$")
    }

    override fun deregister() {
        if(runningTask != null){
            view.showMessagge("c'è un'altra operazione in corso")
            return
        }

        if(currentToken == null){
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

        val task = DeregistrationTask(serverAddress, serverPort,
                this::onSuccessDeregistration, this::onErrorDeregistration)
        runningTask = task
        task.execute(currentToken)
    }

    init {
        showOnlyUI()
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registered =false
    }

    private fun onSuccessDeregistration(){
        showOnlyUI()
        runningTask = null
        currentToken = null
        view.showMessagge("Deregistrazione avvenuta corretamente")
        view.registered = false
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
        view.registrationServerEnabled = true
        view.registrationServerPortEnabled = true
    }

    private fun onErrorDeregistration(msg : String){
        showOnlyUI()
        runningTask = null
        view.showError(msg)
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
    }


    private fun onSuccessRegistration(token : String){
        showOnlyUI()
        runningTask = null
        currentToken = token
        view.showMessagge(token)
        view.registered = true
        view.deregisterButtonEnabled = true
        view.registerButtonEnabled = false
        view.registrationServerEnabled = false
        view.registrationServerPortEnabled = false
    }

    private fun onErrorRegistration(msg : String){
        showOnlyUI()
        runningTask = null
        view.showError(msg)
        view.registered = false
        view.deregisterButtonEnabled = false
        view.registerButtonEnabled = true
    }

    private fun showOnlyUI(){
        view.showUI(true)
        view.showProgress(false)
    }

    private fun showOnlyProgress(){
        view.showUI(false)
        view.showProgress(true)
    }

}