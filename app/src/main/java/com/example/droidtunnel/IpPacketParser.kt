package com.example.droidtunnel

import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.ByteBuffer

object IpPacketParser {

    private const val IPV4_HEADER_SIZE = 20
    private const val TCP_HEADER_SIZE = 20
    private const val UDP_HEADER_SIZE = 8

    fun parse(buffer: ByteArray, length: Int): Packet? {
        val byteBuffer = ByteBuffer.wrap(buffer, 0, length)
        if (byteBuffer.remaining() < IPV4_HEADER_SIZE) return null

        val versionAndIhl = byteBuffer.get().toInt()
        val version = versionAndIhl shr 4
        if (version != 4) return null // Apenas suporta IPv4

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
            val data = if (byteBuffer.hasRemaining()) byteBuffer else ByteBuffer.allocate(0)
            Packet(ipHeader, transportHeader, data)
        } else {
            null
        }
    }

    private fun parseTcp(buffer: ByteBuffer): Packet.TransportHeader? {
        if (buffer.remaining() < TCP_HEADER_SIZE) return null
        val sourcePort = buffer.getShort().toInt() and 0xFFFF
        val destPort = buffer.getShort().toInt() and 0xFFFF
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

    fun buildTcpPacket(destination: InetAddress, source: InetAddress, data: ByteArray, destPort: Short, sourcePort: Short): ByteArray {
        // Implementação simplificada para o encaminhamento de volta. Uma implementação real seria muito mais complexa.
        return data
    }
}

