package com.example.droidtunnel

import android.util.Log
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class NatManager(private val vpnService: DroidTunnelVpnService, private val socksPort: Int) {

    private val sessions = ConcurrentHashMap<String, Pair<SocketChannel, Packet>>()
    private val executor = Executors.newCachedThreadPool()

    fun handlePacket(packet: Packet, vpnOutput: FileOutputStream) {
        val destAddress = packet.ipHeader.destinationAddress
        val destPort = packet.transportHeader.destinationPort
        val sessionKey = "${packet.ipHeader.sourceAddress.hostAddress}:${packet.transportHeader.sourcePort}-${destAddress.hostAddress}:$destPort"

        var channel = sessions[sessionKey]?.first
        if (channel == null || !channel.isConnected) {
            try {
                channel = SocketChannel.open()
                vpnService.protect(channel.socket())
                channel.connect(InetSocketAddress("127.0.0.1", socksPort))
                
                sessions[sessionKey] = Pair(channel, packet)

                executor.submit {
                    val buffer = ByteBuffer.allocate(32767)
                    try {
                        while (channel.read(buffer) > 0) {
                            buffer.flip()
                            val responseData = ByteArray(buffer.remaining())
                            buffer.get(responseData)
                            
                            val responsePacket = IpPacketParser.buildIpPacket(packet, responseData)
                            
                            synchronized(vpnOutput) {
                                vpnOutput.write(responsePacket)
                            }
                            buffer.clear()
                        }
                    } catch (e: IOException) {
                        Log.w("NatManager", "Sessão fechada para $sessionKey: ${e.message}")
                    } finally {
                        sessions.remove(sessionKey)
                        channel.close()
                    }
                }

            } catch (e: IOException) {
                Log.e("NatManager", "Erro ao criar sessão para $sessionKey", e)
                return
            }
        }
        
        try {
            if (packet.data.hasRemaining()) {
                channel.write(packet.data)
            }
        } catch (e: IOException) {
            Log.e("NatManager", "Erro ao escrever na sessão $sessionKey", e)
            sessions.remove(sessionKey)
            channel.close()
        }
    }
}

