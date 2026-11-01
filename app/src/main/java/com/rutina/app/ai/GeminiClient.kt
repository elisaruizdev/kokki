package com.rutina.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Cliente mínimo de la API de Gemini (nivel gratuito de Google AI Studio). */
class GeminiClient(private val apiKey: String, model: String) : AiClient {

    override val providerName = "Gemini"

    /** Modelo en uso; puede cambiar si el configurado ya no existe (ver [generateJson]). */
    override var model: String = model
        private set

    /**
     * Si el modelo configurado ya no existe (Google retira modelos cada pocos meses),
     * busca otro Flash disponible con esta clave y reintenta una vez.
     */
    override suspend fun generateJson(systemInstruction: String, turns: List<Turn>): String =
        generate(systemInstruction, JSONArray().apply {
            turns.forEach { turn ->
                put(
                    JSONObject()
                        .put("role", if (turn.fromUser) "user" else "model")
                        .put("parts", JSONArray().put(JSONObject().put("text", turn.text))),
                )
            }
        })

    /** Un único mensaje con varias partes (texto, ficheros subidos...). */
    suspend fun generateJson(systemInstruction: String, parts: JSONArray): String =
        generate(systemInstruction, JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))

    override suspend fun generateJsonWithImage(systemInstruction: String, text: String, jpeg: ByteArray): String =
        generateJson(
            systemInstruction,
            JSONArray()
                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
                .put(JSONObject().put("text", text)),
        )

    override suspend fun generateJsonWithVideo(systemInstruction: String, text: String, video: File, mimeType: String): String {
        val uri = uploadFile(video, mimeType)
        return generateJson(
            systemInstruction,
            JSONArray()
                .put(JSONObject().put("fileData", JSONObject().put("mimeType", mimeType).put("fileUri", uri)))
                .put(JSONObject().put("text", text)),
        )
    }

    private suspend fun generate(systemInstruction: String, contents: JSONArray): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.4))
            .toString()

        val response = withRetry {
            try {
                request("POST", generateUrl(), body)
            } catch (e: ModelNotFound) {
                model = pickModel(listModels()) ?: throw AiException(describeError(404, e.body))
                request("POST", generateUrl(), body)
            }
        }
        extractText(response)
    }

    /** Modelos de texto disponibles con esta clave. Sirve también para comprobar que la clave funciona. */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val models = JSONObject(request("GET", "$BASE_URL?pageSize=1000", null)).optJSONArray("models") ?: JSONArray()
        (0 until models.length()).mapNotNull { models.optJSONObject(it) }
            .filter { m ->
                val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            }
            .map { it.optString("name").removePrefix("models/") }
    }

    /**
     * Sube un fichero (p. ej. un vídeo) a la API de ficheros de Gemini y espera a que esté procesado.
     * Devuelve su URI para usarla en una parte `fileData`. Google lo borra solo a las 48 h.
     */
    suspend fun uploadFile(file: File, mimeType: String): String = withContext(Dispatchers.IO) {
        val start = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        val uploadUrl = try {
            start.requestMethod = "POST"
            start.doOutput = true
            start.setRequestProperty("x-goog-api-key", apiKey)
            start.setRequestProperty("X-Goog-Upload-Protocol", "resumable")
            start.setRequestProperty("X-Goog-Upload-Command", "start")
            start.setRequestProperty("X-Goog-Upload-Header-Content-Length", file.length().toString())
            start.setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType)
            start.setRequestProperty("Content-Type", "application/json")
            start.outputStream.use { it.write("""{"file":{"display_name":"reto"}}""".toByteArray()) }
            readResponse(start)
            start.getHeaderField("X-Goog-Upload-URL") ?: throw AiException("Google no ha aceptado la subida del vídeo.")
        } catch (e: IOException) {
            throw AiException("No se pudo subir el vídeo. Revisa tu conexión a internet (${e.message}).")
        } finally {
            start.disconnect()
        }

        val upload = URL(uploadUrl).openConnection() as HttpURLConnection
        var info = try {
            upload.requestMethod = "POST"
            upload.doOutput = true
            upload.setFixedLengthStreamingMode(file.length())
            upload.setRequestProperty("X-Goog-Upload-Offset", "0")
            upload.setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
            upload.readTimeout = 120_000
            upload.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            JSONObject(readResponse(upload)).getJSONObject("file")
        } catch (e: IOException) {
            throw AiException("No se pudo subir el vídeo. Revisa tu conexión a internet (${e.message}).")
        } finally {
            upload.disconnect()
        }

        // Los vídeos tardan unos segundos en procesarse antes de poder usarse
        repeat(60) {
            when (info.optString("state")) {
                "ACTIVE" -> return@withContext info.getString("uri")
                "FAILED" -> throw AiException("Google no ha podido procesar el vídeo. Prueba a grabarlo otra vez.")
            }
            delay(2_000)
            info = JSONObject(request("GET", "$API_ROOT/${info.getString("name")}", null))
        }
        throw AiException("El vídeo está tardando demasiado en procesarse. Inténtalo de nuevo en un rato.")
    }

    private fun generateUrl() = "$BASE_URL/${URLEncoder.encode(model, "UTF-8")}:generateContent"

    private class ModelNotFound(val body: String) : Exception()

    private fun request(method: String, url: String, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            // Los modelos que «piensan» pueden tardar en contestar
            connection.readTimeout = 120_000
            connection.setRequestProperty("x-goog-api-key", apiKey)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode == 404 && method == "POST") throw ModelNotFound(readBody(connection))
            return readResponse(connection)
        } catch (e: IOException) {
            throw AiException(
                "No se pudo conectar con Gemini. Revisa tu conexión a internet (${e.javaClass.simpleName}: ${e.message}).",
                retryable = true,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String =
        (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

    /** Cuerpo de la respuesta, o [AiException] con el error explicado si no es 2xx. */
    private fun readResponse(connection: HttpURLConnection): String {
        val body = readBody(connection)
        val code = connection.responseCode
        if (code !in 200..299) throw AiException(describeError(code, body), retryable = code == 429 || code >= 500)
        return body
    }

    private fun extractText(response: String): String {
        val candidate = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException("Gemini no ha devuelto respuesta. Prueba a reformular el mensaje.")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        // Los modelos con razonamiento pueden incluir partes "thought"; solo nos interesa el texto final
        val text = (0 until parts.length())
            .mapNotNull { parts.optJSONObject(it) }
            .filterNot { it.optBoolean("thought", false) }
            .joinToString("") { it.optString("text") }
        if (text.isBlank()) throw AiException("Gemini ha devuelto una respuesta vacía (${candidate.optString("finishReason")}).")
        return text
    }

    /** Explica el error en castellano y añade siempre el mensaje original de Google para poder diagnosticarlo. */
    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching { JSONObject(body).getJSONObject("error").optString("message") }.getOrNull().orEmpty()
        val explanation = when {
            body.contains("API_KEY_INVALID") || apiMessage.contains("API key not valid", true) ->
                "La API key no es válida. Cópiala otra vez entera desde Google AI Studio (aistudio.google.com/apikey); empieza por «AQ.» o por «AIza»."
            apiMessage.contains("expired", true) -> "La API key ha caducado. Crea una nueva en Google AI Studio."
            apiMessage.contains("location is not supported", true) -> "Google no permite usar Gemini desde tu ubicación actual."
            body.contains("SERVICE_DISABLED") || apiMessage.contains("has not been used in project", true) ->
                "Esa clave es de un proyecto de Google Cloud sin la API de Gemini activada. Crea la clave desde Google AI Studio (aistudio.google.com/apikey)."
            code == 401 || code == 403 -> "La API key no tiene permiso para usar Gemini. Crea una nueva en Google AI Studio."
            code == 404 -> "No he encontrado ningún modelo de Gemini disponible con tu clave."
            code == 429 -> "Has llegado al límite gratuito de Gemini por ahora. Espera un minuto y vuelve a intentarlo."
            code in 500..599 -> "Gemini no está disponible ahora mismo. Inténtalo en un rato."
            else -> "Gemini ha rechazado la petición."
        }
        val detail = apiMessage.ifBlank { body.take(200) }
        return if (detail.isBlank()) "$explanation (error $code)" else "$explanation\n\nDetalle de Google ($code): $detail"
    }

    companion object {
        private const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta"
        private const val BASE_URL = "$API_ROOT/models"
        private const val UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files"

        /** Elige el mejor modelo Flash de texto: el alias «latest» si existe, si no el de versión más alta. */
        fun pickModel(available: List<String>): String? {
            if ("gemini-flash-latest" in available) return "gemini-flash-latest"
            val excluded = listOf("lite", "image", "tts", "audio", "live", "embedding", "thinking", "exp")
            val flash = available.filter { name -> name.startsWith("gemini-") && "flash" in name && excluded.none { it in name } }
            val version = { name: String ->
                Regex("""gemini-(\d+(?:\.\d+)?)""").find(name)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            }
            return flash.sortedWith(compareByDescending<String> { version(it) }.thenBy { "preview" in it }.thenBy { it.length })
                .firstOrNull()
        }
    }
}
