package com.rutina.app.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rutina.app.penalty.BlockerService

/**
 * Guía para dar a Kokki el permiso de bloquear apps. Android no deja que una app se lo conceda sola,
 * así que se abre directamente cada pantalla y se explica qué tocar. Detecta sola cuándo está hecho.
 */
@Composable
fun BlockerSetupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(BlockerService.isEnabled(context)) }
    // Tras volver del paso 1 sin éxito casi seguro es el «ajuste restringido»: se resalta el paso 2
    var triedStep1 by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        enabled = BlockerService.isEnabled(context)
        onPauseOrDispose { }
    }

    CuteCard(modifier.fillMaxWidth()) {
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hanko("済", size = 40.dp, filled = false)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Bloqueo de apps activado", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kokki ya puede bloquear apps cuando no cumplas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@CuteCard
        }

        Text("Activa el bloqueo de apps", style = MaterialTheme.typography.titleMedium)
        Text(
            "Android pide que lo actives tú a mano. Son 2 pasos y te llevo a cada pantalla.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        SetupStep(
            number = 1,
            title = "Enciende «Kokki: castigos»",
            text = "Toca el interruptor y luego «Permitir».",
            button = "Abrir el permiso",
            highlighted = !triedStep1,
            onClick = {
                triedStep1 = true
                openBlockerSettings(context)
            },
        )
        Spacer(Modifier.height(12.dp))
        SetupStep(
            number = 2,
            title = "¿Sale «Ajuste restringido» o el interruptor está en gris?",
            text = "Es normal si instalaste la app desde un archivo. En la pantalla que se abre:",
            button = "Abrir información de Kokki",
            highlighted = triedStep1,
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        ) {
            MenuMock()
            Text(
                "Confirma con tu huella o PIN y vuelve al paso 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    text: String,
    button: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Row(Modifier.alpha(if (highlighted) 1f else 0.6f)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontFamily = Script, fontSize = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
            GradientButton(button, onClick = onClick)
        }
    }
}

/** Dibujo del menú ⋮ de la información de la app, para que se vea qué opción tocar. */
@Composable
private fun MenuMock() {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, inkBorder(), shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kokki", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("⋮", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("  ← 1.º toca aquí", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Permitir ajustes restringidos  ← 2.º",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/** Abre directamente el interruptor de Kokki en accesibilidad (o la lista, si el móvil no lo permite). */
fun openBlockerSettings(context: Context) {
    val component = ComponentName(context, BlockerService::class.java).flattenToString()
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(details)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .putExtra(":settings:fragment_args_key", component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: SecurityException) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
