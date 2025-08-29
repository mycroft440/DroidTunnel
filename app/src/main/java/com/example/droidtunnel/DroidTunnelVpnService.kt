package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private var vpnReaderThread: Thread? = null
    private var vpnWriterThread: Thread? = null

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "Serviço recebido com a ação: $action")

        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }

            if (config != null) {
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
        Log.d(TAG, "A iniciar VPN com a configuração: ${config.name}")
        val sshTunnel = SshTunnel(config, this) // Passa o serviço como listener
        tunnelThread = Thread(sshTunnel, "SshTunnelThread").apply {
            start()
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        vpnReaderThread?.interrupt()
        vpnWriterThread?.interrupt()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta $port")

        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Erro: a interface VPN não pôde ser estabelecida.")
                stopVpn()
                return
            }

            // Inicia as threads para ler e escrever na interface VPN
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)

            vpnReaderThread = Thread {
                try {
                    val buffer = ByteArray(32767)
                    while (!Thread.currentThread().isInterrupted) {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            // TODO: 1. Analisar o pacote IP lido do buffer.
                            // TODO: 2. Descobrir o destino (IP e porta).
                            // TODO: 3. Abrir uma ligação para esse destino através do nosso SOCKS proxy (localhost:10800).
                            // TODO: 4. Escrever os dados do pacote nessa ligação SOCKS.
                            // Log.d(TAG, "$bytesRead bytes lidos da VPN")
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Erro na thread de leitura da VPN: ${e.message}")
                }
            }.apply { start() }

            vpnWriterThread = Thread {
                // TODO: 1. Manter um mapa de ligações SOCKS ativas.
                // TODO: 2. Ler os dados de resposta de cada ligação SOCKS.
                // TODO: 3. Construir um pacote IP com esses dados.
                // TODO: 4. Escrever o pacote IP no vpnOutput.
            }.apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "Túnel fechado. A parar o serviço.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Serviço destruído.")
        stopVpn()
    }
}
