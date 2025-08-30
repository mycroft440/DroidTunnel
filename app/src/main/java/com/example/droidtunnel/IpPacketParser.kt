package com.example.droidtunnel

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

object IpPacketParser {

    private const val IPV4_HEADER_SIZE = 20
    private const val TCP_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17

    // A função de parse foi atualizada para guardar os cabeçalhos originais
    fun parse(buffer: ByteArray, length: Int): Packet? {
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length)
        if (byteBuffer.remaining() < IPV4_HEADER_SIZE) return null

        val versionAndIhl = byteBuffer.get().toInt()
        val version = versionAndIhl shr 4
        if (version != 4) return null

        val ipHeaderLength = (versionAndIhl and 0x0F) * 4
        byteBuffer.position(9)
        val protocol = byteBuffer.get().toInt() and 0xFF
        byteBuffer.position(12)
        val sourceIp = ByteArray(4).also { byteBuffer.get(it) }
        val destIp = ByteArray(4).also { byteBuffer.get(it) }

        val sourceAddress: InetAddress
        val destAddress: InetAddress
        try {
            sourceAddress = InetAddress.getByAddress(sourceIp)
            destAddress = InetAddress.getByAddress(destIp)
        } catch (e: UnknownHostException) {
            return null
        }

        byteBuffer.position(ipHeaderLength) // Pula para o início do próximo cabeçalho

        val transportHeader: Packet.TransportHeader? = when (protocol) {
            PROTOCOL_TCP -> parseTcp(byteBuffer)
            PROTOCOL_UDP -> parseUdp(byteBuffer)
            else -> null
        }

        return if (transportHeader != null) {
            val ipHeader = object : Packet.IpHeader {
                override val version: Int = version
                override val protocol: Int = protocol
                override val sourceAddress: InetAddress = sourceAddress
                override val destinationAddress: InetAddress = destAddress
                override val headerLength: Int = ipHeaderLength
                override fun getRawHeader(): ByteBuffer = ByteBuffer.wrap(buffer, 0, ipHeaderLength).asReadOnlyBuffer()
            }
            val data = if (byteBuffer.hasRemaining()) byteBuffer.slice() else ByteBuffer.allocate(0)
            Packet(ipHeader, transportHeader, data)
        } else {
            null
        }
    }

    private fun parseTcp(buffer: ByteBuffer): Packet.TransportHeader? {
        if (buffer.remaining() < TCP_HEADER_SIZE) return null
        val rawHeader = buffer.slice()
        rawHeader.limit(TCP_HEADER_SIZE)
        val sourcePort = buffer.getShort().toInt() and 0xFFFF
        val destPort = buffer.getShort().toInt() and 0xFFFF
        buffer.position(buffer.position() + TCP_HEADER_SIZE - 4) // Avança para o fim do cabeçalho
        return object : Packet.TransportHeader {
            override val sourcePort: Int = sourcePort
            override val destinationPort: Int = destPort
            override fun getRawHeader(): ByteBuffer = rawHeader.asReadOnlyBuffer()
        }
    }
    
    private fun parseUdp(buffer: ByteBuffer): Packet.TransportHeader? {
        if (buffer.remaining() < UDP_HEADER_SIZE) return null
        val rawHeader = buffer.slice()
        rawHeader.limit(UDP_HEADER_SIZE)
        val sourcePort = buffer.getShort().toInt() and 0xFFFF
        val destPort = buffer.getShort().toInt() and 0xFFFF
        return object : Packet.TransportHeader {
            override val sourcePort: Int = sourcePort
            override val destinationPort: Int = destPort
            override fun getRawHeader(): ByteBuffer = rawHeader.asReadOnlyBuffer()
        }
    }

    // --- NOVA LÓGICA PARA CONSTRUIR PACOTES DE RESPOSTA ---

    fun buildResponsePacket(originalPacket: Packet, responseData: ByteBuffer): ByteBuffer {
        val originalIpHeader = originalPacket.ipHeader
        val originalTcpHeader = originalPacket.transportHeader

        // 1. Troca os endereços de origem e destino
        val sourceAddress = originalIpHeader.destinationAddress
        val destAddress = originalIpHeader.sourceAddress
        val sourcePort = originalTcpHeader.destinationPort
        val destPort = originalTcpHeader.sourcePort

        // 2. Prepara o buffer para o novo pacote
        val newPacketLength = IPV4_HEADER_SIZE + TCP_HEADER_SIZE + responseData.remaining()
        val responseBuffer = ByteBuffer.allocate(newPacketLength)

        // 3. Constrói o cabeçalho IP
        responseBuffer.put(0x45.toByte()) // Versão 4, tamanho do cabeçalho 5*4=20
        responseBuffer.put(0.toByte()) // Tipo de serviço
        responseBuffer.putShort(newPacketLength.toShort()) // Tamanho total
        responseBuffer.putShort(0) // Identificação
        responseBuffer.putShort(0x4000) // Flags (Não fragmentar)
        responseBuffer.put(64.toByte()) // TTL
        responseBuffer.put(PROTOCOL_TCP.toByte())
        responseBuffer.putShort(0) // Checksum (calculado no fim)
        responseBuffer.put(sourceAddress.address)
        responseBuffer.put(destAddress.address)

        // Calcula o checksum do cabeçalho IP
        val ipChecksum = calculateChecksum(responseBuffer, 0, IPV4_HEADER_SIZE)
        responseBuffer.putShort(10, ipChecksum)

        // 4. Constrói o cabeçalho TCP (simplificado)
        responseBuffer.putShort(sourcePort.toShort())
        responseBuffer.putShort(destPort.toShort())
        // Números de sequência e ACK - uma implementação real seria mais complexa
        responseBuffer.putInt(0) // Seq
        responseBuffer.putInt(0) // Ack
        responseBuffer.putShort(0x5018) // Tamanho do cabeçalho e flags (ACK, PSH)
        responseBuffer.putShort(0xFFFF.toShort()) // Janela
        responseBuffer.putShort(0) // Checksum TCP (calculado no fim)
        responseBuffer.putShort(0) // Ponteiro de urgência

        // 5. Adiciona os dados
        responseBuffer.put(responseData)

        // Calcula o checksum do cabeçalho TCP (mais complexo)
        // Para simplificar, deixamos a 0 por agora. Muitas pilhas de rede modernas lidam com isto.
        
        responseBuffer.flip()
        return responseBuffer
    }

    private fun calculateChecksum(buffer: ByteBuffer, offset: Int, length: Int): Short {
        var sum = 0
        val originalPosition = buffer.position()
        buffer.position(offset)
        while (buffer.position() < offset + length) {
            sum += buffer.getShort().toInt() and 0xFFFF
        }
        buffer.position(originalPosition)

        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.toShort().inv()
    }
}

