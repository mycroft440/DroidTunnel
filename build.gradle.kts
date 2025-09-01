// Ficheiro de build de nível superior onde pode adicionar opções de configuração
// comuns a todos os subprojetos/módulos.
plugins {
    // Atualizado para a versão estável mais recente do Android Gradle Plugin
    id("com.android.application") version "8.4.1" apply false
    // Mantém a versão do Kotlin alinhada com as dependências do Compose
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
}
