package it.baratta.giovanni.habitat.notificator.clientdemo.persistence

import java.io.*
import java.lang.IllegalStateException

/**
 * Created by Gio on 14/09/2017.
 */

/**
 * @throws IOException
 * @throws IllegalStateException
 */
class PropertiesFile(private var file : File,
                     create : Boolean = false,
                     private val autoUpdate : Boolean = false) {

    private val properties : HashMap<String, String>

    init {
        if(!file.exists() && !create)
            throw IllegalStateException("Il file non esiste e non posso crearlo")

        if(file.exists()) {
            if(file.length() > 0) {
                val ois = ObjectInputStream(FileInputStream(file))
                try {
                    properties = ois.readObject() as HashMap<String, String>
                } catch (exception: ClassCastException) {
                    throw IllegalStateException("Il file non contine delle proprietà")
                }
                ois.close()
            }else{
                properties = HashMap()
            }
        }else {
            file.createNewFile()
            properties = HashMap()
        }
    }

    fun setProperty(key : String, value : String){
        properties[key] = value
        if(autoUpdate)
            update()
    }

    fun removeProperty(key : String){
        properties.remove(key)
        if(autoUpdate)
            update()
    }

    fun getProperty(key : String) : String?
        = properties[key]

    fun update(){
        val oos = ObjectOutputStream(FileOutputStream(file))
        oos.writeObject(properties)
        oos.close()
    }

}