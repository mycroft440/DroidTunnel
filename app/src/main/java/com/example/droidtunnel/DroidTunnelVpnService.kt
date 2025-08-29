package com.example.droidtunnel

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class DroidTunnelVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "start") {
            startVpn()
        } else if (intent?.action == "stop") {
            stopVpn()
        }
        return START_NOT_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("DroidTunnel")
            .addAddress("10.8.0.1", 24) // Exemplo de IP para o túnel
            .addDnsServer("8.8.8.8") // Exemplo de DNS
            .addRoute("0.0.0.0", 0) // Roteia todo o tráfego

        try {
            vpnInterface = builder.establish()
            Log.d("DroidTunnelVpnService", "VPN Started")
            // Aqui você adicionaria a lógica para ler/escrever do túnel
        } catch (e: Exception) {
            Log.e("DroidTunnelVpnService", "Error starting VPN: ${e.message}")
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
            Log.d("DroidTunnelVpnService", "VPN Stopped")
        } catch (e: Exception) {
            Log.e("DroidTunnelVpnService", "Error stopping VPN: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpn()
    }
}

