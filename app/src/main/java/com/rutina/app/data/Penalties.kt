package com.rutina.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

/**
 * Reto para desbloquear las apps antes de que pase el tiempo del castigo.
 * [needsAi]: necesita la IA (y conexión); el resto se comprueba dentro del propio móvil.
 */
enum class Challenge(val label: String, val emoji: String, val needsAi: Boolean = false) {
    NINGUNO("Solo esperar", "⏳"),
    PASOS("Caminar", "🚶‍♀️"),
    LUGAR("Ir a un sitio", "📍"),
    RECUPERAR("Recuperar la actividad", "🔁"),
    CONTEO("Ejercicio contado en directo", "🏋️‍♀️"),
    QR("Escanear tu código", "🔳"),
    ZEN("Modo zen", "🧘‍♀️"),
    REFLEXION("Reflexión a mano", "✍️"),
    INGLES("Leer en inglés en voz alta", "🇬🇧"),
    CODIGO("Mini reto de código", "💻"),
    LECTURA("Leer y responder", "📖"),
    PREGUNTA("Pregunta en voz alta", "🎤", needsAi = true),
    FOTO("Foto de una tarea", "📸", needsAi = true),
    EJERCICIO("Vídeo haciendo ejercicio", "🎥", needsAi = true);

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: NINGUNO
    }
}

/** Ejercicios que la cámara sabe contar. */
enum class PoseExercise(val label: String, val emoji: String, val hint: String) {
    SENTADILLAS("Sentadillas", "🦵", "Pon el móvil de pie a 2-3 metros, que se te vea de lado o de frente de cabeza a pies."),
    FLEXIONES("Flexiones", "💪", "Pon el móvil en el suelo, de lado, a 1-2 metros: que se vean tus brazos y tu cuerpo."),
    JUMPING("Jumping jacks", "⭐", "Pon el móvil de pie a 2-3 metros, de frente: que se vea todo tu cuerpo con los brazos arriba.");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: SENTADILLAS
    }
}

/** Ajustes de los retos que no tienen columna propia. Se guardan como JSON en [PenaltyRule.options] y [AppBlock.options]. */
data class ChallengeOptions(
    /** Segundo reto que hay que superar después del primero (doble reto). */
    val challenge2: Challenge = Challenge.NINGUNO,
    /** Minutos de castigo que se suman por cada intento fallido (0 = no se suman). */
    val growMinutes: Int = 0,
    val placeName: String = "",
    val placeLat: Double = 0.0,
    val placeLng: Double = 0.0,
    val placeMinutes: Int = 20,
    val qrCode: String = "",
    val poseExercise: PoseExercise = PoseExercise.SENTADILLAS,
    val reps: Int = 20,
    val photoTask: String = "",
    val zenMinutes: Int = 10,
    val reflectionWords: Int = 40,
    /** Cómo se comprueba la actividad de recuperación: [Challenge.PASOS] o [Challenge.LUGAR]. */
    val recoverCheck: Challenge = Challenge.PASOS,
) {
    val hasPlace: Boolean get() = placeLat != 0.0 || placeLng != 0.0

    fun toJson(): String = JSONObject()
        .put("challenge2", challenge2.name)
        .put("growMinutes", growMinutes)
        .put("placeName", placeName)
        .put("placeLat", placeLat)
        .put("placeLng", placeLng)
        .put("placeMinutes", placeMinutes)
        .put("qrCode", qrCode)
        .put("poseExercise", poseExercise.name)
        .put("reps", reps)
        .put("photoTask", photoTask)
        .put("zenMinutes", zenMinutes)
        .put("reflectionWords", reflectionWords)
        .put("recoverCheck", recoverCheck.name)
        .toString()

    companion object {
        fun parse(json: String): ChallengeOptions {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return ChallengeOptions()
            val d = ChallengeOptions()
            return ChallengeOptions(
                challenge2 = Challenge.from(o.optString("challenge2")),
                growMinutes = o.optInt("growMinutes", d.growMinutes),
                placeName = o.optString("placeName"),
                placeLat = o.optDouble("placeLat", 0.0),
                placeLng = o.optDouble("placeLng", 0.0),
                placeMinutes = o.optInt("placeMinutes", d.placeMinutes),
                qrCode = o.optString("qrCode"),
                poseExercise = PoseExercise.from(o.optString("poseExercise")),
                reps = o.optInt("reps", d.reps),
                photoTask = o.optString("photoTask"),
                zenMinutes = o.optInt("zenMinutes", d.zenMinutes),
                reflectionWords = o.optInt("reflectionWords", d.reflectionWords),
                recoverCheck = Challenge.from(o.optString("recoverCheck")).takeIf { it == Challenge.LUGAR } ?: Challenge.PASOS,
            )
        }
    }
}

