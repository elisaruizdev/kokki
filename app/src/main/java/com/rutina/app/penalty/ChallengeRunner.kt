package com.rutina.app.penalty

import android.content.Context
import com.rutina.app.ai.AiException
import com.rutina.app.app
import com.rutina.app.data.AppBlock
import com.rutina.app.data.Verdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ejecuta las comprobaciones de los retos (IA, tests de código...) en el ámbito de la app, no de la pantalla:
 * aunque se cierre o se gire la pantalla, la corrección termina y su resultado queda guardado en el bloqueo.
 */
object ChallengeRunner {
    private val running = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Qué se está haciendo ahora en cada grupo de bloqueos («Corrigiendo…»). */
    val busy: StateFlow<Map<String, String>> = running.asStateFlow()

    fun run(context: Context, block: AppBlock, label: String, work: suspend () -> Unit) {
        val key = block.groupKey
        if (key in running.value) return
        running.update { it + (key to label) }
        val app = context.app()
        app.scope.launch {
            try {
                work()
            } catch (e: AiException) {
                app.penalties.setVerdict(block, Verdict(false, e.message.orEmpty(), error = true))
            } catch (e: Exception) {
                app.penalties.setVerdict(block, Verdict(false, "Algo ha fallado: ${e.message}", error = true))
            } finally {
                running.update { it - key }
            }
        }
    }
}
