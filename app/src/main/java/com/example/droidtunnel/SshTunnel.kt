package com.example.droidtunnel

import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream

/**
 * Esta classe gere a ligação SSH. Ela será executada na sua própria thread.
 */
class SshTunnel(private val config: TunnelConfig) : Runnable {

    companion object {
        const val TAG = "SshTunnel"
    }

    private var session: Session? = null

    // Função principal que é executada quando a thread é iniciada.
    override fun run() {
        try {
            Log.d(TAG, "A iniciar a thread do túnel...")

            // 1. Criar uma instância do JSch
            val jsch = JSch()

            // 2. Obter a sessão com o servidor SSH
            session = jsch.getSession(config.sshUser, config.sshHost, config.sshPort.toIntOrNull() ?: 22)
            session?.setPassword(config.sshPassword)

            // Desativa a verificação estrita da chave do anfitrião (NÃO RECOMENDADO PARA PRODUÇÃO, mas útil para testes)
            session?.setConfig("StrictHostKeyChecking", "no")

            Log.d(TAG, "A conectar-se ao servidor SSH: ${config.sshHost}...")
            session?.connect(30000) // Timeout de 30 segundos

            if (session?.isConnected == true) {
                Log.d(TAG, "Ligação SSH estabelecida com sucesso!")

                // TODO: Lógica para ligar o túnel SSH ao proxy/payload e à interface VPN
                // Esta é a parte mais complexa, onde os dados são lidos da interface VPN,
                // processados e enviados através do túnel SSH, e vice-versa.

                // Mantém a thread viva enquanto a ligação estiver ativa
                while (Thread.currentThread().isInterrupted == false && session?.isConnected == true) {
                    // O loop principal de encaminhamento de dados viria aqui.
                    Thread.sleep(1000) // Apenas para manter a thread a correr por agora
                }

            } else {
                Log.e(TAG, "Falha ao estabelecer a ligação SSH.")
            }

        } catch (e: Exception) {
            // Captura qualquer erro durante a ligação
            Log.e(TAG, "Erro na ligação SSH: ${e.message}", e)
        } finally {
            // Garante que a ligação é sempre fechada
            Log.d(TAG, "A fechar a ligação SSH.")
            session?.disconnect()
        }
    }

    // Função para parar a ligação de fora da thread.
    fun stop() {
        Log.d(TAG, "A parar o túnel...")
        session?.disconnect()
    }
}
