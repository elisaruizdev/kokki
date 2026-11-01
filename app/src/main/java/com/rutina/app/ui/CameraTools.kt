package com.rutina.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.rutina.app.data.PoseExercise
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan2

fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Pide el permiso de cámara si hace falta y luego ejecuta [onGranted]. */
@Composable
fun rememberCameraGate(onDenied: () -> Unit = {}, onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onGranted() else onDenied()
    }
    return { if (hasCameraPermission(context)) onGranted() else launcher.launch(Manifest.permission.CAMERA) }
}

/** Vista de la cámara con un analizador de fotogramas. Todo se procesa dentro del móvil. */
@Composable
fun CameraView(front: Boolean, analyzer: ImageAnalysis.Analyzer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView: funciona bien dentro de diálogos
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            provider = future.get().also { p ->
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                runCatching {
                    p.unbindAll()
                    p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView({ previewView }, modifier)
}

/** Pantalla completa con la cámara y una capa encima con la información del reto. */
@Composable
fun CameraScreen(onDismiss: () -> Unit, front: Boolean, analyzer: ImageAnalysis.Analyzer, overlay: @Composable BoxScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CameraView(front, analyzer, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                overlay()
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }
}

/** Lector de códigos QR y de barras: llama a [onCode] con el primero que vea. */
@OptIn(ExperimentalGetImage::class)
@Composable
fun CodeScannerScreen(title: String, onDismiss: () -> Unit, onCode: (String) -> Unit) {
    val scanner = remember { BarcodeScanning.getClient() }
    var done by remember { mutableStateOf(false) }
    val mainExecutor = ContextCompat.getMainExecutor(LocalContext.current)
    val analyzer = remember {
        ImageAnalysis.Analyzer { proxy ->
            val media = proxy.image
            if (media == null || done) {
                proxy.close()
                return@Analyzer
            }
            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener(mainExecutor) { codes ->
                    val value = codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (value != null && !done) {
                        done = true
                        onCode(value)
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }
    }
    DisposableEffect(Unit) { onDispose { scanner.close() } }
    CameraScreen(onDismiss, front = false, analyzer = analyzer) {
        OverlayText(title, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun OverlayText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        textAlign = TextAlign.Center,
        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        modifier = modifier
            .padding(20.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/**
 * Cuenta repeticiones mirando los ángulos del cuerpo que detecta ML Kit:
 * rodilla en las sentadillas, codo en las flexiones y muñecas en los jumping jacks.
 */
class RepCounter(private val exercise: PoseExercise, start: Int) {
    var count = start
        private set
    private var down = false

    /** Devuelve si se ve bien el cuerpo. Suma una repetición al volver arriba tras haber bajado del todo. */
    fun update(pose: Pose): Boolean {
        when (exercise) {
            PoseExercise.SENTADILLAS -> {
                val angle = bestAngle(
                    pose,
                    listOf(PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE),
                    listOf(PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE),
                ) ?: return false
                if (angle < 105) down = true
                if (down && angle > 155) { count++; down = false }
            }
            PoseExercise.FLEXIONES -> {
                val angle = bestAngle(
                    pose,
                    listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST),
                    listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST),
                ) ?: return false
                if (angle < 95) down = true
                if (down && angle > 150) { count++; down = false }
            }
            PoseExercise.JUMPING -> {
                val ids = listOf(
                    PoseLandmark.NOSE, PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST,
                    PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
                )
                val p = ids.map { pose.getPoseLandmark(it)?.takeIf { l -> l.inFrameLikelihood > 0.5f } ?: return false }
                val (nose, lw, rw, ls, rs) = p
                // En la imagen la y crece hacia abajo
                val armsUp = lw.position.y < nose.position.y && rw.position.y < nose.position.y
                val armsDown = lw.position.y > ls.position.y && rw.position.y > rs.position.y
                if (armsUp) down = true
                if (down && armsDown) { count++; down = false }
            }
        }
        return true
    }

    /** Ángulo en la articulación central del lado que mejor se ve. */
    private fun bestAngle(pose: Pose, left: List<Int>, right: List<Int>): Double? =
        listOf(left, right)
            .mapNotNull { side ->
                val points = side.map { pose.getPoseLandmark(it) ?: return@mapNotNull null }
                val likelihood = points.minOf { it.inFrameLikelihood }
                if (likelihood < 0.5f) null else likelihood to angle(points[0], points[1], points[2])
            }
            .maxByOrNull { it.first }?.second

    private fun angle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Double {
        val result = Math.toDegrees(
            (atan2(c.position.y - b.position.y, c.position.x - b.position.x) -
                atan2(a.position.y - b.position.y, a.position.x - b.position.x)).toDouble(),
        )
        val absolute = abs(result)
        return if (absolute > 180) 360 - absolute else absolute
    }
}

/** Cámara frontal que cuenta las repeticiones en directo. */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PoseCounterScreen(
    exercise: PoseExercise,
    target: Int,
    start: Int,
    onCount: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val detector = remember {
        PoseDetection.getClient(PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build())
    }
    val counter = remember { RepCounter(exercise, start) }
    var count by remember { mutableStateOf(start) }
    var visible by remember { mutableStateOf(false) }
    val mainExecutor = ContextCompat.getMainExecutor(LocalContext.current)
    val analyzer = remember {
        ImageAnalysis.Analyzer { proxy: ImageProxy ->
            val media = proxy.image
            if (media == null) {
                proxy.close()
                return@Analyzer
            }
            detector.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener(mainExecutor) { pose ->
                    if (count >= target) return@addOnSuccessListener
                    visible = counter.update(pose)
                    if (counter.count != count) {
                        count = counter.count
                        onCount(count)
                    }
                }
                .addOnCompleteListener { proxy.close() }
        }
    }
    DisposableEffect(Unit) { onDispose { detector.close() } }
    CameraScreen(onDismiss, front = true, analyzer = analyzer) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$count / $target",
                color = Color.White,
                fontFamily = Script,
                fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp),
                modifier = Modifier
                    .background(Shu.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 28.dp, vertical = 8.dp),
            )
            Text(
                exercise.label,
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        OverlayText(
            when {
                count >= target -> "¡Hecho! Ya puedes cerrar"
                visible -> "✓ Te veo. ¡Sigue!"
                else -> "No te veo entero. ${exercise.hint}"
            },
            Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** QR propio para pegar donde cueste ir. Se crea dentro del móvil. */
fun qrBitmap(value: String, size: Int = 800): Bitmap {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 2))
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size) {
        bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
    }
    return bitmap
}

/** Carga una foto reducida (para no mandar fotos de 12 MP a la IA) y la devuelve en JPEG. */
fun shrinkPhoto(file: File, maxSide: Int = 1280): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return java.io.ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
    }
}
