package com.rutina.app.data

import java.time.LocalDate

data class WeekStat(val monday: LocalDate, val done: Int, val total: Int) {
    /** null si esa semana no tenía nada que cumplir todavía. */
    val percent: Int? get() = if (total == 0) null else done * 100 / total
}

data class CategoryStat(val category: Category, val done: Int, val total: Int) {
    val percent: Int get() = if (total == 0) 0 else done * 100 / total
}

data class DayStat(val dayIndex: Int, val missed: Int, val total: Int)

data class ProgressStats(
    val weeks: List<WeekStat>,
    val categories: List<CategoryStat>,
    val overallPercent: Int?,
    /** Semanas seguidas (hasta la actual) por encima de [GOOD_WEEK_PERCENT]. */
    val streak: Int,
    val hardestCategory: CategoryStat?,
    val hardestDay: DayStat?,
) {
    companion object {
        const val GOOD_WEEK_PERCENT = 70
    }
}

/**
 * Calcula el cumplimiento de [weekCount] semanas empezando en [firstMonday].
 * Solo cuentan las actividades con check y que ya han llegado (hoy o antes); lo pendiente de días
 * pasados cuenta como no cumplido.
 */
fun computeStats(
    activities: List<PlannedActivity>,
    firstMonday: LocalDate,
    weekCount: Int,
    today: LocalDate = LocalDate.now(),
): ProgressStats {
    val todayEpoch = today.toEpochDay()
    // Las cambiadas por otras tareas no cuentan: cuentan las tareas que las sustituyen
    val evaluated = activities.filter { it.categoryEnum.asksCheck && it.date <= todayEpoch && it.statusEnum != Status.SUSTITUIDA }
    fun List<PlannedActivity>.doneCount() = count { it.statusEnum == Status.HECHO }

    val weeks = (0 until weekCount).map { i ->
        val monday = firstMonday.plusWeeks(i.toLong())
        val range = monday.toEpochDay()..monday.plusDays(6).toEpochDay()
        val items = evaluated.filter { it.date in range }
        WeekStat(monday, items.doneCount(), items.size)
    }

    val categories = evaluated.groupBy { it.categoryEnum }
        .map { (category, items) -> CategoryStat(category, items.doneCount(), items.size) }
        .sortedByDescending { it.total }

    var streak = 0
    for (week in weeks.asReversed()) {
        val percent = week.percent
        val good = percent != null && percent >= ProgressStats.GOOD_WEEK_PERCENT
        // La semana en curso solo suma si ya va bien; si no, aún no rompe la racha
        if (week.monday == weeks.last().monday && !good) continue
        if (!good) break
        streak++
    }

    val hardestCategory = categories.filter { it.total >= 2 && it.percent < 100 }.minByOrNull { it.percent }
    val hardestDay = evaluated.groupBy { it.localDate.dayOfWeek.value - 1 }
        .map { (day, items) -> DayStat(day, items.size - items.doneCount(), items.size) }
        .filter { it.total >= 2 && it.missed > 0 }
        .maxByOrNull { it.missed.toFloat() / it.total }

    return ProgressStats(
        weeks = weeks,
        categories = categories,
        overallPercent = if (evaluated.isEmpty()) null else evaluated.doneCount() * 100 / evaluated.size,
        streak = streak,
        hardestCategory = hardestCategory,
        hardestDay = hardestDay,
    )
}

/** Resumen en texto para que la IA tenga en cuenta lo que está costando. */
fun insightsForAi(stats: ProgressStats, reflections: List<Reflection>): String = buildString {
    stats.overallPercent?.let { appendLine("Cumplimiento de las últimas semanas: $it%.") }
    stats.categories.filter { it.total > 0 }.takeIf { it.isNotEmpty() }?.let { list ->
        appendLine("Por tipo: " + list.joinToString(", ") { "${it.category.label} ${it.done}/${it.total}" } + ".")
    }
    stats.hardestDay?.let { appendLine("El día que más falla: ${DAY_NAMES[it.dayIndex].lowercase()}.") }
    if (reflections.isNotEmpty()) {
        appendLine("Lo que la persona dice que le está costando más:")
        reflections.forEach { appendLine("- semana del ${weekRangeText(LocalDate.ofEpochDay(it.weekStart))}: \"${it.text}\"") }
    }
}.trim()
