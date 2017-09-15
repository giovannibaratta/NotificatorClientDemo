package it.baratta.giovanni.habitat.notificator.clientdemo.retrofit

import it.baratta.giovanni.habitat.notificator.api.request.RegistrationRequest
import it.baratta.giovanni.habitat.notificator.api.response.IResponse
import it.baratta.giovanni.habitat.notificator.clientdemo.utils.RetrofitCallback
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Created by Gio on 14/09/2017.
 */

class RestAsyncInteractor(server : String){

    val working : Boolean
        get() = currentTask != null

    private var currentTask : Call<IResponse>? = null

    private val notificatorService : NotificatorService

    init {
        retrofitBuilder.addConverterFactory(GsonConverterFactory.create(ResponseParser.customGson))
        notificatorService = retrofitBuilder
                .baseUrl(server)
                .build().create(NotificatorService::class.java)
    }

    fun registration(request : RegistrationRequest,
                     onSuccessCallback : ((IResponse) -> Unit)? = null,
                     onErrorCallback : ((Throwable?) -> Unit)? = null,
                     cancelOtherTask : Boolean = false) : Boolean{

        if(working && !cancelOtherTask)
            return false

        if(working) // cancelOtherTask = True
            currentTask?.cancel()

        currentTask = notificatorService.registration(request)
        enqueueTask(onSuccessCallback, onErrorCallback)
        return true
    }

    fun deregistration(token : String,
                       onSuccessCallback : ((IResponse) -> Unit)? = null,
                       onErrorCallback : ((Throwable?) -> Unit)? = null,
                       cancelOtherTask : Boolean = false) : Boolean{
        if(working && !cancelOtherTask)
            return false

        if(working) // cancelOtherTask = True
            currentTask?.cancel()

        currentTask = notificatorService.deregistration(token)
        enqueueTask(onSuccessCallback, onErrorCallback)
        return true
    }

    fun status(token : String,  onSuccessCallback : ((IResponse) -> Unit)? = null,
               onErrorCallback : ((Throwable?) -> Unit)? = null,
               cancelOtherTask: Boolean = false) : Boolean{

        if(working && !cancelOtherTask)
            return false

        if(working) // cancelOtherTask = True
            currentTask?.cancel()

        currentTask = notificatorService.registrationStatus(token)
        enqueueTask(onSuccessCallback, onErrorCallback)
        return true
    }

    private fun enqueueTask(onSuccessCallback : ((IResponse) -> Unit)? = null,
                            onErrorCallback : ((Throwable?) -> Unit)? = null){
        currentTask?.enqueue(RetrofitCallback<IResponse>(
                { call, response ->
                    if(response != null && response.isSuccessful)
                        onSuccess(response.body()!!, onSuccessCallback)
                    else
                        onError(null, onErrorCallback)}, // onSuccess
                { call, throwable -> onError(throwable, onErrorCallback)})) //onError
    }

    private fun onSuccess(response : IResponse, onSuccess : ((IResponse) -> Unit)?){
        currentTask = null
        onSuccess?.invoke(response)
    }

    private fun onError(throwable: Throwable?,  onError : ((Throwable?) -> Unit)?){
        currentTask = null
        onError?.invoke(throwable)
    }

    fun cancelTask(){
        currentTask?.cancel()
        currentTask = null
    }

    companion object {
        private val retrofitBuilder = Retrofit.Builder()
    }
}