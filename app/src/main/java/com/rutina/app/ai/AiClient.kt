package com.rutina.app.ai

import com.rutina.app.data.Prefs
import kotlinx.coroutines.delay
import java.io.File

/** Error de la IA ya explicado en castellano. [retryable]: es temporal (límite, servidor caído, red) y vale la pena reintentar. */
class AiException(message: String, val retryable: Boolean = false) : Exception(message)

/** Turno de conversación: `fromUser = false` son las respuestas previas del modelo. */
data class Turn(val fromUser: Boolean, val text: String)

/** Proveedor de IA con nivel gratuito. Todas las respuestas se piden en JSON. */
interface AiClient {
    val providerName: String

    /** Modelo en uso; puede cambiar si el configurado ya no existe. */
    val model: String

    suspend fun generateJson(systemInstruction: String, turns: List<Turn>): String

    suspend fun generateJsonWithImage(systemInstruction: String, text: String, jpeg: ByteArray): String

    suspend fun generateJsonWithVideo(systemInstruction: String, text: String, video: File, mimeType: String): String
}

enum class AiProvider(val label: String) {
    GROQ("Groq"),
    GEMINI("Gemini");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value }
    }
}

/** Elige el cliente según los ajustes. */
object Ai {
    /** Cliente para texto (asistente, preguntas): el proveedor elegido en Ajustes. */
    fun client(prefs: Prefs): AiClient? = when (prefs.provider) {
        AiProvider.GROQ -> groq(prefs) ?: gemini(prefs)
        AiProvider.GEMINI -> gemini(prefs) ?: groq(prefs)
    }

    /** Para fotos y vídeos manda Gemini, que es el único gratuito que entiende vídeo y siempre ve fotos. */
    fun mediaClient(prefs: Prefs): AiClient? = gemini(prefs) ?: groq(prefs)

    private fun groq(prefs: Prefs) = prefs.groqKey.takeIf { it.isNotBlank() }?.let { GroqClient(it, prefs.groqModel) }

    private fun gemini(prefs: Prefs) = prefs.apiKey.takeIf { it.isNotBlank() }?.let { GeminiClient(it, prefs.model) }

    /** Si el modelo configurado ya no existía y se ha cambiado por otro, se recuerda el que ha funcionado. */
    fun rememberModel(prefs: Prefs, client: AiClient) {
        when (client) {
            is GroqClient -> if (client.model != prefs.groqModel) prefs.groqModel = client.model
            is GeminiClient -> if (client.model != prefs.model) prefs.model = client.model
        }
    }

    const val MISSING_KEY = "Falta la clave gratuita de la IA: ponla en Ajustes (con Groq o Gemini)."
}

/** Reintenta los errores temporales (límite por minuto, servidor ocupado, red) antes de rendirse. */
suspend fun <T> withRetry(block: suspend () -> T): T {
    val waits = listOf(2_000L, 6_000L)
    for (wait in waits) {
        try {
            return block()
        } catch (e: AiException) {
            if (!e.retryable) throw e
            delay(wait)
        }
    }
    return block()
}
