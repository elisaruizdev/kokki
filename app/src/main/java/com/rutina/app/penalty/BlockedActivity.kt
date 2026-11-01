package com.rutina.app.penalty

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutina.app.app
import com.rutina.app.ui.AppBackground
import com.rutina.app.ui.BlockGroupCard
import com.rutina.app.ui.GradientButton
import com.rutina.app.ui.Hanko
import com.rutina.app.ui.RutinaTheme
import com.rutina.app.ui.Script

/** Pantalla que aparece al abrir una app castigada, o al tocar la notificación del castigo. */
class BlockedActivity : ComponentActivity() {
    private val target = mutableStateOf(Target(null, null, false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        target.value = targetFrom(intent)
        setContent {
            RutinaTheme { BlockedScreen(target.value, onHome = ::goHome) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        target.value = targetFrom(intent)
    }

    /** Salir siempre al inicio: volver atrás llevaría otra vez a la app bloqueada. */
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun targetFrom(intent: Intent) = Target(
        packageName = intent.getStringExtra(EXTRA_PACKAGE),
        activityId = intent.getLongExtra(EXTRA_ACTIVITY, -1).takeIf { it >= 0 },
        guard = intent.getBooleanExtra(EXTRA_GUARD, false),
    )

    /** [guard]: se ha intentado abrir los ajustes de Kokki durante un castigo. */
    data class Target(val packageName: String?, val activityId: Long?, val guard: Boolean)

    companion object {
        private const val EXTRA_PACKAGE = "package"
        private const val EXTRA_ACTIVITY = "activity"
        private const val EXTRA_GUARD = "guard"

        fun forGuard(context: Context): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_GUARD, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun forPackage(context: Context, packageName: String): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_PACKAGE, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun forActivity(context: Context, activityId: Long): Intent =
            Intent(context, BlockedActivity::class.java)
                .putExtra(EXTRA_ACTIVITY, activityId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

@Composable
private fun BlockedScreen(target: BlockedActivity.Target, onHome: () -> Unit) {
    val context = LocalContext.current
    val blocks by remember { context.app().penalties.observeBlocks() }.collectAsState(initial = null)
    BackHandler(onBack = onHome)

    val groups = (blocks ?: emptyList()).filter { it.isActive() }.groupBy { it.groupKey }.values.filter { group ->
        target.guard ||
            group.any { (target.packageName == null || it.packageName == target.packageName) && (target.activityId == null || it.activityId == target.activityId) }
    }
    val appLabel = blocks?.firstOrNull { it.packageName == target.packageName }?.appLabel

    AppBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            if (blocks == null) return@Column
            if (groups.isEmpty()) {
                Hanko("済", size = 72.dp, filled = false)
                Text("¡Desbloqueado!", fontFamily = Script, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
                Text("Ya no tienes ningún castigo aquí", style = MaterialTheme.typography.bodyLarge)
                val launch = target.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                if (launch != null) {
                    GradientButton("Abrir ${appLabel ?: "la app"}", onClick = {
                        context.startActivity(launch)
                        (context as? ComponentActivity)?.finish()
                    })
                }
            } else {
                Hanko("禁", size = 72.dp)
                Text(
                    when {
                        target.guard -> "Los ajustes de Kokki están cerrados"
                        appLabel != null -> "$appLabel está en pausa"
                        else -> "Apps en pausa"
                    },
                    fontFamily = Script,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (target.guard) {
                        "Mientras dure un castigo no puedes desactivar ni desinstalar Kokki. Cumple el reto o espera"
                    } else {
                        "Es tu castigo por no cumplir lo que te propusiste. Respira, haz el reto o espera"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                groups.forEach { BlockGroupCard(it, Modifier.fillMaxWidth()) }
            }
            TextButton(onClick = onHome) { Text("Volver al inicio") }
        }
    }
}
