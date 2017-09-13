package it.baratta.giovanni.habitat.notificator.clientdemo.utils

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * Created by Gio on 13/09/2017.
 */


class RetrofitCallback<T>(private val onResponseMethod : (Call<T>?,Response<T>?) -> Unit,
                          private val onFailureMethod : (Call<T>?,Throwable?) -> Unit) : Callback<T>{

    override fun onFailure(call: Call<T>?, t: Throwable?) {
        onFailureMethod(call, t)
    }

    override fun onResponse(call: Call<T>?, response: Response<T>?) {
        onResponseMethod(call, response)
    }
}