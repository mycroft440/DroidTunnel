package com.example.droidtunnel

// Enum para representar os diferentes estados da ligação VPN.
enum class VpnState {
    IDLE,       // O serviço não está a ser executado
    CONNECTING, // A tentar estabelecer a ligação
    CONNECTED,  // Ligação ativa e a tunelar tráfego
    DISCONNECTED, // O serviço foi parado ou a ligação foi perdida
    RECONNECTING // A tentar reconectar após uma perda de ligação
}

// Objeto para guardar as constantes usadas na comunicação entre o serviço e a UI.
object VpnServiceState {
    // Ação para os intents de broadcast que contêm atualizações de estado.
    const val ACTION_STATE_UPDATE = "com.example.droidtunnel.ACTION_STATE_UPDATE"

    // Chave para extrair o estado (como uma string) do intent.
    const val EXTRA_STATE = "com.example.droidtunnel.EXTRA_STATE"

    // Chave para extrair mensagens (logs, erros) do intent.
    const val EXTRA_MESSAGE = "com.example.droidtunnel.EXTRA_MESSAGE"
}

