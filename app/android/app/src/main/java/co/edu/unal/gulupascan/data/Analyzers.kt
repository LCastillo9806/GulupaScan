package co.edu.unal.gulupascan.data

import co.edu.unal.gulupascan.domain.Analysis
import co.edu.unal.gulupascan.domain.AnalysisImages
import co.edu.unal.gulupascan.domain.Damage
import co.edu.unal.gulupascan.domain.DamageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Error con mensaje listo para mostrar al usuario. */
class AnalysisException(message: String) : Exception(message)

interface Analyzer {
    /** Analiza [jpeg] y guarda las imágenes resultantes dentro de [workDir]. */
    suspend fun analyze(id: String, jpeg: ByteArray, workDir: File): Analysis
}

// ---------------------------------------------------------------------------
// Servidor opcional: api.py (PowderyVision7 / YOLO11 + Chan-Vese)
// ---------------------------------------------------------------------------

@Serializable
private data class ApiResponse(
    val ok: Boolean = false,
    val msg: String? = null,
    @SerialName("msg_en") val msgEn: String? = null,
    @SerialName("msg_es") val msgEs: String? = null,
    @SerialName("cls_name") val clsName: String? = null,
    @SerialName("cls_conf") val clsConf: Double = 0.0,
    @SerialName("cls_time_ms") val clsTimeMs: Double = 0.0,
    @SerialName("seg_time_ms") val segTimeMs: Double = 0.0,
    val species: String? = null,
    @SerialName("crop_name_es") val cropNameEs: String? = null,
    val disease: String? = null,
    @SerialName("disease_es") val diseaseEs: String? = null,
    @SerialName("is_healthy") val isHealthy: Boolean = false,
    @SerialName("leaf_conf") val leafConf: Double = 0.0,
    val damages: List<ApiDamage> = emptyList(),
    val images: ApiImages = ApiImages(),
)

@Serializable
private data class ApiDamage(val key: String, val pct: Double, @SerialName("area_cm2") val areaCm2: Double? = null)

@Serializable
private data class ApiImages(
    val original: String? = null,
    val crop: String? = null,
    val leaf: String? = null,
    val mask: String? = null,
    val overlay: String? = null,
    val result: String? = null,
    val hue: String? = null,
    val panel: String? = null,
    val overlays: Map<String, String?> = emptyMap(),
)

class RemoteAnalyzer(private val baseUrl: String) : Analyzer {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun analyze(id: String, jpeg: ByteArray, workDir: File): Analysis {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "$id.jpg", jpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(baseUrl.trim().trimEnd('/') + "/api/analyze")
            .post(body)
            .build()

        val text = client.newCall(request).await()
        val res = runCatching { json.decodeFromString(ApiResponse.serializer(), text) }
            .getOrElse { throw AnalysisException("Respuesta no válida del servidor. ¿Está desplegado api.py?") }

        if (!res.ok) throw AnalysisException(localized(res) ?: "Error del servidor")

        return withContext(Dispatchers.IO) {
            val imgs = res.images
            Analysis(
                id = id,
                createdAt = System.currentTimeMillis(),
                source = Analysis.SOURCE_SERVER,
                modelClass = res.clsName,
                modelConfidence = res.clsConf,
                leafConfidence = res.leafConf,
                cropName = if (isSpanish()) res.cropNameEs ?: res.species else res.species ?: res.cropNameEs,
                diseaseName = if (isSpanish()) res.diseaseEs ?: res.disease else res.disease ?: res.diseaseEs,
                isHealthy = res.isHealthy,
                damages = res.damages.map { Damage(it.key, it.pct, it.areaCm2) },
                images = AnalysisImages(
                    original = File(workDir, "original.jpg").takeIf { it.exists() }?.absolutePath
                        ?: ImageUtils.saveBase64(workDir, "original.jpg", imgs.original),
                    crop = ImageUtils.saveBase64(workDir, "crop.jpg", imgs.crop),
                    leaf = ImageUtils.saveBase64(workDir, "leaf.jpg", imgs.leaf),
                    mask = ImageUtils.saveBase64(workDir, "mask.png", imgs.mask),
                    overlay = ImageUtils.saveBase64(workDir, "overlay.png", imgs.overlay),
                    result = ImageUtils.saveBase64(workDir, "result.jpg", imgs.result),
                    hue = ImageUtils.saveBase64(workDir, "hue.jpg", imgs.hue),
                    panel = ImageUtils.saveBase64(workDir, "panel.jpg", imgs.panel),
                    overlays = imgs.overlays.mapNotNull { (k, b64) ->
                        ImageUtils.saveBase64(workDir, "overlay_${k.filter { it.isLetterOrDigit() || it == '_' }}.png", b64)?.let { k to it }
                    }.toMap(),
                ),
                clsTimeMs = res.clsTimeMs,
                segTimeMs = res.segTimeMs,
            )
        }
    }

    private fun isSpanish() = Locale.getDefault().language == "es"

    private fun localized(r: ApiResponse): String? =
        if (isSpanish()) r.msgEs ?: r.msg else r.msgEn ?: r.msg

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun Call.await(): String = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                val text = it.body?.string().orEmpty()
                if (text.trimStart().startsWith("{")) cont.resume(text)
                else cont.resumeWithException(AnalysisException("HTTP ${it.code}: el servidor no tiene /api/analyze"))
            }
        }
    })
}

// ---------------------------------------------------------------------------
// Modo demostración: reproduce los valores del catálogo real sin servidor.
// ---------------------------------------------------------------------------
class DemoAnalyzer : Analyzer {
    override suspend fun analyze(id: String, jpeg: ByteArray, workDir: File): Analysis {
        delay(2000)
        val original = File(workDir, "original.jpg").absolutePath
        return Analysis(
            id = id,
            createdAt = System.currentTimeMillis(),
            source = Analysis.SOURCE_DEMO,
            modelClass = "Scab",
            modelConfidence = 0.92,
            cropName = "Gulupa",
            diseaseName = "Sarna (Roña)",
            damages = listOf(
                Damage(DamageCatalog.SCAB, 12.5, 4.8),
                Damage(DamageCatalog.ANTHRACNOSE, 3.2, 1.2),
                Damage(DamageCatalog.OIL_SPOT, 1.8, 0.7),
                Damage(DamageCatalog.HEALTHY, 82.5, 31.7),
            ),
            images = AnalysisImages(original = original),
        )
    }
}
