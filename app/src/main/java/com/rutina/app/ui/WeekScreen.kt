package com.rutina.app.ui

import androidx.compose.foundation.lazy.LazyItemScope

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.rutina.app.data.noteForDisplay
import com.rutina.app.data.isRecovery
import com.rutina.app.data.substituteFor
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePicker
import androidx.compose.runtime.key
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import java.time.LocalTime
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rutina.app.app
import com.rutina.app.data.Category
import com.rutina.app.data.Challenge
import com.rutina.app.data.challengeEnum
import com.rutina.app.data.challengeText
import com.rutina.app.data.durationText
import com.rutina.app.data.labelList
import com.rutina.app.data.matches
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.SPANISH
import com.rutina.app.data.Status
import com.rutina.app.data.categoryEnum
import com.rutina.app.data.dayName
import com.rutina.app.data.findOverlaps
import com.rutina.app.data.minutesToText
import com.rutina.app.data.mondayOf
import com.rutina.app.data.statusEnum
import com.rutina.app.data.weekRangeText
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Confirm { COPY, CLEAR }

@Composable
fun WeekScreen(onOpenAssistant: () -> Unit, vm: WeekViewModel = viewModel()) {
    val context = LocalContext.current
    val monday by vm.monday.collectAsStateWithLifecycle()
    val loadedActivities by vm.activities.collectAsStateWithLifecycle()
    val activities = loadedActivities.orEmpty()
    val message by vm.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlannedActivity?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var confirmMiss by remember { mutableStateOf<PlannedActivity?>(null) }
    var swapping by remember { mutableStateOf<PlannedActivity?>(null) }
    var showFinished by remember { mutableStateOf(false) }
    val penaltyRules by vm.penaltyRules.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val isCurrentWeek = monday == mondayOf(today)
    /** Día desde el que cuenta el compromiso de esta semana, si está fijada. */
    val commitStart = vm.committedWeeks.collectAsStateWithLifecycle().value[monday.toEpochDay()]?.let { LocalDate.ofEpochDay(it) }
    val committed = commitStart != null && !commitStart.isAfter(today)
    var confirmCommit by remember { mutableStateOf(false) }
    // Un día por pantalla: se cambia deslizando o tocando la tira de días
    val pager = key(monday) { rememberPagerState(initialPage = if (isCurrentWeek) today.dayOfWeek.value - 1 else 0) { 7 } }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Al ganar un comodín se celebra aparte
    val rewards by context.app().rewards.state.collectAsStateWithLifecycle()
    var lastEarned by remember { mutableStateOf(rewards.earned) }
    LaunchedEffect(rewards.earned) {
        if (rewards.earned > lastEarned) {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("¡Comodín ganado! Ya tienes ${rewards.bonuses}")
        }
        lastEarned = rewards.earned
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val overlapping = remember(activities) {
        findOverlaps(activities, { it.date }, { it.startMinute }, { it.endMinute })
            .flatMap { listOf(it.first.id, it.second.id) }.toSet()
    }

    fun newActivity(date: LocalDate) = PlannedActivity(
        date = date.toEpochDay(),
        startMinute = 9 * 60,
        endMinute = 10 * 60,
        title = "",
        category = Category.DEPORTE.name,
        reminderMinutes = context.app().prefs.defaultReminder,
    )

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekHeader(
                    monday = monday,
                    isCurrentWeek = isCurrentWeek,
                    onPrevious = vm::previousWeek,
                    onNext = vm::nextWeek,
                    onToday = vm::goToToday,
                    onCopy = { confirm = Confirm.COPY },
                    onClear = if (committed) null else ({ confirm = Confirm.CLEAR }),
                )
                if (commitStart != null) {
                    Pill(
                        when {
                            commitStart.isAfter(today) -> "Tu compromiso empieza el ${dayName(commitStart).lowercase()} ${commitStart.dayOfMonth}: hasta entonces puedes cambiarla"
                            commitStart.isAfter(monday) -> "Fijada desde el ${dayName(commitStart).lowercase()} ${commitStart.dayOfMonth} · solo puedes añadir"
                            else -> "Semana fijada · solo puedes añadir"
                        },
                        background = MaterialTheme.colorScheme.primaryContainer,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (!committed && activities.isNotEmpty()) {
                        OutlinedButton(onClick = { confirm = Confirm.CLEAR }, modifier = Modifier.fillMaxWidth()) {
                            Icon(KIcons.Restart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Borrar todo y empezar de nuevo")
                        }
                    }
                } else if (activities.isNotEmpty() && !monday.plusDays(6).isBefore(today)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { confirm = Confirm.CLEAR }) {
                            Icon(KIcons.Restart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("De nuevo")
                        }
                        GradientButton("Fijar esta semana", onClick = { confirmCommit = true }, modifier = Modifier.weight(1f), icon = KIcons.Lock)
                    }
                }
                DayStrip(monday, today, pager.currentPage, activities) { index -> scope.launch { pager.animateScrollToPage(index) } }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val date = monday.plusDays(page.toLong())
                val dayItems = activities.filter { it.date == date.toEpochDay() }
                // Lo pendiente se ve siempre; lo ya hecho o no cumplido va aparte para no mezclarlo
                val pending = dayItems.filter { it.statusEnum == Status.PENDIENTE }
                val finished = dayItems.filter { it.statusEnum != Status.PENDIENTE }
                @Composable
                fun LazyItemScope.Card(activity: PlannedActivity) {
                    Box(Modifier.fillMaxWidth().animateItem()) {
                        ActivityCard(
                            activity = activity,
                            overlaps = activity.id in overlapping,
                            isPast = date.isBefore(today),
                            onClick = { editing = activity },
                            onDelete = if (vm.isCommittedDay(activity.date)) null else ({
                                vm.delete(activity)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val result = snackbar.showSnackbar("«${activity.title}» borrada", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                                    if (result == SnackbarResult.ActionPerformed) vm.restore(activity)
                                }
                            }),
                            onToggle = { target ->
                                if (activity.statusEnum == Status.NO_HECHO || activity.statusEnum == Status.SUSTITUIDA) return@ActivityCard
                                val newStatus = if (activity.statusEnum == target) Status.PENDIENTE else target
                                if (newStatus == Status.NO_HECHO) {
                                    confirmMiss = activity
                                    return@ActivityCard
                                }
                                vm.setStatus(activity, newStatus)
                                if (newStatus == Status.HECHO) {
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        snackbar.showSnackbar(CHEERS.random())
                                    }
                                }
                            },
                        )
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") { DayHeader(date, date == today, dayItems) }
                    when {
                        loadedActivities == null -> Unit
                        activities.isEmpty() -> item(key = "empty") { EmptyWeekCard(onOpenAssistant) }
                        dayItems.isEmpty() -> item(key = "free") { FreeDayCard { editing = newActivity(date) } }
                        else -> {
                            items(pending, key = { it.id }) { activity -> Card(activity) }
                            if (pending.isEmpty()) item(key = "allMarked") {
                                Text(
                                    "Todo lo de este día ya está marcado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            if (finished.isNotEmpty()) item(key = "toggleFinished") {
                                FinishedToggle(finished, showFinished) { showFinished = !showFinished }
                            }
                            if (showFinished) items(finished, key = { it.id }) { activity -> Card(activity) }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)) { data ->
            Snackbar(
                shape = RoundedCornerShape(50),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(data.visuals.message, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    data.visuals.actionLabel?.let { label ->
                        TextButton(onClick = { data.performAction() }) { Text(label, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            GradientCircle(onClick = {
                editing = newActivity(monday.plusDays(pager.currentPage.toLong()))
            }, size = 64.dp) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir actividad", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }

    editing?.let { current ->
        ActivityEditor(
            initial = current,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = if (current.id != 0L && !vm.isCommittedDay(current.date)) {
                { vm.delete(current); editing = null }
            } else {
                null
            },
            locked = current.id != 0L && vm.isCommittedDay(current.date),
        )
    }

    if (confirmCommit) {
        CommitDialog(
            monday = monday,
            today = today,
            onDismiss = { confirmCommit = false },
            onCommit = { start ->
                vm.commitWeek(start)
                confirmCommit = false
            },
        )
    }

    swapping?.let { activity ->
        SwapScreen(activity, onDismiss = { swapping = null }) { tasks, start ->
            vm.substitute(activity, tasks, start)
            swapping = null
        }
    }

    confirmMiss?.let { activity ->
        val canSwap = activity.substituteFor == null && !activity.isRecovery && activity.statusEnum == Status.PENDIENTE
        AlertDialog(
            onDismissRequest = { confirmMiss = null },
            title = { Text("¿No lo vas a hacer?") },
            text = {
                Column {
                val punishable = vm.isCommittedDay(activity.date)
                val punishments = if (punishable) penaltyRules.filter { it.matches(activity) } else emptyList()
                Text(
                    "Esto es irrevocable: después no podrás marcarlo como hecho." +
                        (if (!punishable && penaltyRules.any { it.matches(activity) }) "\n\nTu compromiso aún no ha empezado este día, así que no hay castigo." else "") +
                        if (punishments.isEmpty()) "" else "\n\nSe activará tu castigo:\n" + punishments.joinToString("\n") {
                            "${it.labelList.joinToString(", ")} durante ${durationText(it.minutes)}" +
                                if (it.challengeEnum != Challenge.NINGUNO) ", o hasta ${it.challengeText}" else ""
                        } +
                        (if (activity.substituteFor != null) "\n\nEs una tarea de cambio: si no la haces, salta el castigo de la actividad a la que sustituía." else ""),
                )
                if (canSwap) {
                    Spacer(Modifier.height(12.dp))
                    GradientButton(
                        "Mejor la cambio por otras tareas",
                        onClick = {
                            swapping = activity
                            confirmMiss = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setStatus(activity, Status.NO_HECHO)
                    confirmMiss = null
                }) { Text("No lo he hecho", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmMiss = null }) { Text("Cancelar") } },
        )
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == Confirm.COPY) "Copiar a la semana siguiente" else "¿Empezar de nuevo?") },
            text = {
                Text(
                    if (action == Confirm.COPY) {
                        "Se copiará esta semana a la del ${weekRangeText(monday.plusWeeks(1))}. Las actividades pendientes que hubiera allí se sustituirán (si esa semana ya está fijada, solo se añaden)."
                    } else {
                        "Se borrarán todas las actividades de esta semana para que la planifiques otra vez. Como aún no la has fijado, " +
                            "no salta ningún castigo." + (if (commitStart != null) " También se anula la fecha de compromiso que habías elegido." else "") +
                            " Tus castigos (las reglas) se quedan como están."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (action == Confirm.COPY) vm.copyToNextWeek() else vm.clearWeek()
                    confirm = null
                }) { Text(if (action == Confirm.COPY) "Copiar" else "Borrar todo", color = if (action == Confirm.COPY) Color.Unspecified else MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun WeekHeader(
    monday: LocalDate,
    isCurrentWeek: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onCopy: () -> Unit,
    /** null en una semana fijada: no se puede vaciar. */
    onClear: (() -> Unit)?,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ScreenTitle(
            title = "Mi semana",
            subtitle = weekRangeText(monday) + if (isCurrentWeek) " · esta semana" else "",
            modifier = Modifier.weight(1f),
            kanji = "週",
        )
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semana anterior") }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semana siguiente") }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Copiar a la semana siguiente") }, onClick = { menuOpen = false; onCopy() })
                if (onClear != null) DropdownMenuItem(text = { Text("Borrar todo y empezar de nuevo") }, onClick = { menuOpen = false; onClear() })
            }
        }
    }
    if (!isCurrentWeek) {
        TextButton(onClick = onToday) { Text("← Volver a esta semana") }
    }
}

/**
 * Fijar la semana: se elige cuándo empieza el compromiso, ahora o un día concreto de esta semana.
 * Hasta ese día se puede seguir cambiando; desde entonces solo se puede añadir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitDialog(monday: LocalDate, today: LocalDate, onDismiss: () -> Unit, onCommit: (LocalDate) -> Unit) {
    val first = if (today.isAfter(monday)) today else monday
    val last = monday.plusDays(6)
    var picking by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<LocalDate?>(null) }

    if (picking) {
        val zone = java.time.ZoneOffset.UTC
        val state = rememberDatePickerState(
            initialSelectedDateMillis = first.atStartOfDay(zone).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val day = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate()
                    return !day.isBefore(first) && !day.isAfter(last)
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    chosen = state.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                    picking = false
                }) { Text("Elegir") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
        return
    }

    val start = chosen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start == null) "¿Cuándo empiezas?" else "¿Fijamos la semana?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (start == null) {
                    Text(
                        "Desde el día que elijas solo podrás añadir actividades: no podrás quitarlas, moverlas de día " +
                            "ni cambiarles la hora o el tipo. Hasta entonces puedes seguir retocándola.",
                    )
                    if (!today.isBefore(monday)) {
                        GradientButton("▶ Empezar ahora", onClick = { onCommit(today) }, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("Elegir una fecha") }
                } else {
                    Text(
                        "Tu compromiso 克己 empieza el ${dayName(start).lowercase()} ${start.dayOfMonth}. " +
                            "Desde ese día solo podrás añadir actividades. Esto no se puede deshacer.",
                    )
                }
            }
        },
        confirmButton = {
            if (start != null) TextButton(onClick = { onCommit(start) }) { Text("Sí, me comprometo") }
        },
        dismissButton = {
            TextButton(onClick = { if (start != null) chosen = null else onDismiss() }) { Text(if (start != null) "Cambiar fecha" else "Aún no") }
        },
    )
}

/** Tira de lunes a domingo con su kanji; el día de hoy va en rojo y los días completados llevan una flor. */
@Composable
private fun DayStrip(monday: LocalDate, today: LocalDate, selected: Int, activities: List<PlannedActivity>, onDayClick: (Int) -> Unit) {
    val pastels = LocalPastels.current
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        for (i in 0..6) {
            val date = monday.plusDays(i.toLong())
            val trackable = activities.filter { it.date == date.toEpochDay() && it.categoryEnum.asksCheck }
            val allDone = trackable.isNotEmpty() && trackable.all { it.statusEnum == Status.HECHO }
            val isToday = i == selected
            val reallyToday = date == today
            val shape = RoundedCornerShape(16.dp)
            val textColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(
                        if (isToday) Modifier.background(pastels.accentVertical).border(1.5.dp, inkBorder(), shape)
                        else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .border(if (reallyToday) 2.dp else 1.dp, if (reallyToday) MaterialTheme.colorScheme.primary else inkBorder(), shape),
                    )
                    .bouncyClick { onDayClick(i) }
                    .padding(vertical = 5.dp),
            ) {
                Text(DAY_LETTERS[i], style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.85f))
                Text("${date.dayOfMonth}", fontFamily = Script, fontSize = 16.sp, lineHeight = 20.sp, color = textColor)
                Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
                    if (allDone) {
                        Icon(KIcons.Flower, contentDescription = "Día completo", tint = if (isToday) textColor else Shu, modifier = Modifier.size(10.dp))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            trackable.take(3).forEach { activity ->
                                val done = activity.statusEnum == Status.HECHO
                                val dot = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .then(if (done) Modifier.background(dot) else Modifier.border(1.dp, dot, CircleShape)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val DAY_LETTERS = listOf("L", "M", "X", "J", "V", "S", "D")

/** Botón para ver u ocultar las actividades ya hechas o no cumplidas del día. */
@Composable
private fun FinishedToggle(finished: List<PlannedActivity>, open: Boolean, onClick: () -> Unit) {
    val done = finished.count { it.statusEnum == Status.HECHO }
    val missed = finished.count { it.statusEnum == Status.NO_HECHO }
    val swapped = finished.size - done - missed
    val detail = listOfNotNull(
        if (done > 0) "$done hechas" else null,
        if (missed > 0) "$missed no cumplidas" else null,
        if (swapped > 0) "$swapped cambiadas" else null,
    ).joinToString(" · ")
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(if (open) KIcons.Hide else KIcons.Show, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (open) "Ocultar hechas y no cumplidas" else "Ver hechas y no cumplidas ($detail)", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FreeDayCard(onAdd: () -> Unit) {
    CuteCard(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(KIcons.Free, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text("Día libre", style = MaterialTheme.typography.titleMedium)
            Text("Descansar también cuenta. ¿O quieres apuntar algo?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onAdd) { Text("＋ Añadir actividad") }
        }
    }
}

@Composable
private fun EmptyWeekCard(onOpenAssistant: () -> Unit) {
    CuteCard(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(KIcons.Calendar, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
            Text("Tu semana está en blanco", style = MaterialTheme.typography.titleLarge)
            Text(
                "Cuéntale a tu asistente qué quieres hacer y te la organiza, o añade cosas con el +",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            GradientButton("Planificar con el asistente", onClick = onOpenAssistant)
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, isToday: Boolean, items: List<PlannedActivity>) {
    val trackable = items.filter { it.categoryEnum.asksCheck }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp, start = 4.dp, end = 4.dp)) {
        Text(
            dayName(date),
            fontFamily = Script,
            fontSize = 19.sp,
            lineHeight = 26.sp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            date.format(DateTimeFormatter.ofPattern("d MMM", SPANISH)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isToday) {
            Spacer(Modifier.width(8.dp))
            Pill("HOY", background = MaterialTheme.colorScheme.primary, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.weight(1f))
        if (trackable.isNotEmpty()) {
            val done = trackable.count { it.statusEnum == Status.HECHO }
            Pill(
                "$done/${trackable.size} ✓",
                background = if (done == trackable.size) DoneGreen.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ActivityCard(
    activity: PlannedActivity,
    overlaps: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    onToggle: (Status) -> Unit,
    /** null si el día ya está fijado: no se puede borrar. */
    onDelete: (() -> Unit)? = null,
) {
    val category = activity.categoryEnum
    val status = activity.statusEnum
    val dark = LocalPastels.current.isDark
    val (background, ink) = when (status) {
        Status.HECHO -> if (dark) Color(0xFF2C3B20) to Color(0xFFDDEBC9) else Color(0xFFDCE8CC) to Color(0xFF26391A)
        Status.NO_HECHO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    val cardColor by animateColorAsState(background, tween(400), label = "cardColor")
    val textColor by animateColorAsState(ink, tween(400), label = "cardText")
    val subColor = if (status == Status.HECHO || status == Status.NO_HECHO) textColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
    CuteCard(
        modifier = Modifier.fillMaxWidth().alpha(if (status == Status.SUSTITUIDA) 0.7f else 1f),
        onClick = onClick,
        color = cardColor,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(minutesToText(activity.startMinute), fontFamily = Script, fontSize = 18.sp, lineHeight = 22.sp, color = textColor)
                    Text(
                        "  – ${minutesToText(activity.endMinute)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = subColor,
                    )
                }
                Text(
                    activity.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    textDecoration = if (status == Status.NO_HECHO || status == Status.SUSTITUIDA) TextDecoration.LineThrough else null,
                )
                Text(
                    if (category.asksCheck) category.label else "${category.label} · horas ocupadas",
                    style = MaterialTheme.typography.labelMedium,
                    color = subColor,
                )
                if (activity.note.isNotBlank()) {
                    Text(activity.noteForDisplay, style = MaterialTheme.typography.bodySmall, color = subColor)
                }
                val warning = when {
                    overlaps -> "Se solapa con otra actividad"
                    isPast && status == Status.PENDIENTE && category.asksCheck -> "Sin marcar"
                    status == Status.HECHO -> "¡Hecho!"
                    status == Status.NO_HECHO -> "No hecho · definitivo"
                    status == Status.SUSTITUIDA -> "Cambiada por otras tareas"
                    else -> null
                }
                AnimatedVisibility(warning != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Pill(
                        warning.orEmpty(),
                        background = when {
                            status == Status.HECHO -> DoneGreen
                            status == Status.NO_HECHO -> MaterialTheme.colorScheme.error
                            status == Status.SUSTITUIDA -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                        color = when {
                            status == Status.HECHO -> Color.White
                            status == Status.NO_HECHO -> MaterialTheme.colorScheme.onError
                            status == Status.SUSTITUIDA -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(KIcons.Delete, contentDescription = "Borrar ${activity.title}", tint = subColor)
                }
            }
            if (category.asksCheck && status != Status.NO_HECHO && status != Status.SUSTITUIDA) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DoneButton(done = status == Status.HECHO) { onToggle(Status.HECHO) }
                    MissButton { onToggle(Status.NO_HECHO) }
                }
            }
        }
    }
}

/** Al marcarlo como hecho se estampa un sello rojo con 済 («terminado») y saltan pétalos. */
@Composable
private fun DoneButton(done: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    // Solo se anima al cambiar, no al aparecer en pantalla lo que ya estaba hecho
    var seen by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done != seen) {
            seen = done
            if (done) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                scale.snapTo(1.8f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
        }
    }
    val press = rememberPress()
    Box(contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .pressScale(press, 0.85f)
                .clip(CircleShape)
                .clickable(press.interaction, LocalIndication.current, onClick = onClick),
        ) {
            if (done) {
                Box(Modifier.scale(scale.value)) { Hanko("済", size = 50.dp, filled = false, rotation = -12f) }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(50.dp).border(2.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Hecho", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                }
            }
        }
        // Los pétalos se salen del botón sin cambiar su tamaño
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
            PetalBurst(trigger = done, play = done, modifier = Modifier.requiredSize(150.dp))
        }
    }
}

@Composable
private fun MissButton(onClick: () -> Unit) {
    val press = rememberPress()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .pressScale(press, 0.85f)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
            .clickable(press.interaction, LocalIndication.current, onClick = onClick),
    ) {
        Text("✕", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
    }
}
