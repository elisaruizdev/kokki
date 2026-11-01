package com.rutina.app.ai

import com.rutina.app.data.Category
import com.rutina.app.data.categoryEnum
import com.rutina.app.data.DAY_NAMES
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.SPANISH
import com.rutina.app.data.dayName
import com.rutina.app.data.localDate
import com.rutina.app.data.minutesToText
import com.rutina.app.data.parseTime
import com.rutina.app.data.statusEnum
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ProposedItem(
    val dayIndex: Int,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val category: Category,
    val reminderMinutes: Int,
    val note: String,
)

data class PlanProposal(
    val viable: Boolean,
    val warnings: List<String>,
    val items: List<ProposedItem>,
) {
    fun toActivities(monday: LocalDate): List<PlannedActivity> = items.map {
        PlannedActivity(
            date = monday.plusDays(it.dayIndex.toLong()).toEpochDay(),
            startMinute = it.startMinute,
            endMinute = it.endMinute,
            title = it.title,
            category = it.category.name,
            reminderMinutes = it.reminderMinutes,
            note = it.note,
        )
    }
}

data class AiReply(val message: String, val proposal: PlanProposal?, val raw: String)

object Planner {

    fun systemInstruction(
        today: LocalDate,
        monday: LocalDate,
        current: List<PlannedActivity>,
        profile: String,
        defaultReminder: Int,
        insights: String = "",
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", SPANISH)
        val days = (0..6).joinToString("\n") { i ->
            val d = monday.plusDays(i.toLong())
            val past = if (d.isBefore(today)) " (ya pasado, no se puede cambiar)" else ""
            "- ${DAY_NAMES[i].lowercase()}: ${d.format(DateTimeFormatter.ISO_DATE)}$past"
        }
        val currentPlan = if (current.isEmpty()) {
            "(la semana está vacía)"
        } else {
            current.joinToString("\n") {
                "- ${dayName(it.localDate).lowercase()} ${minutesToText(it.startMinute)}-${minutesToText(it.endMinute)} " +
                    "${it.title} [${it.category}] estado=${it.statusEnum.name}" +
                    if (it.note.isNotBlank()) " nota=\"${it.note}\"" else ""
            }
        }
        val categories = Category.entries.joinToString(", ") { it.name }
        // Huecos ocupados por trabajo y trayectos, día a día, para que no ponga nada encima
        val busy = current.filter { !it.categoryEnum.asksCheck }.groupBy { it.date }.toSortedMap().entries.joinToString("\n") { (_, items) ->
            "- ${dayName(items.first().localDate).lowercase()}: " +
                items.sortedBy { it.startMinute }.joinToString(", ") { "${minutesToText(it.startMinute)}-${minutesToText(it.endMinute)} (${it.categoryEnum.label.lowercase()})" }
        }

        return """
Eres el asistente de planificación de una app personal de rutina semanal. Hablas en español, de tú, con tono cercano y breve.
Ayudas a organizar una semana realista: deporte, gimnasio, trabajo, trayectos, clases (por ejemplo, inglés), estudio y descanso.

Hoy es ${today.format(dateFmt)}.
Semana que se está planificando (lunes a domingo):
$days

Información fija de la persona (puede estar vacía):
${profile.ifBlank { "(sin datos)" }}

Plan actual de esa semana:
$currentPlan

Horas OCUPADAS por trabajo y trayectos (no se puede poner nada en ellas):
${busy.ifBlank { "(ninguna todavía)" }}

Cómo le está yendo y qué le cuesta (tenlo muy en cuenta):
${insights.ifBlank { "(todavía no hay datos)" }}

REGLAS
1. Responde SIEMPRE con un único objeto JSON válido, sin texto fuera del JSON, con esta forma:
{
  "mensaje": "explicación breve para la persona (qué has hecho, por qué y qué dudas hay)",
  "viable": true,
  "avisos": ["problemas o riesgos detectados; lista vacía si no hay"],
  "plan": [
    {"dia": "lunes", "inicio": "HH:MM", "fin": "HH:MM", "titulo": "Gimnasio", "categoria": "GIMNASIO", "recordatorio": $defaultReminder, "nota": ""}
  ]
}
2. "plan" debe ser la SEMANA COMPLETA resultante (incluyendo trabajo, trayectos y lo que ya estuviera y deba mantenerse) cada vez que te pidan crear, cambiar o reorganizar algo. Si solo te hacen una pregunta o te falta información imprescindible, pon "plan": null y pregunta en "mensaje".
3. "categoria" debe ser una de: $categories. Usa TRAYECTO para ir y volver del trabajo (dos actividades separadas) y TRABAJO para la jornada.
   TRABAJO y TRAYECTO no son tareas: son horas ocupadas en las que la persona no puede hacer nada más (está trabajando o en el transporte).
   NUNCA pongas ni sugieras otra actividad que empiece, acabe o se cruce con esas horas, aunque sea en parte. Ejemplo: con trayecto de 8:30 a 9:00,
   trabajo de 9:00 a 18:00 y trayecto de 18:00 a 18:30, lo primero que se puede poner esa tarde empieza a las 18:30 o más tarde.
   Si la persona te cuenta su horario de trabajo o de transporte, añádelo siempre al plan como TRABAJO y TRAYECTO.
4. "recordatorio" son los minutos de antelación del aviso (por defecto $defaultReminder; para deporte o gimnasio suele venir bien 30 para prepararse).
5. Comprueba la viabilidad y explícala: sin solapamientos, respetando trayectos, al menos 8 horas de sueño, comidas, días de descanso entre entrenamientos intensos y no sobrecargar los días de trabajo. Si algo no encaja, pon "viable": false, explica el motivo en "avisos" y propón la mejor alternativa posible.
6. Si no te dan horas concretas, elige horarios razonables y dilo en el mensaje. No inventes el horario de trabajo: si no lo conoces y hace falta, pregúntalo.
7. No cambies actividades de días ya pasados ni las que ya estén marcadas como HECHO o NO_HECHO.
8. Horas en formato 24 h. Una actividad no puede pasar de medianoche.
9. Si algo le está costando (pocas veces cumplido o lo ha contado), propón versiones más fáciles, horarios mejores o menos frecuencia, y explícalo con cariño. Celebra lo que sí está cumpliendo.
10. Tono cálido, motivador y cercano; puedes usar algún emoji suelto en "mensaje" (🌸🍡🎋✨), sin pasarte.
        """.trimIndent()
    }

