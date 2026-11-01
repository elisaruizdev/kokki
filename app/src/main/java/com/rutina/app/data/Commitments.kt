package com.rutina.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Semanas fijadas: el compromiso. Al fijar una semana se elige cuándo empieza (hoy o una fecha);
 * desde ese día solo se pueden añadir actividades: no se pueden quitar, mover ni cambiar de hora o tipo.
 * Antes de esa fecha la semana se puede seguir cambiando.
 */
class Commitments(context: Context) {
    private val sp = context.getSharedPreferences("compromisos", Context.MODE_PRIVATE)
    private val _weeks = MutableStateFlow(read())

    /** Lunes (epochDay) de cada semana fijada → día (epochDay) desde el que cuenta el compromiso. */
    val weeks: StateFlow<Map<Long, Long>> = _weeks.asStateFlow()

    // Formato «lunes:inicio»; las fijadas antes de poder elegir fecha solo guardaban el lunes
    private fun read(): Map<Long, Long> = sp.getStringSet("weeks", emptySet()).orEmpty().mapNotNull { entry ->
        val monday = entry.substringBefore(":").toLongOrNull() ?: return@mapNotNull null
        monday to (entry.substringAfter(":", "").toLongOrNull() ?: monday)
    }.toMap()

    private fun today() = LocalDate.now().toEpochDay()

    /** El compromiso de esta semana ya ha empezado (hay días que ya no se pueden cambiar). */
    fun isCommitted(monday: LocalDate): Boolean = _weeks.value[monday.toEpochDay()]?.let { today() >= it } ?: false

    /** Ese día está dentro de un compromiso ya empezado: sus actividades no se pueden quitar ni mover. */
    fun isCommittedDay(epochDay: Long): Boolean {
        val start = _weeks.value[mondayOf(LocalDate.ofEpochDay(epochDay)).toEpochDay()] ?: return false
        return today() >= start && epochDay >= start
    }

    /** Fija la semana de [start] a partir de ese día. */
    @Synchronized
    fun commit(start: LocalDate) {
        val updated = _weeks.value + (mondayOf(start).toEpochDay() to start.toEpochDay())
        sp.edit { putStringSet("weeks", updated.map { (monday, from) -> "$monday:$from" }.toSet()) }
        _weeks.value = updated
    }

    /** Anula el compromiso de la semana de [monday] si aún no ha empezado. Devuelve si se ha anulado. */
    @Synchronized
    fun cancelPending(monday: LocalDate): Boolean {
        val start = _weeks.value[monday.toEpochDay()] ?: return false
        if (today() >= start) return false
        val updated = _weeks.value - monday.toEpochDay()
        sp.edit { putStringSet("weeks", updated.map { (m, from) -> "$m:$from" }.toSet()) }
        _weeks.value = updated
        return true
    }

    /**
     * La primera vez que se abre la app en una semana que ya venía planificada (y que no se había fijado
     * con fecha), se fija sola desde el lunes: lo que se planificó antes de empezar es el compromiso.
     */
    @Synchronized
    fun checkWeekStart(currentMonday: LocalDate, hasActivities: Boolean) {
        val lastSeen = sp.getLong("last_seen_monday", Long.MIN_VALUE)
        val now = currentMonday.toEpochDay()
        if (lastSeen == now) return
        sp.edit { putLong("last_seen_monday", now) }
        // La primera vez que se usa la app no hay semana planificada de antes que fijar
        if (lastSeen != Long.MIN_VALUE && lastSeen < now && hasActivities && now !in _weeks.value) commit(currentMonday)
    }
}
