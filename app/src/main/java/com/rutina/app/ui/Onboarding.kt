package com.rutina.app.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import com.rutina.app.data.Challenge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rutina.app.notify.Notifier
import kotlinx.coroutines.launch

private class WelcomePage(val icon: ImageVector, val kanji: String, val title: String, val points: List<String>)

private val PAGES = listOf(
    WelcomePage(
        Challenge.ZEN.icon, "克", "Kokki · 克己",
        listOf(
            "Kokki (克己) significa «disciplina» en japonés: dominar tus propios impulsos.",
            "Te ayuda a cumplir lo que te propones cada semana: deporte, gimnasio, trabajo, clases…",
            "Lo planificas, te aviso, marcas lo que haces… y si no cumples, hay castigo",
        ),
    ),
    WelcomePage(
        KIcons.Chat, "話", "Organízala hablando",
        listOf(
            "Cuéntale al asistente qué quieres hacer: «reparte el deporte en 4 días y el inglés en 2».",
            "Te propone la semana, te dice si es viable y la guarda con un toque.",
            "Cuando la tengas lista, fíjala: desde entonces solo podrás añadir tareas, no quitarlas ni moverlas.",
            "Usa una IA gratuita (Groq o Gemini de Google). La clave se pone en Ajustes.",
        ),
    ),
    WelcomePage(
        KIcons.Bell, "済", "Avisos y checks",
        listOf(
            "Te aviso antes de cada actividad para que te prepares.",
            "Al terminar te pregunto si la has hecho: «Hecho» o «No hecho».",
            "Marcar «No hecho» es definitivo, y si no contestas en 2 horas cuenta como no hecho.",
            "¿Sabes que no vas a poder? Cámbiala por otras tareas útiles (lavadora, barrer, recados…) y, si las haces, te libras.",
        ),
    ),
    WelcomePage(
        KIcons.Lock, "罰", "Castigos de verdad",
        listOf(
            "Desde que empieza tu compromiso, si no cumples, se bloquean las apps que elijas: WhatsApp, Instagram, Spotify…",
            "Para librarte antes, un reto: caminar, ir al gym, sentadillas contadas por la cámara, escanear tu QR, leer en inglés, programar… Puedes poner dos seguidos.",
            "Apagar el móvil no sirve: el tiempo de castigo solo corre con él encendido.",
            "Y premio: cada 5 tareas cumplidas ganas un comodín para saltarte un castigo.",
        ),
    ),
)

/** Pantallas de bienvenida: cómo funciona la app y, al final, los permisos. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { PAGES.size + 1 }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.size

    AppBackground {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES.size) } }) {
                    Text("Saltar", style = MaterialTheme.typography.titleSmall)
                }
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                if (page < PAGES.size) WelcomePageContent(PAGES[page]) else PermissionsPage()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    repeat(PAGES.size + 1) { i ->
                        val width by animateDpAsState(if (i == pager.currentPage) 28.dp else 10.dp, label = "dot")
                        Box(
                            Modifier
                                .size(width = width, height = 10.dp)
                                .background(
                                    if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    CircleShape,
                                ),
                        )
                    }
                }
                GradientButton(
                    if (last) "Empezar" else "Siguiente",
                    onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                )
            }
        }
    }
}

@Composable
private fun WelcomePageContent(page: WelcomePage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        PosterSun(page.icon, page.kanji)
        Spacer(Modifier.height(12.dp))
        Text(
            page.title,
            fontFamily = Script,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        page.points.forEach { point ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 8.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(point, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Ilustración de cartel: sol rojo con un icono crema y un sello con el kanji. */
@Composable
private fun PosterSun(icon: ImageVector, kanji: String) {
    val sun = MaterialTheme.colorScheme.primary
    val ink = inkBorder()
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.size(140.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(ink, radius = size.minDimension / 2.4f, center = center.copy(x = center.x + 6.dp.toPx(), y = center.y + 6.dp.toPx()))
            drawCircle(sun, radius = size.minDimension / 2.4f)
        }
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.align(Alignment.Center).size(56.dp))
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp)) { Hanko(kanji, size = 42.dp) }
    }
}

@Composable
private fun PermissionsPage() {
    val context = LocalContext.current
    var notificationsOk by remember { mutableStateOf(Notifier.canNotify(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOk = Notifier.canNotify(context)
    }
    LifecycleResumeEffect(Unit) {
        notificationsOk = Notifier.canNotify(context)
        onPauseOrDispose { }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Últimos permisos", "Sin esto no puedo avisarte ni castigarte", kanji = "設")
        CuteCard(Modifier.fillMaxWidth()) {
            if (notificationsOk) {
                Text("Notificaciones activadas ✓", style = MaterialTheme.typography.titleMedium)
            } else {
                Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Para avisarte antes de cada actividad y preguntarte si la has hecho.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                GradientButton("Permitir notificaciones", onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                })
            }
        }
        BlockerSetupCard()
        Text(
            "Puedes hacerlo más tarde desde la pestaña Castigos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
