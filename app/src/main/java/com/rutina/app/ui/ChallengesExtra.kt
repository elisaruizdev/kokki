package com.rutina.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rutina.app.app
import com.rutina.app.data.AppBlock
import com.rutina.app.data.ChallengeBank
import com.rutina.app.data.PlannedActivity
import com.rutina.app.data.RECOVERY_NOTE
import com.rutina.app.data.Verdict
import com.rutina.app.data.durationText
import com.rutina.app.data.minutesToText
import com.rutina.app.penalty.ChallengeRunner
import com.rutina.app.penalty.CodeRunner
import com.rutina.app.penalty.PlaceService
import com.rutina.app.penalty.Places
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.sqrt

// ───────────── 📍 Ir a un sitio ─────────────

@Composable
fun PlaceChallenge(block: AppBlock) {
    val context = LocalContext.current
    val options = block.optionsData
    val status = PlaceService.current.collectAsState().value?.takeIf { it.groupKey == block.groupKey }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (Places.hasPermission(context)) PlaceService.start(context, block)
        else showChallengeError(context, block, "Sin permiso de ubicación no puedo comprobar que estás allí.")
    }
    WindowNote(block)
    ProgressBar(block.progress.toFloat() / options.placeMinutes)
    Spacer(Modifier.height(4.dp))
    Text(
        "${durationText(block.progress)} de ${durationText(options.placeMinutes)} en ${options.placeName.ifBlank { "tu sitio" }}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    if (status != null) {
        Text(status.text, style = MaterialTheme.typography.bodyLarge, color = if (status.inside) DoneGreen else MaterialTheme.colorScheme.onSurface)
        Text(
            "Puedes guardar el móvil: sigo contando mientras esté la notificación",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            "Cuando vayas a salir, pulsa el botón. Mientras estés a menos de ${Places.RADIUS_M.toInt()} m del sitio, el tiempo cuenta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        GradientButton("Empezar a comprobar", enabled = block.inWindow(), onClick = {
            if (Places.hasPermission(context)) {
                PlaceService.start(context, block)
            } else {
                permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        })
    }
    TextButton(onClick = {
        val label = Uri.encode(options.placeName.ifBlank { "Mi sitio" })
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${options.placeLat},${options.placeLng}?q=${options.placeLat},${options.placeLng}($label)"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }) { Text("Ver el sitio en el mapa") }
}

// ───────────── 🔁 Recuperar la actividad ─────────────

private class RecoverSlot(val label: String, val start: () -> LocalDateTime)

private val RECOVER_SLOTS = listOf(
    RecoverSlot("Ahora") { LocalDateTime.now() },
    RecoverSlot("En 1 h") { LocalDateTime.now().plusHours(1) },
    RecoverSlot("En 2 h") { LocalDateTime.now().plusHours(2) },
    RecoverSlot("En 3 h") { LocalDateTime.now().plusHours(3) },
    RecoverSlot("Mañana a esta hora") { LocalDateTime.now().plusDays(1) },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecoverChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    val original by produceState<PlannedActivity?>(null, block.activityId) { value = app.repository.get(block.activityId) }
    val duration = original?.let { (it.endMinute - it.startMinute).coerceAtLeast(15) } ?: 60
    val check = if (block.optionsData.recoverCheck == com.rutina.app.data.Challenge.LUGAR) {
        "estar ${durationText(block.optionsData.placeMinutes)} en ${block.optionsData.placeName.ifBlank { "tu sitio" }}"
    } else {
        "caminar ${com.rutina.app.data.stepsText(block.stepsRequired)} pasos"
    }
    Text(
        "Elige cuándo la recuperas (${durationText(duration)}). Se apunta en tu semana y, a esa hora, tendrás que $check.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RECOVER_SLOTS.forEach { slot ->
            Text(
                slot.label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, inkBorder(), RoundedCornerShape(50))
                    .bouncyClick(enabled = busy == null) {
                        ChallengeRunner.run(context, block, "Apuntando la recuperación…") {
                            // Se redondea a los 5 minutos siguientes; si se pasa de medianoche, termina a las 23:59
                            val raw = slot.start()
                            val start = raw.withSecond(0).withNano(0).plusMinutes(((5 - raw.minute % 5) % 5).toLong())
                            val startMinute = start.hour * 60 + start.minute
                            val endMinute = (startMinute + duration).coerceAtMost(23 * 60 + 59)
                            val activity = PlannedActivity(
                                date = start.toLocalDate().toEpochDay(),
                                startMinute = startMinute,
                                endMinute = endMinute,
                                title = "Recuperar: ${block.reason}",
                                category = original?.category ?: com.rutina.app.data.Category.DEPORTE.name,
                                reminderMinutes = 0,
                                note = RECOVERY_NOTE,
                            )
                            val id = app.repository.save(activity)
                            val zone = ZoneId.systemDefault()
                            val startMs = start.atZone(zone).toInstant().toEpochMilli()
                            val endMs = LocalDate.ofEpochDay(activity.date).atStartOfDay(zone).toInstant().toEpochMilli() +
                                endMinute * 60_000L + GRACE_MS
                            app.penalties.startRecovery(block, id, startMs, endMs)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    original?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            "Era: ${it.title} · ${minutesToText(it.startMinute)}–${minutesToText(it.endMinute)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Margen tras el final de la recuperación para terminar de cumplirla. */
private const val GRACE_MS = 15 * 60_000L

// ───────────── 🔳 QR ─────────────

@Composable
fun QrChallenge(block: AppBlock) {
    val context = LocalContext.current
    val app = context.app()
    var scanning by remember { mutableStateOf(false) }
    val open = rememberCameraGate(onDenied = { showChallengeError(context, block, "Sin permiso de cámara no puedo leer el código.") }) {
        scanning = true
    }
    Text(
        "Ve hasta donde pegaste tu código y escanéalo con la cámara.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    GradientButton("Escanear mi código", onClick = open)
    if (scanning) {
        CodeScannerScreen("Apunta a tu código", onDismiss = { scanning = false }) { code ->
            scanning = false
            app.scope.launch {
                if (code == block.optionsData.qrCode) {
                    app.penalties.advance(block, "¡Código correcto!")
                } else {
                    app.penalties.setVerdict(block, Verdict(false, "Ese no es tu código. Busca el que guardaste al crear el castigo."))
                }
            }
        }
    }
}

// ───────────── 🏋️ Ejercicio contado en directo ─────────────

@Composable
fun PoseChallenge(block: AppBlock) {
    val context = LocalContext.current
    val app = context.app()
    val options = block.optionsData
    var counting by remember { mutableStateOf(false) }
    val open = rememberCameraGate(onDenied = { showChallengeError(context, block, "Sin permiso de cámara no puedo contar.") }) {
        counting = true
    }
    ProgressBar(block.progress.toFloat() / options.reps)
    Spacer(Modifier.height(4.dp))
    Text(
        "${block.progress} de ${options.reps} ${options.poseExercise.label.lowercase()}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(options.poseExercise.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    GradientButton(if (block.progress > 0) "Seguir contando" else "Empezar", onClick = open)
    if (counting) {
        PoseCounterScreen(
            exercise = options.poseExercise,
            target = options.reps,
            start = block.progress,
            onCount = { n ->
                app.scope.launch {
                    app.penalties.setProgress(block, n)
                    if (n >= options.reps) app.penalties.advance(block, "¡${options.reps} ${options.poseExercise.label.lowercase()}!")
                }
            },
            onDismiss = { counting = false },
        )
    }
}

// ───────────── 🧘 Modo zen ─────────────

@Composable
fun ZenChallenge(block: AppBlock) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Text(
        "Pulsa empezar y deja el móvil boca abajo, quieto, sobre una mesa. Si lo coges o lo mueves, el tiempo vuelve a empezar.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    GradientButton("Empezar modo zen", onClick = { open = true })
    if (open) {
        ZenScreen(block.optionsData.zenMinutes, onDismiss = { open = false }) {
            open = false
            val app = context.app()
            app.scope.launch { app.penalties.advance(block, "Has aguantado ${durationText(block.optionsData.zenMinutes)} sin el móvil") }
        }
    }
}

@Composable
private fun ZenScreen(minutes: Int, onDismiss: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val goal = minutes * 60
    var seconds by remember { mutableIntStateOf(0) }
    var faceDown by remember { mutableStateOf(false) }
    var lastMove by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var message by remember { mutableStateOf("Ponlo boca abajo para empezar") }

    // La pantalla tiene que seguir encendida para que el sensor siga midiendo
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val sensors = context.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                faceDown = z < -7.5f
                val force = sqrt(x * x + y * y + z * z)
                if (abs(force - SensorManager.GRAVITY_EARTH) > 1.3f) lastMove = SystemClock.elapsedRealtime()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose {
            view.keepScreenOn = false
            sensors.unregisterListener(listener)
        }
    }
    LaunchedEffect(Unit) {
        while (seconds < goal) {
            delay(1_000)
            val still = SystemClock.elapsedRealtime() - lastMove > 1_500
            if (faceDown && still) {
                seconds++
                message = "Respira…"
            } else if (seconds > 0) {
                seconds = 0
                message = "¡Lo has movido! Vuelta a empezar"
                vibrate(context, 300)
            }
        }
        vibrate(context, 800)
        onDone()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1A1615)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(progress = seconds.toFloat() / goal, size = 240.dp, stroke = 16.dp) {
                    val left = goal - seconds
                    Text("%d:%02d".format(left / 60, left % 60), fontFamily = Script, fontSize = 48.sp, color = Color(0xFFF3E9D8))
                }
                Spacer(Modifier.height(24.dp))
                Text(message, style = MaterialTheme.typography.titleLarge, color = Color(0xFFF3E9D8), textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = onDismiss) { Text("Salir", color = Color(0xFFF3E9D8)) }
            }
        }
    }
}

private fun vibrate(context: Context, ms: Long) {
    runCatching {
        context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

// ───────────── ✍️ Reflexión a mano ─────────────

@Composable
fun ReflectionChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    val words = block.optionsData.reflectionWords
    val takePhoto = rememberPhoto(onError = { showChallengeError(context, block, it) }) { file ->
        ChallengeRunner.run(context, block, "Leyendo lo que has escrito…") {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val text = try {
                recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file))).awaitTask().text
            } finally {
                recognizer.close()
                file.delete()
            }
            val count = Regex("""\p{L}{2,}""").findAll(text).count()
            if (count >= words) {
                app.penalties.advance(block, "He leído $count palabras escritas a mano. ¡Bien reflexionado!")
            } else {
                app.penalties.setVerdict(
                    block,
                    Verdict(false, "Solo he podido leer $count de $words palabras. Escribe más (y con letra clara) y vuelve a hacer la foto."),
                )
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
    ) {
        Text(
            "Coge papel y boli y escribe al menos $words palabras: por qué no hiciste «${block.reason}» y qué vas a hacer distinto la próxima vez.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    Spacer(Modifier.height(10.dp))
    GradientButton("Hacer foto de lo escrito", enabled = busy == null, onClick = takePhoto)
}

/** Espera a una tarea de ML Kit sin bloquear. */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

// ───────────── 🇬🇧 Leer en inglés ─────────────

private val NUMBERS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
    "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty")

/** Palabras normalizadas: minúsculas, sin signos y con los números en letra (el reconocedor escribe «10» por «ten»). */
private fun englishWords(text: String): List<String> =
    Regex("""[a-z0-9']+""").findAll(text.lowercase().replace('’', '\'')).map { w ->
        w.value.toIntOrNull()?.let { NUMBERS.getOrNull(it) } ?: w.value
    }.toList()

/** Posiciones del texto que coinciden, en orden, con lo dicho (subsecuencia común más larga). */
private fun matchedWords(target: List<String>, spoken: List<String>): Set<Int> {
    val n = target.size
    val m = spoken.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
        dp[i][j] = if (target[i] == spoken[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
    }
    val matched = mutableSetOf<Int>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            target[i] == spoken[j] -> { matched += i; i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> i++
            else -> j++
        }
    }
    return matched
}

@Composable
fun EnglishChallenge(block: AppBlock) {
    val context = LocalContext.current
    val app = context.app()
    LaunchedEffect(block.groupKey, block.stageData) {
        if (block.stageData.toIntOrNull() == null) {
            app.penalties.setStageData(block, ChallengeBank.pick(app.prefs, "en", ChallengeBank.ENGLISH.size).toString())
        }
    }
    val index = block.stageData.toIntOrNull() ?: return
    val text = ChallengeBank.ENGLISH[index % ChallengeBank.ENGLISH.size]
    var spoken by rememberSaveable(block.groupKey, index) { mutableStateOf("") }
    val target = remember(text) { englishWords(text) }
    val matched = remember(target, spoken) { matchedWords(target, englishWords(spoken)) }
    val score = if (target.isEmpty()) 0 else matched.size * 100 / target.size

    val speech = rememberSpeech("en-US", "Read the text aloud", onError = { showChallengeError(context, block, it) }) { said ->
        spoken = (spoken + " " + said).trim()
        val newScore = matchedWords(target, englishWords(spoken)).size * 100 / target.size
        if (newScore >= ENGLISH_PASS) {
            app.scope.launch { app.penalties.advance(block, "¡Great job! Has leído bien el $newScore% del texto") }
        }
    }

    // Las palabras ya leídas bien se ponen en verde
    val green = DoneGreen
    val annotated = remember(text, matched) {
        buildAnnotatedString {
            var wordIndex = 0
            Regex("""[A-Za-z0-9'’]+|[^A-Za-z0-9'’]+""").findAll(text).forEach { piece ->
                if (piece.value.first().isLetterOrDigit()) {
                    if (wordIndex in matched) withStyle(SpanStyle(color = green)) { append(piece.value) } else append(piece.value)
                    wordIndex++
                } else {
                    append(piece.value)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp)) {
        Text(annotated, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Spacer(Modifier.height(8.dp))
    ProgressBar(score / ENGLISH_PASS.toFloat())
    Text(
        "Has leído bien el $score % · necesitas el $ENGLISH_PASS %. Puedes leerlo en varias partes.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientButton("Leer en voz alta", onClick = speech)
        if (spoken.isNotBlank()) TextButton(onClick = { spoken = "" }) { Text("Empezar de nuevo") }
    }
}

private const val ENGLISH_PASS = 80

// ───────────── 💻 Mini reto de código ─────────────

@Composable
fun CodeChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    LaunchedEffect(block.groupKey, block.stageData) {
        if (block.stageData.toIntOrNull() == null) {
            app.penalties.setStageData(block, ChallengeBank.pick(app.prefs, "code", ChallengeBank.KATAS.size).toString())
        }
    }
    val index = block.stageData.toIntOrNull() ?: return
    val kata = ChallengeBank.KATAS[index % ChallengeBank.KATAS.size]
    var code by remember(block.groupKey, index) { mutableStateOf(TextFieldValue(kata.starter)) }
    var pasteWarning by remember { mutableStateOf(false) }
    var results by remember(index) { mutableStateOf<com.rutina.app.penalty.CodeResult?>(null) }

    Text("${kata.title}", style = MaterialTheme.typography.titleMedium)
    Text(kata.description, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Ejemplo: ${kata.function}(${kata.tests.first().argsJson.removePrefix("[").removeSuffix("]")}) → ${kata.tests.first().expectedJson}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { new ->
            // Nada de pegar: si entra de golpe mucho texto, se ignora
            if (new.text.length - code.text.length > 12) pasteWarning = true else { code = new; pasteWarning = false }
        },
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp),
        minLines = 7,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    if (pasteWarning) WarningText("Nada de pegar código: escríbelo tú")
    Spacer(Modifier.height(10.dp))
    GradientButton("Pasar los tests ▶", enabled = busy == null, onClick = {
        val source = code.text
        ChallengeRunner.run(context, block, "Ejecutando tus tests…") {
            val result = CodeRunner.run(context, source, kata)
            results = result
            if (result.allPassed) {
                app.penalties.advance(block, "¡Todos los tests en verde!")
            } else {
                app.penalties.setVerdict(block, Verdict(false, result.error ?: "Pasan ${result.passed.count { it }} de ${result.passed.size} tests. ¡Casi!"))
            }
        }
    })
    results?.takeIf { it.error == null }?.let { r ->
        Spacer(Modifier.height(8.dp))
        r.details.forEachIndexed { i, detail ->
            Text(
                (if (r.passed[i]) "✓ " else "✕ ") + detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (r.passed[i]) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ───────────── 📖 Leer y responder ─────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingChallenge(block: AppBlock) {
    val context = LocalContext.current
    val app = context.app()
    LaunchedEffect(block.groupKey, block.stageData) {
        if (block.stageData.toIntOrNull() == null) {
            app.penalties.setStageData(block, ChallengeBank.pick(app.prefs, "read", ChallengeBank.READINGS.size).toString())
        }
    }
    val index = block.stageData.toIntOrNull() ?: return
    val reading = ChallengeBank.READINGS[index % ChallengeBank.READINGS.size]
    var secondsRead by remember(index) { mutableIntStateOf(0) }
    val answers = remember(index) { mutableStateOf(List(reading.questions.size) { -1 }) }
    LaunchedEffect(index) {
        while (secondsRead < READ_SECONDS) {
            delay(1_000)
            secondsRead++
        }
    }

    Text("${reading.title}", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp)) {
        Text(reading.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Spacer(Modifier.height(10.dp))
    if (secondsRead < READ_SECONDS) {
        ProgressBar(secondsRead / READ_SECONDS.toFloat())
        Text(
            "Léelo con calma: las preguntas salen en ${READ_SECONDS - secondsRead} s",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    reading.questions.forEachIndexed { q, question ->
        Text("${q + 1}. ${question.text}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            question.options.forEachIndexed { o, option ->
                val selected = answers.value[q] == o
                Text(
                    option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, inkBorder().copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .bouncyClick { answers.value = answers.value.toMutableList().also { it[q] = o } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    GradientButton("Comprobar", enabled = answers.value.none { it < 0 }, onClick = {
        val right = reading.questions.indices.count { answers.value[it] == reading.questions[it].correct }
        app.scope.launch {
            if (right == reading.questions.size) {
                app.penalties.advance(block, "¡3 de 3! Lo has entendido todo")
            } else {
                // Al fallar toca otro texto, para que no se pueda acertar probando
                app.penalties.fail(block, Verdict(false, "Has acertado $right de ${reading.questions.size}. Te pongo otro texto: léelo con calma."))
                app.penalties.setStageData(block, ChallengeBank.pick(app.prefs, "read", ChallengeBank.READINGS.size).toString())
            }
        }
    })
}

private const val READ_SECONDS = 40
