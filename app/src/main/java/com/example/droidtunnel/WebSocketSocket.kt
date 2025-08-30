package com.example.droidtunnel

import okhttp3.*
import okio.ByteString
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

/**
 * Uma classe adaptadora que faz uma ligação WebSocket parecer um Socket normal.
 * Isto é necessário para que a biblioteca JSch consiga comunicar através do WebSocket sem saber o que é.
 */
class WebSocketSocket(
    host: String,
    port: Int,
    private val payload: String,
    private val useSsl: Boolean,
    private val sni: String?
) : Socket() {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private lateinit var webSocket: WebSocket
    private val serverToClientPipe = PipedOutputStream()
    private val clientToServerPipe = PipedOutputStream()

    private val inputStream = PipedInputStream(serverToClientPipe)
    private val outputStream = PipedInputStream(clientToServerPipe)

    private val socketOutputStream = object : OutputStream() {
        override fun write(b: Int) {
            clientToServerPipe.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            clientToServerPipe.write(b, off, len)
        }
    }

    private val readerThread = Thread {
        try {
            val buffer = ByteArray(4096)
            while (!isClosed) {
                val read = outputStream.read(buffer)
                if (read == -1) break
                webSocket.send(ByteString.of(buffer, 0, read))
            }
        } catch (e: Exception) {
            // A stream foi fechada, o que é normal ao desconectar.
        }
    }

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        val scheme = if (useSsl) "wss" else "ws"
        val url = "$scheme://$host:$port/" // O caminho pode ser ajustado se necessário na payload.

        // Extrai o caminho e os cabeçalhos da payload
        val lines = payload.replace("\\r\\n", "\r\n").split("\r\n")
        val requestLine = lines[0]
        val path = requestLine.split(" ")[1]
        val finalUrl = "$scheme://$host:$port$path"

        val requestBuilder = Request.Builder().url(finalUrl)
        lines.drop(1).forEach { line ->
            if (line.contains(":")) {
                val parts = line.split(":", limit = 2)
                val key = parts[0].trim()
                val value = parts[1].trim()
                // O OkHttp adiciona estes automaticamente.
                if (!key.equals("Host", ignoreCase = true) &&
                    !key.equals("Upgrade", ignoreCase = true) &&
                    !key.equals("Connection", ignoreCase = true)
                ) {
                    requestBuilder.addHeader(key, value)
                }
            }
        }

        // Adiciona o SNI ao Host se especificado
        if (!sni.isNullOrBlank()) {
            requestBuilder.header("Host", sni)
        } else {
            requestBuilder.header("Host", "$host:$port")
        }


        val request = requestBuilder.build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                this@WebSocketSocket.webSocket = webSocket
                readerThread.start()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    serverToClientPipe.write(bytes.toByteArray())
                    serverToClientPipe.flush()
                } catch (e: Exception) {
                    close()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close()
            }
        }
        client.newWebSocket(request, listener)
    }

    override fun getInputStream(): InputStream = inputStream
    override fun getOutputStream(): OutputStream = socketOutputStream

    override fun close() {
        if (!isClosed) {
            super.close()
            try {
                webSocket.close(1000, "Connection closed")
            } catch (ignored: Exception) {}
            client.dispatcher.executorService.shutdown()
            inputStream.close()
            outputStream.close()
            serverToClientPipe.close()
            clientToServerPipe.close()
            if(readerThread.isAlive) readerThread.interrupt()
        }
    }

    override fun isConnected(): Boolean = !isClosed && ::webSocket.isInitialized
}
