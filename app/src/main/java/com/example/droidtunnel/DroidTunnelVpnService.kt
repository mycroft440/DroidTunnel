package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()
    private val isRunning = AtomicBoolean(false)

    companion object {
        const val TAG = "DroidTunnelVpnService"
        var currentState: VpnState = VpnState.IDLE
            private set
    }

    // Envia uma atualização de estado para a MainActivity.
    private fun sendStateBroadcast(state: VpnState, message: String? = null) {
        currentState = state
        val intent = Intent(VpnServiceState.ACTION_STATE_UPDATE).apply {
            putExtra(VpnServiceState.EXTRA_STATE, state.name)
            message?.let { putExtra(VpnServiceState.EXTRA_MESSAGE, it) }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start" && !isRunning.getAndSet(true)) {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                Log.e(TAG, "Configuração nula, a parar serviço.")
                stopVpn("Configuração inválida.")
            }
        } else if (action == "stop") {
            stopVpn("Parado pelo utilizador.")
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn(reason: String?) {
        if (!isRunning.getAndSet(false)) return

        Log.d(TAG, "A parar a VPN... Razão: $reason")
        sendStateBroadcast(VpnState.DISCONNECTED, reason)
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelConnecting() {
        sendStateBroadcast(VpnState.CONNECTING, "A ligar ao servidor SSH...")
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        sendStateBroadcast(VpnState.CONNECTING, "Túnel estabelecido. A configurar a rede...")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopVpn("A interface VPN não pôde ser estabelecida. O utilizador negou a permissão?")
                return
            }

            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            sendStateBroadcast(VpnState.CONNECTED, "Ligado com sucesso!")

            executor.submit {
                val buffer = ByteArray(32767)
                while (isRunning.get()) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        } else if (bytesRead == -1) {
                            Log.d(TAG, "Fim do stream da interface VPN.")
                            break
                        }
                    } catch (e: IOException) {
                        if (isRunning.get()) {
                            Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn("Erro ao iniciar a VPN: ${e.message}")
        }
    }

    override fun onTunnelClosed(reason: String?) {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn(reason)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn("Serviço destruído.")
    }
}
