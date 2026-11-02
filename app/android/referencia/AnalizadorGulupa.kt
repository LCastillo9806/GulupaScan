/*
 * =============================================================================
 * GuluScan · Análisis de daños epidérmicos en gulupa (ONNX Runtime, Android)
 * =============================================================================
 * Autor     : Luis Alberto Castillo Sanabria <alcastillosa@unal.edu.co>
 * Afiliación: Agrocomputation and Epidemiological Analysis Laboratory,
 *             Facultad de Ciencias Agrarias, Universidad Nacional de Colombia
 *
 * Traducción a Kotlin de la implementación de referencia validada en
 * segmentacion/06_exportar.py (inferencia_referencia), y del preprocesamiento
 * de segmentacion/05_predecir.py.
 *
 *   foto → Modelo A (localiza el fruto) → recorte + fondo gris →
 *   Modelo B (fruto + lesiones) → tipo de daño + severidad (%)
 *
 * Dependencia (app/build.gradle.kts):
 *   implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
 *
 * Archivos en app/src/main/assets/:
 *   gulupa_fruto.onnx       (Modelo A, entrada 480)
 *   gulupa_lesiones.onnx    (Modelo B, entrada 640)
 *
 * IMPORTANTE: el Bitmap de entrada debe venir ya ENDEREZADO según el EXIF de
 * la cámara (ver guía); de lo contrario el fruto puede llegar rotado.
 * =============================================================================
 */
package co.edu.unal.guluscan.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// -----------------------------------------------------------------------------
// Parámetros (deben coincidir con config.yaml / *_metadata.json)
// -----------------------------------------------------------------------------
object ConfigGulupa {
    const val LADO_FRUTO = 480            // entrenamiento_fruto.imgsz
    const val LADO_LESIONES = 640         // entrenamiento.imgsz
    const val CONFIANZA = 0.25f           // prediccion.conf
    const val UMBRAL_MASCARA = 0.5f
    const val LADO_MAXIMO_FOTO = 2048     // preanotacion.max_lado
    const val MARGEN_RECORTE = 0.10f      // recorte.margen
    const val HOLGURA_FONDO = 0.004f      // recorte.holgura
    val COLOR_FONDO = Color.rgb(128, 128, 128)
    const val RELLENO_LETTERBOX = 114
    const val UMBRAL_SANO = 0.5f          // % de área lesionada

    const val ID_FRUTO = 0
    val LESIONES = mapOf(1 to "scab", 2 to "anthracnose", 3 to "oil_spot")
    val CLASE_IMAGEN = mapOf("scab" to "Scab", "anthracnose" to "Anthracnose", "oil_spot" to "Oil spot")
    const val CLASE_SANA = "Healthy"
}

/** Una detección con su máscara sobre la rejilla lado×lado del letterbox. */
class Deteccion(
    val clase: Int,
    val confianza: Float,
    val cajaOriginal: RectF,          // en píxeles de la imagen de entrada
    val mascara: BooleanArray,        // lado×lado (rejilla del letterbox)
)

/** Geometría del letterbox para pasar de la imagen original a la rejilla y viceversa. */
class Letterbox(val lado: Int, val escala: Float, val padX: Int, val padY: Int, val ancho: Int, val alto: Int) {
    /** Píxel de la imagen original -> índice en la rejilla lado×lado (vecino más cercano). */
    fun indice(x: Int, y: Int): Int {
        val u = min(lado - 1, max(0, ((x + 0.5f) * escala + padX).toInt()))
        val v = min(lado - 1, max(0, ((y + 0.5f) * escala + padY).toInt()))
        return v * lado + u
    }
}

class ResultadoGulupa(
    val clase: String,                          // Healthy, Scab, Anthracnose u Oil spot
    val severidadTotal: Float,                  // % del fruto con lesiones
    val severidadPorLesion: Map<String, Float>, // % por tipo de lesión
    val frutoLocalizado: Boolean,
    val recorte: Bitmap,                        // fruto recortado con fondo gris (para mostrar)
    val detecciones: List<Deteccion>,           // salida del Modelo B (para dibujar máscaras)
)

