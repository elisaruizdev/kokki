package com.rutina.app.ui

import androidx.compose.material3.Icon
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.app

/** Comodines que tienes y cuánto falta para el siguiente: un círculo por tarea. */
@Composable
fun RewardsCard(modifier: Modifier = Modifier) {
    val rewards by LocalContext.current.app().rewards.state.collectAsState()
    val left = rewards.goal - rewards.progress
    CuteCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // El número da un saltito cada vez que cambia
            val pop by animateFloatAsState(
                if (rewards.bonuses > 0) 1f else 0.9f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "bonus",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer { scaleX = pop; scaleY = pop }
                    .clip(CircleShape)
                    .background(if (rewards.bonuses > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, inkBorder(), CircleShape),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(KIcons.Bonus, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("${rewards.bonuses}", fontFamily = Script, fontSize = 18.sp, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    when (rewards.bonuses) {
                        0 -> "Aún no tienes comodines"
                        1 -> "Tienes 1 comodín"
                        else -> "Tienes ${rewards.bonuses} comodines"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(rewards.goal) { i ->
                        val filled = i < rewards.progress
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .then(
                                    if (filled) Modifier.background(MaterialTheme.colorScheme.primary)
                                    else Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                ),
                        )
                    }
                }
                Text(
                    "Cumple $left ${if (left == 1) "tarea más" else "tareas más"} y ganas otro. Cada comodín te salta un castigo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (rewards.used > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Has ganado ${rewards.earned} y usado ${rewards.used}.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
