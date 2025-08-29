package com.example.droidtunnel

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Esta classe gere a ligação SSH. Ela será executada na sua própria thread.
 */
class SshTunnel(
    private val config: TunnelConfig,
    private val listener: TunnelListener // Listener para notificar o VpnService
) : Runnable {

    companion object {
        const val TAG = "SshTunnel"
        const val SOCKS_PROXY_PORT = 10800 // Porta local para o nosso SOCKS proxy
    }

    private var session: Session? = null

    override fun run() {
        try {
            Log.d(TAG, "A iniciar a thread do túnel...")

            val jsch = JSch()
            session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort.toIntOrNull() ?: 22)
            session?.setPassword(config.sshPassword)
            session?.setConfig("StrictHostKeyChecking", "no")

            if (config.proxyHost.isNotBlank() && config.proxyPort.isNotBlank()) {
                session?.setProxy(HttpProxy(config))
                Log.d(TAG, "Proxy HTTP configurado para ${config.proxyHost}:${config.proxyPort}")
            }

            Log.d(TAG, "A conectar-se ao servidor SSH...")
            session?.connect(30000)

            if (session?.isConnected == true) {
                Log.d(TAG, "Ligação SSH estabelecida com sucesso!")

                // ATIVA O SOCKS PROXY (DYNAMIC PORT FORWARDING)
                // Qualquer tráfego enviado para localhost:10800 será encaminhado pelo túnel.
                session?.setPortForwardingD(SOCKS_PROXY_PORT)
                Log.d(TAG, "Proxy SOCKS iniciado na porta $SOCKS_PROXY_PORT")

                // Notifica o VpnService que estamos prontos para encaminhar o tráfego.
                listener.onTunnelReady(SOCKS_PROXY_PORT)

                while (!Thread.currentThread().isInterrupted && session?.isConnected == true) {
                    Thread.sleep(1000)
                }
            } else {
                Log.e(TAG, "Falha ao estabelecer a ligação SSH.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro na ligação SSH: ${e.message}", e)
        } finally {
            Log.d(TAG, "A fechar a ligação SSH.")
            session?.disconnect()
            // Notifica o VpnService que o túnel foi fechado.
            listener.onTunnelClosed()
        }
    }
}


/**
 * Classe HttpProxy (SEM ALTERAÇÕES)
 */
private class HttpProxy(private val config: TunnelConfig) : Proxy {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        try {
            val plainSocket = socketFactory?.createSocket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)
                ?: Socket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)

            if (config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)) {
                Log.d(SshTunnel.TAG, "A iniciar ligação SSL para ${config.proxyHost} com SNI: ${config.sni}")
                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(plainSocket, config.proxyHost, config.proxyPort.toIntOrNull() ?: 80, true) as SSLSocket
                if (config.sni.isNotBlank()) {
                    val params = sslSocket.sslParameters
                    params.serverNames = listOf(SNIHostName(config.sni))
                    sslSocket.sslParameters = params
                }
                Log.d(SshTunnel.TAG, "A iniciar handshake SSL...")
                sslSocket.startHandshake()
                Log.d(SshTunnel.TAG, "Handshake SSL concluído com sucesso.")
                this.socket = sslSocket
            } else {
                this.socket = plainSocket
            }

            this.socket?.soTimeout = timeout
            inputStream = this.socket?.getInputStream()
            outputStream = this.socket?.getOutputStream()

            if (config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL)) {
                val processedPayload = processPayload(config.payload, config.sshHost, config.sshPort)
                Log.d(SshTunnel.TAG, "Payload processada a ser enviada:\n$processedPayload")
                outputStream?.write(processedPayload.toByteArray())
                outputStream?.flush()
                val buffer = ByteArray(1024)
                val bytesRead = inputStream?.read(buffer)
                if (bytesRead != null && bytesRead > 0) {
                    val response = String(buffer, 0, bytesRead)
                    Log.d(SshTunnel.TAG, "Resposta do Proxy:\n$response")
                    if (!response.contains(" 200 ")) {
                        throw Exception("O Proxy recusou a ligação: $response")
                    }
                } else {
                    throw Exception("O Proxy não respondeu.")
                }
            } else {
                Log.d(SshTunnel.TAG, "Ligação direta (SSL ou normal) estabelecida, a aguardar pelo SSH.")
            }
        } catch (e: Exception) {
            Log.e(SshTunnel.TAG, "Erro ao conectar-se ao proxy: ${e.message}", e)
            close()
            throw e
        }
    }

    override fun getInputStream(): InputStream? = inputStream
    override fun getOutputStream(): OutputStream? = outputStream
    override fun getSocket(): Socket? = socket
    override fun close() {
        inputStream?.close()
        outputStream?.close()
        socket?.close()
    }

    private fun processPayload(payload: String, host: String, port: String): String {
        return payload
            .replace("[host_port]", "$host:$port")
            .replace("[ssh_host]", host)
            .replace("[ssh_port]", port)
            .replace("[crlf]", "\r\n")
            .replace("\\r\\n", "\r\n")
    }
}

