package com.rutina.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rutina.app.data.Category
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.Status
import com.rutina.app.data.dayName
import com.rutina.app.data.durationText
import com.rutina.app.data.minutesToText
import com.rutina.app.data.mondayOf
import com.rutina.app.data.statusEnum
import java.time.LocalDate

val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 30, 60)

fun reminderLabel(minutes: Int) = if (minutes == 0) "Al empezar" else "$minutes min"

private val QUICK_DURATIONS = listOf(30, 45, 60, 90, 120)
private val DAY_KANJI = listOf("月", "火", "水", "木", "金", "土", "日")

/** Pantalla completa para crear o editar una actividad, por secciones y con vista previa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityEditor(
    initial: PlannedActivity,
    onDismiss: () -> Unit,
    onSave: (PlannedActivity) -> Unit,
    onDelete: (() -> Unit)?,
    /** Actividad de una semana fijada: solo se puede cambiar el nombre, el aviso y la nota. */
    locked: Boolean = false,
) {
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableLongStateOf(initial.date) }
    var start by remember { mutableIntStateOf(initial.startMinute) }
    var end by remember { mutableIntStateOf(initial.endMinute) }
    var category by remember { mutableStateOf(Category.from(initial.category)) }
    var reminder by remember { mutableIntStateOf(initial.reminderMinutes) }
    var note by remember { mutableStateOf(initial.note) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }
    // Tres pasos cortos en vez de un formulario largo: qué, cuándo y avisos
    var page by remember { mutableIntStateOf(0) }

    val weekMonday = remember { mondayOf(LocalDate.ofEpochDay(initial.date)) }
    val valid = end > start
    // Si no se escribe nada, el nombre del tipo sirve de título
    val finalTitle = title.trim().ifBlank { category.label }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AppBackground {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
                    Text(
                        if (initial.id == 0L) "Nueva actividad" else "Editar actividad",
                        fontFamily = Script,
                        fontSize = 22.sp,
                        modifier = Modifier.weight(1f),
                    )
                    GradientButton(
                        "Guardar",
                        enabled = valid,
                        onClick = {
                            onSave(
                                initial.copy(
                                    title = finalTitle,
                                    date = date,
                                    startMinute = start,
                                    endMinute = end,
                                    category = category.name,
                                    reminderMinutes = reminder,
                                    note = note.trim(),
                                ),
                            )
                        },
                    )
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Preview(category, finalTitle, LocalDate.ofEpochDay(date), start, end)
                    SegmentedTabs(listOf("1 · Qué", "2 · Cuándo", "3 · Avisos"), page, { page = it })
                    if (locked) {
                        Pill(
                            "Semana fijada: puedes cambiar el nombre, el aviso y la nota, pero no el tipo, el día ni la hora",
                            background = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }

                    if (page == 0) {
                    Section("1", "¿Qué vas a hacer?") {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            placeholder = { Text("Ej.: ${category.label}: pierna") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Section("2", "Tipo") {
                        Category.entries.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { c ->
                                    CategoryTile(c, selected = c == category, modifier = Modifier.weight(1f)) { if (!locked) category = c }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }

                    }
                    if (page == 1) {
                    Section("3", "Día") {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (i in 0..6) {
                                val day = weekMonday.plusDays(i.toLong())
                                DayPill(i, day, selected = day.toEpochDay() == date, modifier = Modifier.weight(1f)) {
                                    if (!locked) date = day.toEpochDay()
                                }
                            }
                        }
                    }

                    Section("4", "Horario") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeCard("Empieza", start, Modifier.weight(1f)) { if (!locked) pickingStart = true }
                            TimeCard("Termina", end, Modifier.weight(1f)) { if (!locked) pickingStart = false }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (valid) {
                            Text("Dura ${durationText(end - start)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("La hora de fin debe ser posterior a la de inicio", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            QUICK_DURATIONS.forEach { d ->
                                OptionPill(durationText(d), selected = valid && end - start == d) {
                                    if (!locked) end = (start + d).coerceAtMost(24 * 60 - 1)
                                }
                            }
                        }
                    }

                    }
                    if (page == 2) {
                    Section("5", "Avisarme antes") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            REMINDER_OPTIONS.forEach { m -> OptionPill(reminderLabel(m), selected = reminder == m) { reminder = m } }
                        }
                    }

                    Section("6", "Nota (opcional)") {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = { Text("Ej.: llevar botella de agua") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    }
                    if (initial.statusEnum == Status.NO_HECHO && onDelete == null) {
                        Text(
                            "Marcada como no hecha: no se puede borrar ni cambiar su estado.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onDelete != null) {
                        TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Eliminar actividad", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text("← Atrás") }
                    Spacer(Modifier.weight(1f))
                    if (page < 2) GradientButton("Siguiente →", onClick = { page++ })
                }
            }
        }
    }

    pickingStart?.let { isStart ->
        TimeDialog(
            initialMinutes = if (isStart) start else end,
            onDismiss = { pickingStart = null },
            onConfirm = { minutes ->
                if (isStart) {
                    // Al mover el inicio se mantiene la duración
                    val duration = (end - start).coerceAtLeast(30)
                    start = minutes
                    end = (minutes + duration).coerceAtMost(24 * 60 - 1)
                } else {
                    end = minutes
                }
                pickingStart = null
            },
        )
    }
}

/** Cómo quedará la actividad en la semana. */
@Composable
private fun Preview(category: Category, title: String, day: LocalDate, start: Int, end: Int) {
    CuteCard(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${dayName(day)} · ${minutesToText(start)} – ${minutesToText(end)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(number: String, title: String, content: @Composable () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontFamily = Script,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).width(28.dp).padding(vertical = 3.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}

@Composable
private fun CategoryTile(category: Category, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val emojiScale by animateFloatAsState(
        if (selected) 1.2f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tile",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .aspectRatio(0.9f)
            .clip(shape)
            .background(categorySoft(category.softColor, category.color))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else inkBorder().copy(alpha = 0.3f), shape)
            .bouncyClick(onClick = onClick)
            .padding(4.dp),
    ) {
        Icon(
            category.icon,
            contentDescription = null,
            tint = categoryTint(category),
            modifier = Modifier.size(30.dp).graphicsLayer { scaleX = emojiScale; scaleY = emojiScale },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            category.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DayPill(index: Int, day: LocalDate, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, inkBorder(), shape)
            .bouncyClick(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(DAY_KANJI[index], fontFamily = Fude, fontSize = 19.sp, color = if (selected) textColor else Shu)
        Text(dayName(day).take(2), style = MaterialTheme.typography.labelSmall, color = textColor)
        Text("${day.dayOfMonth}", fontFamily = Script, fontSize = 18.sp, color = textColor)
    }
}

@Composable
private fun TimeCard(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    CuteCard(modifier, onClick = onClick, contentPadding = 14.dp) {
        Text("$label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(minutesToText(minutes), fontFamily = Script, fontSize = 30.sp, lineHeight = 36.sp)
        Text("Toca para cambiar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OptionPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, inkBorder(), shape)
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (initialMinutes / 60) % 24,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
