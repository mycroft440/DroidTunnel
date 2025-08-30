package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    // Usamos um executor para gerir as nossas threads de rede de forma eficiente.
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            // Obtém a configuração do Intent de forma segura, independentemente da versão do Android.
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            if (config != null) {
                startVpn(config)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(config: TunnelConfig) {
        // Inicia o nosso túnel SSH numa nova thread, passando este serviço como o "listener"
        // para que o túnel nos possa notificar quando estiver pronto.
        tunnelThread = Thread(SshTunnel(config, this), "SshTunnelThread").apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        // Encerra todas as threads de rede e o túnel.
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        // Pára o serviço.
        stopSelf()
    }

    /**
     * Esta função é chamada pela SshTunnel quando a ligação está pronta.
     */
    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        // Constrói a interface de rede virtual do Android.
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            // Cria o nosso gestor de sessões (o cérebro do NAT).
            val natManager = NatManager(this, port)

            // Inicia uma thread dedicada para ler continuamente os pacotes da interface VPN.
            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            // Descodifica o pacote lido.
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                // Entrega o pacote ao nosso gestor NAT para ser processado.
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    /**
     * Esta função é chamada pela SshTunnel quando a ligação é fechada.
     */
    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

