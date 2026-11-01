package com.rutina.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import com.rutina.app.ai.Ai
import androidx.compose.runtime.saveable.rememberSaveable
import com.rutina.app.data.Rewards
import androidx.compose.runtime.collectAsState
import com.rutina.app.data.ThemeMode
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rutina.app.app
import com.rutina.app.notify.Notifier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onShowWelcome: () -> Unit) {
    val context = LocalContext.current
    val app = context.app()
    val prefs = app.prefs

    var profile by remember { mutableStateOf(prefs.profile) }
    var reminder by remember { mutableIntStateOf(prefs.defaultReminder) }

    // Los permisos pueden cambiar en los ajustes del sistema: se vuelven a leer al volver a la app
    var notificationsOk by remember { mutableStateOf(true) }
    var exactAlarmsOk by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        notificationsOk = Notifier.canNotify(context)
        exactAlarmsOk = app.scheduler.canScheduleExact()
        onPauseOrDispose { }
    }

    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val rewards by app.rewards.state.collectAsState()
    val theme by app.themeMode

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenTitle("Ajustes", "Deja la app a tu gusto", kanji = "設")
        val aiReady = Ai.client(prefs) != null
        MenuRow(KIcons.Ai, "Inteligencia artificial", if (aiReady) "${prefs.provider.label} · lista" else "Falta la clave gratuita", warn = !aiReady) {
            section = SettingsSection.AI
        }
        MenuRow(KIcons.Bell, "Recordatorios y permisos", if (notificationsOk && exactAlarmsOk) reminderLabel(reminder) + " antes" else "Falta algún permiso", warn = !(notificationsOk && exactAlarmsOk)) {
            section = SettingsSection.REMINDERS
        }
        MenuRow(KIcons.Person, "Sobre ti", profile.ifBlank { "Lo que tu asistente debe saber siempre" }.take(60)) { section = SettingsSection.ABOUT }
        MenuRow(KIcons.Bonus, "Comodines", "1 cada ${rewards.goal} tareas cumplidas") { section = SettingsSection.BONUS }
        MenuRow(KIcons.Palette, "Apariencia", theme.label) { section = SettingsSection.LOOK }
        MenuRow(KIcons.Book, "Cómo funciona la app", "Ver otra vez la bienvenida", onClick = onShowWelcome)
    }

    when (section) {
        SettingsSection.AI -> SubScreen("Inteligencia artificial", onBack = { section = null }) { AiSettingsCard() }
        SettingsSection.REMINDERS -> SubScreen("Recordatorios", onBack = { section = null }) {
                CuteCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Recordatorios")
                    Text("Aviso por defecto antes de cada actividad", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        REMINDER_OPTIONS.forEach { m ->
                            FilterChip(
                                selected = reminder == m,
                                onClick = { reminder = m; prefs.defaultReminder = m },
                                label = { Text(reminderLabel(m)) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        label = "Notificaciones",
                        ok = notificationsOk,
                        onFix = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    )
                    PermissionRow(
                        label = "Alarmas a la hora exacta",
                        ok = exactAlarmsOk,
                        onFix = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    GradientButton("Probar notificación", onClick = { Notifier.showTest(context) })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Cada domingo a las 20:00 te preguntaré qué es lo más difícil que llevas. " +
                            "Si algún aviso no llega, quita la optimización de batería para Kokki en los ajustes del móvil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
        SettingsSection.ABOUT -> SubScreen("Sobre ti", onBack = { section = null }) {
                CuteCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Sobre ti")
                    Text(
                        "Lo que escribas aquí se le da siempre a tu asistente, así no tienes que repetirlo cada semana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = profile,
                        onValueChange = { profile = it; prefs.profile = it },
                        placeholder = { Text("Ej.: Trabajo miércoles y jueves de 9:00 a 17:00. Tardo 40 min en llegar. Prefiero entrenar por la mañana.") },
                        minLines = 3,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

        }
        SettingsSection.BONUS -> SubScreen("Comodines", onBack = { section = null }) {
            RewardsCard()
                CuteCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Comodines")
                    Text("Cada cuántas tareas cumplidas ganas un comodín para saltarte un castigo.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Rewards.GOAL_OPTIONS.forEach { goal ->
                            FilterChip(
                                selected = rewards.goal == goal,
                                onClick = { app.rewards.setGoal(goal) },
                                label = { Text("$goal tareas") },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }

        }
        SettingsSection.LOOK -> SubScreen("Apariencia", onBack = { section = null }) {
                CuteCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Apariencia")
                    Text("Elige si quieres la app clara (papel crema) u oscura.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = theme == mode,
                                onClick = {
                                    prefs.themeMode = mode
                                    app.themeMode.value = mode
                                },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    Icon(
                                        when (mode) {
                                            ThemeMode.CLARO -> KIcons.Light
                                            ThemeMode.OSCURO -> KIcons.Dark
                                            ThemeMode.SISTEMA -> KIcons.Phone
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }

        }
        null -> Unit
    }
}

private enum class SettingsSection { AI, REMINDERS, ABOUT, BONUS, LOOK }

/** Fila del menú de Ajustes: icono, nombre, estado actual y flecha. */
@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, warn: Boolean = false, onClick: () -> Unit) {
    CuteCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun PermissionRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (ok) {
            Pill("✓ Activado", background = DoneGreen.copy(alpha = 0.2f), color = MaterialTheme.colorScheme.onSurface)
        } else {
            TextButton(onClick = onFix) { Text("Activar", color = MaterialTheme.colorScheme.error) }
        }
    }
}
