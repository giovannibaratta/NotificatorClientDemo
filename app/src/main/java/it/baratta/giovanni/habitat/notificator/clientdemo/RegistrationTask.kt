package it.baratta.giovanni.habitat.notificator.clientdemo

import android.os.AsyncTask
import android.util.Log
import com.google.gson.Gson
import it.baratta.giovanni.habitat.notificator.api.ModuleRequest
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * Created by Gio on 11/09/2017.
 */
class RegistrationTask(private val serverAddress : String,
                       private val serverPort : Int,
                       private val onSuccess : (token : String) -> Unit,
                       private val onError : (msg : String) -> Unit)
    : AsyncTask<Pair<List<ModuleRequest>,List<ModuleRequest>>,Int,String>(){

    private val gson = Gson()

    override fun onPostExecute(result: String) {
        if(result.isNotEmpty())
            onSuccess(result)
        else
            onError("Errore durante la registraione")
    }

    override fun doInBackground(vararg modules: Pair<List<ModuleRequest>, List<ModuleRequest>>?): String {
        if(modules.size != 1
                || modules[0] == null) {
            return ""
        }

        // richiesta tramite socket
        val socket : Socket
        try {
            socket = Socket(serverAddress,serverPort)
            val os = socket.getOutputStream()
            val ins = socket.getInputStream()

            // tipologia richiesta 2 byte - codice 00 per registrazione
            os.write(0)
            os.write(0)
            // tipologia dati 1 byte
            os.write(1)
            // numeri moduli ev source 1 byte
            os.write(modules[0]!!.first.size)
            // numeri moduli notificator 1 byte
            os.write(modules[0]!!.second.size)

            val buffer = ByteBuffer.allocate(4)

            // scrivo tutti i moduli
            for (i in 0.until(modules[0]!!.first.size)){
                val jsonModule = gson.toJson(modules[0]!!.first[i])
                buffer.putInt(0, jsonModule.length)
                for(j in 0..3) {
                    os.write(buffer[j].toInt())
                }
                os.write(jsonModule.toByteArray())
            }

            // scrivo tutti i moduli
            for (i in 0.until(modules[0]!!.second.size)){
                val jsonModule = gson.toJson(modules[0]!!.second[i])
                buffer.putInt(0, jsonModule.length)
                for(j in 0..3) {
                    os.write(buffer[j].toInt())
                }
                os.write(jsonModule.toByteArray())
            }

            val response = ins.read()
            Log.d("RegistrationTask", "result ${response}")

            var token : ByteArray = kotlin.ByteArray(0)

            if(response == 0){
                token = ins.readBytes(32)
            }

            socket.close()

            return token.toString(Charset.defaultCharset())

        }catch (exception : IOException){
            return ""
        }
    }

}