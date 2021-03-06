package it.baratta.giovanni.habitat.notificator.clientdemo

import android.content.Context
import io.reactivex.Observable

/**
 * Created by Gio on 11/09/2017.
 */
interface IMainView {

    val context : Context

    /* INPUT */
    val mqttServer : String
    val mqttTopic : String
    var registrationServer : String
    val mqttEnabled : Boolean
    val fcmEnabled : Boolean
    val fcmServer : String
    val sepaEnabled : Boolean
    val pingEnabled : Boolean
    val sepaServer : String
    val sepaQuery : String

    /* UI */
    fun showUI(show : Boolean)
    fun showProgress(show : Boolean)
    fun showError(msg : String)
    fun showMessagge(msg : String)
    var registered : Boolean
    var registerButtonEnabled : Boolean
    var deregisterButtonEnabled : Boolean
    var registrationServerEnabled : Boolean
    fun showRegistrationStatusProgress(show : Boolean)
    val registrationServerChanged : Observable<String>

    /**
     * Permette di bloccare/sbloccare tutti i campi editabile e i tasti
     * ad esclusione dei tasti di registrazione/deregistration
     * @param lock true blocca i campi, false li sblocca e li rende modificabili
     */
    fun lockInteractiveComponents(lock : Boolean)
}