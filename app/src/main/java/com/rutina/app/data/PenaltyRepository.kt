package com.rutina.app.data

import android.content.Context
import com.rutina.app.notify.Notifier
import com.rutina.app.penalty.BlockerService
import java.util.Locale

/** Reglas de castigo y bloqueos de apps cuando no se cumple una actividad. */
class PenaltyRepository(private val context: Context, private val dao: PenaltyDao, private val commitments: Commitments) {

    /** Los castigos empiezan con el compromiso: antes de la fecha de inicio (o en una semana sin fijar) no castigan. */
    private fun punishable(activity: PlannedActivity) = commitments.isCommittedDay(activity.date)


    /** Marca la actividad de recuperación como hecha o no hecha (lo pone [com.rutina.app.RutinaApp]). */
    var onRecoveryResult: (suspend (activityId: Long, done: Boolean) -> Unit)? = null

    fun observeRules() = dao.observeRules()

    /** Bloqueos sin superar; filtrar con [AppBlock.isActive] para saber si siguen. */
    fun observeBlocks() = dao.observeBlocks()

    suspend fun activeBlocks() = dao.activeBlocks().filter { it.isActive() }

    fun observeEditPasses() = dao.observeEditPasses()

    /**
     * Para cambiar una regla bloqueada hay que superar su reto (o, si no tiene, caminar). Se crea como un castigo
     * sin apps que dura lo que le queda al bloqueo: si se espera, la regla se libera sola a esa hora.
     */
    suspend fun requestEditPass(rule: PenaltyRule, lock: RuleLock) {
        val passId = -rule.id
        if (dao.group(passId, rule.id).any { it.isActive() }) return
        val options = rule.optionsData
        val ownChallenge = rule.challengeEnum.takeIf { it != Challenge.NINGUNO && it != Challenge.RECUPERAR }
        val now = System.currentTimeMillis()
        dao.upsertBlocks(
            listOf(
                AppBlock(
                    ruleId = rule.id,
                    activityId = passId,
                    packageName = "",
                    appLabel = "Cambiar el castigo",
                    reason = lock.activity.title,
                    until = lock.until,
                    remainingMs = (lock.until - now).coerceAtLeast(60_000),
                    challenge = (ownChallenge ?: Challenge.PASOS).name,
                    stepsRequired = rule.steps.takeIf { it > 0 } ?: EDIT_PASS_STEPS,
                    topics = rule.topics,
                    exercise = rule.exercise,
                    options = options.copy(challenge2 = Challenge.NINGUNO, growMinutes = 0).toJson(),
                ),
            ),
        )
    }


    suspend fun saveRule(rule: PenaltyRule) = dao.upsertRule(rule)

    suspend fun deleteRule(rule: PenaltyRule) = dao.deleteRule(rule)

    suspend fun saveBlocks(blocks: List<AppBlock>) = dao.upsertBlocks(blocks)

