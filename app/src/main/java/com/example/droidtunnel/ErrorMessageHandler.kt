package com.example.droidtunnel

import com.jcraft.jsch.JSchException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Um objeto singleton para "traduzir" exceções técnicas em mensagens
 * de erro amigáveis para o utilizador.
 */
object ErrorMessageHandler {

    fun translate(e: Exception): String {
        return when (e) {
            is JSchException -> handleJschException(e)
            is UnknownHostException -> "Servidor não encontrado. Verifique o endereço do host e a sua ligação à internet."
            is SocketTimeoutException -> "O tempo para ligar ao servidor esgotou. Verifique a rede ou as configurações do proxy."
            is ConnectException -> "A ligação foi recusada pelo servidor. Verifique a porta e as configurações de firewall."
            else -> e.message ?: "Ocorreu um erro desconhecido durante a ligação."
        }
    }

    private fun handleJschException(e: JSchException): String {
        val message = e.message?.lowercase() ?: ""
        return when {
            "auth fail" in message || "auth cancel" in message -> "Falha na autenticação. Verifique o utilizador e a senha."
            "session is down" in message -> "A sessão SSH foi terminada inesperadamente."
            "connection refused" in message -> "A ligação foi recusada pelo servidor SSH."
            "timeout" in message -> "O tempo de ligação ao servidor SSH esgotou."
            else -> "Erro SSH: ${e.message}"
        }
    }
}