// -----------------------------------------------------------------------------
// Modelo YOLO26-seg end-to-end en ONNX Runtime
// -----------------------------------------------------------------------------
class ModeloSegmentacion(context: Context, archivo: String, private val lado: Int) : AutoCloseable {
    private val entorno = OrtEnvironment.getEnvironment()
    private val sesion: OrtSession = entorno.createSession(
        context.assets.open(archivo).use { it.readBytes() },
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) },
    )
    private val nombreEntrada = sesion.inputNames.first()

    /** Paso 1: letterbox, RGB, /255, CHW. */
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
            buffer.put(i, ((p shr 16) and 0xFF) / 255f)              // R
            buffer.put(plano + i, ((p shr 8) and 0xFF) / 255f)       // G
            buffer.put(2 * plano + i, (p and 0xFF) / 255f)           // B
        }
        return buffer to Letterbox(lado, escala, padX, padY, bitmap.width, bitmap.height)
    }

    /** Pasos 2-5: inferencia y decodificación de cajas y máscaras. */
    @Suppress("UNCHECKED_CAST")
    fun predecir(bitmap: Bitmap, confianza: Float = ConfigGulupa.CONFIANZA): Pair<List<Deteccion>, Letterbox> {
        val (buffer, lb) = preparar(bitmap)
        OnnxTensor.createTensor(entorno, buffer, longArrayOf(1, 3, lado.toLong(), lado.toLong())).use { tensor ->
            sesion.run(mapOf(nombreEntrada to tensor)).use { salida ->
                val filas = (salida[0].value as Array<Array<FloatArray>>)[0]           // [300][38]
                val protos = (salida[1].value as Array<Array<Array<FloatArray>>>)[0]   // [32][ph][pw]
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
                        cajaOriginal = RectF((f[0] - lb.padX) / lb.escala, (f[1] - lb.padY) / lb.escala,
                                             (f[2] - lb.padX) / lb.escala, (f[3] - lb.padY) / lb.escala),
                        mascara = ampliarYRecortar(baja, ph, pw, f[0], f[1], f[2], f[3], lb),
                    )
                }
                return detecciones to lb
            }
        }
    }

    /** Ampliación bilineal (como cv2.INTER_LINEAR) a lado×lado, umbral 0.5,
     *  solo dentro de la caja y fuera del relleno del letterbox. */
    private fun ampliarYRecortar(m: FloatArray, ph: Int, pw: Int, x1: Float, y1: Float, x2: Float, y2: Float,
                                 lb: Letterbox): BooleanArray {
        val salida = BooleanArray(lado * lado)
        val sx = pw.toFloat() / lado
        val sy = ph.toFloat() / lado
        val cx0 = max(max(0, x1.toInt()), lb.padX)
        val cy0 = max(max(0, y1.toInt()), lb.padY)
        val cx1 = min(min(lado, ceil(x2).toInt()), lado - lb.padX)
        val cy1 = min(min(lado, ceil(y2).toInt()), lado - lb.padY)
        for (v in cy0 until cy1) {
            val fy = max(0f, (v + 0.5f) * sy - 0.5f)
            val y0 = min(ph - 1, floor(fy).toInt()); val yb = min(ph - 1, y0 + 1); val dy = fy - y0
            for (u in cx0 until cx1) {
                val fx = max(0f, (u + 0.5f) * sx - 0.5f)
                val x0 = min(pw - 1, floor(fx).toInt()); val xb = min(pw - 1, x0 + 1); val dx = fx - x0
                val valor = (1 - dy) * ((1 - dx) * m[y0 * pw + x0] + dx * m[y0 * pw + xb]) +
                            dy * ((1 - dx) * m[yb * pw + x0] + dx * m[yb * pw + xb])
                salida[v * lado + u] = valor > ConfigGulupa.UMBRAL_MASCARA
            }
        }
        return salida
    }

    override fun close() = sesion.close()
}

// -----------------------------------------------------------------------------
// Pipeline completo: Modelo A → recorte + fondo gris → Modelo B → severidad
// -----------------------------------------------------------------------------
class AnalizadorGulupa(context: Context) : AutoCloseable {
    private val modeloFruto = ModeloSegmentacion(context, "gulupa_fruto.onnx", ConfigGulupa.LADO_FRUTO)
    private val modeloLesiones = ModeloSegmentacion(context, "gulupa_lesiones.onnx", ConfigGulupa.LADO_LESIONES)

