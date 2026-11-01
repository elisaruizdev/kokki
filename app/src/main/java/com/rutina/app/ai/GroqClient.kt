package com.rutina.app.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de Groq (API compatible con la de OpenAI). Tiene un plan gratuito y contesta en uno o dos segundos.
 * Las claves se crean en console.groq.com/keys y empiezan por «gsk_».
 */
class GroqClient(private val apiKey: String, model: String) : AiClient {

    override val providerName = "Groq"

    override var model: String = model.ifBlank { DEFAULT_MODEL }
        private set

    override suspend fun generateJson(systemInstruction: String, turns: List<Turn>): String {
        val messages = JSONArray().put(message("system", systemInstruction))
        turns.forEach { messages.put(message(if (it.fromUser) "user" else "assistant", it.text)) }
        return chat(messages, vision = false)
    }

    override suspend fun generateJsonWithImage(systemInstruction: String, text: String, jpeg: ByteArray): String {
        val image = "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", text))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
        val messages = JSONArray()
            .put(message("system", systemInstruction))
            .put(JSONObject().put("role", "user").put("content", content))
        return chat(messages, vision = true)
    }

    override suspend fun generateJsonWithVideo(systemInstruction: String, text: String, video: File, mimeType: String): String =
        throw AiException(
            "Groq no puede ver vídeos. Pon también tu clave de Gemini en Ajustes, o cambia este reto por " +
                "«Ejercicio contado en directo», que cuenta las repeticiones con la cámara sin necesitar IA.",
        )

    /** Modelos disponibles con esta clave. Sirve también para comprobar que la clave funciona. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val data = JSONObject(request("GET", "$BASE_URL/models", null)).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { data.optJSONObject(it) }
            .filter { it.optBoolean("active", true) }
            .map { it.optString("id") }
    }

    private suspend fun chat(messages: JSONArray, vision: Boolean): String = withContext(Dispatchers.IO) {
        val useModel = if (vision) visionModel() else model
        fun body(m: String) = JSONObject()
            .put("model", m)
            .put("messages", messages)
            .put("temperature", 0.4)
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()

        val response = withRetry {
            try {
                request("POST", "$BASE_URL/chat/completions", body(useModel))
            } catch (e: ModelNotFound) {
                if (vision) throw AiException(NO_VISION)
                model = pickModel(listModels()) ?: throw AiException(describeError(404, e.body))
                request("POST", "$BASE_URL/chat/completions", body(model))
            }
        }
        val text = JSONObject(response).optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) throw AiException("Groq ha devuelto una respuesta vacía. Inténtalo otra vez.", retryable = true)
        text
    }

    private suspend fun visionModel(): String =
        pickVisionModel(listModels()) ?: throw AiException(NO_VISION)

    private fun message(role: String, text: String) = JSONObject().put("role", role).put("content", text)

    private class ModelNotFound(val body: String) : Exception()

    private fun request(method: String, url: String, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code == 404 || (code == 400 && "model_not_found" in text) || (code == 400 && "does not exist" in text)) {
                if (method == "POST") throw ModelNotFound(text)
            }
            // A veces el modelo devuelve un JSON mal formado: se vuelve a pedir
            val badJson = code == 400 && "json_validate_failed" in text
            if (code !in 200..299) throw AiException(describeError(code, text), retryable = code == 429 || code >= 500 || badJson)
            return text
        } catch (e: IOException) {
            throw AiException(
                "No se pudo conectar con Groq. Revisa tu conexión a internet (${e.javaClass.simpleName}: ${e.message}).",
                retryable = true,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        val explanation = when {
            code == 401 -> "La clave de Groq no es válida. Cópiala otra vez entera desde console.groq.com/keys (empieza por «gsk_»)."
            code == 403 -> "Groq no permite usar esta clave desde aquí. Crea una nueva en console.groq.com/keys."
            code == 404 -> "No he encontrado ningún modelo de Groq disponible con tu clave."
            code == 413 -> "La petición es demasiado grande para Groq."
            code == 429 -> "Has llegado al límite gratuito de Groq por ahora. Espera un minuto y vuelve a intentarlo."
            code in 500..599 -> "Groq no está disponible ahora mismo. Inténtalo en un rato."
            else -> "Groq ha rechazado la petición."
        }
        val detail = apiMessage.ifBlank { body.take(200) }
        return if (detail.isBlank()) "$explanation (error $code)" else "$explanation\n\nDetalle de Groq ($code): $detail"
    }

    companion object {
        private const val BASE_URL = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        private const val NO_VISION =
            "Groq no tiene ahora ningún modelo que entienda fotos. Para el reto de la foto pon también tu clave de Gemini en Ajustes."

        private val PREFERRED = listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant")
        private val NOT_CHAT = listOf("whisper", "guard", "tts", "orpheus", "playai", "safeguard", "compound")

        /** Mejor modelo de texto disponible: los conocidos por orden y, si no, el primero que sea de chat. */
        fun pickModel(available: List<String>): String? =
            PREFERRED.firstOrNull { it in available } ?: available.firstOrNull { id -> NOT_CHAT.none { it in id } }

        /** Modelo que acepta imágenes, si hay alguno (los de visión cambian a menudo). */
        fun pickVisionModel(available: List<String>): String? =
            available.firstOrNull { id -> listOf("scout", "maverick", "vision", "-vl", "llama-4").any { it in id.lowercase() } }
    }
}
