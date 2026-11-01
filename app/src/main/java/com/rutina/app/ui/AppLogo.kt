package com.rutina.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Logotipo de una app instalada (o un hueco gris si ya no está instalada). */
@Composable
fun AppLogo(packageName: String, size: Dp = 32.dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap() }.getOrNull()
        }
    }
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

/** Logotipos de varias apps seguidos. */
@Composable
fun AppLogos(packages: List<String>, size: Dp = 24.dp, max: Int = 6) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        packages.take(max).forEach { AppLogo(it, size) }
    }
}
