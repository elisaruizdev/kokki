package com.rutina.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.RoutineRepository
import com.rutina.app.data.SUBSTITUTE_IDEAS
import com.rutina.app.data.durationText
import com.rutina.app.data.minutesToText
import java.time.LocalDate
import java.time.LocalDateTime

private class SwapStart(val label: String, val at: (LocalDateTime) -> LocalDateTime)

/** Cuándo empezar las tareas de cambio: ahora, en un rato o a una hora fija de hoy (si aún no ha pasado). */
private fun swapStarts(now: LocalDateTime): List<SwapStart> = buildList {
    add(SwapStart("Ahora") { it })
    add(SwapStart("En 1 h") { it.plusHours(1) })
    add(SwapStart("En 2 h") { it.plusHours(2) })
    listOf(18, 20).filter { now.hour < it }.forEach { h -> add(SwapStart("A las $h:00") { n -> n.withHour(h).withMinute(0) }) }
}

/** Mínimo de tareas para cambiar una actividad: 2, o 3 si duraba más de una hora. */
fun swapMinimum(activity: PlannedActivity) = if (activity.endMinute - activity.startMinute > 60) 3 else 2

/**
 * Cambiar una actividad que no vas a poder hacer por tareas útiles (casa, recados…). Si las haces todas,
 * no hay castigo; si alguna no la haces, salta el castigo de la original.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwapScreen(activity: PlannedActivity, onDismiss: () -> Unit, onSwap: (List<String>, LocalDateTime) -> Unit) {
    val chosen = remember { mutableStateListOf<String>() }
    val ideas = remember { mutableStateListOf<String>().apply { addAll(SUBSTITUTE_IDEAS) } }
    var custom by remember { mutableStateOf("") }
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    // Si la actividad es de un día futuro, las tareas se hacen ese día a su hora
    val activityDay = LocalDate.ofEpochDay(activity.date)
    val starts = remember {
        if (activityDay.isAfter(now.toLocalDate())) {
            listOf(SwapStart("A la misma hora, ese día") { activityDay.atTime(activity.startMinute / 60, activity.startMinute % 60) })
        } else {
            swapStarts(now)
        }
    }
    var start by remember { mutableStateOf(starts.first()) }
    var confirming by remember { mutableStateOf(false) }
    val minimum = swapMinimum(activity)
    val ready = chosen.size >= minimum
    val firstAt = start.at(now)

    SubScreen("Cambiar por otras tareas", onBack = onDismiss) {
        Text(
            "¿No vas a poder hacer «${activity.title}»? Cámbiala por tareas útiles. Si las haces todas, te libras del castigo; " +
                "si alguna no la haces, salta el castigo de «${activity.title}». Lo importante es moverse, no apalancarse",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("1. Elige al menos $minimum (${chosen.size}/$minimum)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ideas.forEach { idea ->
                val selected = idea in chosen
                FilterChip(
                    selected = selected,
                    onClick = { if (selected) chosen.remove(idea) else chosen.add(idea) },
                    label = { Text((if (selected) "✓ " else "") + idea) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it },
                placeholder = { Text("Otra tarea…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            TextButton(enabled = custom.isNotBlank(), onClick = {
                val task = custom.trim()
                if (task !in ideas) ideas.add(task)
                if (task !in chosen) chosen.add(task)
                custom = ""
            }) { Text("Añadir") }
        }

        Text("2. ¿Cuándo las haces?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            starts.forEach { option ->
                FilterChip(
                    selected = option == start,
                    onClick = { start = option },
                    label = { Text(option.label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        if (chosen.isNotEmpty()) {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("Así queda tu plan", style = MaterialTheme.typography.titleSmall)
                val first = firstAt.hour * 60 + firstAt.minute
                chosen.forEachIndexed { i, task ->
                    val at = (first + i * RoutineRepository.SUBSTITUTE_MINUTES).coerceAtMost(23 * 60 + 30)
                    Text("${minutesToText(at)} · $task", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "${durationText(RoutineRepository.SUBSTITUTE_MINUTES)} cada una. Te aviso de cada tarea y luego la marcas como hecha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GradientButton(
                if (ready) "Cambiar por ${chosen.size} tareas" else "Elige ${minimum - chosen.size} más",
                enabled = ready,
                onClick = { confirming = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Hacemos el cambio?") },
            text = {
                Text(
                    "«${activity.title}» se cambia por: ${chosen.joinToString(", ")}. Esto no se puede deshacer, " +
                        "y si no haces alguna, salta el castigo igualmente.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onSwap(chosen.toList(), firstAt)
                }) { Text("Sí, cambiar") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Modificar algo") } },
        )
    }
}
