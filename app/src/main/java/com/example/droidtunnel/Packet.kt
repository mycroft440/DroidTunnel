package com.example.droidtunnel

import java.net.InetAddress
import java.nio.ByteBuffer

data class Packet(
    val ipHeader: IpHeader,
    val transportHeader: TransportHeader,
    val data: ByteBuffer
) {
    interface IpHeader {
        val version: Int
        val protocol: Int
        val sourceAddress: InetAddress
        val destinationAddress: InetAddress
    }

    interface TransportHeader {
        val sourcePort: Int
        val destinationPort: Int
    }
}
