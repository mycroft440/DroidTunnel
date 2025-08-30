package com.example.droidtunnel

import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

object IpPacketParser {

    private const val IPV4_HEADER_SIZE = 20
    private const val TCP_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8

    fun parse(buffer: ByteArray, length: Int): Packet? {
        if (length < IPV4_HEADER_SIZE) return null
        
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length)
        val versionAndIhl = byteBuffer.get().toInt()
        val version = versionAndIhl shr 4
        if (version != 4) return null

        byteBuffer.position(9)
        val protocol = byteBuffer.get().toInt() and 0xFF
        byteBuffer.position(12)

        val sourceIp = ByteArray(4)
        byteBuffer.get(sourceIp)
        val destIp = ByteArray(4)
        byteBuffer.get(destIp)

        val sourceAddress: InetAddress
        val destAddress: InetAddress
        try {
            sourceAddress = InetAddress.getByAddress(sourceIp)
            destAddress = InetAddress.getByAddress(destIp)
        } catch (e: UnknownHostException) {
            return null
        }

        val ipHeader = object : Packet.IpHeader {
            override val version: Int = version
            override val protocol: Int = protocol
            override val sourceAddress: InetAddress = sourceAddress
            override val destinationAddress: InetAddress = destAddress
        }
        
        val transportHeader: Packet.TransportHeader? = when (protocol) {
            6 /* TCP */ -> parseTcp(byteBuffer)
            17 /* UDP */ -> parseUdp(byteBuffer)
            else -> null
        }

        return if (transportHeader != null) {
            val data = if (byteBuffer.hasRemaining()) byteBuffer.slice() else ByteBuffer.allocate(0)
            // Guarda o cabeçalho completo (IP + TCP/UDP)
            val originalHeader = ByteBuffer.wrap(buffer, 0, byteBuffer.position())
            Packet(ipHeader, transportHeader, data, originalHeader)
        } else {
            null
        }
    }

    private fun parseTcp(buffer: ByteBuffer): Packet.TransportHeader? {
        if (buffer.remaining() < TCP_HEADER_SIZE) return null
        val sourcePort = buffer.getShort().toInt() and 0xFFFF
        val destPort = buffer.getShort().toInt() and 0xFFFF
        // Pula o resto do cabeçalho TCP para chegar aos dados
        buffer.position(buffer.position() + TCP_HEADER_SIZE - 4)
        return object : Packet.TransportHeader {
            override val sourcePort: Int = sourcePort
            override val destinationPort: Int = destPort
        }
    }
    
    private fun parseUdp(buffer: ByteBuffer): Packet.TransportHeader? {
        if (buffer.remaining() < UDP_HEADER_SIZE) return null
        val sourcePort = buffer.getShort().toInt() and 0xFFFF
        val destPort = buffer.getShort().toInt() and 0xFFFF
        return object : Packet.TransportHeader {
            override val sourcePort: Int = sourcePort
            override val destinationPort: Int = destPort
        }
    }

    fun buildIpPacket(originalPacket: Packet, data: ByteArray): ByteArray {
        val originalHeader = originalPacket.originalHeader.duplicate()
        val totalLength = originalHeader.limit() + data.size
        
        // Troca os endereços de origem e destino
        originalHeader.position(12)
        originalHeader.put(originalPacket.ipHeader.destinationAddress.address)
        originalHeader.put(originalPacket.ipHeader.sourceAddress.address)
        
        // Atualiza o comprimento total do pacote
        originalHeader.putShort(2, totalLength.toShort())
        
        // Recalcula o checksum do cabeçalho IP
        originalHeader.putShort(10, 0) // Zera o checksum antes de calcular
        val checksum = calculateChecksum(originalHeader.array(), 0, IPV4_HEADER_SIZE)
        originalHeader.putShort(10, checksum.toShort())
        
        // Combina o cabeçalho modificado com os novos dados
        val newPacket = ByteArray(totalLength)
        System.arraycopy(originalHeader.array(), 0, newPacket, 0, originalHeader.limit())
        System.arraycopy(data, 0, newPacket, originalHeader.limit(), data.size)
        
        return newPacket
    }

    private fun calculateChecksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < length - 1) {
            sum += (buf[i].toInt() and 0xFF shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (length % 2 != 0) {
            sum += (buf[i].toInt() and 0xFF shl 8)
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}

