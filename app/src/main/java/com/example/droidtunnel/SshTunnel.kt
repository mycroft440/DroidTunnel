package com.example.droidtunnel

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Proxy
import com.jcraft.jsch.ProxySOCKS5
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class SshTunnel(
    private val config: TunnelConfig,
    private val listener: TunnelListener,
    private val useCompression: Boolean,
    private val useTcpNoDelay: Boolean,
    private val useKeepAlive: Boolean
) : Runnable {

    companion object {
        const val TAG = "SshTunnel"
        const val SOCKS_PROXY_PORT = 10800
    }

    private var session: Session? = null

    override fun run() {
        var closeReason: String? = "Ligação terminada."
        try {
            listener.onTunnelConnecting()
            Log.d(TAG, "A iniciar a thread do túnel...")

            val jsch = JSch()
            session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort.toIntOrNull() ?: 22)
            session?.setPassword(config.sshPassword)
            session?.setConfig("StrictHostKeyChecking", "no")

            if (useTcpNoDelay) {
                session?.setConfig("TCP_NODELAY", "yes")
                Log.d(TAG, "TCP NoDelay ativado.")
            }
            if (useKeepAlive) {
                session?.serverAliveInterval = 30 * 1000
                Log.d(TAG, "KeepAlive ativado (intervalo de 30s).")
            }
            if (useCompression) {
                session?.setConfig("compression.s2c", "zlib@openssh.com,zlib,none")
                session?.setConfig("compression.c2s", "zlib@openssh.com,zlib,none")
                Log.d(TAG, "Compressão SSH ativada.")
            }

            if (config.proxyHost.isNotBlank() && config.proxyPort.isNotBlank()) {
                when (config.connectionType) {
                    SshConnectionType.SOCKS5 -> {
                        session?.setProxy(ProxySOCKS5(config.proxyHost, config.proxyPort.toIntOrNull() ?: 1080))
                        Log.d(TAG, "Proxy SOCKS5 configurado para ${config.proxyHost}:${config.proxyPort}")
                    }
                    else -> { // Para os tipos SSHPROXY
                        session?.setProxy(HttpProxy(config))
                        Log.d(TAG, "Proxy HTTP/WebSocket configurado para ${config.proxyHost}:${config.proxyPort}")
                    }
                }
            }

            Log.d(TAG, "A conectar-se ao servidor SSH...")
            session?.connect(30000)

            if (session?.isConnected == true) {
                Log.d(TAG, "Ligação SSH estabelecida com sucesso!")
                session?.setPortForwardingD(SOCKS_PROXY_PORT)
                Log.d(TAG, "Proxy SOCKS iniciado na porta $SOCKS_PROXY_PORT")
                listener.onTunnelReady(SOCKS_PROXY_PORT)

                while (!Thread.currentThread().isInterrupted && session?.isConnected == true) {
                    Thread.sleep(1000)
                }
                if (Thread.currentThread().isInterrupted) {
                    closeReason = "Parado pelo utilizador."
                }
            } else {
                 closeReason = "Falha ao conectar. Verifique as credenciais e o servidor."
                 Log.e(TAG, closeReason)
            }
        } catch (e: Exception) {
            closeReason = ErrorMessageHandler.translate(e)
            Log.e(TAG, "Erro na ligação SSH: $closeReason", e)
        } finally {
            Log.d(TAG, "A fechar a ligação SSH.")
            session?.disconnect()
            listener.onTunnelClosed(closeReason)
        }
    }
}

private class HttpProxy(private val config: TunnelConfig) : Proxy {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        try {
            val isWebSocket = config.payload.contains("Upgrade: websocket", ignoreCase = true)
            val useSsl = config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)

            if (isWebSocket) {
                Log.d(SshTunnel.TAG, "Detetada payload WebSocket. A iniciar ligação WebSocket...")
                this.socket = WebSocketSocket(
                    host = config.proxyHost,
                    port = config.proxyPort.toIntOrNull() ?: (if (useSsl) 443 else 80),
                    payload = config.payload,
                    useSsl = useSsl,
                    sni = config.sni
                )
            } else {
                Log.d(SshTunnel.TAG, "Payload HTTP padrão. A iniciar ligação TCP...")
                val plainSocket = socketFactory?.createSocket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)
                    ?: Socket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)

                if (useSsl) {
                    Log.d(SshTunnel.TAG, "A iniciar ligação SSL para ${config.proxyHost} com SNI: ${config.sni}")
                    val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    val sslSocket = sslFactory.createSocket(plainSocket, config.proxyHost, config.proxyPort.toIntOrNull() ?: 443, true) as SSLSocket
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
                
                // Processar a payload para ligações HTTP normais
                if (config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL)) {
                    val processedPayload = processPayload(config.payload, config.sshHost, config.sshPort)
                    Log.d(SshTunnel.TAG, "Payload processada a ser enviada:\n$processedPayload")
                    this.socket?.getOutputStream()?.write(processedPayload.toByteArray())
                    this.socket?.getOutputStream()?.flush()

                    val buffer = ByteArray(1024)
                    val bytesRead = this.socket?.getInputStream()?.read(buffer)
                    if (bytesRead != null && bytesRead > 0) {
                        val response = String(buffer, 0, bytesRead)
                        Log.d(SshTunnel.TAG, "Resposta do Proxy:\n$response")
                        if (!response.contains(" 200 ")) {
                            throw Exception("O Proxy recusou a ligação: $response")
                        }
                    } else {
                        throw Exception("O Proxy não respondeu.")
                    }
                }
            }
            this.socket?.soTimeout = timeout
        } catch (e: Exception) {
            Log.e(SshTunnel.TAG, "Erro ao conectar-se ao proxy: ${e.message}", e)
            close()
            throw e
        }
    }
    
    override fun getInputStream(): InputStream? {
        if (inputStream == null) inputStream = socket?.getInputStream()
        return inputStream
    }

    override fun getOutputStream(): OutputStream? {
        if (outputStream == null) outputStream = socket?.getOutputStream()
        return outputStream
    }

    override fun getSocket(): Socket? = socket
    
    override fun close() {
        try { inputStream?.close() } catch (e: Exception) {}
        try { outputStream?.close() } catch (e: Exception) {}
        try { socket?.close() } catch (e: Exception) {}
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