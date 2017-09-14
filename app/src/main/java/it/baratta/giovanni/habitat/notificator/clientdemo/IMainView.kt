package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Context
import io.reactivex.Observable

/**
 * Created by Gio on 11/09/2017.
 */
interface IMainView {

    val context : Context
    fun showUI(show : Boolean)
    fun showProgress(show : Boolean)
    val mqttEnabled : Boolean
    val mqttServer : String
    val mqttTopic : String
    val fcmEnabled : Boolean
    fun showError(msg : String)
    fun showMessagge(msg : String)
    var registered : Boolean
    var registerButtonEnabled : Boolean
    var deregisterButtonEnabled : Boolean
    val registrationServer : String
    val registrationPort: Int?
    var registrationServerEnabled : Boolean
    var registrationServerPortEnabled : Boolean
    fun showRegistrationStatusProgress(show : Boolean)
    val registrationServerChanged : Observable<String>
    val registrationPortChanged : Observable<Int>
}