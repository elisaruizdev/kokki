package com.rutina.app.ai

import com.rutina.app.data.Verdict
import org.json.JSONObject
import java.io.File

/** Retos que corrige la IA: preguntas de entrevista contestadas en voz alta, fotos de tareas y vídeos de ejercicio. */
object Challenges {

    suspend fun question(client: AiClient, topics: String, recent: List<String>): String {
        val system = """
            Preparas a una persona para entrevistas de trabajo de desarrollo. Genera UNA pregunta técnica de entrevista sobre: $topics.
            - Conceptual, que se pueda responder hablando en 30-90 segundos, sin escribir código largo.
            - Nivel junior o medio. Varía de tema y de subtema cada vez.
            - Formúlala en castellano, como la haría una entrevistadora.
            - No repitas ninguna de estas: ${recent.joinToString(" | ").ifBlank { "(ninguna)" }}
            Responde solo con JSON: {"pregunta": "..."}
        """.trimIndent()
        val raw = client.generateJson(system, listOf(Turn(fromUser = true, text = "Hazme una pregunta.")))
        return runCatching { JSONObject(raw).getString("pregunta") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: throw AiException("No he podido preparar la pregunta. Inténtalo otra vez.")
    }

    suspend fun checkAnswer(client: AiClient, question: String, transcript: String): Verdict {
        val system = """
            Eres una entrevistadora técnica justa pero exigente. Te paso una pregunta de entrevista y la respuesta que la persona
            ha dicho en voz alta, transcrita automáticamente: puede haber errores de transcripción en los términos técnicos
            ("yeison" por JSON, "a sinc" por async...), interprétalos con sentido común.
            Dala por válida solo si explica correctamente lo esencial, como se esperaría en una entrevista junior o media.
            Si es vaga, incorrecta, demasiado corta o no responde a lo que se pregunta, no es válida.
            No importa si ha buscado la información antes: lo que cuenta es que sepa explicarla con sus palabras.
            Responde solo con JSON: {"valida": true o false, "comentario": "2-4 frases en castellano. Si es válida, felicítala por
            la respuesta y añade algún matiz útil para la entrevista. Si no, explica qué falta o qué está mal para que lo busque,
            sin darle la respuesta completa."}
        """.trimIndent()
        val raw = client.generateJson(system, listOf(Turn(fromUser = true, text = "Pregunta: $question\n\nRespuesta dicha en voz alta: $transcript")))
        return parseVerdict(raw)
    }

    suspend fun checkExercise(client: AiClient, exercise: String, video: File, mimeType: String): Verdict {
        val system = """
            Revisas vídeos de retos de ejercicio. El reto es: "$exercise".
            1. Comprueba que sale una persona real haciendo ese ejercicio en directo (no una pantalla, una foto ni otro vídeo grabado).
            2. Cuenta las repeticiones completas y bien hechas (o el tiempo, si el reto es de tiempo).
            Solo es válido si se completa el reto entero. Sé razonable con la técnica, pero no cuentes repeticiones a medias.
            Responde solo con JSON: {"valido": true o false, "repeticiones": número o null, "comentario": "1-3 frases en castellano,
            con ánimo; si no es válido, di por qué"}
        """.trimIndent()
        return parseVerdict(client.generateJsonWithVideo(system, "Revisa este vídeo del reto.", video, mimeType))
    }

    /** Foto hecha en el momento con la cámara de una tarea de casa (cama hecha, platos fregados...). */
    suspend fun checkPhoto(client: AiClient, task: String, jpeg: ByteArray): Verdict {
        val system = """
            Revisas fotos de retos de tareas. La persona tenía que hacer una foto de: "$task".
            1. Comprueba que es una foto real hecha ahora con la cámara de algo físico (no una foto de una pantalla, de otra foto o una imagen de internet).
            2. Comprueba que se ve claramente la tarea TERMINADA y bien hecha.
            Sé razonable, pero no la des por buena si está a medias, no se ve o no tiene nada que ver.
            Responde solo con JSON: {"valido": true o false, "comentario": "1-3 frases en castellano, con ánimo; si no es válido, di qué falta"}
        """.trimIndent()
        return parseVerdict(client.generateJsonWithImage(system, "Revisa esta foto del reto.", jpeg))
    }

    private fun parseVerdict(raw: String): Verdict {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: throw AiException("No he entendido la corrección de la IA. Inténtalo otra vez.")
        val ok = json.optBoolean("valida", false) || json.optBoolean("valido", false)
        val reps = if (json.has("repeticiones") && !json.isNull("repeticiones")) " (${json.optInt("repeticiones")} repeticiones)" else ""
        return Verdict(ok, json.optString("comentario").ifBlank { if (ok) "¡Bien!" else "No es válido." } + reps)
    }
}
