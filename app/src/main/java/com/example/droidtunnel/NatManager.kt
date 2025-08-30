package com.example.droidtunnel

import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

class NatManager(private val vpnService: DroidTunnelVpnService, private val socksPort: Int) {
    // Agora, guardamos o pacote original que iniciou a sessão para construir a resposta
    private val sessions = ConcurrentHashMap<String, Pair<SocketChannel, Packet>>()

    fun handlePacket(packet: Packet, vpnOutput: FileOutputStream) {
        val destAddress = packet.ipHeader.destinationAddress
        val destPort = packet.transportHeader.destinationPort
        val sessionKey = "${destAddress.hostAddress}:$destPort:${packet.transportHeader.sourcePort}"

        var channel = sessions[sessionKey]?.first
        if (channel == null || !channel.isConnected) {
            try {
                channel = SocketChannel.open()
                vpnService.protect(channel.socket())
                channel.connect(InetSocketAddress("127.0.0.1", socksPort))
                
                // Guarda o canal E o pacote original
                sessions[sessionKey] = Pair(channel, packet)

                // Inicia a thread para ler as respostas do túnel
                Thread {
                    val buffer = ByteBuffer.allocate(32767)
                    try {
                        while (channel.read(buffer) > 0) {
                            buffer.flip()
                            // Constrói o pacote de resposta usando o pacote original
                            val responsePacketBuffer = IpPacketParser.buildResponsePacket(packet, buffer)
                            
                            // Escreve a resposta de volta na interface da VPN
                            synchronized(vpnOutput) {
                                vpnOutput.write(responsePacketBuffer.array(), 0, responsePacketBuffer.limit())
                            }
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
            // Escreve os dados do pacote de saída para o túnel
            channel.write(packet.data)
        } catch (e: IOException) {
            Log.e("NatManager", "Erro ao escrever na sessão: $sessionKey", e)
            sessions.remove(sessionKey)
            channel.close()
        }
    }
}

