package it.baratta.giovanni.habitat.notificator.clientdemo

import android.os.AsyncTask
import android.util.Log
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Created by Gio on 12/09/2017.
 */
class DeregistrationTask(private val serverAddress : String,
                         private val serverPort : Int,
                         private val onSuccess : () -> Unit,
                         private val onError : (msg : String) -> Unit) : AsyncTask<String, Unit, Int>() {

    private val SUCCESS = 0
    private val ERROR = 1

    override fun onPostExecute(result: Int?) {
        when(result){
            SUCCESS -> onSuccess()
            ERROR -> onError("Errore durante la deregistrazione")
        }
    }

    override fun doInBackground(vararg token: String?): Int {
        if(token.size != 1)
            return ERROR

        // richiesta tramite socket
        val socket : Socket
        try {
            socket = Socket(serverAddress,serverPort)
            val os = socket.getOutputStream()
            val ins = socket.getInputStream()

            // tipologia richiesta 2 byte - codice 01 per deregistrazione
            os.write(0)
            os.write(1)

            // inivio i token
            os.write(token[0]!!.toByteArray())

            val response = ins.read()
            Log.d("DeregistrationTask", "result ${response}")

            socket.close()

            if(response == 0)
                return SUCCESS
            else
                return ERROR

        }catch (exception : IOException){
            return ERROR
        }
    }

}