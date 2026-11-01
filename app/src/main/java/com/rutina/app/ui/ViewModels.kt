package com.rutina.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rutina.app.ai.AiException
import com.rutina.app.ai.Ai
import com.rutina.app.ai.PlanProposal
import com.rutina.app.ai.Planner
import com.rutina.app.ai.Turn
import com.rutina.app.app
import com.rutina.app.data.dayName
import com.rutina.app.data.PenaltyRule
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.ProgressStats
import com.rutina.app.data.Reflection
import com.rutina.app.data.computeStats
import com.rutina.app.data.insightsForAi
import com.rutina.app.data.Status
import com.rutina.app.data.mondayOf
import com.rutina.app.data.weekRangeText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.app().repository

    private val _monday = MutableStateFlow(mondayOf(LocalDate.now()))
    val monday: StateFlow<LocalDate> = _monday.asStateFlow()

    /** null mientras se carga, para no enseñar "semana en blanco" un instante. */
    val activities: StateFlow<List<PlannedActivity>?> = _monday
        .flatMapLatest { repository.observeWeek(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val penaltyRules: StateFlow<List<PenaltyRule>> = application.app().penalties.observeRules()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Lunes de las semanas fijadas. */
    val committedWeeks = repository.commitments.weeks

    init {
        // Por si el sistema ha borrado alarmas (actualizaciones, cambios de hora...)
        viewModelScope.launch { repository.rescheduleAll() }
        // Si esta semana ya venía planificada de antes, al empezarla queda fijada
        viewModelScope.launch {
            val monday = mondayOf(LocalDate.now())
            repository.commitments.checkWeekStart(monday, repository.getWeek(monday).isNotEmpty())
        }
    }

    fun substitute(original: PlannedActivity, tasks: List<String>, start: java.time.LocalDateTime) = viewModelScope.launch {
        val count = repository.substitute(original, tasks, start)
        if (count > 0) _message.value = "«${original.title}» cambiada por $count tareas. ¡A por ellas!"
    }

    fun isCommittedDay(epochDay: Long) = repository.commitments.isCommittedDay(epochDay)

    /** Fija la semana a partir de [start] (hoy o la fecha elegida). */
    fun commitWeek(start: LocalDate) {
        repository.commitments.commit(start)
        _message.value = if (start.isAfter(LocalDate.now())) "Tu compromiso empieza el ${dayName(start).lowercase()} ${start.dayOfMonth}"
        else "Semana fijada. ¡A por ella! 克己"
    }

    fun previousWeek() = _monday.update { it.minusWeeks(1) }
    fun nextWeek() = _monday.update { it.plusWeeks(1) }
    fun goToToday() { _monday.value = mondayOf(LocalDate.now()) }

    fun setStatus(activity: PlannedActivity, status: Status) = viewModelScope.launch {
        repository.setStatus(activity.id, status)
    }

    fun save(activity: PlannedActivity) = viewModelScope.launch { repository.save(activity) }

    fun delete(activity: PlannedActivity) = viewModelScope.launch {
        if (!repository.delete(activity)) _message.value = "Semana fijada: no se pueden quitar actividades"
    }

    fun copyToNextWeek() = viewModelScope.launch {
        val from = _monday.value
        val count = repository.copyWeek(from, from.plusWeeks(1))
        _message.value = "Copiadas $count actividades a la semana del ${weekRangeText(from.plusWeeks(1))}"
    }

    /** Borra la semana para planificarla otra vez; si el compromiso aún no había empezado, también se anula. */
    fun clearWeek() = viewModelScope.launch {
        val monday = _monday.value
        val count = repository.clearWeek(monday)
        val cancelled = repository.commitments.cancelPending(monday)
        _message.value = "Borradas $count actividades" + (if (cancelled) " y anulado el compromiso" else "") + ". Puedes empezar de nuevo"
    }

    /** Borrar una sola actividad con opción de deshacer (se vuelve a crear igual). */
    fun restore(activity: PlannedActivity) = viewModelScope.launch { repository.save(activity.copy(id = 0)) }

    fun consumeMessage() { _message.value = null }
}

data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    /** Lo que se reenvía al modelo como historial (su JSON original en las respuestas). */
    val rawForModel: String = text,
    val proposal: PlanProposal? = null,
    val targetMonday: LocalDate? = null,
    val applied: Boolean = false,
    val isError: Boolean = false,
    /** Mensajes generados por la app (errores, confirmaciones): no se reenvían a la IA. */
    val localOnly: Boolean = isError,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.app()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    // El fin de semana lo normal es planificar la semana que viene
    private val _targetNextWeek = MutableStateFlow(LocalDate.now().dayOfWeek >= DayOfWeek.SATURDAY)
    val targetNextWeek: StateFlow<Boolean> = _targetNextWeek.asStateFlow()

    private var nextId = 0L

    /** Texto que otra pantalla quiere dejar escrito en el chat (p. ej. desde Progreso). */
    private val _draft = MutableStateFlow<String?>(null)
    val draft: StateFlow<String?> = _draft.asStateFlow()

    fun prefill(text: String) { _draft.value = text }
    fun consumeDraft() { _draft.value = null }

    fun hasApiKey() = Ai.client(app.prefs) != null

    fun setTargetNextWeek(value: Boolean) { _targetNextWeek.value = value }

    fun targetMonday(): LocalDate = mondayOf(LocalDate.now()).let { if (_targetNextWeek.value) it.plusWeeks(1) else it }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _loading.value) return
        append(ChatMessage(nextId++, fromUser = true, text = trimmed))
        _loading.value = true

        viewModelScope.launch {
            val monday = targetMonday()
            try {
                val prefs = app.prefs
                val client = Ai.client(prefs) ?: throw AiException("Primero añade tu clave gratuita de IA (Groq o Gemini) en Ajustes.")
                val system = Planner.systemInstruction(
                    today = LocalDate.now(),
                    monday = monday,
                    current = app.repository.getWeek(monday),
                    profile = prefs.profile,
                    defaultReminder = prefs.defaultReminder,
                    insights = loadInsights(),
                )
                // Se mandan los últimos turnos válidos para no gastar cuota de más
                val history = _messages.value.filterNot { it.localOnly }.takeLast(MAX_TURNS)
                    .map { Turn(it.fromUser, it.rawForModel) }
                    .dropWhile { !it.fromUser }
                val raw = client.generateJson(system, history)
                // Si el modelo configurado ya no existía, se recuerda el que ha funcionado
                Ai.rememberModel(prefs, client)
                val reply = Planner.parse(raw)
                append(
                    ChatMessage(
                        nextId++, fromUser = false, text = reply.message, rawForModel = reply.raw,
                        proposal = reply.proposal, targetMonday = monday,
                    ),
                )
            } catch (e: Exception) {
                val text = (e as? AiException)?.message ?: "Algo ha fallado: ${e.message}"
                append(ChatMessage(nextId++, fromUser = false, text = text, isError = true))
            } finally {
                _loading.value = false
            }
        }
    }

    fun apply(message: ChatMessage) {
        val proposal = message.proposal ?: return
        val monday = message.targetMonday ?: return
        viewModelScope.launch {
            val count = app.repository.replaceWeek(monday, proposal.toActivities(monday))
            _messages.update { list -> list.map { if (it.id == message.id) it.copy(applied = true) else it } }
            append(
                ChatMessage(
                    nextId++, fromUser = false,
                    text = "Listo: $count actividades guardadas en la semana del ${weekRangeText(monday)}, con sus recordatorios.",
                    localOnly = true,
                ),
            )
        }
    }

    fun clear() { _messages.value = emptyList() }

    private suspend fun loadInsights(): String {
        val firstMonday = mondayOf(LocalDate.now()).minusWeeks(INSIGHT_WEEKS - 1L)
        val activities = app.repository.getRange(firstMonday, mondayOf(LocalDate.now()).plusDays(6))
        return insightsForAi(computeStats(activities, firstMonday, INSIGHT_WEEKS), app.repository.recentReflections(3))
    }

    private fun append(message: ChatMessage) = _messages.update { it + message }

    companion object {
        private const val MAX_TURNS = 12
        private const val INSIGHT_WEEKS = 4
    }
}

class ProgressViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = application.app().repository
    val currentMonday: LocalDate = mondayOf(LocalDate.now())
    private val firstMonday = currentMonday.minusWeeks(WEEKS - 1L)

    /** Actividades de esta semana, para el resumen «X de Y hechas». */
    val thisWeek: StateFlow<List<PlannedActivity>> = repository.observeWeek(currentMonday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<ProgressStats?> = repository.observeRange(firstMonday, currentMonday.plusDays(6))
        .map { computeStats(it, firstMonday, WEEKS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val reflections: StateFlow<List<Reflection>> = repository.observeReflections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveReflection(text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repository.saveReflection(currentMonday, text)
    }

    companion object {
        const val WEEKS = 8
    }
}