    fun analizar(fotoEnderezada: Bitmap): ResultadoGulupa {
        val foto = reducir(fotoEnderezada, ConfigGulupa.LADO_MAXIMO_FOTO)
        val (recorte, frutoOk) = recortarFruto(foto)
        val (detecciones, lb) = modeloLesiones.predecir(recorte)
        return calcularSeveridad(detecciones, lb, recorte, frutoOk)
    }

    private fun reducir(b: Bitmap, ladoMax: Int): Bitmap {
        val escala = ladoMax.toFloat() / max(b.width, b.height)
        return if (escala >= 1f) b
        else Bitmap.createScaledBitmap(b, (b.width * escala).roundToInt(), (b.height * escala).roundToInt(), true)
    }

    /** Modelo A: fruto de mayor confianza → recorte con margen → fondo gris fuera del fruto. */
    private fun recortarFruto(foto: Bitmap): Pair<Bitmap, Boolean> {
        val (dets, lb) = modeloFruto.predecir(foto)
        val fruto = dets.filter { it.clase == ConfigGulupa.ID_FRUTO }.maxByOrNull { it.confianza }
            ?: return foto to false                       // sin fruto: se analiza la foto completa

        val c = fruto.cajaOriginal
        val margen = (ConfigGulupa.MARGEN_RECORTE * max(c.width(), c.height())).roundToInt()
        val r = Rect(max(0, c.left.toInt() - margen), max(0, c.top.toInt() - margen),
                     min(foto.width, c.right.toInt() + margen), min(foto.height, c.bottom.toInt() + margen))
        val recorte = Bitmap.createBitmap(foto, r.left, r.top, r.width(), r.height()).copy(Bitmap.Config.ARGB_8888, true)

        // Máscara del fruto en píxeles del recorte (desde la rejilla del Modelo A)
        val w = recorte.width; val h = recorte.height
        var dentro = BooleanArray(w * h) { i -> fruto.mascara[lb.indice(r.left + i % w, r.top + i / w)] }
        dentro = dilatar(dentro, w, h, max(1, (ConfigGulupa.HOLGURA_FONDO * max(w, h)).roundToInt()))

        val pixeles = IntArray(w * h)
        recorte.getPixels(pixeles, 0, w, 0, 0, w, h)
        for (i in pixeles.indices) if (!dentro[i]) pixeles[i] = ConfigGulupa.COLOR_FONDO
        recorte.setPixels(pixeles, 0, w, 0, 0, w, h)
        return recorte to true
    }

    /** Dilatación con elemento cuadrado (aproximación rápida del elíptico de OpenCV). */
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

    /** Severidad = 100 × área(lesiones ∩ fruto) / área(fruto ∪ lesiones), sobre la rejilla del Modelo B. */
    private fun calcularSeveridad(dets: List<Deteccion>, lb: Letterbox, recorte: Bitmap, frutoOk: Boolean): ResultadoGulupa {
        val n = lb.lado * lb.lado
        val fruto = BooleanArray(n)
        val porLesion = ConfigGulupa.LESIONES.keys.associateWith { BooleanArray(n) }
        for (d in dets) {
            val destino = if (d.clase == ConfigGulupa.ID_FRUTO) fruto else porLesion[d.clase] ?: continue
            for (i in 0 until n) if (d.mascara[i]) destino[i] = true
        }
        val union = BooleanArray(n)
        for (m in porLesion.values) for (i in 0 until n) if (m[i]) union[i] = true
        for (i in 0 until n) if (union[i]) fruto[i] = true          // fruto ∪ lesiones

        val areaFruto = fruto.count { it }.toFloat()
        fun pct(m: BooleanArray) = if (areaFruto > 0) 100f * m.indices.count { m[it] && fruto[it] } / areaFruto else 0f

        val severidades = porLesion.map { (id, m) -> ConfigGulupa.LESIONES.getValue(id) to pct(m) }.toMap()
        val total = pct(union)
        val clase = if (total < ConfigGulupa.UMBRAL_SANO) ConfigGulupa.CLASE_SANA
                    else ConfigGulupa.CLASE_IMAGEN.getValue(severidades.maxBy { it.value }.key)
        return ResultadoGulupa(clase, total, severidades, frutoOk, recorte, dets)
    }

    override fun close() {
        modeloFruto.close()
        modeloLesiones.close()
    }
}
