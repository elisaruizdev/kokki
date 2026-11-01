package com.rutina.app.ui

import com.rutina.app.app
import androidx.compose.runtime.collectAsState
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rutina.app.R
import com.rutina.app.ai.PlanProposal
import com.rutina.app.data.DAY_NAMES
import com.rutina.app.data.findOverlaps
import com.rutina.app.data.minutesToText
import com.rutina.app.data.weekRangeText

private val SUGGESTIONS = listOf(
    com.rutina.app.data.Category.TRABAJO.icon to "Trabajo miércoles y jueves de 9:00 a 17:00 y tardo 40 minutos en ir y otros 40 en volver",
    KIcons.Calendar to "Reparte el deporte en 4 días y las clases de inglés en 2 días por la tarde",
    KIcons.Chart to "¿Es viable mi semana tal y como está?",
)

@Composable
fun ChatScreen(onOpenSettings: () -> Unit, vm: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val targetNextWeek by vm.targetNextWeek.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(draft) {
        draft?.let {
            input = it
            vm.consumeDraft()
        }
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) input = "$input $spoken".trim()
        }
    }

    LaunchedEffect(messages.size, loading) {
        val count = 1 + messages.size + (if (loading) 1 else 0)
        listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ScreenTitle("Tu asistente", "Organizamos tu semana", Modifier.weight(1f), kanji = "話")
                        if (messages.isNotEmpty()) TextButton(onClick = vm::clear) { Text("Nueva charla") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekToggle("Esta semana", selected = !targetNextWeek) { vm.setTargetNextWeek(false) }
                        WeekToggle("Semana siguiente", selected = targetNextWeek) { vm.setTargetNextWeek(true) }
                    }
                    if (messages.isEmpty()) Intro(vm.hasApiKey(), vm.targetMonday().let(::weekRangeText), onOpenSettings) { input = it }
                }
            }
            items(messages, key = { it.id }) { message ->
                Box(Modifier.animateItem()) { MessageBubble(message, onApply = { vm.apply(message) }) }
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(KIcons.Ai, MaterialTheme.colorScheme.primaryContainer, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Pensando tu plan…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.5.dp, inkBorder(), CircleShape)
                    .bouncyClick {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Qué quieres planificar?")
                        try {
                            speech.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "Este móvil no tiene reconocimiento de voz", Toast.LENGTH_SHORT).show()
                        }
                    },
            ) {
                Icon(painterResource(R.drawable.ic_mic), contentDescription = "Hablar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Escribe o pulsa el micro…") },
                maxLines = 4,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.weight(1f),
            )
            GradientCircle(
                size = 54.dp,
                enabled = input.isNotBlank() && !loading,
                onClick = {
                    vm.send(input)
                    input = ""
                },
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun WeekToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(LocalPastels.current.accent)
                else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .border(1.dp, inkBorder(), shape),
            )
            .bouncyClick(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun Intro(hasKey: Boolean, weekText: String, onOpenSettings: () -> Unit, onSuggestion: (String) -> Unit) {
    if (!hasKey) {
        CuteCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
            Text("Falta tu clave gratuita de IA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Es gratis y se crea en un minuto.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onOpenSettings) { Text("Ir a Ajustes →") }
        }
    }
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(KIcons.Ai, MaterialTheme.colorScheme.primaryContainer, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "¡Hola! Cuéntame tu semana: horario de trabajo, trayectos, cuántos días quieres entrenar, clases… " +
                    "Te propongo un plan, te digo si es viable y, si te gusta, lo guardo con sus recordatorios.\n\n" +
                    "Planificando la semana del $weekText.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Text("Ideas para empezar", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    SUGGESTIONS.forEach { (icon, suggestion) ->
        val shape = RoundedCornerShape(18.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .border(1.dp, inkBorder(), shape)
                .bouncyClick { onSuggestion(suggestion) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text(suggestion, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, onApply: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.fromUser) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text(
                    message.text,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                        .background(LocalPastels.current.accent)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                IconBadge(if (message.isError) KIcons.Warning else KIcons.Ai, MaterialTheme.colorScheme.primaryContainer, size = 40.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp))
                        .background(if (message.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            inkBorder().copy(alpha = 0.5f),
                            RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 22.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        message.proposal?.let { proposal ->
            ProposalCard(
                proposal = proposal,
                weekText = message.targetMonday?.let(::weekRangeText).orEmpty(),
                applied = message.applied,
                monday = message.targetMonday,
                onApply = onApply,
            )
        }
    }
}

@Composable
private fun ProposalCard(proposal: PlanProposal, weekText: String, applied: Boolean, monday: java.time.LocalDate?, onApply: () -> Unit) {
    val commitments = LocalContext.current.app().repository.commitments
    val committed = commitments.weeks.collectAsState().value.let { monday != null && commitments.isCommitted(monday) }
    val overlaps = findOverlaps(proposal.items, { it.dayIndex.toLong() }, { it.startMinute }, { it.endMinute })
    val viable = proposal.viable && overlaps.isEmpty()

    CuteCard(Modifier.fillMaxWidth()) {
        Pill(
            if (viable) "✓ Plan viable" else "Plan con problemas",
            background = if (viable) DoneGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer,
            color = if (viable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(6.dp))
        proposal.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        overlaps.forEach { (a, b) ->
            Text(
                "• Se solapan «${a.title}» y «${b.title}» el ${DAY_NAMES[a.dayIndex].lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        proposal.items.groupBy { it.dayIndex }.toSortedMap().forEach { (day, items) ->
            Text(
                DAY_NAMES[day],
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
            items.sortedBy { it.startMinute }.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Icon(item.category.icon, contentDescription = null, tint = categoryTint(item.category), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${minutesToText(item.startMinute)}–${minutesToText(item.endMinute)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(item.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GradientButton(
            if (applied) "Guardado" else "Guardar en la semana del $weekText",
            onClick = onApply,
            enabled = !applied,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (committed) "Esa semana está fijada: se añaden las actividades nuevas, sin quitar ninguna."
            else "Sustituye lo pendiente de esa semana. Lo que ya hayas marcado y los días pasados se conservan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