    fun parse(raw: String): AiReply {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
            ?: return AiReply(message = raw, proposal = null, raw = raw)

        val message = json.optString("mensaje").ifBlank { "Aquí tienes la propuesta." }
        val planArray = json.optJSONArray("plan")
        val proposal = planArray?.let { arr ->
            val items = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::parseItem) }
            val warnings = json.optJSONArray("avisos")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
            PlanProposal(viable = json.optBoolean("viable", true), warnings = warnings.filter { it.isNotBlank() }, items = items)
        }?.takeIf { it.items.isNotEmpty() }
        return AiReply(message = message, proposal = proposal, raw = cleaned)
    }

    private fun parseItem(obj: JSONObject): ProposedItem? {
        val day = dayIndex(obj.optString("dia")) ?: return null
        val start = parseTime(obj.optString("inicio")) ?: return null
        var end = parseTime(obj.optString("fin")) ?: return null
        if (end == 0) end = 24 * 60
        if (end <= start) end = minOf(start + 60, 24 * 60)
        return ProposedItem(
            dayIndex = day,
            startMinute = start,
            endMinute = end,
            title = obj.optString("titulo").ifBlank { "Actividad" },
            category = Category.from(obj.optString("categoria")),
            reminderMinutes = obj.optInt("recordatorio", 15).coerceIn(0, 240),
            note = obj.optString("nota").takeUnless { it == "null" }.orEmpty(),
        )
    }

    private fun dayIndex(name: String): Int? {
        val normalized = Normalizer.normalize(name.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        return DAY_NAMES.indexOfFirst {
            Normalizer.normalize(it.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "") == normalized
        }.takeIf { it >= 0 }
    }
}
