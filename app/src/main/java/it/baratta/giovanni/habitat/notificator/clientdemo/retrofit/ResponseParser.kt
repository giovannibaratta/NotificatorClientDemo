package it.baratta.giovanni.habitat.notificator.clientdemo.retrofit

import com.google.gson.*
import it.baratta.giovanni.habitat.notificator.api.response.IResponse
import java.lang.reflect.Type


/**
 * Created by Gio on 13/09/2017.
 * soluzione da https://stackoverflow.com/questions/38071530/gson-deserialize-interface-to-its-class-implementation
 */

class ResponseParser : JsonDeserializer<IResponse>{

    companion object {
        // campo nella stringa json da utilizzare per il recupera il tipo
        // di oggetto da parsare
        private val classField = "className"
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): IResponse {
        if(json == null || context == null)
            throw JsonParseException("element null || contex null")

        val jsonObject = json.getAsJsonObject()
        val prim = jsonObject.get(classField) as JsonPrimitive
        val className = prim.asString
        val clazz = getClassInstance(className)
        return context.deserialize(jsonObject, clazz)
    }

    fun getClassInstance(className: String): Class<IResponse> {
        try {
            return Class.forName(className) as Class<IResponse>
        } catch (cnfe: ClassNotFoundException) {
            throw JsonParseException(cnfe.message)
        }
    }
}