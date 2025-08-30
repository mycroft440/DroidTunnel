package com.example.droidtunnel

import java.net.InetAddress
import java.nio.ByteBuffer

data class Packet(
    val ipHeader: IpHeader,
    val transportHeader: TransportHeader,
    val data: ByteBuffer
) {
    // Interface para o cabeçalho IP
    interface IpHeader {
        val version: Int
        val protocol: Int
        val sourceAddress: InetAddress
        val destinationAddress: InetAddress
        val headerLength: Int
        // Adicionámos uma função para obter o cabeçalho original em bytes
        fun getRawHeader(): ByteBuffer
    }

    // Interface para o cabeçalho de transporte (TCP/UDP)
    interface TransportHeader {
        val sourcePort: Int
        val destinationPort: Int
        // Adicionámos uma função para obter o cabeçalho original em bytes
        fun getRawHeader(): ByteBuffer
    }
}

