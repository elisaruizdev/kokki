package com.rutina.app.penalty

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/** Ubicación para el reto de ir a un sitio. Solo usa el GPS del móvil (sin servicios de pago ni mapas). */
object Places {
    /** Distancia a la que se considera que estás en el sitio. */
    const val RADIUS_M = 150f

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Ubicación actual (o null si no hay permiso o el GPS no responde en 30 s). */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { manager.isProviderEnabled(it) }
        for (provider in providers) {
            val location = withTimeoutOrNull(30_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) {
                        if (cont.isActive) cont.resume(it)
                    }
                }
            }
            if (location != null) return location
        }
        return null
    }

    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val result = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, result)
        return result[0]
    }

    /**
     * Saca las coordenadas de un enlace de Google Maps (o de un texto «40.41, -3.70»).
     * Los enlaces cortos (maps.app.goo.gl) se siguen hasta la dirección completa, que lleva las coordenadas.
     */
    suspend fun fromLink(text: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        coordinatesIn(text)?.let { return@withContext it }
        var url = Regex("""https?://\S+""").find(text)?.value ?: return@withContext null
        repeat(6) {
            coordinatesIn(url)?.let { return@withContext it }
            val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext null
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                val location = connection.getHeaderField("Location")
                if (location == null) {
                    val body = runCatching { connection.inputStream.bufferedReader().use { it.readText().take(200_000) } }.getOrNull()
                    return@withContext body?.let { coordinatesIn(it) }
                }
                url = URL(URL(url), location).toString()
            } catch (e: Exception) {
                return@withContext null
            } finally {
                connection.disconnect()
            }
        }
        null
    }

    private fun coordinatesIn(text: String): Pair<Double, Double>? {
        val decoded = runCatching { java.net.URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
        val patterns = listOf(
            Regex("""!3d(-?\d{1,2}\.\d+)!4d(-?\d{1,3}\.\d+)"""),
            Regex("""@(-?\d{1,2}\.\d+),(-?\d{1,3}\.\d+)"""),
            Regex("""[?&](?:q|query|ll|center)=(-?\d{1,2}\.\d+),\s*(-?\d{1,3}\.\d+)"""),
            Regex("""^\s*(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*$"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(decoded) ?: continue
            val lat = match.groupValues[1].toDoubleOrNull() ?: continue
            val lng = match.groupValues[2].toDoubleOrNull() ?: continue
            if (lat in -90.0..90.0 && lng in -180.0..180.0) return lat to lng
        }
        return null
    }
}
