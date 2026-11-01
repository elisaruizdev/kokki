package com.rutina.app.penalty

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import com.rutina.app.data.ChallengeBank
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** Resultado de pasar los tests a un mini reto: uno por test, o [error] si el código ni siquiera se puede ejecutar. */
data class CodeResult(val passed: List<Boolean>, val details: List<String>, val error: String? = null) {
    val allPassed: Boolean get() = error == null && passed.isNotEmpty() && passed.all { it }
}

/**
 * Ejecuta el JavaScript del reto en el motor del propio móvil (un WebView invisible), sin internet ni IA,
 * y lo compara con los resultados esperados.
 */
object CodeRunner {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun run(context: Context, code: String, kata: ChallengeBank.Kata): CodeResult {
        val web = WebView(context.applicationContext)
        web.settings.javaScriptEnabled = true
        val script = buildScript(code, kata)
        return try {
            val raw = withTimeoutOrNull(6_000) {
                suspendCancellableCoroutine<String> { cont ->
                    web.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value) }
                }
            } ?: return CodeResult(emptyList(), emptyList(), "Tu código tarda demasiado. ¿Hay algún bucle infinito?")
            parse(raw, kata)
        } finally {
            web.destroy()
        }
    }

    private fun buildScript(code: String, kata: ChallengeBank.Kata): String {
        val tests = JSONArray().apply {
            kata.tests.forEach { put(JSONObject().put("args", it.argsJson).put("expected", it.expectedJson)) }
        }
        // El código va dentro de una función para que no pueda tocar nada de fuera, y cada test se ejecuta por separado
        return """
            (function() {
              try {
                const fn = (function() { ${code}
                ; return typeof ${kata.function} === 'function' ? ${kata.function} : null; })();
                if (!fn) return JSON.stringify({ error: 'No encuentro la función ${kata.function}. No le cambies el nombre.' });
                const tests = ${tests};
                return JSON.stringify({ results: tests.map(function(t) {
                  try {
                    const got = JSON.stringify(fn.apply(null, JSON.parse(t.args)));
                    return { ok: got === JSON.stringify(JSON.parse(t.expected)), got: String(got) };
                  } catch (e) { return { ok: false, got: 'Error: ' + e.message }; }
                }) });
              } catch (e) { return JSON.stringify({ error: 'Tu código tiene un error: ' + e.message }); }
            })()
        """.trimIndent()
    }

    private fun parse(raw: String, kata: ChallengeBank.Kata): CodeResult {
        // evaluateJavascript devuelve el valor en JSON: nuestra cadena viene entre comillas
        val inner = runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()
            ?: return CodeResult(emptyList(), emptyList(), "No he podido ejecutar el código.")
        val json = runCatching { JSONObject(inner) }.getOrNull()
            ?: return CodeResult(emptyList(), emptyList(), "No he podido ejecutar el código.")
        json.optString("error").takeIf { it.isNotBlank() }?.let { return CodeResult(emptyList(), emptyList(), it) }
        val results = json.optJSONArray("results") ?: JSONArray()
        val passed = (0 until results.length()).map { results.getJSONObject(it).optBoolean("ok") }
        val details = (0 until results.length()).map { i ->
            val test = kata.tests[i]
            val args = test.argsJson.removePrefix("[").removeSuffix("]")
            "${kata.function}($args) → esperaba ${test.expectedJson}, da ${results.getJSONObject(i).optString("got")}"
        }
        return CodeResult(passed, details)
    }
}
