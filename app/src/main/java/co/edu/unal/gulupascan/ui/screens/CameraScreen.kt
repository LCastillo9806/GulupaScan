package co.edu.unal.gulupascan.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.data.AnalysisResolution
import co.edu.unal.gulupascan.ui.components.GsTopBar
import co.edu.unal.gulupascan.ui.components.PrimaryButton
import co.edu.unal.gulupascan.ui.components.StepBadge
import co.edu.unal.gulupascan.ui.theme.Pulp
import java.io.File
import java.util.concurrent.TimeUnit

private const val JPEG_QUALITY = 95
private const val FOCUS_HOLD_SECONDS = 5L
private const val FOCUS_TIMEOUT_MS = 2000L

@Composable
fun CameraScreen(
    resolution: AnalysisResolution = AnalysisResolution.HIGH,
    onResolutionChange: (AnalysisResolution) -> Unit = {},
    onGuide: () -> Unit,
    onImage: (Uri) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImage(uri)
    }
    fun openGallery() = galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    var flashOn by remember { mutableStateOf(false) }
    var backCamera by remember { mutableStateOf(true) }
    var showTips by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var tapPoint by remember { mutableStateOf<Offset?>(null) }
    var tapCount by remember { mutableIntStateOf(0) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    // Ajusta la resolución de captura de CameraX según el modo elegido (Baja 640p, Media 1024p, Alta 2048p, Original del sensor o Ultra).
    val imageCapture = remember(resolution) {
        val resStrategy = when (resolution) {
            AnalysisResolution.LOW -> ResolutionStrategy(
                android.util.Size(480, 640),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
            AnalysisResolution.MEDIUM -> ResolutionStrategy(
                android.util.Size(768, 1024),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
            AnalysisResolution.HIGH -> ResolutionStrategy(
                android.util.Size(1536, 2048),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
            )
            AnalysisResolution.ORIGINAL, AnalysisResolution.ULTRA -> ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
        }
        val captureMode = if (resolution == AnalysisResolution.LOW) {
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        } else {
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
        ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setJpegQuality(JPEG_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(resStrategy)
                    .build(),
            )
            .build()
    }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    // (Re)vincula la cámara cuando cambia el lente, el permiso o la resolución del sensor.
    DisposableEffect(hasPermission, backCamera, imageCapture) {
        var provider: ProcessCameraProvider? = null
        if (hasPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                provider = future.get()
                // Misma proporción 4:3 que la captura: lo que se ve en pantalla es lo que se fotografía.
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build(),
                    )
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (backCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                runCatching {
                    provider?.unbindAll()
                    camera = provider?.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            camera = null
            tapPoint = null
            runCatching { provider?.unbindAll() }
        }
    }
    LaunchedEffect(flashOn) {
        imageCapture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    fun focusAt(x: Float, y: Float) = camera?.cameraControl?.startFocusAndMetering(
        FocusMeteringAction.Builder(
            previewView.meteringPointFactory.createPoint(x, y),
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS).build(),
    )

    fun takePhoto() {
        if (busy) return
        busy = true
        // Las capturas ahora pesan varios MB: no se acumulan en la caché.
        context.cacheDir.listFiles { f -> f.name.startsWith("capture_") }?.forEach { it.delete() }
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val mainExecutor = ContextCompat.getMainExecutor(context)

        var captured = false
        fun capture() {
            if (captured) return
            captured = true
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        busy = false
                        onImage(Uri.fromFile(file))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        busy = false
                        Toast.makeText(context, R.string.cam_capture_error, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }

        // Enfoca justo antes de disparar, en el punto tocado o en el centro del encuadre.
        val point = tapPoint ?: Offset(previewView.width / 2f, previewView.height / 2f)
        val focus = focusAt(point.x, point.y)
        if (focus == null) {
            capture()
        } else {
            focus.addListener({
                val focused = runCatching { focus.get().isFocusSuccessful }.getOrDefault(true)
                if (!focused && !captured) Toast.makeText(context, R.string.cam_focus_failed, Toast.LENGTH_LONG).show()
                capture()
            }, mainExecutor)
            // Algunos teléfonos nunca responden a la solicitud de enfoque: no se bloquea el disparo.
            Handler(Looper.getMainLooper()).postDelayed({ capture() }, FOCUS_TIMEOUT_MS)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        GsTopBar(stringResource(R.string.cam_title), onBack = null, actions = {
            IconButton(onClick = { showTips = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.cam_tips_title))
            }
        })

        Box(
            Modifier.weight(1f).fillMaxWidth().background(if (hasPermission) Color.Black else Color(0xFFDADFE3)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                Box(Modifier.aspectRatio(3f / 4f)) {
                    AndroidView({ previewView }, Modifier.fillMaxSize())
                    FrameCorners(Modifier.fillMaxSize().padding(28.dp))
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures { offset ->
                                tapPoint = offset
                                tapCount++
                                focusAt(offset.x, offset.y)
                            }
                        },
                    )
                    tapPoint?.let { FocusRing(it, tapCount) }
                    Text(
                        stringResource(R.string.cam_tap_focus),
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            } else {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhotoCamera, null, Modifier.size(56.dp), tint = Color.DarkGray)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.cam_permission), textAlign = TextAlign.Center, color = Color.DarkGray)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(stringResource(R.string.cam_grant), { permissionLauncher.launch(Manifest.permission.CAMERA) })
                }
            }
        }

        // Barra inferior oscura con selector rápido de resolución (Baja 640p / Media 1024p / Alta 2048p / Original / Ultra 2x2)
        val isEn = context.resources.configuration.locales[0].language == "en"
        Column(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(vertical = 12.dp)) {
            // Desplazable para que en pantallas angostas ningún botón se parta en varias líneas
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(
                    AnalysisResolution.LOW to (if (isEn) "Low·640p" else "Baja·640p"),
                    AnalysisResolution.MEDIUM to (if (isEn) "Med·1024p" else "Media·1024p"),
                    AnalysisResolution.HIGH to (if (isEn) "High·2K" else "Alta·2K"),
                    AnalysisResolution.ORIGINAL to (if (isEn) "Original" else "Original"),
                    AnalysisResolution.ULTRA to (if (isEn) "Ultra·2×2" else "Ultra·2×2"),
                ).forEach { (res, label) ->
                    val selected = resolution == res
                    Text(
                        label,
                        color = if (selected) Color.Black else Color.White.copy(alpha = 0.85f),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .background(
                                if (selected) Pulp else Color.White.copy(alpha = 0.14f),
                                RoundedCornerShape(50),
                            )
                            .clickable { onResolutionChange(res) }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.cam_photo), color = Pulp, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.size(40.dp, 1.dp))
                Text(
                    stringResource(R.string.cam_gallery), color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clickable { openGallery() },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { flashOn = !flashOn }) {
                    Icon(if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, stringResource(R.string.cam_flash), tint = Color.White)
                }
                Box(
                    Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(6.dp)
                        .background(if (busy) Color.Gray else Color.White, CircleShape)
                        .clickable(enabled = hasPermission && !busy) { takePhoto() },
                )
                IconButton(onClick = { backCamera = !backCamera }) {
                    Icon(Icons.Outlined.Cameraswitch, stringResource(R.string.cam_switch), tint = Color.White)
                }
            }
        }
    }

    if (showTips) {
        AlertDialog(
            onDismissRequest = { showTips = false },
            confirmButton = { TextButton(onClick = { showTips = false }) { Text(stringResource(R.string.ok)) } },
            dismissButton = {
                TextButton(onClick = { showTips = false; onGuide() }) { Text(stringResource(R.string.cam_open_guide)) }
            },
            title = { Text(stringResource(R.string.cam_tips_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(R.string.cam_tip1, R.string.cam_tip2, R.string.cam_tip3, R.string.cam_tip4).forEachIndexed { i, res ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepBadge(i + 1, 24.dp)
                            Spacer(Modifier.size(10.dp))
                            Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
        )
    }
}

/** Aro que se contrae y se desvanece en el punto donde se tocó para enfocar. */
@Composable
private fun FocusRing(point: Offset, key: Int) {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(900)) }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        drawCircle(
            color = Pulp.copy(alpha = 1f - p * p),
            radius = (44.dp.toPx() - 14.dp.toPx() * minOf(p * 3f, 1f)),
            center = point,
            style = Stroke(2.dp.toPx()),
        )
    }
}

/** Esquinas guía para centrar el fruto, como en el mockup. */
@Composable
private fun FrameCorners(modifier: Modifier) {
    Canvas(modifier) {
        val len = size.minDimension * 0.12f
        val sw = 3.dp.toPx()
        val c = Color.White
        val w = size.width
        val h = size.height
        fun l(a: Offset, b: Offset) = drawLine(c, a, b, sw, StrokeCap.Round)
        l(Offset(0f, 0f), Offset(len, 0f)); l(Offset(0f, 0f), Offset(0f, len))
        l(Offset(w, 0f), Offset(w - len, 0f)); l(Offset(w, 0f), Offset(w, len))
        l(Offset(0f, h), Offset(len, h)); l(Offset(0f, h), Offset(0f, h - len))
        l(Offset(w, h), Offset(w - len, h)); l(Offset(w, h), Offset(w, h - len))
    }
}
