package com.rutina.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rutina.app.notify.Notifier
import com.rutina.app.ui.AppBackground
import com.rutina.app.ui.ChatScreen
import com.rutina.app.ui.ChatViewModel
import com.rutina.app.ui.OnboardingScreen
import com.rutina.app.ui.PenaltiesScreen
import com.rutina.app.ui.ProgressScreen
import com.rutina.app.ui.RutinaTheme
import com.rutina.app.ui.SettingsScreen
import com.rutina.app.ui.WeekScreen

class MainActivity : ComponentActivity() {
    /** Pestaña pedida desde una notificación (p. ej. la pregunta del domingo abre Progreso). */
    private val requestedTab = mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = tabFrom(intent)
        setContent {
            RutinaTheme {
                val prefs = app().prefs
                var welcome by rememberSaveable { mutableStateOf(!prefs.onboardingDone) }
                if (welcome) {
                    OnboardingScreen(onFinish = {
                        prefs.onboardingDone = true
                        welcome = false
                    })
                } else {
                    RutinaRoot(requestedTab, onShowWelcome = { welcome = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.value = tabFrom(intent)
    }

    private fun tabFrom(intent: Intent?): Tab? = when (intent?.getStringExtra(Notifier.EXTRA_OPEN_TAB)) {
        Notifier.TAB_PROGRESS -> Tab.PROGRESO
        Notifier.TAB_PENALTIES -> Tab.CASTIGOS
        else -> null
    }
}

internal enum class Tab(val label: String, val icon: ImageVector) {
    SEMANA("Semana", Icons.Rounded.CalendarMonth),
    ASISTENTE("Asistente", Icons.Rounded.AutoAwesome),
    PROGRESO("Progreso", Icons.Rounded.Insights),
    CASTIGOS("Castigos", Icons.Rounded.Lock),
    AJUSTES("Ajustes", Icons.Rounded.Settings),
}

@Composable
private fun RutinaRoot(requestedTab: MutableState<Tab?>, onShowWelcome: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tab by rememberSaveable { mutableIntStateOf(Tab.SEMANA.ordinal) }
    // Mismo ViewModel que usa ChatScreen: así Progreso puede dejarle un mensaje preparado
    val chatViewModel: ChatViewModel = viewModel()

    LaunchedEffect(requestedTab.value) {
        requestedTab.value?.let {
            tab = it.ordinal
            requestedTab.value = null
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    AppBackground {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 0.dp,
                ) {
                    Tab.entries.forEach { item ->
                        val selected = tab == item.ordinal
                        // El icono de la pestaña elegida da un saltito
                        val iconScale by animateFloatAsState(
                            if (selected) 1.18f else 1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "tabIcon",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                tab = item.ordinal
                            },
                            icon = {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                Tab.entries[tab],
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 24 }) togetherWith fadeOut(tween(120))
                },
                modifier = Modifier.padding(padding),
                label = "tabs",
            ) { current ->
                when (current) {
                    Tab.SEMANA -> WeekScreen(onOpenAssistant = { tab = Tab.ASISTENTE.ordinal })
                    Tab.ASISTENTE -> ChatScreen(onOpenSettings = { tab = Tab.AJUSTES.ordinal }, vm = chatViewModel)
                    Tab.PROGRESO -> ProgressScreen(onAskAssistant = { text ->
                        chatViewModel.prefill(text)
                        tab = Tab.ASISTENTE.ordinal
                    })
                    Tab.CASTIGOS -> PenaltiesScreen()
                    Tab.AJUSTES -> SettingsScreen(onShowWelcome)
                }
            }
        }
    }
}
