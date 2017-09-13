package it.baratta.giovanni.habitat.notificator.clientdemo.retrofit

import it.baratta.giovanni.habitat.notificator.api.request.RegistrationRequest
import it.baratta.giovanni.habitat.notificator.api.response.IResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Interfaccia per rendere le chiamate REST locali tramite RETROFIT 2
 */
interface NotificatorService {
    @POST("rest/registration")
    fun registration(@Body request : RegistrationRequest ) : Call<IResponse>

    @DELETE("rest/deregistration")
    fun deregistration(@Query("token") token : String) : Call<IResponse>

}