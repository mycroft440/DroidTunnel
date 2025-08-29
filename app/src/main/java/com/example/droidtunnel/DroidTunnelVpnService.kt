package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log

class DroidTunnelVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Serviço recebido com a ação: $action")

        if (action == "start") {
            // Extrai a configuração do Intent
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }

            if (config != null) {
                Log.d(TAG, "A iniciar VPN com a configuração: ${config.name}")
                Log.d(TAG, "Detalhes: Proxy=${config.proxyHost}:${config.proxyPort}, Tipo=${config.connectionType.displayName}")
                startVpn(config)
            } else {
                Log.e(TAG, "Erro: A configuração não foi recebida pelo serviço.")
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(config: TunnelConfig) {
        // A lógica de conexão real seria implementada aqui.
        // Por agora, apenas estabelecemos o túnel.
        val builder = Builder()
            .setSession(config.name)
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            Log.d(TAG, "Interface VPN estabelecida com sucesso.")
            // TODO: Iniciar a thread que irá gerir o túnel (ler/escrever pacotes)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao estabelecer a interface VPN: ${e.message}")
            stopSelf() // Pára o serviço se a interface não puder ser criada
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf() // Garante que o serviço é terminado
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço destruído.")
        stopVpn()
    }
}
