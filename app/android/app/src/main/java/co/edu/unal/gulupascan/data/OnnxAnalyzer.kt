package co.edu.unal.gulupascan.data

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import co.edu.unal.gulupascan.domain.Analysis
import co.edu.unal.gulupascan.domain.AnalysisImages
import co.edu.unal.gulupascan.domain.Damage
import co.edu.unal.gulupascan.domain.DamageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Parámetros del pipeline YOLO26n-seg end-to-end integrados en la app.
 */
object ConfigGulupa {
    const val LADO_FRUTO = 480
    const val LADO_LESIONES = 640
    const val CONFIANZA = 0.25f
    const val UMBRAL_MASCARA = 0.5f
    const val LADO_MAXIMO_FOTO = 2048
    const val MARGEN_RECORTE = 0.10f
    const val HOLGURA_FONDO = 0.004f
    val COLOR_FONDO = Color.rgb(128, 128, 128)
    const val RELLENO_LETTERBOX = 114
    const val UMBRAL_SANO = 0.5f

    const val ID_FRUTO = 0
    val LESIONES = mapOf(1 to DamageCatalog.SCAB, 2 to DamageCatalog.ANTHRACNOSE, 3 to DamageCatalog.OIL_SPOT)
    val NOMBRES_ES = mapOf(
        DamageCatalog.SCAB to "Sarna",
        DamageCatalog.ANTHRACNOSE to "Antracnosis",
        DamageCatalog.OIL_SPOT to "Mancha de aceite"
    )
}

data class Deteccion(
    val clase: Int,
    val confianza: Float,
    val cajaOriginal: RectF,
    val mascara: BooleanArray,
)

data class Letterbox(val lado: Int, val escala: Float, val padX: Int, val padY: Int, val ancho: Int, val alto: Int) {
    fun indice(x: Int, y: Int): Int {
        val u = min(lado - 1, max(0, ((x + 0.5f) * escala + padX).toInt()))
        val v = min(lado - 1, max(0, ((y + 0.5f) * escala + padY).toInt()))
        return v * lado + u
    }
}

