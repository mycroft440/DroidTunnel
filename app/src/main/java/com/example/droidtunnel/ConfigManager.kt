package com.example.droidtunnel

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConfigManager(context: Context) {

    // SharedPreferences é o sistema de armazenamento simples do Android, ideal para preferências e dados pequenos.
    private val sharedPreferences = context.getSharedPreferences("DroidTunnelPrefs", Context.MODE_PRIVATE)
    private val gson = Gson() // Objeto para manipular JSON.

    companion object {
        private const val CONFIG_KEY = "tunnel_configurations" // Chave para guardar a nossa lista.
    }

    /**
     * Guarda uma lista de configurações no armazenamento do dispositivo.
     * A lista é convertida para uma string JSON antes de ser guardada.
     */
    fun saveConfigs(configs: List<TunnelConfig>) {
        val jsonString = gson.toJson(configs)
        sharedPreferences.edit().putString(CONFIG_KEY, jsonString).apply()
    }

    /**
     * Carrega a lista de configurações do armazenamento do dispositivo.
     * Lê a string JSON e converte-a de volta para uma lista de objetos TunnelConfig.
     */
    fun loadConfigs(): MutableList<TunnelConfig> {
        val jsonString = sharedPreferences.getString(CONFIG_KEY, null)
        if (jsonString != null) {
            // Define o tipo de dados que esperamos do JSON (uma lista de TunnelConfig).
            val type = object : TypeToken<MutableList<TunnelConfig>>() {}.type
            return gson.fromJson(jsonString, type)
        }
        return mutableListOf() // Retorna uma lista vazia se não houver nada guardado.
    }
}
