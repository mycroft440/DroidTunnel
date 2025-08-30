package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

class DroidTunnelVpnService : VpnService(), TunnelListener {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val TAG = "DroidTunnelVpnService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "start") {
            val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CONFIG", TunnelConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CONFIG") as? TunnelConfig
            }
            
            val useCompression = intent.getBooleanExtra("USE_COMPRESSION", false)
            val useTcpNoDelay = intent.getBooleanExtra("USE_TCP_NO_DELAY", true)
            val useKeepAlive = intent.getBooleanExtra("USE_KEEP_ALIVE", true)

            if (config != null) {
                startVpn(config, useCompression, useTcpNoDelay, useKeepAlive)
            } else {
                stopVpn()
            }
        } else if (action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        config: TunnelConfig,
        useCompression: Boolean,
        useTcpNoDelay: Boolean,
        useKeepAlive: Boolean
    ) {
        tunnelThread = Thread(
            SshTunnel(config, this, useCompression, useTcpNoDelay, useKeepAlive),
            "SshTunnelThread"
        ).apply { start() }
    }

    private fun stopVpn() {
        Log.d(TAG, "A parar a VPN...")
        executor.shutdownNow()
        tunnelThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Erro ao fechar a interface VPN: ${e.message}")
        }
        stopSelf()
    }

    override fun onTunnelReady(port: Int) {
        Log.d(TAG, "Túnel pronto! A iniciar o encaminhamento de tráfego para a porta SOCKS local $port")
        
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24)
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
            val natManager = NatManager(this, port)

            executor.submit {
                val buffer = ByteArray(32767)
                while (vpnInterface != null && !Thread.currentThread().isInterrupted) {
                    try {
                        val bytesRead = vpnInput.read(buffer)
                        if (bytesRead > 0) {
                            val packet = IpPacketParser.parse(buffer, bytesRead)
                            if (packet != null) {
                                natManager.handlePacket(packet, vpnOutput)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Erro ao ler da interface VPN, a parar.", e)
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro fatal ao estabelecer a interface VPN: ${e.message}", e)
            stopVpn()
        }
    }

    override fun onTunnelClosed() {
        Log.d(TAG, "O túnel foi fechado, a parar o serviço VPN.")
        stopVpn()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