    /** Texto para avisar en las notificaciones de lo que pasará si no se cumple. */
    suspend fun warningFor(activity: PlannedActivity): String? =
        dao.enabledRules().filter { punishable(activity) && it.matches(activity) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ") { "${it.labelList.joinToString(", ")} ${durationText(it.minutes)}" }
            ?.let { "Si no lo cumples: $it" }

    /** Aplica los castigos al marcar una actividad como no hecha. Una misma regla no castiga dos veces la misma actividad. */
    suspend fun punish(activity: PlannedActivity) {
        val now = System.currentTimeMillis()
        dao.deleteOld(now - 7 * DAY)
        val alreadyPunished = dao.blocksForActivity(activity.id).map { it.ruleId }.toSet()
        val blocks = dao.enabledRules()
            .filter { punishable(activity) && it.matches(activity) && it.id !in alreadyPunished }
            .flatMap { rule ->
                rule.packageList.zip(rule.labelList).map { (pkg, label) ->
                    AppBlock(
                        ruleId = rule.id,
                        activityId = activity.id,
                        packageName = pkg,
                        appLabel = label,
                        reason = activity.title,
                        until = now + rule.minutes * 60_000L,
                        remainingMs = rule.minutes * 60_000L,
                        challenge = rule.challenge,
                        stepsRequired = rule.steps,
                        topics = rule.topics,
                        exercise = rule.exercise,
                        options = rule.options,
                    )
                }
            }
        if (blocks.isEmpty()) return
        dao.upsertBlocks(blocks)
        Notifier.showBlocked(context, activity, blocks)
        // Sin el permiso de accesibilidad el castigo no se puede aplicar: se avisa para activarlo
        if (!BlockerService.isEnabled(context)) Notifier.showBlockerOff(context)
    }

    /** Cambia todos los bloqueos del grupo de [block] (todas las apps de una regla para una actividad). */
    suspend fun updateGroup(block: AppBlock, change: (AppBlock) -> AppBlock) =
        dao.upsertBlocks(dao.group(block.activityId, block.ruleId).map(change))

    /** Guarda la pregunta asignada a un grupo de bloqueos para que no se pueda cambiar por otra. */
    suspend fun setQuestion(block: AppBlock, question: String) = updateGroup(block) { it.copy(question = question) }

    suspend fun setStageData(block: AppBlock, data: String) = updateGroup(block) { it.copy(stageData = data) }

    suspend fun setProgress(block: AppBlock, progress: Int) = updateGroup(block) { it.copy(progress = progress) }

    suspend fun setVerdict(block: AppBlock, verdict: Verdict?) = updateGroup(block) { it.copy(verdict = verdict?.encode().orEmpty()) }

    /**
     * Intento fallido: se guarda la corrección y, si la regla lo dice, se suma tiempo de castigo
     * (como mucho otro tanto como el castigo inicial, para que no crezca sin fin).
     */
    suspend fun fail(block: AppBlock, verdict: Verdict) {
        val growMs = block.optionsData.growMinutes * 60_000L
        val maxAdded = block.until - block.createdAt
        updateGroup(block) {
            val add = growMs.coerceAtMost((maxAdded - it.addedMs).coerceAtLeast(0))
            val note = if (add > 0) "\n\n +${durationText((add / 60_000).toInt())} de castigo por fallar." else ""
            it.copy(
                verdict = verdict.copy(comment = verdict.comment + note).encode(),
                remainingMs = it.remainingMs + add,
                addedMs = it.addedMs + add,
            )
        }
    }

    /** Reto en curso superado: pasa al siguiente o, si era el último, se desbloquean todas las apps del grupo. */
    suspend fun advance(block: AppBlock, comment: String = "") {
        recoveryActivity(block)?.let { onRecoveryResult?.invoke(it, true) }
        val next = block.stage + 1
        if (next >= block.stages.size) {
            updateGroup(block) { it.copy(solved = true, verdict = Verdict(true, comment).encode()) }
            Notifier.showUnlocked(context, block, byTime = false)
            return
        }
        // La franja de la recuperación sigue valiendo para su comprobación; después ya no
        val keepWindow = block.current == Challenge.RECUPERAR
        updateGroup(block) {
            it.copy(
                stage = next,
                progress = 0,
                stepBaseline = -1,
                stepsDone = 0,
                question = "",
                stageData = if (keepWindow) it.stageData else "",
                windowStart = if (keepWindow) it.windowStart else 0,
                windowEnd = if (keepWindow) it.windowEnd else 0,
                verdict = Verdict(true, comment.ifBlank { "¡Reto superado! Ahora el siguiente." }).encode(),
            )
        }
    }

    /** Castigo saltado con un comodín (ya gastado): se desbloquean todas las apps del grupo. */
    suspend fun skipWithBonus(block: AppBlock) {
        updateGroup(block) { it.copy(solved = true, verdict = Verdict(true, "Comodín usado").encode()) }
        Notifier.showUnlocked(context, block, byTime = false)
    }

    /** Fija la franja de la recuperación (y la actividad creada) antes de pasar a su comprobación. */
    suspend fun startRecovery(block: AppBlock, activityId: Long, start: Long, end: Long) {
        updateGroup(block) { it.copy(stageData = activityId.toString(), windowStart = start, windowEnd = end) }
        advance(dao.group(block.activityId, block.ruleId).first(), "Recuperación apuntada en tu semana")
    }

    /** Se acabó la franja de la recuperación sin cumplirla: ya solo queda esperar. */
    suspend fun loseChallenge(block: AppBlock, reason: String) {
        recoveryActivity(block)?.let { onRecoveryResult?.invoke(it, false) }
        updateGroup(block) { it.copy(stage = it.stages.size, verdict = Verdict(false, reason).encode()) }
    }

    /** Si el reto en curso es la comprobación de una recuperación, el id de esa actividad. */
    private fun recoveryActivity(block: AppBlock): Long? =
        block.stageData.toLongOrNull()?.takeIf { block.windowStart > 0 && block.current != Challenge.RECUPERAR }

    companion object {
        private const val DAY = 24 * 60 * 60_000L
        private const val EDIT_PASS_STEPS = 2_000
    }
}

fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun stepsText(steps: Int): String = String.format(Locale.GERMANY, "%,d", steps)
