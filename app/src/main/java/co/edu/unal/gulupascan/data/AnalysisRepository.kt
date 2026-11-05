package co.edu.unal.gulupascan.data

import android.content.Context
import co.edu.unal.gulupascan.domain.Analysis
import co.edu.unal.gulupascan.domain.DamageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Historial local (offline): cada análisis vive en files/analyses/<id>/ con sus imágenes
 * y un analysis.json. Los que no se guardan se borran al abrir la app.
 */
class AnalysisRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val root = File(context.filesDir, "analyses").apply { mkdirs() }

    fun newWorkDir(id: String): File = File(root, id).apply { mkdirs() }

    suspend fun write(a: Analysis) = withContext(Dispatchers.IO) {
        File(newWorkDir(a.id), "analysis.json").writeText(json.encodeToString(Analysis.serializer(), a))
    }

    suspend fun saved(): List<Analysis> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().mapNotNull { dir ->
            val f = File(dir, "analysis.json")
            if (!f.exists()) null
            else runCatching { json.decodeFromString(Analysis.serializer(), f.readText()) }.getOrNull()
        }.filter { it.saved }.sortedByDescending { it.createdAt }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        File(root, id).deleteRecursively()
    }

    /** Borra análisis temporales (no guardados) de sesiones anteriores. */
    suspend fun purgeUnsaved(keepId: String? = null) = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().forEach { dir ->
            if (dir.name == keepId) return@forEach
            val f = File(dir, "analysis.json")
            val saved = f.exists() && runCatching {
                json.decodeFromString(Analysis.serializer(), f.readText()).saved
            }.getOrDefault(false)
            if (!saved) dir.deleteRecursively()
        }
    }

    /** Exporta el historial a CSV (como el botón "Descargar CSV" de la versión web). */
    suspend fun exportCsv(items: List<Analysis>): File = withContext(Dispatchers.IO) {
        val keys = items.flatMap { it.damages.map { d -> d.key } }.distinct()
            .sortedBy { if (it == DamageCatalog.HEALTHY) 1 else 0 }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("id,fecha,fuente,clase_modelo,confianza,cultivo,enfermedad,sano,dano_total_pct,clasificacion")
        keys.forEach { sb.append(",pct_").append(it) }
        sb.append('\n')
        items.forEach { a ->
            sb.append(a.id).append(',')
                .append(fmt.format(Date(a.createdAt))).append(',')
                .append(a.source).append(',')
                .append(a.modelClass.orEmpty()).append(',')
                .append("%.3f".format(Locale.US, a.modelConfidence)).append(',')
                .append(a.cropName.orEmpty().csv()).append(',')
                .append(a.diseaseName.orEmpty().csv()).append(',')
                .append(a.isHealthy).append(',')
                .append("%.2f".format(Locale.US, a.totalDamagePct)).append(',')
                .append(a.classification.name)
            keys.forEach { k ->
                val v = a.damages.firstOrNull { it.key == k }?.pct
                sb.append(',').append(v?.let { "%.2f".format(Locale.US, it) } ?: "")
            }
            sb.append('\n')
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "gulupascan_historial.csv").apply { writeText(sb.toString()) }
    }

    private fun String.csv() = if (contains(',') || contains('"')) "\"" + replace("\"", "\"\"") + "\"" else this
}
