package com.rutina.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rutina.app.data.Category
import com.rutina.app.data.Challenge
import com.rutina.app.data.PoseExercise

/** Iconos de línea redondeados de toda la app, en vez de emojis. Se pintan con los colores del cartel. */
object KIcons {
    val Lock = Icons.Rounded.Lock
    val Unlock = Icons.Rounded.LockOpen
    val Bonus = Icons.Rounded.ConfirmationNumber
    val Free = Icons.Rounded.Cloud
    val Flower = Icons.Rounded.LocalFlorist
    val Bell = Icons.Rounded.Notifications
    val Ai = Icons.Rounded.AutoAwesome
    val Fast = Icons.Rounded.Bolt
    val Person = Icons.Rounded.Face
    val Palette = Icons.Rounded.Palette
    val Book = Icons.AutoMirrored.Rounded.MenuBook
    val Light = Icons.Rounded.LightMode
    val Dark = Icons.Rounded.DarkMode
    val Phone = Icons.Rounded.Smartphone
    val Warning = Icons.Rounded.WarningAmber
    val Swap = Icons.Rounded.SwapHoriz
    val Delete = Icons.Rounded.DeleteOutline
    val Restart = Icons.Rounded.RestartAlt
    val Show = Icons.Rounded.Visibility
    val Hide = Icons.Rounded.VisibilityOff
    val Calendar = Icons.Rounded.CalendarMonth
    val Timer = Icons.Rounded.Timer
    val Chart = Icons.Rounded.BarChart
    val Detail = Icons.Rounded.Insights
    val Diary = Icons.Rounded.EditNote
    val Chat = Icons.Rounded.ChatBubble
    val Grow = Icons.AutoMirrored.Rounded.TrendingUp
    val Done = Icons.Rounded.CheckCircle
    val Key = Icons.Rounded.Key
    val Place = Icons.Rounded.Place
    val Camera = Icons.Rounded.PhotoCamera
    val Mic = Icons.Rounded.Mic
    val Wait = Icons.Rounded.HourglassBottom
}

val Category.icon: ImageVector
    get() = when (this) {
        Category.DEPORTE -> Icons.AutoMirrored.Rounded.DirectionsRun
        Category.GIMNASIO -> Icons.Rounded.FitnessCenter
        Category.TRABAJO -> Icons.Rounded.Work
        Category.TRAYECTO -> Icons.Rounded.Train
        Category.INGLES -> Icons.Rounded.Translate
        Category.ESTUDIO -> Icons.AutoMirrored.Rounded.MenuBook
        Category.DESCANSO -> Icons.Rounded.LocalCafe
        Category.CASA -> Icons.Rounded.CleaningServices
        Category.OTRO -> Icons.Rounded.AutoAwesome
    }

val Challenge.icon: ImageVector
    get() = when (this) {
        Challenge.NINGUNO -> Icons.Rounded.HourglassBottom
        Challenge.PASOS -> Icons.AutoMirrored.Rounded.DirectionsWalk
        Challenge.LUGAR -> Icons.Rounded.Place
        Challenge.RECUPERAR -> Icons.Rounded.EventRepeat
        Challenge.CONTEO -> Icons.Rounded.SportsGymnastics
        Challenge.QR -> Icons.Rounded.QrCodeScanner
        Challenge.ZEN -> Icons.Rounded.SelfImprovement
        Challenge.REFLEXION -> Icons.Rounded.EditNote
        Challenge.INGLES -> Icons.Rounded.RecordVoiceOver
        Challenge.CODIGO -> Icons.Rounded.Code
        Challenge.LECTURA -> Icons.Rounded.AutoStories
        Challenge.PREGUNTA -> Icons.Rounded.Mic
        Challenge.FOTO -> Icons.Rounded.PhotoCamera
        Challenge.EJERCICIO -> Icons.Rounded.Videocam
    }

val PoseExercise.icon: ImageVector
    get() = when (this) {
        PoseExercise.SENTADILLAS -> Icons.Rounded.AccessibilityNew
        PoseExercise.FLEXIONES -> Icons.Rounded.FitnessCenter
        PoseExercise.JUMPING -> Icons.Rounded.SportsGymnastics
    }

/** Color del icono de una categoría: su tono de cartel, algo más claro de noche para que se vea. */
@Composable
fun categoryTint(category: Category): Color =
    if (LocalPastels.current.isDark) lerp(Color(category.color), Color.White, 0.4f) else Color(category.color)

/** Círculo de color con un icono dentro, como un sello. */
@Composable
fun IconBadge(icon: ImageVector, background: Color, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.54f))
    }
}

@Composable
fun CategoryBadge(category: Category, size: Dp = 48.dp) =
    IconBadge(category.icon, categorySoft(category.softColor, category.color), categoryTint(category), size)

/** Icono delante de un texto, del tamaño de la letra. */
@Composable
fun IconText(
    icon: ImageVector,
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val iconSize = (style.fontSize.value * 1.25f).dp
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(6.dp))
        Text(text, style = style, color = if (color == Color.Unspecified) LocalContentColor.current else color)
    }
}
