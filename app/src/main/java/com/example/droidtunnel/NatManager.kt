package com.example.droidtunnel

import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

class NatManager(private val vpnService: DroidTunnelVpnService, private val socksPort: Int) {
    private val sessions = ConcurrentHashMap<String, SocketChannel>()

    fun handlePacket(packet: Packet, vpnOutput: FileOutputStream) {
        val destAddress = packet.ipHeader.destinationAddress
        val destPort = packet.transportHeader.destinationPort
        val sessionKey = "${destAddress.hostAddress}:$destPort"

        var channel = sessions[sessionKey]
        if (channel == null || !channel.isConnected) {
            try {
                channel = SocketChannel.open()
                vpnService.protect(channel.socket()) // Protege o socket da VPN
                channel.connect(InetSocketAddress("127.0.0.1", socksPort))
                sessions[sessionKey] = channel

                // Inicia uma nova thread para ler as respostas do túnel para esta sessão
                Thread {
                    val buffer = ByteBuffer.allocate(32767)
                    try {
                        while (channel.read(buffer) > 0) {
                            // TODO: Construir um pacote IP de resposta e escrever no vpnOutput
                            buffer.clear()
                        }
                    } catch (e: IOException) {
                        Log.w("NatManager", "Sessão fechada: $sessionKey")
                    } finally {
                        sessions.remove(sessionKey)
                        channel.close()
                    }
                }.start()

            } catch (e: IOException) {
                Log.e("NatManager", "Erro ao criar sessão: $sessionKey", e)
                return
            }
        }
        
        try {
            channel.write(packet.data)
        } catch (e: IOException) {
            Log.e("NatManager", "Erro ao escrever na sessão: $sessionKey", e)
            sessions.remove(sessionKey)
            channel.close()
        }
    }
}

