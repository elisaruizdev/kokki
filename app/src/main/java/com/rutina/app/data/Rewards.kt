package com.rutina.app.data

import android.content.Context
import androidx.core.content.edit
import com.rutina.app.notify.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Comodines: cada [goal] tareas cumplidas se gana uno, que sirve para saltarse un castigo. */
data class RewardState(val bonuses: Int, val progress: Int, val goal: Int, val earned: Int, val used: Int)

/**
 * Recompensas. Se guarda qué actividades ya han sumado, así que marcar y desmarcar la misma no da puntos de más:
 * al desmarcarla se le quita lo que sumó.
 */
class Rewards(private val context: Context) {
    private val sp = context.getSharedPreferences("recompensas", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<RewardState> = _state.asStateFlow()

    private fun read() = RewardState(
        bonuses = sp.getInt("bonuses", 0),
        progress = sp.getInt("progress", 0),
        goal = sp.getInt("goal", DEFAULT_GOAL),
        earned = sp.getInt("earned", 0),
        used = sp.getInt("used", 0),
    )

    private fun write(state: RewardState, credited: Set<String>? = null) {
        sp.edit {
            putInt("bonuses", state.bonuses)
            putInt("progress", state.progress)
            putInt("goal", state.goal)
            putInt("earned", state.earned)
            putInt("used", state.used)
            if (credited != null) putStringSet("credited", credited)
        }
        _state.value = state
    }

    private val credited: Set<String> get() = sp.getStringSet("credited", emptySet()).orEmpty()

    /** Cuenta una actividad cumplida. Las de días futuros no cuentan (no se puede haber hecho aún). */
    @Synchronized
    fun onDone(activity: PlannedActivity) {
        val key = activity.id.toString()
        // Recuperaciones y tareas de cambio son para librarse de un castigo: no suman comodines
        if (!activity.categoryEnum.asksCheck || activity.isRecovery || activity.substituteFor != null || key in credited) return
        if (activity.date > java.time.LocalDate.now().toEpochDay()) return
        var s = _state.value.copy(progress = _state.value.progress + 1)
        if (s.progress >= s.goal) {
            s = s.copy(progress = s.progress - s.goal, bonuses = s.bonuses + 1, earned = s.earned + 1)
            Notifier.showBonus(context, s.bonuses)
        }
        write(s, credited + key)
    }

    /** Se desmarca una actividad que había sumado: se le quita su punto (y el comodín, si fue el que lo completó). */
    @Synchronized
    fun onUndone(activity: PlannedActivity) {
        val key = activity.id.toString()
        if (key !in credited) return
        var s = _state.value
        s = when {
            s.progress > 0 -> s.copy(progress = s.progress - 1)
            s.bonuses > 0 -> s.copy(progress = s.goal - 1, bonuses = s.bonuses - 1, earned = s.earned - 1)
            else -> s
        }
        write(s, credited - key)
    }

    /** Gasta un comodín. Devuelve si había alguno. */
    @Synchronized
    fun spend(): Boolean {
        val s = _state.value
        if (s.bonuses <= 0) return false
        write(s.copy(bonuses = s.bonuses - 1, used = s.used + 1))
        return true
    }

    @Synchronized
    fun setGoal(goal: Int) {
        var s = _state.value.copy(goal = goal)
        while (s.progress >= s.goal) s = s.copy(progress = s.progress - s.goal, bonuses = s.bonuses + 1, earned = s.earned + 1)
        write(s)
    }

    companion object {
        const val DEFAULT_GOAL = 5
        val GOAL_OPTIONS = listOf(3, 5, 7, 10)
    }
}
