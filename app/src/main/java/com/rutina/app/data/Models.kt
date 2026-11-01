package com.rutina.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class Category(
    val label: String,
    val emoji: String,
    val color: Long,
    val softColor: Long,
    val asksCheck: Boolean = true,
) {
    // Colores de cartel japonés: bermellón, ciruela, añil, castaña, azul ola, mostaza, matcha y sakura
    DEPORTE("Deporte", "🏃‍♀️", 0xFFD9604A, 0xFFF7DDD3),
    GIMNASIO("Gimnasio", "🏋️‍♀️", 0xFF8C4F6E, 0xFFEEDCE5),
    // Trabajo y trayectos no son tareas sino horas ocupadas: no se marcan ni llevan castigo,
    // sirven para que el asistente no ponga nada encima
    TRABAJO("Trabajo", "💼", 0xFF2F4A6D, 0xFFDCE3EC, asksCheck = false),
    TRAYECTO("Trayecto", "🚃", 0xFF9C7A5B, 0xFFEFE5D8, asksCheck = false),
    INGLES("Inglés", "🇬🇧", 0xFF4E7FA0, 0xFFDDE9F1),
    ESTUDIO("Estudio", "📚", 0xFFC9962E, 0xFFF5E7C8),
    DESCANSO("Descanso", "🍵", 0xFF6F8A4E, 0xFFE4EAD6),
    CASA("Casa", "🧹", 0xFF7A8C9E, 0xFFE3E8EC),
    OTRO("Otro", "🎐", 0xFFD98A93, 0xFFF7E0E2);

    companion object {
        fun from(value: String?): Category =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: OTRO
    }
}

enum class Status {
    /** SUSTITUIDA: se cambió por otras tareas; si se hacen todas, no hay castigo. */
    PENDIENTE, HECHO, NO_HECHO, SUSTITUIDA;

    companion object {
        fun from(value: String?): Status = entries.firstOrNull { it.name == value } ?: PENDIENTE
    }
}

/** Una actividad concreta de un día de la semana. `date` es el epochDay de LocalDate. */
@Entity(tableName = "activities", indices = [Index("date")])
data class PlannedActivity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val category: String,
    val reminderMinutes: Int = 15,
    val status: String = Status.PENDIENTE.name,
    val note: String = "",
)

/** Nota que llevan las actividades creadas para recuperar un castigo: no castigan si no se hacen. */
const val RECOVERY_NOTE = "🔁 Recuperación de un castigo"

val PlannedActivity.isRecovery: Boolean get() = note.startsWith(RECOVERY_NOTE)

/** Las tareas que sustituyen a otra llevan en la nota el id de la original: «🔄 En lugar de #12 «Gimnasio»». */
private const val SUBSTITUTE_PREFIX = "🔄 En lugar de #"

fun substituteNote(original: PlannedActivity) = "$SUBSTITUTE_PREFIX${original.id} «${original.title}»"

/** Id de la actividad a la que sustituye esta, si es una tarea de cambio. */
val PlannedActivity.substituteFor: Long?
    get() = if (note.startsWith(SUBSTITUTE_PREFIX)) note.removePrefix(SUBSTITUTE_PREFIX).substringBefore(" ").toLongOrNull() else null

/** Nota para enseñar: en las tareas de cambio, sin el id interno. */
val PlannedActivity.noteForDisplay: String
    get() = when {
        substituteFor != null -> "En lugar de «" + note.substringAfter("«", "")
        isRecovery -> "Recuperación de un castigo"
        else -> note
    }

/** Tareas útiles para cambiar por una que no se va a poder hacer. */
val SUBSTITUTE_IDEAS = listOf(
    "Poner una lavadora y tenderla", "Recoger el lavavajillas", "Fregar los platos", "Barrer", "Fregar el suelo",
    "Ir a la farmacia", "Hacer la compra", "Limpiar el baño", "Sacar la basura", "Cambiar las sábanas",
    "Ordenar el armario", "Planchar",
)

/** Respuesta semanal a "¿qué es lo más difícil que llevas?". `weekStart` es el epochDay del lunes. */
@Entity(tableName = "reflections")
data class Reflection(
    @PrimaryKey val weekStart: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

val PlannedActivity.localDate: LocalDate get() = LocalDate.ofEpochDay(date)
val PlannedActivity.categoryEnum: Category get() = Category.from(category)
val PlannedActivity.statusEnum: Status get() = Status.from(status)

val SPANISH: Locale = Locale.forLanguageTag("es-ES")

val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun dayName(date: LocalDate): String = DAY_NAMES[date.dayOfWeek.value - 1]

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun minutesToText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** Acepta "7:30", "07:30" o "7". Devuelve minutos desde medianoche; "24:00" vale como fin de día. */
fun parseTime(text: String): Int? {
    val parts = text.trim().split(":", ".", "h")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
    if (h !in 0..24 || m !in 0..59 || (h == 24 && m > 0)) return null
    return h * 60 + m
}

fun weekRangeText(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", SPANISH)
    return "${monday.format(fmt)} – ${sunday.format(fmt)}"
}

/** Devuelve los pares de elementos que se solapan en el mismo día. */
fun <T> findOverlaps(items: List<T>, day: (T) -> Long, start: (T) -> Int, end: (T) -> Int): List<Pair<T, T>> {
    val result = mutableListOf<Pair<T, T>>()
    items.groupBy(day).values.forEach { sameDay ->
        val sorted = sameDay.sortedBy(start)
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                if (start(sorted[j]) >= end(sorted[i])) break
                result += sorted[i] to sorted[j]
            }
        }
    }
    return result
}