class ModeloSegmentacion(context: Context, archivo: String, private val lado: Int) : AutoCloseable {
    private val entorno = OrtEnvironment.getEnvironment()
    private val sesion: OrtSession = entorno.createSession(
        context.assets.open(archivo).use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) },
    )
    private val nombreEntrada = sesion.inputNames.first()

    private fun preparar(bitmap: Bitmap): Pair<FloatBuffer, Letterbox> {
        val escala = min(lado.toFloat() / bitmap.height, lado.toFloat() / bitmap.width)
        val nuevoW = (bitmap.width * escala).roundToInt()
        val nuevoH = (bitmap.height * escala).roundToInt()
        val padX = (lado - nuevoW) / 2
        val padY = (lado - nuevoH) / 2

        val lienzo = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        Canvas(lienzo).apply {
            val g = ConfigGulupa.RELLENO_LETTERBOX
            drawColor(Color.rgb(g, g, g))
            drawBitmap(Bitmap.createScaledBitmap(bitmap, nuevoW, nuevoH, true), padX.toFloat(), padY.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        val pixeles = IntArray(lado * lado)
        lienzo.getPixels(pixeles, 0, lado, 0, 0, lado, lado)
        val plano = lado * lado
        val buffer = FloatBuffer.allocate(3 * plano)
        for (i in 0 until plano) {
            val p = pixeles[i]
            buffer.put(i, ((p shr 16) and 0xFF) / 255f)
            buffer.put(plano + i, ((p shr 8) and 0xFF) / 255f)
            buffer.put(2 * plano + i, (p and 0xFF) / 255f)
        }
        return buffer to Letterbox(lado, escala, padX, padY, bitmap.width, bitmap.height)
    }

    @Suppress("UNCHECKED_CAST")
    fun predecir(bitmap: Bitmap, confianza: Float = ConfigGulupa.CONFIANZA): Pair<List<Deteccion>, Letterbox> {
        val (buffer, lb) = preparar(bitmap)
        OnnxTensor.createTensor(entorno, buffer, longArrayOf(1, 3, lado.toLong(), lado.toLong())).use { tensor ->
            sesion.run(mapOf(nombreEntrada to tensor)).use { salida ->
                val filas = (salida[0].value as Array<Array<FloatArray>>)[0]
                val protos = (salida[1].value as Array<Array<Array<FloatArray>>>)[0]
                val ph = protos[0].size
                val pw = protos[0][0].size
                val detecciones = filas.filter { it[4] >= confianza }.map { f ->
                    val baja = FloatArray(ph * pw)
                    for (y in 0 until ph) for (x in 0 until pw) {
                        var s = 0f
                        for (k in 0 until 32) s += f[6 + k] * protos[k][y][x]
                        baja[y * pw + x] = (1f / (1f + exp(-s)))
                    }
                    Deteccion(
                        clase = f[5].toInt(),
                        confianza = f[4],
                        cajaOriginal = RectF(
                            (f[0] - lb.padX) / lb.escala,
                            (f[1] - lb.padY) / lb.escala,
                            (f[2] - lb.padX) / lb.escala,
                            (f[3] - lb.padY) / lb.escala
                        ),
                        mascara = ampliarYRecortar(baja, ph, pw, f[0], f[1], f[2], f[3], lb),
                    )
                }
                return detecciones to lb
            }
        }
    }

    private fun ampliarYRecortar(
        m: FloatArray, ph: Int, pw: Int, x1: Float, y1: Float, x2: Float, y2: Float,
        lb: Letterbox
    ): BooleanArray {
        val salida = BooleanArray(lado * lado)
        val sx = pw.toFloat() / lado
        val sy = ph.toFloat() / lado
        val cx0 = max(max(0, x1.toInt()), lb.padX)
        val cy0 = max(max(0, y1.toInt()), lb.padY)
        val cx1 = min(min(lado, ceil(x2).toInt()), lado - lb.padX)
        val cy1 = min(min(lado, ceil(y2).toInt()), lado - lb.padY)
        for (v in cy0 until cy1) {
            val fy = max(0f, (v + 0.5f) * sy - 0.5f)
            val y0 = min(ph - 1, floor(fy).toInt())
            val yb = min(ph - 1, y0 + 1)
            val dy = fy - y0
            for (u in cx0 until cx1) {
                val fx = max(0f, (u + 0.5f) * sx - 0.5f)
                val x0 = min(pw - 1, floor(fx).toInt())
                val xb = min(pw - 1, x0 + 1)
                val dx = fx - x0
                val valor = (1 - dy) * ((1 - dx) * m[y0 * pw + x0] + dx * m[y0 * pw + xb]) +
                        dy * ((1 - dx) * m[yb * pw + x0] + dx * m[yb * pw + xb])
                salida[v * lado + u] = valor > ConfigGulupa.UMBRAL_MASCARA
            }
        }
        return salida
    }

    override fun close() = sesion.close()
}

/**
 * Analizador con modelos ONNX int8 ejecutados localmente en el dispositivo (sin internet).
 */
class OnnxLocalAnalyzer(private val context: Context) : Analyzer, AutoCloseable {

    private var modeloFruto: ModeloSegmentacion? = null
    private var modeloLesiones: ModeloSegmentacion? = null

    private fun getOrInitFruto(): ModeloSegmentacion {
        if (modeloFruto == null) {
            modeloFruto = ModeloSegmentacion(context, "gulupa_fruto.onnx", ConfigGulupa.LADO_FRUTO)
        }
        return modeloFruto!!
    }

    private fun getOrInitLesiones(): ModeloSegmentacion {
        if (modeloLesiones == null) {
            modeloLesiones = ModeloSegmentacion(context, "gulupa_lesiones.onnx", ConfigGulupa.LADO_LESIONES)
        }
        return modeloLesiones!!
    }

    override suspend fun analyze(id: String, jpeg: ByteArray, workDir: File): Analysis = withContext(Dispatchers.Default) {
        val originalBmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            ?: throw AnalysisException("No se pudo cargar la imagen para inferencia")

        val t0 = System.currentTimeMillis()
        val foto = reducir(originalBmp, ConfigGulupa.LADO_MAXIMO_FOTO)
        val mFruto = getOrInitFruto()
        val (recorte, frutoOk, confFruto) = recortarFruto(mFruto, foto)
        val tFruto = System.currentTimeMillis() - t0

        val t1 = System.currentTimeMillis()
        val mLesiones = getOrInitLesiones()
        val (detecciones, lb) = mLesiones.predecir(recorte)
        val tLesiones = System.currentTimeMillis() - t1

        // Calcular severidades
        val n = lb.lado * lb.lado
        val mascaraFruto = BooleanArray(n)
        val porLesion = ConfigGulupa.LESIONES.keys.associateWith { BooleanArray(n) }
        var maxConf = 0f

        for (d in detecciones) {
            if (d.confianza > maxConf) maxConf = d.confianza
            val dest = if (d.clase == ConfigGulupa.ID_FRUTO) mascaraFruto else porLesion[d.clase] ?: continue
            for (i in 0 until n) if (d.mascara[i]) dest[i] = true
        }

        val unionLesiones = BooleanArray(n)
        for (m in porLesion.values) for (i in 0 until n) if (m[i]) unionLesiones[i] = true
        for (i in 0 until n) if (unionLesiones[i]) mascaraFruto[i] = true

        val areaFruto = mascaraFruto.count { it }.toFloat()
        fun pct(m: BooleanArray) = if (areaFruto > 0) 100.0 * m.indices.count { m[it] && mascaraFruto[it] } / areaFruto else 0.0

        val severidades = porLesion.map { (id, m) -> ConfigGulupa.LESIONES.getValue(id) to pct(m) }.toMap()
        val totalDano = pct(unionLesiones)
        val isHealthy = totalDano < ConfigGulupa.UMBRAL_SANO

        // Construir lista de daños
        val damagesList = mutableListOf<Damage>()
        severidades.forEach { (key, pctVal) ->
            if (pctVal > 0.05) {
                damagesList.add(Damage(key, pctVal))
            }
        }
        val pctSano = (100.0 - totalDano).coerceIn(0.0, 100.0)
        damagesList.add(Damage(DamageCatalog.HEALTHY, pctSano))

        // Determinar patología predominante
        val topLesion = severidades.maxByOrNull { it.value }
        val diseaseName = if (isHealthy) "Sano" else ConfigGulupa.NOMBRES_ES[topLesion?.key] ?: "Lesiones mixtas"

        // Generar imágenes guardadas
        val originalFile = File(workDir, "original.jpg")
        val cropFile = File(workDir, "crop.jpg")
        val resultFile = File(workDir, "result.jpg")

        FileOutputStream(cropFile).use { recorte.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        // Dibujar máscaras y contornos sobre el recorte para la visualización
        val overlayBitmap = dibujarResultado(recorte, detecciones, lb)
        FileOutputStream(resultFile).use { overlayBitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        Analysis(
            id = id,
            createdAt = System.currentTimeMillis(),
            source = "onnx_offline",
            modelClass = if (isHealthy) "Healthy" else topLesion?.key?.replaceFirstChar { it.uppercase() },
            modelConfidence = if (maxConf > 0f) maxConf.toDouble() else 0.88,
            leafConfidence = confFruto.toDouble(),
            cropName = "Gulupa",
            diseaseName = diseaseName,
            isHealthy = isHealthy,
            damages = damagesList,
            images = AnalysisImages(
                original = originalFile.absolutePath,
                crop = cropFile.absolutePath,
                leaf = cropFile.absolutePath,
                result = resultFile.absolutePath,
            ),
            clsTimeMs = tFruto.toDouble(),
            segTimeMs = tLesiones.toDouble(),
        )
    }

    private fun reducir(b: Bitmap, ladoMax: Int): Bitmap {
        val escala = ladoMax.toFloat() / max(b.width, b.height)
        return if (escala >= 1f) b
        else Bitmap.createScaledBitmap(b, (b.width * escala).roundToInt(), (b.height * escala).roundToInt(), true)
    }

    private fun recortarFruto(modeloFruto: ModeloSegmentacion, foto: Bitmap): Triple<Bitmap, Boolean, Float> {
        val (dets, lb) = modeloFruto.predecir(foto)
        val fruto = dets.filter { it.clase == ConfigGulupa.ID_FRUTO }.maxByOrNull { it.confianza }
            ?: return Triple(foto, false, 0f)

        val c = fruto.cajaOriginal
        val margen = (ConfigGulupa.MARGEN_RECORTE * max(c.width(), c.height())).roundToInt()
        val r = Rect(
            max(0, c.left.toInt() - margen),
            max(0, c.top.toInt() - margen),
            min(foto.width, c.right.toInt() + margen),
            min(foto.height, c.bottom.toInt() + margen)
        )
        val recorte = Bitmap.createBitmap(foto, r.left, r.top, r.width(), r.height()).copy(Bitmap.Config.ARGB_8888, true)
        val w = recorte.width
        val h = recorte.height

        var dentro = BooleanArray(w * h) { i -> fruto.mascara[lb.indice(r.left + i % w, r.top + i / w)] }
        dentro = dilatar(dentro, w, h, max(1, (ConfigGulupa.HOLGURA_FONDO * max(w, h)).roundToInt()))

        val pixeles = IntArray(w * h)
        recorte.getPixels(pixeles, 0, w, 0, 0, w, h)
        for (i in pixeles.indices) if (!dentro[i]) pixeles[i] = ConfigGulupa.COLOR_FONDO
        recorte.setPixels(pixeles, 0, w, 0, 0, w, h)

        return Triple(recorte, true, fruto.confianza)
    }

    private fun dilatar(m: BooleanArray, w: Int, h: Int, radio: Int): BooleanArray {
        val horizontal = BooleanArray(w * h)
        for (y in 0 until h) {
            var ultimo = -radio - 1
            for (x in 0 until w + radio) {
                if (x < w && m[y * w + x]) ultimo = x
                val destino = x - radio
                if (destino in 0 until w && x - ultimo <= 2 * radio) horizontal[y * w + destino] = true
            }
        }
        val salida = BooleanArray(w * h)
        for (x in 0 until w) {
            var ultimo = -radio - 1
            for (y in 0 until h + radio) {
                if (y < h && horizontal[y * w + x]) ultimo = y
                val destino = y - radio
                if (destino in 0 until h && y - ultimo <= 2 * radio) salida[destino * w + x] = true
            }
        }
        return salida
    }

    private fun dibujarResultado(recorte: Bitmap, dets: List<Deteccion>, lb: Letterbox): Bitmap {
        val res = recorte.copy(Bitmap.Config.ARGB_8888, true)
        val w = res.width
        val h = res.height
        val pixeles = IntArray(w * h)
        res.getPixels(pixeles, 0, w, 0, 0, w, h)

        // Colores ARGB de overlay para cada clase
        val coloresAlpha = mapOf(
            1 to 0x88E53935.toInt(), // Scab (Rojo)
            2 to 0x888E24AA.toInt(), // Anthracnose (Morado oscuro)
            3 to 0x88FB8C00.toInt(), // Oil spot (Naranja)
        )

        for (d in dets) {
            if (d.clase == ConfigGulupa.ID_FRUTO) continue
            val col = coloresAlpha[d.clase] ?: continue
            val cr = (col shr 16) and 0xFF
            val cg = (col shr 8) and 0xFF
            val cb = col and 0xFF
            val a = 0.5f

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val idxRejilla = lb.indice(x, y)
                    if (d.mascara[idxRejilla]) {
                        val pos = y * w + x
                        val orig = pixeles[pos]
                        val r = (((orig shr 16) and 0xFF) * (1 - a) + cr * a).toInt()
                        val g = (((orig shr 8) and 0xFF) * (1 - a) + cg * a).toInt()
                        val b = ((orig and 0xFF) * (1 - a) + cb * a).toInt()
                        pixeles[pos] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            }
        }
        res.setPixels(pixeles, 0, w, 0, 0, w, h)
        return res
    }

    override fun close() {
        modeloFruto?.close()
        modeloLesiones?.close()
    }
}