/**
 * Regla de castigo: si no cumplo algo de [trigger], se bloquean [packages] durante [minutes],
 * salvo que antes supere el reto ([challenge]).
 */
@Entity(tableName = "penalty_rules")
data class PenaltyRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre de una [Category] o [TRIGGER_ANY]. */
    val trigger: String,
    /** Paquetes de las apps separados por comas. */
    val packages: String,
    /** Nombres de las apps separados por comas, en el mismo orden. */
    val appLabels: String,
    val minutes: Int,
    val challenge: String = Challenge.NINGUNO.name,
    val steps: Int = 0,
    /** Temas de la pregunta, p. ej. "JavaScript, TypeScript, Angular y Vue". */
    val topics: String = "",
    /** Ejercicio que hay que grabar, p. ej. "20 sentadillas". */
    val exercise: String = "",
    val enabled: Boolean = true,
    /** [ChallengeOptions] en JSON. */
    @ColumnInfo(defaultValue = "")
    val options: String = "",
)

const val TRIGGER_ANY = "CUALQUIERA"
private const val SEPARATOR = "|"

val PenaltyRule.packageList: List<String> get() = packages.split(SEPARATOR).filter { it.isNotBlank() }
val PenaltyRule.labelList: List<String> get() = appLabels.split(SEPARATOR).filter { it.isNotBlank() }
val PenaltyRule.challengeEnum: Challenge get() = Challenge.from(challenge)
val PenaltyRule.optionsData: ChallengeOptions get() = ChallengeOptions.parse(options)

fun joinApps(values: List<String>) = values.joinToString(SEPARATOR)

fun PenaltyRule.matches(activity: PlannedActivity) =
    enabled && activity.categoryEnum.asksCheck && !activity.isRecovery && activity.substituteFor == null &&
        (trigger == TRIGGER_ANY || trigger == activity.category)

val PenaltyRule.triggerText: String
    get() = if (trigger == TRIGGER_ANY) "✨ cualquier actividad" else Category.from(trigger).let { "${it.emoji} ${it.label}" }

/** Retos en orden: el de recuperar lleva detrás su comprobación, y el doble reto añade el segundo. */
fun stagesOf(first: Challenge, options: ChallengeOptions): List<Challenge> {
    fun expand(c: Challenge) = if (c == Challenge.RECUPERAR) listOf(c, options.recoverCheck) else listOf(c)
    val stages = expand(first).toMutableList()
    if (first != Challenge.NINGUNO && options.challenge2 != Challenge.NINGUNO && options.challenge2 != first) {
        stages += expand(options.challenge2)
    }
    return stages
}

/** Qué hay que hacer para superar un reto, en una frase. */
fun challengeText(challenge: Challenge, steps: Int, topics: String, exercise: String, options: ChallengeOptions): String = when (challenge) {
    Challenge.NINGUNO -> "esperar a que pase el tiempo"
    Challenge.PASOS -> "caminar ${stepsText(steps)} pasos"
    Challenge.PREGUNTA -> "responder en voz alta una pregunta de $topics"
    Challenge.EJERCICIO -> "grabarte haciendo $exercise"
    Challenge.LUGAR -> "estar ${durationText(options.placeMinutes)} en ${options.placeName.ifBlank { "tu sitio" }}"
    Challenge.RECUPERAR -> "recuperar la actividad hoy o mañana (lo compruebo con " +
        (if (options.recoverCheck == Challenge.LUGAR) "tu ubicación)" else "tus pasos)")
    Challenge.CONTEO -> "hacer ${options.reps} ${options.poseExercise.label.lowercase()} delante de la cámara"
    Challenge.QR -> "escanear tu código QR"
    Challenge.ZEN -> "dejar el móvil boca abajo y quieto ${durationText(options.zenMinutes)}"
    Challenge.REFLEXION -> "escribir a mano una reflexión de ${options.reflectionWords} palabras"
    Challenge.INGLES -> "leer en voz alta un texto en inglés"
    Challenge.CODIGO -> "resolver un mini reto de JavaScript"
    Challenge.LECTURA -> "leer un texto y acertar 3 preguntas"
    Challenge.FOTO -> "hacer una foto de ${options.photoTask.ifBlank { "la tarea" }}"
}

