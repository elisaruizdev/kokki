package com.rutina.app.ui

import kotlinx.coroutines.launch
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rutina.app.R
import com.rutina.app.ai.Ai
import com.rutina.app.ai.AiException
import com.rutina.app.ai.Challenges
import com.rutina.app.app
import com.rutina.app.data.AppBlock
import com.rutina.app.data.Challenge
import com.rutina.app.data.Verdict
import com.rutina.app.data.challengeText
import com.rutina.app.data.isEditPass
import com.rutina.app.data.durationText
import com.rutina.app.data.stepsText
import com.rutina.app.penalty.BlockerService
import com.rutina.app.penalty.CaptureGuard
import com.rutina.app.penalty.ChallengeRunner
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Un castigo en marcha (las apps de una regla para una actividad) con su reto para desbloquearlo antes. */
@Composable
fun BlockGroupCard(group: List<AppBlock>, modifier: Modifier = Modifier) {
    val first = group.first()
    val minutesLeft = ((first.remainingMs + 59_999) / 60_000).toInt().coerceAtLeast(1)
    val busy = ChallengeRunner.busy.collectAsState().value[first.groupKey]
    CuteCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hanko(if (first.isEditPass) "設" else "禁", size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (first.isEditPass) "Permiso para cambiar un castigo" else group.joinToString(", ") { it.appLabel },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!first.isEditPass) {
                    Spacer(Modifier.height(4.dp))
                    AppLogos(group.map { it.packageName }, size = 26.dp)
                }
                Text(
                    if (first.isEditPass) "Ya ha saltado el aviso de «${first.reason}». Supera el reto para poder cambiar su castigo ahora."
                    else "Por no cumplir «${first.reason}»",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Pill(
            if (first.isEditPass) "O espera ${durationText(minutesLeft)}: entonces se libera solo" else "Quedan ${durationText(minutesLeft)} de castigo",
            background = MaterialTheme.colorScheme.primaryContainer,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            "El tiempo solo corre con el móvil encendido: apagarlo no sirve",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (!first.isEditPass) BonusButton(first)

        if (first.stages.size > 1) {
            Spacer(Modifier.height(12.dp))
            StageTrack(first)
        }
        val current = first.current
        if (first.challengeLost) {
            Spacer(Modifier.height(10.dp))
            Text("Ya no hay reto: toca esperar a que pase el tiempo", style = MaterialTheme.typography.titleSmall)
        } else if (current != Challenge.NINGUNO) {
            Spacer(Modifier.height(12.dp))
            IconText(current.icon, "Para desbloquear antes: ${first.challengeText}", MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }
        when (current) {
            Challenge.PASOS -> StepsChallenge(first)
            Challenge.PREGUNTA -> QuizChallenge(first, busy)
            Challenge.EJERCICIO -> VideoChallenge(first, busy)
            Challenge.FOTO -> PhotoChallenge(first, busy)
            Challenge.LUGAR -> PlaceChallenge(first)
            Challenge.RECUPERAR -> RecoverChallenge(first, busy)
            Challenge.QR -> QrChallenge(first)
            Challenge.CONTEO -> PoseChallenge(first)
            Challenge.ZEN -> ZenChallenge(first)
            Challenge.REFLEXION -> ReflectionChallenge(first, busy)
            Challenge.INGLES -> EnglishChallenge(first)
            Challenge.CODIGO -> CodeChallenge(first, busy)
            Challenge.LECTURA -> ReadingChallenge(first)
            Challenge.NINGUNO -> Unit
        }
        if (busy != null) BusyRow(busy)
        Verdict.decode(first.verdict)?.let { VerdictText(it) }
    }
}

/** Gastar un comodín para saltarse este castigo, con confirmación. */
@Composable
private fun BonusButton(block: AppBlock) {
    val context = LocalContext.current
    val app = context.app()
    val rewards by app.rewards.state.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    if (rewards.bonuses <= 0) return
    Spacer(Modifier.height(10.dp))
    GradientButton(
        "Usar un comodín (tienes ${rewards.bonuses})",
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Gastar un comodín?") },
            text = {
                Text(
                    "Se desbloquea ahora mismo este castigo de «${block.reason}». " +
                        "Te quedarán ${rewards.bonuses - 1} comodines. Para ganar otro, cumple ${rewards.goal} tareas.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    app.scope.launch { if (app.rewards.spend()) app.penalties.skipWithBonus(block) }
                }) { Text("Sí, usarlo") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Mejor no") } },
        )
    }
}

/** Retos del doble reto en fila: hechos, el de ahora y los que faltan. */
@Composable
private fun StageTrack(block: AppBlock) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        block.stages.forEachIndexed { i, stage ->
            val done = i < block.stage
            val now = i == block.stage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (now) 44.dp else 36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> DoneGreen.copy(alpha = 0.3f)
                            now -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .border(if (now) 2.dp else 1.dp, if (now) MaterialTheme.colorScheme.primary else inkBorder().copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(if (done) KIcons.Done else stage.icon, contentDescription = stage.label, tint = if (done) DoneGreen else MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (now) 24.dp else 18.dp))
            }
            if (i < block.stages.lastIndex) Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "Reto ${(block.stage + 1).coerceAtMost(block.stages.size)} de ${block.stages.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Texto con la franja de la recuperación, si este reto la tiene y aún no ha empezado. */
@Composable
fun WindowNote(block: AppBlock) {
    if (block.windowStart == 0L) return
    val fmt = DateTimeFormatter.ofPattern("EEEE HH:mm", com.rutina.app.data.SPANISH)
    val start = Instant.ofEpochMilli(block.windowStart).atZone(ZoneId.systemDefault())
    val end = Instant.ofEpochMilli(block.windowEnd).atZone(ZoneId.systemDefault())
    val text = if (System.currentTimeMillis() < block.windowStart) {
        "Tu recuperación es el ${start.format(fmt)}. Solo cuenta lo que hagas a partir de esa hora."
    } else {
        "Recuperación en marcha: tienes hasta las ${end.format(DateTimeFormatter.ofPattern("HH:mm"))}."
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun StepsChallenge(block: AppBlock) {
    val context = LocalContext.current
    var permissionOk by remember { mutableStateOf(BlockerService.hasStepPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionOk = it }
    WindowNote(block)
    ProgressBar(block.stepsDone.toFloat() / block.stepsRequired)
    Spacer(Modifier.height(4.dp))
    Text(
        "${stepsText(block.stepsDone)} de ${stepsText(block.stepsRequired)} pasos",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        !BlockerService.hasStepSensor(context) ->
            WarningText("Tu móvil no tiene contador de pasos: tendrás que esperar a que pase el tiempo.")
        !permissionOk -> TextButton(onClick = { permission.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) {
            Text("Permitir contar pasos", color = MaterialTheme.colorScheme.error)
        }
        !BlockerService.isEnabled(context) ->
            WarningText("Los pasos los cuenta el bloqueo de apps: actívalo en la pestaña Castigos.")
    }
}

@Composable
fun ProgressBar(progress: Float) {
    Box(
        Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(LocalPastels.current.accent))
    }
}

/** Abre el reconocimiento de voz de Google. Durante la grabación no se bloquea nada (el micro puede ser de una app castigada). */
@Composable
fun rememberSpeech(language: String, prompt: String, onError: (String) -> Unit, onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        CaptureGuard.stop()
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) onResult(said)
    }
    return {
        CaptureGuard.start(minutes = 5)
        try {
            launcher.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt),
            )
        } catch (e: ActivityNotFoundException) {
            CaptureGuard.stop()
            onError("No encuentro el reconocimiento de voz de Google en este móvil.")
        }
    }
}

/** Foto en el momento con la cámara del móvil (no se puede elegir una de la galería). */
@Composable
fun rememberPhoto(onError: (String) -> Unit, onPhoto: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val file = remember { File(File(context.cacheDir, "retos").apply { mkdirs() }, "foto.jpg") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        CaptureGuard.stop()
        if (taken && file.length() > 0) onPhoto(file)
    }
    return {
        file.delete()
        CaptureGuard.start(minutes = 5)
        try {
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
        } catch (e: ActivityNotFoundException) {
            CaptureGuard.stop()
            onError("No encuentro ninguna app de cámara.")
        }
    }
}

/** Pregunta de entrevista que solo se puede contestar hablando: no hay ningún campo de texto donde pegar nada. */
@Composable
private fun QuizChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    var transcript by rememberSaveable(block.groupKey) { mutableStateOf("") }
    val speech = rememberSpeech("es-ES", "Responde en voz alta", onError = { showChallengeError(context, block, it) }) {
        transcript = (transcript + " " + it).trim()
    }

    if (Ai.client(app.prefs) == null) {
        WarningText(Ai.MISSING_KEY)
        return
    }

    if (block.question.isBlank()) {
        GradientButton(
            "Hazme la pregunta",
            enabled = busy == null,
            onClick = {
                ChallengeRunner.run(context, block, "Preparando tu pregunta…") {
                    val client = Ai.client(app.prefs) ?: throw AiException(Ai.MISSING_KEY)
                    val question = Challenges.question(client, block.topics, app.prefs.recentQuestions)
                    Ai.rememberModel(app.prefs, client)
                    app.prefs.recentQuestions = app.prefs.recentQuestions + question
                    app.penalties.setQuestion(block, question)
                    app.penalties.setVerdict(block, null)
                }
            },
        )
        return
    }

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(14.dp),
    ) {
        Text("${block.question}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "Si no lo sabes, búscalo, pero luego tienes que explicarlo tú en voz alta. Puedes grabar en varias partes.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (transcript.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text("«$transcript»", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientCircle(onClick = speech, size = 56.dp, enabled = busy == null) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = "Responder hablando", tint = MaterialTheme.colorScheme.onPrimary)
        }
        if (transcript.isNotBlank()) {
            GradientButton(
                "Enviar respuesta",
                enabled = busy == null,
                modifier = Modifier.weight(1f),
                onClick = {
                    val answer = transcript
                    ChallengeRunner.run(context, block, "Corrigiendo tu respuesta…") {
                        val client = Ai.client(app.prefs) ?: throw AiException(Ai.MISSING_KEY)
                        val result = Challenges.checkAnswer(client, block.question, answer)
                        // Si falla la conexión la respuesta se conserva para reenviarla
                        transcript = ""
                        if (result.ok) app.penalties.advance(block, result.comment) else app.penalties.fail(block, result)
                    }
                },
            )
            TextButton(onClick = { transcript = "" }, enabled = busy == null) { Text("Borrar") }
        } else {
            Text("Pulsa el micro y responde", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Reto de ejercicio: se graba en el momento con la cámara (no se puede elegir un vídeo de la galería) y lo revisa la IA. */
@Composable
private fun VideoChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    val videoFile = remember { File(File(context.cacheDir, "retos").apply { mkdirs() }, "reto.mp4") }

    val camera = rememberLauncherForActivityResult(RecordVideo()) { recorded ->
        CaptureGuard.stop()
        if (!recorded || videoFile.length() == 0L) return@rememberLauncherForActivityResult
        ChallengeRunner.run(context, block, "Subiendo y revisando el vídeo") {
            try {
                val client = Ai.mediaClient(app.prefs) ?: throw AiException(Ai.MISSING_KEY)
                val result = Challenges.checkExercise(client, block.exercise, videoFile, "video/mp4")
                if (result.ok) app.penalties.advance(block, result.comment) else app.penalties.fail(block, result)
            } finally {
                videoFile.delete()
            }
        }
    }

    if (app.prefs.apiKey.isBlank()) {
        WarningText(
            "Para revisar vídeos hace falta la clave gratuita de Gemini (Groq no ve vídeos). Ponla en Ajustes, " +
                "o cambia este castigo por «Ejercicio contado en directo», que no necesita IA.",
        )
        return
    }
    Text(
        "Colócate donde se vea bien todo tu cuerpo y haz el ejercicio de seguido. La IA contará las repeticiones.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    GradientButton(
        "Grabarme",
        enabled = busy == null,
        onClick = {
            CaptureGuard.start(minutes = 10)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", videoFile)
            try {
                camera.launch(uri)
            } catch (e: ActivityNotFoundException) {
                CaptureGuard.stop()
                showChallengeError(context, block, "No encuentro ninguna app de cámara para grabar.")
            }
        },
    )
}

/** Foto de una tarea terminada, revisada por la IA. */
@Composable
private fun PhotoChallenge(block: AppBlock, busy: String?) {
    val context = LocalContext.current
    val app = context.app()
    val options = block.optionsData
    val takePhoto = rememberPhoto(onError = { showChallengeError(context, block, it) }) { file ->
        ChallengeRunner.run(context, block, "Revisando la foto") {
            val jpeg = shrinkPhoto(file) ?: throw AiException("No he podido abrir la foto. Prueba otra vez.")
            file.delete()
            val client = Ai.mediaClient(app.prefs) ?: throw AiException(Ai.MISSING_KEY)
            val result = Challenges.checkPhoto(client, options.photoTask, jpeg)
            if (result.ok) app.penalties.advance(block, result.comment) else app.penalties.fail(block, result)
        }
    }
    if (Ai.mediaClient(app.prefs) == null) {
        WarningText(Ai.MISSING_KEY)
        return
    }
    Text(
        "Haz la tarea y luego una foto en la que se vea terminada. Tiene que ser en el momento, con la cámara.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    GradientButton("Hacer la foto", enabled = busy == null, onClick = takePhoto)
}

/** Grabación con la cámara, limitada a 2 minutos para que el vídeo no pese demasiado. */
private class RecordVideo : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).putExtra(MediaStore.EXTRA_DURATION_LIMIT, 120)
}

/** Guarda un error para que se vea en la tarjeta (no cuenta como intento fallido). */
fun showChallengeError(context: Context, block: AppBlock, text: String) {
    ChallengeRunner.run(context, block, "…") { context.app().penalties.setVerdict(block, Verdict(false, text, error = true)) }
}

@Composable
private fun BusyRow(text: String) {
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun VerdictText(verdict: Verdict) {
    Spacer(Modifier.height(10.dp))
    val (background, color) = when {
        verdict.error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        verdict.ok -> DoneGreen.copy(alpha = 0.2f) to MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Text(
        verdict.comment,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background).padding(12.dp),
    )
}

@Composable
fun WarningText(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}
