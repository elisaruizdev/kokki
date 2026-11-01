package com.rutina.app.ui

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fondo de papel washi con sol (o luna), olas seigaiha y pétalos de sakura cayendo. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val pastels = LocalPastels.current
    Box(Modifier.fillMaxSize().background(pastels.background)) {
        // Grano del papel en su propia capa: así no se redibuja con cada fotograma de los pétalos
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            drawPoints(
                GRAIN.map { (x, y) -> Offset(x * size.width, y * size.height) },
                PointMode.Points,
                pastels.grain,
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(pastels.sun, radius = 96.dp.toPx(), center = Offset(size.width * 0.9f, 54.dp.toPx()))
        }
        Seigaiha(pastels.wave, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(230.dp))
        // Pétalos quietos, como en un cartel: sin animación no se redibuja nada mientras no cambie la pantalla
        Canvas(Modifier.fillMaxSize()) {
            PETALS.forEach { p ->
                val petalScale = p.size.dp.toPx() / 10f
                withTransform({
                    translate(p.x * size.width, p.y * size.height)
                    rotate(p.spin, pivot = Offset.Zero)
                    scale(petalScale, petalScale, pivot = Offset.Zero)
                }) {
                    drawPath(PetalPath, pastels.petal)
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

private val GRAIN = Random(3).let { r -> List(2_500) { r.nextFloat() to r.nextFloat() } }

private class Petal(val x: Float, val y: Float, val size: Float, val spin: Float)

private val PETALS = Random(8).let { r ->
    List(12) { Petal(r.nextFloat(), r.nextFloat(), 7f + r.nextFloat() * 6f, r.nextFloat() * 360f) }
}

/** Pétalo de cerezo de unos 10 px de largo con la muesca típica en la punta, centrado en el origen. */
private val PetalPath = Path().apply {
    moveTo(0f, 5f)
    cubicTo(-4.5f, 2f, -5f, -3.5f, -2f, -5f)
    lineTo(0f, -3.6f)
    lineTo(2f, -5f)
    cubicTo(5f, -3.5f, 4.5f, 2f, 0f, 5f)
    close()
}

/** Patrón de olas japonés (seigaiha) que se difumina hacia arriba. */
@Composable
private fun Seigaiha(color: Color, modifier: Modifier) {
    Canvas(modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        val r = 30.dp.toPx()
        val stroke = Stroke(1.4.dp.toPx())
        var row = 0
        var y = 0f
        while (y < size.height + r) {
            var x = if (row % 2 == 0) 0f else r
            while (x < size.width + r) {
                val center = Offset(x, y)
                drawCircle(Color.Black, r, center, blendMode = BlendMode.Clear)
                for (k in 0..3) drawCircle(color, r * (1f - k * 0.22f), center, style = stroke)
                x += 2 * r
            }
            y += r / 2
            row++
        }
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Sello japonés (hanko) con un kanji: relleno para títulos, solo contorno para marcar lo cumplido. */
@Composable
fun Hanko(text: String, size: Dp = 44.dp, filled: Boolean = true, rotation: Float = -6f) {
    val shape = if (filled) RoundedCornerShape(size * 0.2f) else CircleShape
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .rotate(rotation)
            .clip(shape)
            .then(if (filled) Modifier.background(Shu) else Modifier.border(2.dp, Shu, shape)),
    ) {
        Text(
            text,
            fontFamily = Fude,
            fontSize = (size.value * 0.56f).sp,
            lineHeight = (size.value * 0.6f).sp,
            color = if (filled) Color(0xFFFBF3E4) else Shu,
        )
    }
}

/** Título de pantalla en letra de cartel, con un sello rojo con su kanji y subtítulo. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, kanji: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (kanji != null) {
            Hanko(kanji, size = 38.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                title,
                fontFamily = Script,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Tarjeta de papel con borde de tinta y una sombra dura desplazada, como un cartel impreso. */
@Composable
fun CuteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    PrintShadow(shape, modifier, pressed = pressed) {
        Surface(
            modifier = if (onClick != null) {
                Modifier.clip(shape).clickable(interaction, LocalIndication.current) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
            } else {
                Modifier
            },
            shape = shape,
            color = color,
            border = BorderStroke(1.5.dp, inkBorder()),
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Borde de tinta: negro de día, crema suave de noche. */
@Composable
fun inkBorder(): Color =
    if (LocalPastels.current.isDark) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline

/**
 * Sombra dura desplazada abajo a la derecha, sin difuminar. Mientras se pulsa, el contenido
 * baja hasta tapar la sombra, como un botón de verdad.
 */
@Composable
private fun PrintShadow(
    shape: Shape,
    modifier: Modifier = Modifier,
    offset: Dp = 4.dp,
    pressed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shadowColor = LocalPastels.current.cardShadow
    val push by animateFloatAsState(
        if (pressed) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "push",
    )
    Box(modifier.padding(end = offset, bottom = offset)) {
        Box(Modifier.matchParentSize().offset(offset, offset).background(shadowColor, shape))
        Box(Modifier.graphicsLayer {
            translationX = push * offset.toPx()
            translationY = push * offset.toPx()
        }) { content() }
    }
}

/** Interacción de un elemento pulsable y si está pulsado ahora mismo. */
class Press(val interaction: MutableInteractionSource, val pressed: Boolean)

@Composable
fun rememberPress(): Press {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return Press(interaction, pressed)
}

/** Encoge un poco el elemento mientras se pulsa, con rebote al soltar. */
fun Modifier.pressScale(press: Press, amount: Float = 0.92f): Modifier = composed {
    val scale by animateFloatAsState(
        if (press.pressed) amount else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Clic con efecto de encoger y una vibración suave. */
fun Modifier.bouncyClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val press = rememberPress()
    val haptic = LocalHapticFeedback.current
    pressScale(press).clickable(press.interaction, LocalIndication.current, enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}

/**
 * Explosión de pétalos de sakura desde el centro cuando cambia [trigger] (si [play] es cierto).
 * No salta al aparecer en pantalla, y solo se anima un instante, así que no gasta batería.
 */
@Composable
fun PetalBurst(trigger: Any?, play: Boolean, modifier: Modifier = Modifier, petals: Int = 10, radius: Dp = 56.dp) {
    val progress = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger != seen) {
            seen = trigger
            if (play) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            }
        }
    }
    val color = Shu
    val pink = LocalPastels.current.petal.copy(alpha = 1f)
    if (progress.value < 1f) {
        Canvas(modifier) {
            val t = progress.value
            val distance = radius.toPx() * t
            // El pétalo dibujado mide 10 unidades: empieza en unos 15 dp y encoge al alejarse
            val scalePx = (1.3f - 0.5f * t) * 1.2.dp.toPx()
            repeat(petals) { i ->
                val angle = (i * 2 * PI / petals + i * 0.37).toFloat()
                val position = center + Offset(cos(angle) * distance, sin(angle) * distance - 10.dp.toPx() * t)
                withTransform({
                    translate(position.x, position.y)
                    rotate(angle * 57f + t * 220f, pivot = Offset.Zero)
                    scale(scalePx, scalePx, pivot = Offset.Zero)
                }) {
                    drawPath(PetalPath, (if (i % 3 == 0) color else pink).copy(alpha = 1f - t * t))
                }
            }
        }
    }
}

/** Número que sube contando hasta su valor, para los porcentajes. */
@Composable
fun CountUpText(value: Int, suffix: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val shown by animateIntAsState(value, tween(900, easing = FastOutSlowInEasing), label = "countUp")
    Text("$shown$suffix", style = style, color = color, modifier = modifier)
}

/** Botón rojo bermellón con borde de tinta y sombra dura. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    val press = rememberPress()
    val haptic = LocalHapticFeedback.current
    Box(modifier.alpha(if (enabled) 1f else 0.45f).pressScale(press, 0.96f)) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(LocalPastels.current.accent)
                .border(1.5.dp, inkBorder(), shape)
                .clickable(press.interaction, LocalIndication.current, enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                }
                .padding(horizontal = 22.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Círculo rojo, como el sol de los carteles, con contenido centrado (FAB, botón enviar...). */
@Composable
fun GradientCircle(onClick: () -> Unit, size: Dp = 56.dp, enabled: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val press = rememberPress()
    val haptic = LocalHapticFeedback.current
    PrintShadow(CircleShape, Modifier.alpha(if (enabled) 1f else 0.45f), offset = 4.dp, pressed = press.pressed) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(LocalPastels.current.accent)
                .border(1.5.dp, inkBorder(), CircleShape)
                .clickable(press.interaction, LocalIndication.current, enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) { content() }
        }
    }
}

/** Anillo de progreso con degradado. [progress] entre 0 y 1. */
@Composable
fun ProgressRing(progress: Float, size: Dp, stroke: Dp = 12.dp, content: @Composable BoxScope.() -> Unit) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = 60f), label = "ring")
    val track = MaterialTheme.colorScheme.outlineVariant
    val red = MaterialTheme.colorScheme.primary
    val colors = listOf(red, red)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(strokePx / 2, strokePx / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
            if (animated > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(colors),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

@Composable
fun Pill(
    text: String,
    background: Color,
    color: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Fondo pastel de una categoría, más oscuro en modo noche para que el emoji resalte. */
@Composable
fun categorySoft(soft: Long, accent: Long): Color =
    if (LocalPastels.current.isDark) Color(accent).copy(alpha = 0.25f) else Color(soft)

fun motivation(percent: Int?): String = when {
    percent == null -> "Tu semana te está esperando"
    percent >= 90 -> "すごい! Lo estás bordando"
    percent >= 70 -> "¡Vas genial, sigue así!"
    percent >= 50 -> "Vas por buen camino"
    percent >= 25 -> "Poco a poco, como el bambú"
    else -> "Cada pasito cuenta. 頑張って"
}

val CHEERS = listOf(
    "¡Bien hecho! よくできました", "¡Sugoi!", "¡Una menos!", "¡Ganbatte! 頑張って",
    "¡Orgullo total!", "¡Sumando a tu semana!", "¡Imparable!",
)

/** Pestañas dentro de una pantalla, para repartir el contenido y no tener que bajar tanto. */
@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.5.dp, inkBorder(), shape)
            .padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(if (isSelected) Modifier.background(LocalPastels.current.accent) else Modifier)
                    .bouncyClick { onSelect(i) }
                    .padding(vertical = 9.dp),
            )
        }
    }
}

/** Pantalla completa encima de la actual, con flecha para volver. Para secciones que no caben en una sola pantalla. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onBack,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AppBackground {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                        )
                    }
                    Text(title, fontFamily = Script, fontSize = 20.sp)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content,
                )
            }
        }
    }
}
