package com.rutina.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.saveable.rememberSaveable
import com.rutina.app.data.RuleLock
import com.rutina.app.data.isEditPass
import com.rutina.app.data.ruleLock
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.rutina.app.data.ChallengeOptions
import com.rutina.app.data.PoseExercise
import com.rutina.app.data.optionsData
import com.rutina.app.data.stagesOf
import com.rutina.app.penalty.Places
import java.io.File
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rutina.app.app
import com.rutina.app.data.Category
import com.rutina.app.data.Challenge
import com.rutina.app.data.PenaltyRule
import com.rutina.app.data.TRIGGER_ANY
import com.rutina.app.data.challengeEnum
import com.rutina.app.data.challengeText
import com.rutina.app.data.durationText
import com.rutina.app.data.joinApps
import com.rutina.app.data.labelList
import com.rutina.app.data.packageList
import com.rutina.app.data.stepsText
import com.rutina.app.data.triggerText
import com.rutina.app.penalty.BlockerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DURATIONS = listOf(30, 60, 120, 180, 360, 720)
private val STEP_OPTIONS = listOf(1_000, 2_000, 3_000, 5_000, 8_000)
private val EXERCISE_IDEAS = listOf("20 sentadillas", "15 flexiones", "1 minuto de plancha", "30 jumping jacks", "20 zancadas")
private const val DEFAULT_TOPICS = "JavaScript, TypeScript, Angular y Vue"
private val GROW_OPTIONS = listOf(0, 15, 30)
private val REP_OPTIONS = listOf(10, 15, 20, 30, 50)
private val ZEN_OPTIONS = listOf(5, 10, 15, 20, 30)
private val WORD_OPTIONS = listOf(30, 40, 60, 80)
private val PLACE_MINUTES = listOf(10, 20, 30, 45, 60)
private val PHOTO_IDEAS = listOf("la cama hecha", "los platos fregados", "el escritorio ordenado", "la mochila del gym preparada", "la ropa doblada")

