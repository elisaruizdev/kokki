package com.rutina.app.penalty

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.rutina.app.app
import com.rutina.app.data.AppBlock
import com.rutina.app.data.Challenge
import com.rutina.app.data.isEditPass
import com.rutina.app.data.stepsText
import com.rutina.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio de accesibilidad que vigila qué app se abre: si está castigada, vuelve al inicio y
 * muestra la pantalla de bloqueo. Además:
 *  - Descuenta el tiempo de castigo solo mientras está en marcha (móvil encendido y Kokki activa).
 *  - Durante un castigo impide abrir los ajustes de Kokki, para que no se pueda desactivar ni desinstalar.
 *  - Cuenta los pasos de los retos de caminar.
 * Android lo vuelve a arrancar solo al encender el móvil, así que los castigos siguen tras reiniciar.
 */
class BlockerService : AccessibilityService(), SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var blocks: List<AppBlock> = emptyList()
    private var stepSensorOn = false
    private var lastBlockedAt = 0L

    override fun onServiceConnected() {
        scope.launch {
            app().penalties.observeBlocks().collect {
                blocks = it
                updateStepSensor()
            }
        }
        scope.launch { countDown() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || CaptureGuard.active) return
        if (blocks.none { it.isActive() }) return
        val now = SystemClock.elapsedRealtime()
        // Una app puede lanzar varios eventos seguidos al abrirse
        if (now - lastBlockedAt < 1_500) return

        val target = when {
            blocks.any { it.packageName == pkg && it.isActive() } -> BlockedActivity.forPackage(this, pkg)
            opensRutinaSettings(pkg) -> BlockedActivity.forGuard(this)
            else -> return
        }
        lastBlockedAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(target)
    }

    /**
     * ¿Es una pantalla de ajustes (accesibilidad, información de la app, desinstalar...) que muestra Kokki?
     * Se compara el texto exacto para no confundirlo con otras cosas (p. ej. «Modos y rutinas» de Samsung).
     */
    private fun opensRutinaSettings(pkg: String): Boolean {
        if (SETTINGS_PACKAGE_HINTS.none { it in pkg }) return false
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(APP_LABEL).any { node ->
            val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
            text == APP_LABEL || text.startsWith("$APP_LABEL:")
        }
    }

    /** Resta al castigo el tiempo transcurrido desde la última vez; con el móvil apagado no corre. */
    private suspend fun countDown() {
        var last = SystemClock.elapsedRealtime()
        while (true) {
            delay(TICK_MS)
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - last
            last = now
            val active = blocks.filter { it.isActive() }
            if (active.isEmpty()) continue
            val updated = active.map { it.copy(remainingMs = (it.remainingMs - elapsed).coerceAtLeast(0)) }
            blocks = blocks.map { b -> updated.firstOrNull { it.id == b.id } ?: b }
            app().penalties.saveBlocks(updated)
            // Al permiso de cambiar una regla se le acaba el tiempo cuando la regla ya se libera sola: no hace falta avisar
            updated.filter { it.remainingMs == 0L && !it.isEditPass }.distinctBy { it.groupKey }.forEach {
                Notifier.showUnlocked(this, it, byTime = true)
            }
            // Recuperaciones cuya hora ya ha pasado sin cumplirlas
            val wall = System.currentTimeMillis()
            updated.filter { it.remainingMs > 0 && it.windowEnd > 0 && wall > it.windowEnd && it.current in WINDOWED }
                .distinctBy { it.groupKey }
                .forEach {
                    app().penalties.loseChallenge(it, "Se acabó la hora de la recuperación sin cumplirla. Ahora solo queda esperar")
                }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        if (stepSensorOn) getSystemService(SensorManager::class.java).unregisterListener(this)
        super.onDestroy()
    }

    /** El sensor de pasos solo se escucha mientras haya un reto de caminar pendiente. */
    private fun updateStepSensor() {
        val sensorManager = getSystemService(SensorManager::class.java)
        val needed = hasStepPermission(this) &&
            blocks.any { it.current == Challenge.PASOS && it.isActive() }
        if (needed && !stepSensorOn) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
            stepSensorOn = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (!needed && stepSensorOn) {
            sensorManager.unregisterListener(this)
            stepSensorOn = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // El contador cuenta los pasos desde que se encendió el móvil
        val total = event.values[0].toLong()
        // Si es la comprobación de una recuperación, solo cuentan los pasos dentro de su franja
        val changed = blocks.filter { it.current == Challenge.PASOS && it.isActive() && it.inWindow() }.mapNotNull { block ->
            when {
                block.stepBaseline < 0 -> block.copy(stepBaseline = total)
                // Se ha reiniciado el móvil: el contador vuelve a 0
                total < block.stepBaseline -> block.copy(stepBaseline = total - block.stepsDone)
                else -> {
                    val done = (total - block.stepBaseline).toInt()
                    // Se guarda cada 10 pasos para no escribir en la base de datos con cada uno
                    block.copy(stepsDone = done).takeIf { done - block.stepsDone >= 10 || done >= block.stepsRequired }
                }
            }
        }
        if (changed.isEmpty()) return
        blocks = blocks.map { b -> changed.firstOrNull { it.id == b.id } ?: b }
        scope.launch {
            val penalties = app().penalties
            penalties.saveBlocks(changed)
            changed.filter { it.stepsDone >= it.stepsRequired }.distinctBy { it.groupKey }.forEach {
                penalties.advance(it, "¡${stepsText(it.stepsRequired)} pasos!")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val TICK_MS = 30_000L
        private val WINDOWED = setOf(Challenge.PASOS, Challenge.LUGAR)
        private const val APP_LABEL = "Kokki"

        /** Paquetes de ajustes del sistema y de los fabricantes (Samsung, Xiaomi...) y del desinstalador. */
        private val SETTINGS_PACKAGE_HINTS = listOf("settings", "packageinstaller", "permissioncontroller", "accessibility", "securitycenter")

        fun isEnabled(context: Context): Boolean =
            context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> s.packageName == context.packageName && s.name == BlockerService::class.java.name } }

        fun hasStepPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        fun hasStepSensor(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
}

/**
 * Mientras se responde un reto (micrófono de Google, cámara...) no se bloquea nada,
 * aunque esas apps estén castigadas: si no, el propio reto sería imposible.
 */
object CaptureGuard {
    @Volatile
    private var until = 0L

    val active: Boolean get() = System.currentTimeMillis() < until

    fun start(minutes: Int) { until = System.currentTimeMillis() + minutes * 60_000L }

    fun stop() { until = 0L }
}
