package com.rutina.app.data

import com.rutina.app.notify.AlarmScheduler
import java.time.LocalDate

/** Punto único para modificar actividades: mantiene la base de datos y las alarmas sincronizadas. */
class RoutineRepository(
    private val dao: ActivityDao,
    private val reflectionDao: ReflectionDao,
    private val penalties: PenaltyRepository,
    private val scheduler: AlarmScheduler,
    private val rewards: Rewards,
    val commitments: Commitments,
) {
    fun observeRange(from: LocalDate, to: LocalDate) = dao.observeRange(from.toEpochDay(), to.toEpochDay())

    suspend fun getRange(from: LocalDate, to: LocalDate) = dao.getRange(from.toEpochDay(), to.toEpochDay())

    fun observeReflections() = reflectionDao.observeAll()

    suspend fun recentReflections(limit: Int) = reflectionDao.getRecent(limit)

    suspend fun saveReflection(monday: LocalDate, text: String) {
        reflectionDao.upsert(Reflection(weekStart = monday.toEpochDay(), text = text.trim()))
    }

    fun observeWeek(monday: LocalDate) =
        dao.observeRange(monday.toEpochDay(), monday.plusDays(6).toEpochDay())

    suspend fun getWeek(monday: LocalDate): List<PlannedActivity> =
        dao.getRange(monday.toEpochDay(), monday.plusDays(6).toEpochDay())

    suspend fun get(id: Long): PlannedActivity? = dao.getById(id)

    /**
     * Guarda una actividad. Si ya existía en una semana fijada, solo cambian el nombre, la nota y el aviso:
     * el día, la hora y el tipo se quedan como estaban (es el compromiso).
     */
    suspend fun save(activity: PlannedActivity): Long {
        val original = if (activity.id == 0L) null else dao.getById(activity.id)
        val toSave = if (original != null && commitments.isCommittedDay(original.date)) {
            activity.copy(
                date = original.date,
                startMinute = original.startMinute,
                endMinute = original.endMinute,
                category = original.category,
                status = original.status,
            )
        } else {
            activity
        }
        val id = if (toSave.id == 0L) dao.insert(toSave) else toSave.id.also { dao.update(toSave) }
        scheduler.cancel(id)
        scheduler.schedule(toSave.copy(id = id))
        return id
    }

    /** Borra una actividad, salvo que sea de una semana fijada. Devuelve si se ha borrado. */
    suspend fun delete(activity: PlannedActivity): Boolean {
        if (commitments.isCommittedDay(activity.date)) return false
        scheduler.cancel(activity.id)
        dao.delete(activity)
        return true
    }

    /** "No hecho" es definitivo: una vez marcado ya no se puede cambiar (si no, bastaría con desmarcarlo para librarse). */
    suspend fun setStatus(id: Long, status: Status) {
        val current = dao.getById(id) ?: return
        // Ni lo no hecho ni lo ya cambiado por otras tareas se puede volver a marcar
        if (current.statusEnum == Status.NO_HECHO || current.statusEnum == Status.SUSTITUIDA) return
        val updated = current.copy(status = status.name)
        dao.update(updated)
        scheduler.cancel(id)
        scheduler.schedule(updated)
        // Si no se hace una tarea de cambio, salta el castigo de la actividad a la que sustituía
        if (status == Status.NO_HECHO && current.statusEnum != Status.NO_HECHO) penalties.punish(punishTarget(updated))
        // Cada tarea cumplida suma para el siguiente comodín; desmarcarla se lo quita
        if (status == Status.HECHO && current.statusEnum != Status.HECHO) rewards.onDone(updated)
        if (current.statusEnum == Status.HECHO && status != Status.HECHO) rewards.onUndone(updated)
    }

    /** Actividad cuyo castigo se aplica si no se cumple esta: la original, si es una tarea de cambio. */
    suspend fun punishTarget(activity: PlannedActivity): PlannedActivity =
        activity.substituteFor?.let { dao.getById(it) } ?: activity

    /**
     * Cambia una actividad que no se va a poder hacer por varias tareas de casa o recados, de media hora cada una
     * y seguidas desde [start]. La original queda como «cambiada»; si alguna tarea no se hace, se castiga la original.
     */
    suspend fun substitute(original: PlannedActivity, tasks: List<String>, start: java.time.LocalDateTime): Int {
        val current = dao.getById(original.id) ?: return 0
        if (current.statusEnum != Status.PENDIENTE || tasks.isEmpty()) return 0
        val date = start.toLocalDate().toEpochDay()
        var minute = start.hour * 60 + start.minute
        tasks.forEach { task ->
            val begin = minute.coerceAtMost(23 * 60 + 30)
            save(
                PlannedActivity(
                    date = date,
                    startMinute = begin,
                    endMinute = (begin + SUBSTITUTE_MINUTES).coerceAtMost(23 * 60 + 59),
                    title = task,
                    category = Category.CASA.name,
                    reminderMinutes = 0,
                    note = substituteNote(current),
                ),
            )
            minute += SUBSTITUTE_MINUTES
        }
        val swapped = current.copy(status = Status.SUSTITUIDA.name)
        dao.update(swapped)
        scheduler.cancel(current.id)
        return tasks.size
    }

    /**
     * Sustituye el plan de una semana. Los días ya pasados y las actividades ya marcadas
     * (hecho / no hecho) se conservan para no perder el registro de cumplimiento.
     * Devuelve cuántas actividades nuevas se han creado.
     */
    suspend fun replaceWeek(monday: LocalDate, newItems: List<PlannedActivity>, today: LocalDate = LocalDate.now()): Int {
        val todayEpoch = today.toEpochDay()
        val existing = getWeek(monday)
        // En los días ya fijados no se quita nada: solo se añade lo que no estuviera ya
        val keep = existing.filter { commitments.isCommittedDay(it.date) || it.date < todayEpoch || it.statusEnum != Status.PENDIENTE }
        existing.filterNot { it in keep }.forEach { delete(it) }

        val toInsert = newItems
            .filter { it.date >= todayEpoch }
            .filterNot { n -> keep.any { k -> k.date == n.date && k.startMinute == n.startMinute && k.title.equals(n.title, true) } }
            .map { it.copy(id = 0, status = Status.PENDIENTE.name) }
        dao.insertAll(toInsert).zip(toInsert).forEach { (id, a) -> scheduler.schedule(a.copy(id = id)) }
        return toInsert.size
    }

    /** Copia el plan de una semana a otra (sin los checks). */
    suspend fun copyWeek(from: LocalDate, to: LocalDate): Int {
        val shiftDays = to.toEpochDay() - from.toEpochDay()
        val items = getWeek(from).map { it.copy(id = 0, date = it.date + shiftDays, status = Status.PENDIENTE.name) }
        return replaceWeek(to, items)
    }

    /**
     * Empezar de nuevo: borra todas las actividades de los días sin compromiso (también las marcadas, porque ahí
     * no hay castigo). Se quedan las recuperaciones de castigos, que vienen de otro día. Devuelve cuántas se han borrado.
     */
    suspend fun clearWeek(monday: LocalDate): Int =
        getWeek(monday).filter { !it.isRecovery && !commitments.isCommittedDay(it.date) }.count { delete(it) }

    /** Vuelve a programar todas las alarmas futuras (al arrancar el móvil o abrir la app). */
    suspend fun rescheduleAll() {
        dao.getFrom(LocalDate.now().minusDays(1).toEpochDay()).forEach { scheduler.schedule(it) }
        scheduler.scheduleWeeklyReflection()
    }

    companion object {
        const val SUBSTITUTE_MINUTES = 30
    }
}