@Composable
fun PenaltiesScreen() {
    val context = LocalContext.current
    val penalties = context.app().penalties
    val scope = rememberCoroutineScope()
    val rules by remember { penalties.observeRules() }.collectAsState(initial = emptyList())
    val blocks by remember { penalties.observeBlocks() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<PenaltyRule?>(null) }
    var lockedInfo by remember { mutableStateOf<Pair<PenaltyRule, RuleLock>?>(null) }

    // Actividades de ayer a mañana, para saber qué reglas están bloqueadas porque ya ha saltado su aviso
    val nearby by remember { context.app().repository.observeRange(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)) }
        .collectAsState(initial = emptyList())
    val passes by remember { penalties.observeEditPasses() }.collectAsState(initial = emptyList())
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    /** Bloqueo de la regla, salvo que ya se haya superado el reto para cambiarla. */
    val commitments = context.app().repository.commitments
    val committedWeeks by commitments.weeks.collectAsState()
    // Solo bloquean las actividades que ya pueden castigar (las de días dentro del compromiso)
    val punishable = remember(nearby, committedWeeks, now) { nearby.filter { commitments.isCommittedDay(it.date) } }
    fun lockOf(rule: PenaltyRule): RuleLock? = ruleLock(rule, punishable, now)
        ?.takeIf { lock -> passes.none { it.ruleId == rule.id && it.until >= lock.until } }

    var blockerOk by remember { mutableStateOf(true) }
    var stepsOk by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        blockerOk = BlockerService.isEnabled(context)
        stepsOk = BlockerService.hasStepPermission(context)
        onPauseOrDispose { }
    }
    val stepPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { stepsOk = it }

    val active = blocks.filter { it.isActive() }.groupBy { it.groupKey }.values
    // Pestañas: lo que está en marcha y las reglas, para no tenerlo todo en una lista larga
    var tab by rememberSaveable { mutableIntStateOf(if (active.isNotEmpty()) 0 else 1) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Castigos", "Si no cumples, se bloquean apps", kanji = "罰")

        if (!blockerOk) BlockerSetupCard()
        if (!stepsOk && rules.any { Challenge.PASOS in stagesOf(it.challengeEnum, it.optionsData) }) {
            CuteCard(Modifier.fillMaxWidth()) {
                Text("Para los retos de caminar hace falta contar tus pasos.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { stepPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
                    Text("Permitir contar pasos", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        SegmentedTabs(listOf("En marcha (${active.size})", "Mis castigos"), tab, { tab = it })

        if (tab == 0) {
            if (active.isEmpty()) {
                CuteCard(Modifier.fillMaxWidth()) {
                    Text("Nada bloqueado ahora", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Cuando no cumplas algo, aquí verás el castigo y el reto para librarte antes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            active.forEach { BlockGroupCard(it) }
            RewardsCard()
        } else {
        CuteCard(Modifier.fillMaxWidth()) {
            Text("Tus castigos", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Solo en semanas fijadas, desde el día en que empieza tu compromiso: si marcas una actividad como «no hecho», " +
                    "o no contestas al aviso en 2 horas, se bloquean las apps que elijas. " +
                    "Para desbloquearlas antes tendrás que superar el reto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (rules.isEmpty()) {
                Text("Aún no tienes ninguno", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }
            rules.forEach { rule ->
                val lock = lockOf(rule)
                RuleRow(
                    rule,
                    lock = lock,
                    onClick = { if (lock != null) lockedInfo = rule to lock else editing = rule },
                    onToggle = { on ->
                        // Volver a activarla siempre se puede; quitarla con el aviso ya saltado, no
                        if (lock != null && !on) lockedInfo = rule to lock else scope.launch { penalties.saveRule(rule.copy(enabled = on)) }
                    },
                )
            }
            GradientButton("Añadir castigo", onClick = {
                editing = PenaltyRule(trigger = TRIGGER_ANY, packages = "", appLabels = "", minutes = 60)
            })
        }

        Text(
            "Para saltarte un castigo tendrías que quitarle a Kokki el permiso de accesibilidad… pero eso sería hacerte trampas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        }
        Spacer(Modifier.height(16.dp))
    }

    lockedInfo?.let { (rule, lock) ->
        val pending = blocks.any { it.isEditPass && it.ruleId == rule.id && it.isActive() }
        val hour = Instant.ofEpochMilli(lock.until).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        AlertDialog(
            onDismissRequest = { lockedInfo = null },
            title = { Text("Este castigo está bloqueado") },
            text = {
                Text(
                    "Ya ha saltado el aviso de «${lock.activity.title}», así que ahora no puedes cambiarlo, desactivarlo ni borrarlo. " +
                        "Se libera cuando marques la actividad o a las $hour.\n\n" +
                        if (pending) "Ya tienes el reto para cambiarlo en la pestaña «En marcha»."
                        else "Si de verdad necesitas cambiarlo antes, tendrás que superar un reto.",
                )
            },
            confirmButton = {
                if (!pending) {
                    TextButton(onClick = {
                        scope.launch { penalties.requestEditPass(rule, lock) }
                        lockedInfo = null
                        tab = 0
                    }) { Text("Hacer el reto") }
                }
            },
            dismissButton = { TextButton(onClick = { lockedInfo = null }) { Text("Vale") } },
        )
    }

    editing?.let { rule ->
        RuleEditor(
            initial = rule,
            onDismiss = { editing = null },
            onSave = { scope.launch { penalties.saveRule(it) }; editing = null },
            onDelete = if (rule.id != 0L) ({ scope.launch { penalties.deleteRule(rule) }; editing = null }) else null,
        )
    }
}

@Composable
private fun RuleRow(rule: PenaltyRule, lock: RuleLock?, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Si no cumplo ${rule.triggerText}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                AppLogos(rule.packageList, size = 22.dp)
                Spacer(Modifier.width(8.dp))
                Text(durationText(rule.minutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(rule.labelList.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            if (lock != null) {
                val hour = Instant.ofEpochMilli(lock.until).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
                Pill(
                    "Bloqueado hasta las $hour",
                    background = MaterialTheme.colorScheme.errorContainer,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            if (rule.challengeEnum != Challenge.NINGUNO) {
                Text(
                    "Antes si consigo ${rule.challengeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}

/** Pasos del asistente para crear un castigo. Los de ajustes del reto solo salen si se ha elegido reto. */
private enum class RuleStep(val title: String, val explanation: String) {
    TRIGGER(
        "¿Qué tienes que cumplir?",
        "El castigo salta cuando marques como «no hecha» una actividad de este tipo, o cuando no contestes al aviso en 2 horas.",
    ),
    APPS(
        "¿Qué apps se bloquean?",
        "Elige las apps que más te distraen. Mientras dure el castigo, al abrirlas volverás a la pantalla de inicio.",
    ),
    DURATION(
        "¿Durante cuánto tiempo?",
        "Las apps estarán bloqueadas este tiempo. Solo corre con el móvil encendido: apagarlo no lo acorta.",
    ),
    CHALLENGE(
        "¿Cómo puedes librarte antes?",
        "Elige un reto. Si lo superas, las apps se desbloquean sin esperar. «Sin internet» funciona siempre; «con IA» necesita la clave de Ajustes.",
    ),
    SETTINGS(
        "Ajusta el reto",
        "Decide cuánto te va a costar librarte.",
    ),
    COMBO(
        "¿Quieres un segundo reto?",
        "Opcional. Si eliges otro, tendrás que superar los dos, uno detrás de otro, para desbloquear las apps.",
    ),
    GROW(
        "¿Y si fallas un intento?",
        "Opcional. Puedes hacer que cada intento fallido sume tiempo de castigo, para no responder a lo loco.",
    ),
    SUMMARY(
        "Repasa tu castigo",
        "Así queda. Toca «Cambiar» en lo que quieras modificar.",
    ),
}

/** Asistente a pantalla completa, un paso por pantalla, con el resumen de lo elegido siempre a la vista. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(initial: PenaltyRule, onDismiss: () -> Unit, onSave: (PenaltyRule) -> Unit, onDelete: (() -> Unit)?) {
    val isNew = initial.id == 0L
    var trigger by remember { mutableStateOf(initial.trigger) }
    val apps = remember { mutableStateListOf<Pair<String, String>>().apply { addAll(initial.packageList.zip(initial.labelList)) } }
    var minutes by remember { mutableIntStateOf(initial.minutes) }
    var challenge by remember { mutableStateOf(initial.challengeEnum) }
    var steps by remember { mutableIntStateOf(initial.steps.takeIf { it > 0 } ?: 3_000) }
    var topics by remember { mutableStateOf(initial.topics.ifBlank { DEFAULT_TOPICS }) }
    var exercise by remember { mutableStateOf(initial.exercise.ifBlank { EXERCISE_IDEAS.first() }) }
    var options by remember { mutableStateOf(initial.optionsData) }
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    // Al editar uno que ya existe se empieza por el resumen
    var step by remember { mutableStateOf(if (isNew) RuleStep.TRIGGER else RuleStep.SUMMARY) }
    var forward by remember { mutableStateOf(true) }

    val visibleSteps = RuleStep.entries.filter {
        challenge != Challenge.NINGUNO || it !in setOf(RuleStep.SETTINGS, RuleStep.COMBO, RuleStep.GROW)
    }
    val index = visibleSteps.indexOf(step).coerceAtLeast(0)

    fun draft(): PenaltyRule {
        val finalOptions = if (challenge == Challenge.NINGUNO) options.copy(challenge2 = Challenge.NINGUNO, growMinutes = 0) else options
        val uses = listOf(challenge, finalOptions.challenge2)
        return initial.copy(
            trigger = trigger,
            packages = joinApps(apps.map { it.first }),
            appLabels = joinApps(apps.map { it.second }),
            minutes = minutes,
            challenge = challenge.name,
            steps = if (Challenge.PASOS in uses || Challenge.RECUPERAR in uses) steps else 0,
            topics = if (Challenge.PREGUNTA in uses) topics.trim() else "",
            exercise = if (Challenge.EJERCICIO in uses) exercise.trim() else "",
            options = finalOptions.toJson(),
        )
    }

    fun problemsOf(c: Challenge): List<String> = buildList {
        if (c == Challenge.PREGUNTA && topics.isBlank()) add("Escribe los temas de la pregunta")
        if (c == Challenge.EJERCICIO && exercise.isBlank()) add("Escribe el ejercicio del vídeo")
        if (c == Challenge.FOTO && options.photoTask.isBlank()) add("Escribe de qué será la foto")
        if (c == Challenge.QR && options.qrCode.isBlank()) add("Crea o escanea tu código")
        val needsPlace = c == Challenge.LUGAR || (c == Challenge.RECUPERAR && options.recoverCheck == Challenge.LUGAR)
        if (needsPlace && !options.hasPlace) add("Elige el sitio")
    }

    /** Lo que falta para poder pasar del paso actual. */
    val blocking: List<String> = when (step) {
        RuleStep.APPS -> if (apps.isEmpty()) listOf("Elige al menos una app") else emptyList()
        RuleStep.SETTINGS -> problemsOf(challenge)
        RuleStep.COMBO -> problemsOf(options.challenge2)
        else -> emptyList()
    }

    fun go(to: RuleStep) {
        forward = visibleSteps.indexOf(to) >= index
        step = to
    }

    fun next() {
        if (blocking.isNotEmpty()) return
        visibleSteps.getOrNull(index + 1)?.let { go(it) }
    }

    fun back() {
        if (index == 0) onDismiss() else go(visibleSteps[index - 1])
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        AppBackground {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                // Cabecera: cerrar, título y avance
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
                    Text(
                        if (isNew) "Nuevo castigo" else "Editar castigo",
                        fontFamily = Script,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Paso ${index + 1} de ${visibleSteps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                StepDots(visibleSteps.size, index, Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(10.dp))
                if (step != RuleStep.SUMMARY) {
                    MiniSummary(draft(), apps.isNotEmpty(), Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(6.dp))
                }

                AnimatedContent(
                    step,
                    transitionSpec = {
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(260)) { it / 3 * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { -it / 3 * dir } + fadeOut(tween(150)))
                    },
                    modifier = Modifier.weight(1f),
                    label = "ruleStep",
                ) { current ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(current.title, fontFamily = Script, fontSize = 22.sp, lineHeight = 30.sp)
                        Text(current.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        when (current) {
                            RuleStep.TRIGGER -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Choice("Cualquier actividad", trigger == TRIGGER_ANY) { trigger = TRIGGER_ANY }
                                Category.entries.filter { it.asksCheck }.forEach { c ->
                                    Choice(c.label, trigger == c.name, icon = c.icon) { trigger = c.name }
                                }
                            }
                            RuleStep.APPS -> {
                                if (apps.isNotEmpty()) {
                                    CuteCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
                                        apps.toList().forEach { app ->
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                                AppLogo(app.first, 36.dp)
                                                Spacer(Modifier.width(12.dp))
                                                Text(app.second, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { apps.remove(app) }) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Quitar ${app.second}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                                GradientButton(if (apps.isEmpty()) "Elegir apps" else "Añadir o quitar apps", onClick = { picking = true })
                            }
                            RuleStep.DURATION -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DURATIONS.forEach { m -> Choice(durationText(m), minutes == m) { minutes = m } }
                            }
                            RuleStep.CHALLENGE -> ChallengeGrid(selected = challenge, exclude = emptySet()) {
                                challenge = it
                                if (options.challenge2 == it) options = options.copy(challenge2 = Challenge.NINGUNO)
                            }
                            RuleStep.SETTINGS -> {
                                IconText(challenge.icon, challenge.label, MaterialTheme.typography.titleMedium)
                                ChallengeSettings(challenge, steps, { steps = it }, topics, { topics = it }, exercise, { exercise = it }, options) { options = it }
                            }
                            RuleStep.COMBO -> {
                                ChallengeGrid(selected = options.challenge2, exclude = setOf(challenge), noneLabel = "No, solo uno") {
                                    options = options.copy(challenge2 = it)
                                }
                                if (options.challenge2 != Challenge.NINGUNO) {
                                    IconText(options.challenge2.icon, "Ajusta el segundo reto", MaterialTheme.typography.titleMedium)
                                    ChallengeSettings(options.challenge2, steps, { steps = it }, topics, { topics = it }, exercise, { exercise = it }, options) {
                                        options = it
                                    }
                                }
                            }
                            RuleStep.GROW -> {
                                Text(
                                    "Cuenta como fallo una respuesta, foto o vídeo que la IA no da por buena, o equivocarte en el test de lectura.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    GROW_OPTIONS.forEach { g ->
                                        Choice(if (g == 0) "No pasa nada" else "+${durationText(g)} por fallo", options.growMinutes == g) {
                                            options = options.copy(growMinutes = g)
                                        }
                                    }
                                }
                                if (options.growMinutes > 0) {
                                    Text(
                                        "Como mucho se suma otro tanto como el castigo inicial (${durationText(minutes)}).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            RuleStep.SUMMARY -> {
                                SummaryCard(draft(), apps.map { it.second }, onChange = { go(it) })
                                if (onDelete != null) {
                                    TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                        Text("Borrar castigo", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        if (blocking.isNotEmpty()) {
                            Text("Para seguir: " + blocking.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // Botones de navegación
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    TextButton(onClick = { back() }) { Text(if (index == 0) "Cancelar" else "← Atrás") }
                    Spacer(Modifier.weight(1f))
                    if (step == RuleStep.SUMMARY) {
                        GradientButton(if (isNew) "Guardar castigo" else "Guardar cambios", onClick = { confirming = true })
                    } else {
                        GradientButton("Siguiente →", enabled = blocking.isEmpty(), onClick = { next() })
                    }
                }
            }
        }

        if (confirming) {
            val rule = draft()
            AlertDialog(
                onDismissRequest = { confirming = false },
                title = { Text(if (isNew) "¿Creamos este castigo?" else "¿Guardamos los cambios?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(ruleSentence(rule), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Una vez activo no se puede saltar: solo cumpliendo el reto o esperando. ¿Lo dejas así o quieres modificar algo?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirming = false
                        onSave(rule)
                    }) { Text(if (isNew) "Sí, crearlo" else "Sí, guardar") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        confirming = false
                        go(RuleStep.SUMMARY)
                    }) { Text("Modificar algo") }
                },
            )
        }
    }

    if (picking) {
        AppPicker(
            selected = apps.map { it.first }.toSet(),
            onDismiss = { picking = false },
            onDone = { list ->
                apps.clear()
                apps.addAll(list)
                picking = false
            },
        )
    }
}

/** Barra de avance: un tramo por paso. */
@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.fillMaxWidth()) {
        repeat(count) { i ->
            val color by animateColorAsState(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "stepDot",
            )
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

/** El castigo en una frase, para el resumen y la confirmación. */
private fun ruleSentence(rule: PenaltyRule): String {
    val apps = rule.labelList.ifEmpty { listOf("(sin apps)") }
    val appsText = if (apps.size == 1) apps.first() else apps.dropLast(1).joinToString(", ") + " y " + apps.last()
    val trigger = if (rule.trigger == TRIGGER_ANY) "cualquier actividad" else Category.from(rule.trigger).label
    val base = "Si no cumplo $trigger, se bloquea${if (apps.size > 1) "n" else ""} $appsText durante ${durationText(rule.minutes)}"
    return if (rule.challengeEnum == Challenge.NINGUNO) "$base, sin forma de librarme antes." else "$base, salvo que antes consiga ${rule.challengeText}."
}

/** Resumen corto que acompaña a cada paso. */
@Composable
private fun MiniSummary(rule: PenaltyRule, hasApps: Boolean, modifier: Modifier = Modifier) {
    val parts = buildList {
        add(if (rule.trigger == TRIGGER_ANY) "Cualquiera" else Category.from(rule.trigger).label)
        if (hasApps) add(rule.labelList.joinToString(", "))
        add(durationText(rule.minutes))
        val challenges = listOf(rule.challengeEnum, rule.optionsData.challenge2).filter { it != Challenge.NINGUNO }
        if (challenges.isNotEmpty()) add(challenges.joinToString(" + ") { it.label })
    }
    Text(
        "Tu castigo: " + parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 2,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Resumen final: cada parte con su botón para volver a ese paso. */
@Composable
private fun SummaryCard(rule: PenaltyRule, appLabels: List<String>, onChange: (RuleStep) -> Unit) {
    val options = rule.optionsData
    CuteCard(Modifier.fillMaxWidth()) {
        Text(ruleSentence(rule), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        SummaryRow(if (rule.trigger == TRIGGER_ANY) KIcons.Ai else Category.from(rule.trigger).icon, "Si no cumplo", if (rule.trigger == TRIGGER_ANY) "Cualquier actividad" else Category.from(rule.trigger).label) {
            onChange(RuleStep.TRIGGER)
        }
        SummaryRow(KIcons.Lock, "Se bloquean", appLabels.joinToString(", ").ifBlank { "—" }) { onChange(RuleStep.APPS) }
        SummaryRow(KIcons.Timer, "Durante", durationText(rule.minutes)) { onChange(RuleStep.DURATION) }
        SummaryRow(rule.challengeEnum.icon, "Reto", challengeText(rule.challengeEnum, rule.steps, rule.topics, rule.exercise, options)) {
            onChange(if (rule.challengeEnum == Challenge.NINGUNO) RuleStep.CHALLENGE else RuleStep.SETTINGS)
        }
        if (rule.challengeEnum != Challenge.NINGUNO) {
            SummaryRow(
                if (options.challenge2 == Challenge.NINGUNO) KIcons.Swap else options.challenge2.icon,
                "Segundo reto",
                if (options.challenge2 == Challenge.NINGUNO) "No" else challengeText(options.challenge2, rule.steps, rule.topics, rule.exercise, options),
            ) { onChange(RuleStep.COMBO) }
            SummaryRow(KIcons.Grow, "Si fallo un intento", if (options.growMinutes == 0) "No pasa nada" else "+${durationText(options.growMinutes)}") {
                onChange(RuleStep.GROW)
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, size = 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onChange) { Text("Cambiar") }
    }
}

/** Retos en cuadrícula de 3, con una etiqueta que dice si necesitan IA. */
@Composable
private fun ChallengeGrid(selected: Challenge, exclude: Set<Challenge>, noneLabel: String? = null, onSelect: (Challenge) -> Unit) {
    val list = Challenge.entries.filter { it !in exclude }
    list.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            row.forEach { c ->
                val isSelected = c == selected
                val shape = RoundedCornerShape(14.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(104.dp)
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else inkBorder().copy(alpha = 0.35f), shape)
                        .bouncyClick { onSelect(c) }
                        .padding(6.dp),
                ) {
                    Icon(c.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text(
                        if (c == Challenge.NINGUNO && noneLabel != null) noneLabel else c.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        lineHeight = 17.sp,
                    )
                    if (c != Challenge.NINGUNO) {
                        Text(
                            if (c.needsAi) "con IA" else "sin internet",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = if (c.needsAi) MaterialTheme.colorScheme.tertiary else DoneGreen,
                        )
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** Ajustes del reto elegido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeSettings(
    challenge: Challenge,
    steps: Int,
    onSteps: (Int) -> Unit,
    topics: String,
    onTopics: (String) -> Unit,
    exercise: String,
    onExercise: (String) -> Unit,
    options: ChallengeOptions,
    onOptions: (ChallengeOptions) -> Unit,
) {
    @Composable
    fun Info(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable
    fun StepChoices() = FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        STEP_OPTIONS.forEach { s -> Choice("${stepsText(s)} pasos", steps == s) { onSteps(s) } }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (challenge) {
            Challenge.NINGUNO -> Info("Sin reto: las apps se desbloquean solo cuando pase el tiempo.")
            Challenge.PASOS -> StepChoices()
            Challenge.PREGUNTA -> OutlinedTextField(
                value = topics,
                onValueChange = onTopics,
                label = { Text("Temas de las preguntas") },
                supportingText = { Text("La IA te hará una pregunta de entrevista y tendrás que contestarla en voz alta.") },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Challenge.EJERCICIO -> {
                OutlinedTextField(
                    value = exercise,
                    onValueChange = onExercise,
                    label = { Text("Ejercicio que tendrás que grabar") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXERCISE_IDEAS.forEach { e -> Choice(e, exercise == e) { onExercise(e) } }
                }
                Info("Revisar vídeos solo lo hace Gemini. Sin su clave, mejor «Ejercicio contado en directo».")
            }
            Challenge.LUGAR -> PlaceSettings(options, onOptions)
            Challenge.RECUPERAR -> {
                Info("Eliges cuándo recuperarla (ahora, en unas horas o mañana), se apunta en tu semana y a esa hora se comprueba que la haces:")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Choice("Con pasos", options.recoverCheck == Challenge.PASOS) { onOptions(options.copy(recoverCheck = Challenge.PASOS)) }
                    Choice("Estando en el sitio", options.recoverCheck == Challenge.LUGAR) { onOptions(options.copy(recoverCheck = Challenge.LUGAR)) }
                }
                if (options.recoverCheck == Challenge.LUGAR) PlaceSettings(options, onOptions) else StepChoices()
            }
            Challenge.CONTEO -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PoseExercise.entries.forEach { e ->
                        Choice(e.label, options.poseExercise == e, icon = e.icon) { onOptions(options.copy(poseExercise = e)) }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    REP_OPTIONS.forEach { r -> Choice("$r", options.reps == r) { onOptions(options.copy(reps = r)) } }
                }
                Info("La cámara cuenta las repeticiones en directo, dentro del móvil. No se graba ni se envía nada.")
            }
            Challenge.QR -> QrSettings(options, onOptions)
            Challenge.ZEN -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ZEN_OPTIONS.forEach { m -> Choice(durationText(m), options.zenMinutes == m) { onOptions(options.copy(zenMinutes = m)) } }
            }
            Challenge.REFLEXION -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WORD_OPTIONS.forEach { w -> Choice("$w palabras", options.reflectionWords == w) { onOptions(options.copy(reflectionWords = w)) } }
                }
                Info("Escribes en papel por qué no lo hiciste y qué harás distinto, y le haces una foto. El móvil lee la letra sin internet.")
            }
            Challenge.INGLES -> Info("Te sale un texto corto en inglés y tienes que leerlo en voz alta. Tiene que entenderse al menos el 80 %.")
            Challenge.CODIGO -> Info("Una función pequeña de JavaScript, escrita a mano (pegar no funciona). La comprueban unos tests dentro del móvil.")
            Challenge.LECTURA -> Info("Un texto corto sobre JavaScript, TypeScript, Angular o Vue y 3 preguntas. Hay que acertar las 3.")
            Challenge.FOTO -> {
                OutlinedTextField(
                    value = options.photoTask,
                    onValueChange = { onOptions(options.copy(photoTask = it)) },
                    label = { Text("Foto de…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PHOTO_IDEAS.forEach { p -> Choice(p, options.photoTask == p) { onOptions(options.copy(photoTask = p)) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceSettings(options: ChallengeOptions, onOptions: (ChallengeOptions) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!Places.hasPermission(context)) status = "Sin permiso de ubicación no puedo guardar dónde estás."
    }

    OutlinedTextField(
        value = options.placeName,
        onValueChange = { onOptions(options.copy(placeName = it)) },
        label = { Text("Nombre del sitio") },
        placeholder = { Text("Ej.: el gimnasio") },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    if (options.hasPlace) {
        Pill(
            "Guardado: %.5f, %.5f".format(options.placeLat, options.placeLng),
            background = DoneGreen.copy(alpha = 0.2f),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    GradientButton(if (locating) "Buscando…" else "Usar dónde estoy ahora", enabled = !locating, onClick = {
        if (!Places.hasPermission(context)) {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return@GradientButton
        }
        locating = true
        scope.launch {
            val location = Places.current(context)
            locating = false
            if (location == null) {
                status = "No consigo tu ubicación. Activa la ubicación del móvil y prueba otra vez."
            } else {
                onOptions(options.copy(placeLat = location.latitude, placeLng = location.longitude))
                status = "✓ Sitio guardado"
            }
        }
    })
    Text("…o pega un enlace de Google Maps (Compartir → Copiar enlace):", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            placeholder = { Text("https://maps.app.goo.gl/…") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = link.isNotBlank(), onClick = {
            scope.launch {
                status = "Buscando el sitio…"
                val coords = Places.fromLink(link)
                if (coords == null) {
                    status = "No encuentro las coordenadas en ese enlace."
                } else {
                    onOptions(options.copy(placeLat = coords.first, placeLng = coords.second))
                    status = "✓ Sitio guardado"
                    link = ""
                }
            }
        }) { Text("Usar") }
    }
    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    Text("Tiempo que tienes que estar allí", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PLACE_MINUTES.forEach { m -> Choice(durationText(m), options.placeMinutes == m) { onOptions(options.copy(placeMinutes = m)) } }
    }
}

@Composable
private fun QrSettings(options: ChallengeOptions, onOptions: (ChallengeOptions) -> Unit) {
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var showing by remember { mutableStateOf(false) }
    val openScanner = rememberCameraGate { scanning = true }

    Text(
        "Imprime un QR (o usa cualquier código de barras, p. ej. el de un bote) y pégalo donde te cueste ir: " +
            "en la bolsa del gym, en la nevera, junto a la esterilla…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (options.qrCode.isNotBlank()) {
        Pill("Código guardado", background = DoneGreen.copy(alpha = 0.2f), color = MaterialTheme.colorScheme.onSurface)
    }
    GradientButton("Crear mi QR para imprimir", onClick = {
        if (options.qrCode.isBlank()) {
            val code = "RUTINA-" + (1..10).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
            onOptions(options.copy(qrCode = code))
        }
        showing = true
    })
    TextButton(onClick = openScanner) { Text("…o escanear un código que ya tenga") }

    if (scanning) {
        CodeScannerScreen("Escanea el código que quieras usar", onDismiss = { scanning = false }) {
            onOptions(options.copy(qrCode = it))
            scanning = false
        }
    }
    if (showing && options.qrCode.isNotBlank()) {
        val bitmap = remember(options.qrCode) { qrBitmap(options.qrCode) }
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Tu código") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bitmap.asImageBitmap(), contentDescription = "Código QR", modifier = Modifier.size(240.dp))
                    Text("Compártelo para imprimirlo o guárdalo. Sin él no podrás superar este reto.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { shareQr(context, bitmap) }) { Text("Compartir / imprimir") } },
            dismissButton = { TextButton(onClick = { showing = false }) { Text("Listo") } },
        )
    }
}

private fun shareQr(context: Context, bitmap: Bitmap) {
    val file = File(File(context.cacheDir, "retos").apply { mkdirs() }, "mi-qr-rutina.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val share = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(share, "Compartir tu QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private data class InstalledApp(val packageName: String, val label: String, val icon: ImageBitmap?)

/** Apps que se pueden bloquear: las que salen en el cajón de apps, salvo Kokki y los ajustes. */
private suspend fun loadApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val excluded = setOf(context.packageName, "com.android.settings")
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { it.first !in excluded }
        .sortedBy { it.second.lowercase() }
        .map { (pkg, label) ->
            val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            InstalledApp(pkg, label, icon)
        }
}

@Composable
private fun AppPicker(selected: Set<String>, onDismiss: () -> Unit, onDone: (List<Pair<String, String>>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) { value = loadApps(context) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    var search by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Qué apps se bloquean?") },
        text = {
            Column {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar…") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                val list = apps
                if (list == null) {
                    Text("Cargando apps…")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(list.filter { it.label.contains(search.trim(), ignoreCase = true) }, key = { it.packageName }) { app ->
                            val checked = app.packageName in chosen
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (checked) chosen.remove(app.packageName) else chosen.add(app.packageName)
                                }.padding(vertical = 4.dp),
                            ) {
                                if (app.icon != null) Image(app.icon, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(app.label, modifier = Modifier.weight(1f))
                                Checkbox(checked = checked, onCheckedChange = null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val all = apps.orEmpty()
                onDone(chosen.mapNotNull { pkg -> all.firstOrNull { it.packageName == pkg }?.let { pkg to it.label } })
            }) { Text("Listo (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Choice(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
