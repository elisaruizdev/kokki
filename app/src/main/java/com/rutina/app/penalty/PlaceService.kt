package com.rutina.app.penalty

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.rutina.app.app
import com.rutina.app.data.AppBlock
import com.rutina.app.data.Challenge
import com.rutina.app.data.durationText
import com.rutina.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lo que ve el reto del sitio mientras se comprueba: a qué distancia estás y si cuenta. */
data class PlaceStatus(val groupKey: String, val distanceM: Float?, val inside: Boolean, val text: String)

/**
 * Comprueba con el GPS que pasas el tiempo pedido en el sitio del reto. Es un servicio en primer plano
 * (con su notificación) para que siga contando aunque guardes el móvil en el bolsillo.
 */
class PlaceService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var last: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val activityId = intent?.getLongExtra(EXTRA_ACTIVITY, -1) ?: -1
        val ruleId = intent?.getLongExtra(EXTRA_RULE, -1) ?: -1
        // Sin permiso de ubicación Android no deja arrancar este servicio: se para sin más
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, Notifier.placeTracking(this, "Buscando tu ubicación…"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started || activityId < 0 || !Places.hasPermission(this)) {
            stop()
            return START_NOT_STICKY
        }
        val manager = getSystemService(LocationManager::class.java)
        manager.removeUpdates(this)
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }.forEach {
            manager.requestLocationUpdates(it, 20_000L, 0f, this, Looper.getMainLooper())
        }
        job?.cancel()
        job = scope.launch { track(activityId, ruleId) }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val previous = last
        // Se queda con la lectura más precisa de las recientes
        if (previous == null || location.accuracy <= previous.accuracy || location.time - previous.time > 60_000) last = location
    }

    private suspend fun track(activityId: Long, ruleId: Long) {
        val penalties = app().penalties
        var lastTick = SystemClock.elapsedRealtime()
        var insideMs = -1L
        while (true) {
            val block = penalties.activeBlocks().firstOrNull { it.activityId == activityId && it.ruleId == ruleId }
            // Fuera de la franja de la recuperación no tiene sentido gastar batería con el GPS
            if (block == null || block.current != Challenge.LUGAR || !block.inWindow()) break
            if (insideMs < 0) insideMs = block.progress * 60_000L
            val options = block.optionsData
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastTick
            lastTick = now

            val fix = last?.takeIf { System.currentTimeMillis() - it.time < 3 * 60_000 }
            val distance = fix?.let { Places.distance(it.latitude, it.longitude, options.placeLat, options.placeLng) }
            val inside = fix != null && distance!! <= Places.RADIUS_M + fix.accuracy.coerceAtMost(100f)
            val text = when {
                fix == null -> "Buscando tu ubicación… sal a un sitio con cielo abierto si tarda"
                inside -> "Estás en ${options.placeName.ifBlank { "tu sitio" }}: ${minutes(insideMs)} de ${durationText(options.placeMinutes)}"
                else -> "Estás a ${distanceText(distance!!)} de ${options.placeName.ifBlank { "tu sitio" }}"
            }
            status.value = PlaceStatus(block.groupKey, distance, inside, text)
            Notifier.updatePlaceTracking(this, text)

            if (inside && block.inWindow()) {
                insideMs += elapsed
                val doneMinutes = (insideMs / 60_000).toInt()
                if (doneMinutes != block.progress) penalties.setProgress(block, doneMinutes)
                if (doneMinutes >= options.placeMinutes) {
                    penalties.advance(block, "¡Has estado ${durationText(options.placeMinutes)} en ${options.placeName.ifBlank { "tu sitio" }}!")
                    break
                }
            }
            delay(TICK_MS)
        }
        stop()
    }

    private fun minutes(ms: Long) = durationText((ms / 60_000).toInt())

    private fun distanceText(meters: Float) = if (meters < 1000) "${meters.toInt()} m" else String.format("%.1f km", meters / 1000)

    private fun stop() {
        status.value = null
        getSystemService(LocationManager::class.java).removeUpdates(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        getSystemService(LocationManager::class.java).removeUpdates(this)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_RULE = "rule"
        const val NOTIFICATION_ID = Int.MAX_VALUE - 3
        private const val TICK_MS = 20_000L

        private val status = MutableStateFlow<PlaceStatus?>(null)
        val current: StateFlow<PlaceStatus?> = status.asStateFlow()

        fun start(context: Context, block: AppBlock) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PlaceService::class.java).putExtra(EXTRA_ACTIVITY, block.activityId).putExtra(EXTRA_RULE, block.ruleId),
            )
        }
    }
}