val PenaltyRule.challengeText: String
    get() {
        val options = optionsData
        val chosen = if (challengeEnum == Challenge.NINGUNO) listOf(Challenge.NINGUNO) else listOf(challengeEnum, options.challenge2)
        return chosen.filter { it != Challenge.NINGUNO || chosen.size == 1 }.distinct()
            .joinToString(" y después ") { challengeText(it, steps, topics, exercise, options) }
    }

/**
 * Bloqueo en marcha de una app. Se levanta cuando se cumple el tiempo ([remainingMs] llega a 0) o al superar el reto.
 * Los bloqueos de una misma actividad y regla forman un grupo y se superan juntos.
 */
@Entity(tableName = "app_blocks")
data class AppBlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val activityId: Long,
    val packageName: String,
    val appLabel: String,
    /** Título de la actividad que no se cumplió. */
    val reason: String,
    /** Fin previsto del bloqueo (solo informativo: lo que manda es [remainingMs]). */
    val until: Long,
    /** Primer reto; los siguientes salen de [stagesOf]. */
    val challenge: String,
    val stepsRequired: Int = 0,
    val topics: String = "",
    val exercise: String = "",
    /** Pregunta asignada; se guarda para que no se pueda pedir otra hasta dar con una fácil. */
    val question: String = "",
    /** Lectura del contador de pasos del móvil al empezar; -1 hasta la primera lectura. */
    val stepBaseline: Long = -1,
    val stepsDone: Int = 0,
    /** Reto superado. */
    val solved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Tiempo de castigo que falta. Solo se descuenta con el móvil encendido y con el bloqueo activo, así que
     * apagar el móvil, cambiar la hora o desactivar Kokki no hace que pase antes.
     */
    @ColumnInfo(defaultValue = "0")
    val remainingMs: Long = 0,
    /** [ChallengeOptions] de la regla en JSON. */
    @ColumnInfo(defaultValue = "")
    val options: String = "",
    /** Reto en curso dentro de [stagesOf]; si se pasa del último sin resolver, solo queda esperar. */
    @ColumnInfo(defaultValue = "0")
    val stage: Int = 0,
    /** Avance del reto en curso (minutos en el sitio, repeticiones...). */
    @ColumnInfo(defaultValue = "0")
    val progress: Int = 0,
    /** Datos del reto en curso: id del texto o del ejercicio asignado, id de la actividad de recuperación... */
    @ColumnInfo(defaultValue = "")
    val stageData: String = "",
    /** Franja (hora del reloj) en la que hay que hacer el reto, para la actividad de recuperación. 0 = cuando sea. */
    @ColumnInfo(defaultValue = "0")
    val windowStart: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val windowEnd: Long = 0,
    /** Resultado del último intento ([Verdict.encode]), para no perderlo aunque se cierre la pantalla. */
    @ColumnInfo(defaultValue = "")
    val verdict: String = "",
    /** Tiempo ya sumado por intentos fallidos. */
    @ColumnInfo(defaultValue = "0")
    val addedMs: Long = 0,
) {
    val optionsData: ChallengeOptions get() = ChallengeOptions.parse(options)

    val stages: List<Challenge> get() = stagesOf(Challenge.from(challenge), optionsData)

    /** Reto que toca ahora. */
    val current: Challenge get() = stages.getOrNull(stage) ?: Challenge.NINGUNO

    /** El reto de recuperación falló: ya solo vale esperar. */
    val challengeLost: Boolean get() = stage >= stages.size && Challenge.from(challenge) != Challenge.NINGUNO

    val groupKey: String get() = "$activityId-$ruleId"

    fun inWindow(now: Long = System.currentTimeMillis()) = windowStart == 0L || now in windowStart..windowEnd

    fun isActive() = !solved && remainingMs > 0 && !(current == Challenge.PASOS && stepsDone >= stepsRequired)
}

