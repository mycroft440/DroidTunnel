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
class SshTunnel(private val config: TunnelConfig) : Runnable {

    companion object {
        const val TAG = "SshTunnel"
    }

    private var session: Session? = null

    override fun run() {
        try {
            Log.d(TAG, "A iniciar a thread do túnel...")

            val jsch = JSch()
            session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort.toIntOrNull() ?: 22)
            session?.setPassword(config.sshPassword)
            session?.setConfig("StrictHostKeyChecking", "no")

            // --- LÓGICA DO PROXY E PAYLOAD ---
            // Se um proxy estiver definido, configuramo-lo antes de nos ligarmos.
            if (config.proxyHost.isNotBlank() && config.proxyPort.isNotBlank()) {
                session?.setProxy(HttpProxy(config))
                Log.d(TAG, "Proxy HTTP configurado para ${config.proxyHost}:${config.proxyPort}")
            }

            Log.d(TAG, "A conectar-se ao servidor SSH através do proxy...")
            session?.connect(30000) // Timeout de 30 segundos

            if (session?.isConnected == true) {
                Log.d(TAG, "Ligação SSH estabelecida com sucesso!")

                // TODO: Lógica para encaminhar o tráfego da interface VPN através do túnel SSH.
                // Esta é a próxima grande etapa.

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
        }
    }

    fun stop() {
        Log.d(TAG, "A parar o túnel...")
        session?.disconnect()
    }
}


/**
 * Classe personalizada para gerir a ligação Proxy HTTP, permitindo a injeção de payload e ligações SSL/TLS.
 */
private class HttpProxy(private val config: TunnelConfig) : Proxy {
    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override fun connect(socketFactory: SocketFactory?, host: String?, port: Int, timeout: Int) {
        try {
            // 1. Cria o socket inicial para o proxy
            val plainSocket = socketFactory?.createSocket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)
                ?: Socket(config.proxyHost, config.proxyPort.toIntOrNull() ?: 80)

            // 2. Verifica se é necessário envolvê-lo em SSL/TLS
            if (config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)) {
                Log.d(SshTunnel.TAG, "A iniciar ligação SSL para ${config.proxyHost} com SNI: ${config.sni}")

                val sslFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val sslSocket = sslFactory.createSocket(
                    plainSocket,
                    config.proxyHost,
                    config.proxyPort.toIntOrNull() ?: 80,
                    true // autoClose
                ) as SSLSocket

                // Define o SNI (Server Name Indication) se estiver preenchido
                if (config.sni.isNotBlank()) {
                    // Requer API 24+, que é o nosso minSdk
                    val params = sslSocket.sslParameters
                    params.serverNames = listOf(SNIHostName(config.sni))
                    sslSocket.sslParameters = params
                }

                Log.d(SshTunnel.TAG, "A iniciar handshake SSL...")
                sslSocket.startHandshake()
                Log.d(SshTunnel.TAG, "Handshake SSL concluído com sucesso.")

                // Usa o socket SSL a partir de agora
                this.socket = sslSocket
            } else {
                // Usa o socket normal
                this.socket = plainSocket
            }

            this.socket?.soTimeout = timeout
            inputStream = this.socket?.getInputStream()
            outputStream = this.socket?.getOutputStream()

            // 3. Prepara e envia a payload (APENAS se aplicável)
            if (config.connectionType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL)) {
                val processedPayload = processPayload(config.payload, config.sshHost, config.sshPort)
                Log.d(SshTunnel.TAG, "Payload processada a ser enviada:\n$processedPayload")

                outputStream?.write(processedPayload.toByteArray())
                outputStream?.flush()

                // Lê a resposta do proxy para confirmar que a payload foi aceite
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
            close() // Garante que tudo é fechado em caso de erro
            throw e // Lança a exceção para que a JSch saiba que falhou
        }
    }

    // Funções necessárias para a interface Proxy
    override fun getInputStream(): InputStream? = inputStream
    override fun getOutputStream(): OutputStream? = outputStream
    override fun getSocket(): Socket? = socket
    override fun close() {
        inputStream?.close()
        outputStream?.close()
        socket?.close()
    }

    /**
     * Substitui os placeholders na payload pelos valores reais.
     */
    private fun processPayload(payload: String, host: String, port: String): String {
        return payload
            .replace("[host_port]", "$host:$port")
            .replace("[ssh_host]", host)
            .replace("[ssh_port]", port)
            .replace("[crlf]", "\r\n")
            .replace("\\r\\n", "\r\n") // Garante que as quebras de linha estão corretas
    }
}

