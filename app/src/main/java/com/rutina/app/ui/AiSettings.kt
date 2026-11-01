package com.rutina.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rutina.app.ai.AiException
import com.rutina.app.ai.AiProvider
import com.rutina.app.ai.GeminiClient
import com.rutina.app.ai.GroqClient
import com.rutina.app.ai.Turn
import com.rutina.app.app
import com.rutina.app.data.Prefs
import kotlinx.coroutines.launch

/** Elegir la IA gratuita (Groq o Gemini), pegar su clave y probarla de verdad. */
@Composable
fun AiSettingsCard() {
    val context = LocalContext.current
    val prefs = context.app().prefs
    var provider by remember { mutableStateOf(prefs.provider) }

    CuteCard(Modifier.fillMaxWidth()) {
        Text("Inteligencia artificial (gratis)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "La usan el asistente y los retos de pregunta, foto y vídeo. El resto de retos funcionan sin IA ni internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderTile(KIcons.Fast, "Groq", "Rápida (1-2 s). Recomendada", provider == AiProvider.GROQ, Modifier.weight(1f)) {
                provider = AiProvider.GROQ
                prefs.provider = AiProvider.GROQ
            }
            ProviderTile(KIcons.Ai, "Gemini", "De Google. Ve fotos y vídeos", provider == AiProvider.GEMINI, Modifier.weight(1f)) {
                provider = AiProvider.GEMINI
                prefs.provider = AiProvider.GEMINI
            }
        }
        Spacer(Modifier.height(12.dp))
        when (provider) {
            AiProvider.GROQ -> KeyEditor(
                steps = "1. Entra en console.groq.com con tu cuenta de Google.\n" +
                    "2. Ve a «API Keys» y pulsa «Create API Key».\n" +
                    "3. Copia la clave (empieza por «gsk_») y pégala aquí.",
                linkLabel = "Conseguir clave de Groq",
                link = "https://console.groq.com/keys",
                label = "Clave de Groq",
                initial = prefs.groqKey,
                onChange = { prefs.groqKey = it },
                test = { testGroq(prefs) },
            )
            AiProvider.GEMINI -> KeyEditor(
                steps = "1. Abre Google AI Studio con tu cuenta de Google.\n" +
                    "2. Pulsa «Create API key».\n" +
                    "3. Copia la clave (empieza por «AQ.» o «AIza») y pégala aquí.",
                linkLabel = "Conseguir clave de Gemini",
                link = "https://aistudio.google.com/apikey",
                label = "Clave de Gemini",
                initial = prefs.apiKey,
                onChange = { prefs.apiKey = it },
                test = { testGemini(prefs) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Puedes poner las dos: la elegida se usa para el asistente, y Gemini (si está) para revisar fotos y vídeos. " +
                "Las claves solo se guardan en este móvil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else inkBorder().copy(alpha = 0.35f), shape)
            .bouncyClick(onClick = onClick)
            .padding(12.dp),
    ) {
        IconText(icon, title, MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyEditor(
    steps: String,
    linkLabel: String,
    link: String,
    label: String,
    initial: String,
    onChange: (String) -> Unit,
    test: suspend () -> Pair<Boolean, String>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var key by remember(label) { mutableStateOf(initial) }
    var show by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember(label) { mutableStateOf<Pair<Boolean, String>?>(null) }

    Text(steps, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    GradientButton(linkLabel, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) })
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = key,
        onValueChange = { key = it; onChange(it); result = null },
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Ocultar" else "Ver") } },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    GradientButton(if (testing) "Probando…" else "Probar la clave", enabled = !testing && key.isNotBlank(), onClick = {
        testing = true
        result = null
        scope.launch {
            result = test()
            testing = false
        }
    })
    result?.let { (ok, text) ->
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        )
    }
}

/** Prueba completa: lista los modelos y hace una petición real, para ver que contesta de verdad. */
private suspend fun testGroq(prefs: Prefs): Pair<Boolean, String> = try {
    val client = GroqClient(prefs.groqKey, prefs.groqModel)
    val models = client.listModels()
    if (prefs.groqModel !in models) GroqClient.pickModel(models)?.let { prefs.groqModel = it }
    val start = System.currentTimeMillis()
    GroqClient(prefs.groqKey, prefs.groqModel).generateJson(TEST_PROMPT, listOf(Turn(true, "Hola")))
    val seconds = (System.currentTimeMillis() - start) / 100 / 10.0
    true to "✓ ¡Funciona! Groq ha contestado en $seconds s con ${prefs.groqModel}."
} catch (e: AiException) {
    false to e.message.orEmpty()
}

private suspend fun testGemini(prefs: Prefs): Pair<Boolean, String> = try {
    val models = GeminiClient(prefs.apiKey, prefs.model).listModels()
    if (prefs.model !in models) GeminiClient.pickModel(models)?.let { prefs.model = it }
    val start = System.currentTimeMillis()
    val client = GeminiClient(prefs.apiKey, prefs.model)
    client.generateJson(TEST_PROMPT, listOf(Turn(true, "Hola")))
    if (client.model != prefs.model) prefs.model = client.model
    val seconds = (System.currentTimeMillis() - start) / 100 / 10.0
    true to "✓ ¡Funciona! Gemini ha contestado en $seconds s con ${prefs.model}."
} catch (e: AiException) {
    false to e.message.orEmpty()
}

private const val TEST_PROMPT = "Responde solo con este JSON: {\"ok\": true}"
