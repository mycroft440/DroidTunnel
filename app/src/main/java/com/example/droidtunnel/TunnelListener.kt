package com.example.droidtunnel

/**
 * Interface para comunicar o estado do SshTunnel para o VpnService.
 */
interface TunnelListener {
    /**
     * Chamado quando a ligação SSH está estabelecida e o proxy SOCKS local está pronto.
     * @param port A porta em que o proxy SOCKS está a ouvir.
     */
    fun onTunnelReady(port: Int)

    /**
     * Chamado quando a ligação do túnel é fechada, seja por erro ou intencionalmente.
     */
    fun onTunnelClosed()
}