val AppBlock.challengeText: String
    get() = challengeText(current, stepsRequired, topics, exercise, optionsData)

/**
 * No es el castigo de una app sino el permiso para cambiar una regla bloqueada ([ruleLock]).
 * Usa como actividad el id de la regla en negativo y no bloquea ninguna app.
 */
val AppBlock.isEditPass: Boolean get() = activityId < 0

/** Una regla no se puede tocar desde que salta el aviso de una actividad que vigila hasta que se resuelve. */
data class RuleLock(val activity: PlannedActivity, val until: Long)

/**
 * Bloqueo de cambios de una regla: desde el aviso de preparación de una actividad pendiente que vigila
 * hasta que se marca o, si no se contesta, hasta que cuenta como no hecha.
 */
fun ruleLock(rule: PenaltyRule, activities: List<PlannedActivity>, now: Long = System.currentTimeMillis()): RuleLock? {
    val zone = java.time.ZoneId.systemDefault()
    val byId = activities.associateBy { it.id }
    // Una tarea de cambio pendiente bloquea las reglas de la actividad a la que sustituye
    return activities
        .filter { a ->
            a.statusEnum == Status.PENDIENTE &&
                (rule.matches(a) || a.substituteFor?.let { byId[it] }?.let { rule.matches(it) } == true)
        }
        .mapNotNull { a ->
            val day = java.time.LocalDate.ofEpochDay(a.date).atStartOfDay(zone).toInstant().toEpochMilli()
            val from = day + (a.startMinute - a.reminderMinutes) * 60_000L
            val until = day + (a.endMinute + com.rutina.app.notify.AlarmScheduler.EXPIRE_AFTER_MINUTES) * 60_000L
            if (now in from until until) RuleLock(a, until) else null
        }
        .maxByOrNull { it.until }
}

@Dao
interface PenaltyDao {
    @Query("SELECT * FROM penalty_rules ORDER BY id")
    fun observeRules(): Flow<List<PenaltyRule>>

    @Query("SELECT * FROM penalty_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<PenaltyRule>

    @Upsert
    suspend fun upsertRule(rule: PenaltyRule)

    @Delete
    suspend fun deleteRule(rule: PenaltyRule)

    /** Bloqueos que aún pueden estar activos (después se filtran con [AppBlock.isActive]). */
    @Query("SELECT * FROM app_blocks WHERE solved = 0 AND remainingMs > 0 ORDER BY createdAt")
    fun observeBlocks(): Flow<List<AppBlock>>

    @Query("SELECT * FROM app_blocks WHERE solved = 0 AND remainingMs > 0")
    suspend fun activeBlocks(): List<AppBlock>

    /** Permisos ya conseguidos para cambiar reglas bloqueadas. */
    @Query("SELECT * FROM app_blocks WHERE activityId < 0 AND solved = 1")
    fun observeEditPasses(): Flow<List<AppBlock>>

    @Query("SELECT * FROM app_blocks WHERE activityId = :activityId")
    suspend fun blocksForActivity(activityId: Long): List<AppBlock>

    @Query("SELECT * FROM app_blocks WHERE activityId = :activityId AND ruleId = :ruleId")
    suspend fun group(activityId: Long, ruleId: Long): List<AppBlock>

    @Upsert
    suspend fun upsertBlocks(blocks: List<AppBlock>)

    /** Limpia los castigos ya terminados de hace tiempo. */
    @Query("DELETE FROM app_blocks WHERE createdAt < :before AND (solved = 1 OR remainingMs = 0)")
    suspend fun deleteOld(before: Long)
}

/** Resultado de un intento de reto. [error]: no se pudo corregir (sin conexión, sin clave...), no cuenta como fallo. */
data class Verdict(val ok: Boolean, val comment: String, val error: Boolean = false) {
    fun encode() = (if (error) "ERR" else if (ok) "OK" else "NO") + "\n" + comment

    companion object {
        fun decode(value: String): Verdict? {
            if (value.isBlank()) return null
            val kind = value.substringBefore("\n")
            return Verdict(ok = kind == "OK", comment = value.substringAfter("\n"), error = kind == "ERR")
        }
    }
}
