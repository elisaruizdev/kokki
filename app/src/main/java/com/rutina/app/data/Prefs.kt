package com.rutina.app.data

import android.content.Context
import androidx.core.content.edit
import com.rutina.app.ai.AiProvider
import com.rutina.app.ai.GroqClient

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("rutina", Context.MODE_PRIVATE)

    var apiKey: String
        get() = cleanKey(sp.getString("api_key", "").orEmpty())
        set(value) = sp.edit { putString("api_key", cleanKey(value)) }

    // Al pegarla pueden colarse espacios, saltos de línea o caracteres invisibles. Se quitan, pero se respeta
    // cualquier otro signo: las claves nuevas de AI Studio (desde mayo de 2026) empiezan por "AQ." y llevan punto
    private fun cleanKey(value: String) = value.filter { it.code in 33..126 }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = sp.edit { putString("model", value.trim()) }

    /** Proveedor de IA elegido. Si no se ha elegido, Groq salvo que ya hubiera una clave de Gemini. */
    var provider: AiProvider
        get() = AiProvider.from(sp.getString("provider", null))
            ?: if (apiKey.isNotBlank() && groqKey.isBlank()) AiProvider.GEMINI else AiProvider.GROQ
        set(value) = sp.edit { putString("provider", value.name) }

    var groqKey: String
        get() = cleanKey(sp.getString("groq_key", "").orEmpty())
        set(value) = sp.edit { putString("groq_key", cleanKey(value)) }

    var groqModel: String
        get() = sp.getString("groq_model", GroqClient.DEFAULT_MODEL).orEmpty().ifBlank { GroqClient.DEFAULT_MODEL }
        set(value) = sp.edit { putString("groq_model", value.trim()) }

    /** Textos y ejercicios de los retos sin IA que ya han salido hace poco, para no repetirlos. */
    var recentBankItems: List<String>
        get() = sp.getString("recent_bank", "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) = sp.edit { putString("recent_bank", value.takeLast(30).joinToString("\n")) }

    /** Información fija que siempre se le pasa a la IA (horarios, preferencias...). */
    var profile: String
        get() = sp.getString("profile", "").orEmpty()
        set(value) = sp.edit { putString("profile", value.trim()) }

    var defaultReminder: Int
        get() = sp.getInt("default_reminder", 15)
        set(value) = sp.edit { putInt("default_reminder", value) }

    /** Tema de la app. Por defecto claro, que es el diseño principal (papel crema). */
    var themeMode: ThemeMode
        get() = ThemeMode.from(sp.getString("theme", null))
        set(value) = sp.edit { putString("theme", value.name) }

    /** Ya se han visto las pantallas de bienvenida. */
    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(value) = sp.edit { putBoolean("onboarding_done", value) }

    /** Últimas preguntas de rescate, para que la IA no las repita. */
    var recentQuestions: List<String>
        get() = sp.getString("recent_questions", "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) = sp.edit { putString("recent_questions", value.takeLast(20).joinToString("\n")) }

    companion object {
        // Alias de Google que siempre apunta al último modelo Flash (incluido en el nivel gratuito)
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}

enum class ThemeMode(val label: String) {
    CLARO("Claro"),
    OSCURO("Oscuro"),
    SISTEMA("Como el móvil");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: CLARO
    }
}
