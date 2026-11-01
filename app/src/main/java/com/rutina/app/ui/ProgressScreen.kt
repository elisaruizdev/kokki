package com.rutina.app.ui

import com.rutina.app.app
import com.rutina.app.data.categoryEnum
import com.rutina.app.data.statusEnum
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rutina.app.data.CategoryStat
import com.rutina.app.data.DAY_NAMES
import com.rutina.app.data.ProgressStats
import com.rutina.app.data.Reflection
import com.rutina.app.data.SPANISH
import com.rutina.app.data.WeekStat
import com.rutina.app.data.weekRangeText
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(onAskAssistant: (String) -> Unit, vm: ProgressViewModel = viewModel()) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val reflections by vm.reflections.collectAsStateWithLifecycle()
    val thisWeek = reflections.firstOrNull { it.weekStart == vm.currentMonday.toEpochDay() }
    val thisWeekActivities by vm.thisWeek.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Tu progreso", "Últimas ${ProgressViewModel.WEEKS} semanas", kanji = "歩")

        var tab by rememberSaveable { mutableIntStateOf(0) }
        SegmentedTabs(listOf("Resumen", "Detalle", "Diario"), tab, { tab = it })

        val current = stats
        val previous = reflections.filter { it.weekStart != vm.currentMonday.toEpochDay() }
        when (tab) {
            0 -> {
                ThisWeekCard(thisWeekActivities)
                when {
                    // Cargando: no se muestra nada para no enseñar un estado vacío falso
                    current == null -> Unit
                    current.overallPercent == null -> NothingYetCard()
                    else -> {
                        SummaryCard(current)
                        WeeksChart(current.weeks, vm.currentMonday)
                    }
                }
            }
            1 -> when {
                current == null -> Unit
                current.overallPercent == null -> NothingYetCard()
                else -> {
                    HardestCard(current)
                    if (current.categories.isNotEmpty()) CategoriesCard(current.categories)
                }
            }
            else -> {
                ReflectionCard(
                    existing = thisWeek,
                    onSave = vm::saveReflection,
                    onAskAssistant = { text ->
                        onAskAssistant("Lo más difícil esta semana está siendo: $text. ¿Cómo ajustamos mi plan para que me resulte más fácil?")
                    },
                )
                if (previous.isNotEmpty()) ReflectionHistory(previous)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Esta semana en una tarjeta: % cumplido, cuántas van y los comodines. */
@Composable
private fun ThisWeekCard(activities: List<com.rutina.app.data.PlannedActivity>) {
    val trackable = activities.filter { it.categoryEnum.asksCheck && it.statusEnum != com.rutina.app.data.Status.SUSTITUIDA }
    if (trackable.isEmpty()) return
    val done = trackable.count { it.statusEnum == com.rutina.app.data.Status.HECHO }
    val percent = done * 100 / trackable.size
    val bonuses = androidx.compose.ui.platform.LocalContext.current.app().rewards.state.collectAsStateWithLifecycle().value.bonuses
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = done.toFloat() / trackable.size, size = 64.dp, stroke = 8.dp) {
                CountUpText(percent, "%", MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Esta semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("$done de ${trackable.size} hechas", style = MaterialTheme.typography.titleMedium)
                Text(motivation(percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(
                "$bonuses",
                icon = KIcons.Bonus,
                background = if (bonuses > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun NothingYetCard() {
    CuteCard(Modifier.fillMaxWidth()) {
        Icon(KIcons.Chart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text("Aún no hay nada que medir", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cuando marques tus primeras actividades como hechas o no hechas, aquí verás cómo vas semana a semana.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryCard(stats: ProgressStats) {
    CuteCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = (stats.overallPercent ?: 0) / 100f, size = 120.dp, stroke = 15.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CountUpText(stats.overallPercent ?: 0, "%", MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    Text("en total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(motivation(stats.overallPercent), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (stats.streak > 0) {
                        "Racha de ${stats.streak} ${if (stats.streak == 1) "semana" else "semanas"} por encima del ${ProgressStats.GOOD_WEEK_PERCENT}%"
                    } else {
                        "Una semana por encima del ${ProgressStats.GOOD_WEEK_PERCENT}% y empieza tu racha"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                stats.weeks.filter { it.percent != null }.maxByOrNull { it.percent!! }?.let { best ->
                    Text(
                        "Mejor semana: ${weekRangeText(best.monday)} (${best.percent}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Barras verticales con degradado, una por semana. */
@Composable
private fun WeeksChart(weeks: List<WeekStat>, currentMonday: LocalDate) {
    val maxBar = 140.dp
    val pastels = LocalPastels.current
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val fmt = DateTimeFormatter.ofPattern("d MMM", SPANISH)
    CuteCard(Modifier.fillMaxWidth()) {
        Text("Semana a semana", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { index, week ->
                val isCurrent = week.monday == currentMonday
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(
                        week.percent?.let { "$it%" } ?: "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    val target = week.percent?.let { maxBar * (it / 100f) }?.coerceAtLeast(6.dp) ?: 6.dp
                    val height by animateDpAsState(
                        if (grown) target else 6.dp,
                        tween(700, delayMillis = index * 70, easing = FastOutSlowInEasing),
                        label = "bar",
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(
                                if (week.percent == null) {
                                    androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
                                } else {
                                    pastels.accentVertical
                                },
                                alpha = if (isCurrent || week.percent == null) 1f else 0.7f,
                            ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isCurrent) "esta" else week.monday.format(fmt),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else null,
                        fontSize = 12.sp,
                        maxLines = 1,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HardestCard(stats: ProgressStats) {
    val category = stats.hardestCategory
    val day = stats.hardestDay
    if (category == null && day == null) return
    CuteCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("Lo que más te está costando", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.height(6.dp))
        category?.let {
            Text(
                "${it.category.label}: ${it.done} de ${it.total} (${it.percent}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        day?.let {
            Text(
                "Los ${DAY_NAMES[it.dayIndex].lowercase()} se te escapan ${it.missed} de ${it.total}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Text(
            "Sin culpa: es información para ajustar el plan a tu vida real",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun CategoriesCard(categories: List<CategoryStat>) {
    var grown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    CuteCard(Modifier.fillMaxWidth()) {
        Text("Por tipo de actividad", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            categories.forEachIndexed { index, stat ->
                val fill by animateFloatAsState(
                    if (grown) stat.percent / 100f else 0f,
                    tween(800, delayMillis = index * 90, easing = FastOutSlowInEasing),
                    label = "category",
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(stat.category.icon, contentDescription = null, tint = categoryTint(stat.category), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stat.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${stat.done}/${stat.total} · ${stat.percent}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50))
                            .background(categorySoft(stat.category.softColor, stat.category.color)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(fill).height(14.dp).clip(RoundedCornerShape(50))
                                .background(Color(stat.category.color)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReflectionCard(existing: Reflection?, onSave: (String) -> Unit, onAskAssistant: (String) -> Unit) {
    var editing by remember(existing) { mutableStateOf(existing == null) }
    var text by rememberSaveable(existing) { mutableStateOf(existing?.text.orEmpty()) }

    CuteCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text("¿Qué es lo más difícil que llevas?", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            "Esta semana. Lo que sea: horarios, energía, ganas… Tu asistente lo tendrá en cuenta al planificar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(10.dp))
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ej.: levantarme pronto para ir al gym después de trabajar…") },
                minLines = 2,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                "Guardar",
                enabled = text.isNotBlank(),
                onClick = { onSave(text); editing = false },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "«${existing?.text}»",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(12.dp),
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                "Pedir consejo al asistente",
                onClick = { onAskAssistant(existing?.text.orEmpty()) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { editing = true }) { Text("Editar respuesta") }
        }
    }
}

@Composable
private fun ReflectionHistory(reflections: List<Reflection>) {
    CuteCard(Modifier.fillMaxWidth()) {
        Text("Tu diario de semanas", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            reflections.forEach {
                Column {
                    Text(
                        weekRangeText(LocalDate.ofEpochDay(it.weekStart)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(it.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
