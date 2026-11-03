package co.edu.unal.gulupascan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.domain.Analysis
import co.edu.unal.gulupascan.domain.DamageCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generador de Reporte Técnico en formato PDF de alta fidelidad, con métricas completas
 * de inferencia, tiempos de ejecución (Modelos A y B), desglose por patógeno,
 * comparación de imagen original vs. procesada y recomendaciones agronómicas.
 */
object PdfReportGenerator {

    suspend fun generateReport(context: Context, analysis: Analysis): File = withContext(Dispatchers.IO) {
        fun s(id: Int) = context.getString(id)
        val doc = PdfDocument()
        // Página A4 estándar: 595 x 842 puntos
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fondo general
        paint.color = Color.rgb(248, 247, 249)
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Cabecera institucional (Morado Gulupa UNAL)
        paint.color = Color.rgb(144, 52, 189)
        canvas.drawRect(0f, 0f, 595f, 72f, paint)

        // Franja de acento verde
        paint.color = Color.rgb(0, 88, 40)
        canvas.drawRect(0f, 72f, 595f, 77f, paint)

        // Título del reporte
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_title), 30f, 42f, paint)

        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        canvas.drawText(context.getString(R.string.pdf_subtitle, dateFormat.format(Date(analysis.createdAt)), analysis.id), 30f, 60f, paint)

        // ---------------------------------------------------------------------
        // SECCIÓN 1: IMÁGENES COMPARATIVAS (Original vs. Fruto Segmentado)
        // ---------------------------------------------------------------------
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_evidence), 30f, 96f, paint)

        // Tarjeta para Foto Original
        drawPhotoCard(
            canvas, paint,
            title = s(R.string.pdf_original),
            filePath = analysis.images.original,
            rect = RectF(30f, 106f, 290f, 256f),
            unavailable = s(R.string.pdf_no_image),
        )

        // Tarjeta para Foto Procesada
        drawPhotoCard(
            canvas, paint,
            title = s(R.string.pdf_masks),
            filePath = analysis.images.result ?: analysis.images.crop ?: analysis.images.original,
            rect = RectF(305f, 106f, 565f, 256f),
            unavailable = s(R.string.pdf_no_image),
        )

        // ---------------------------------------------------------------------
        // SECCIÓN 2: DIAGNÓSTICO Y CLASIFICACIÓN COMERCIAL
        // ---------------------------------------------------------------------
        val cardDiag = RectF(30f, 268f, 565f, 350f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(cardDiag, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(225, 220, 230)
        canvas.drawRoundRect(cardDiag, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 11.5f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_diagnosis), 45f, 288f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(100, 90, 100)
        canvas.drawText(s(R.string.pdf_class_label), 45f, 308f, paint)

        val clsTitle = when (analysis.classification.name) {
            "HEALTHY" -> s(R.string.pdf_cls_healthy)
            "LIGHT" -> s(R.string.pdf_cls_light)
            "MODERATE" -> s(R.string.pdf_cls_moderate)
            else -> s(R.string.pdf_cls_severe)
        }
        val clsColor = when (analysis.classification.name) {
            "HEALTHY" -> Color.rgb(46, 158, 68)
            "LIGHT" -> Color.rgb(230, 120, 0)
            "MODERATE" -> Color.rgb(210, 70, 0)
            else -> Color.rgb(220, 40, 40)
        }
        paint.color = clsColor
        paint.textSize = 11.5f
        paint.isFakeBoldText = true
        canvas.drawText(clsTitle, 45f, 326f, paint)

        // Severidad a la derecha
        paint.color = Color.rgb(100, 90, 100)
        paint.textSize = 10f
        paint.isFakeBoldText = false
        canvas.drawText(s(R.string.pdf_total), 380f, 308f, paint)

        paint.color = Color.rgb(144, 52, 189)
        paint.textSize = 18f
        paint.isFakeBoldText = true
        canvas.drawText(String.format(Locale.US, "%.2f %%", analysis.totalDamagePct), 380f, 330f, paint)

        // ---------------------------------------------------------------------
        // SECCIÓN 3: MÉTRICAS COMPLETAS DE INFERENCIA Y MODELOS ONNX
        // ---------------------------------------------------------------------
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_metrics), 30f, 372f, paint)

        val metricsCard = RectF(30f, 380f, 565f, 475f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(metricsCard, 10f, 10f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(225, 220, 230)
        canvas.drawRoundRect(metricsCard, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        var mY = 400f
        val col1 = 45f
        val col2 = 300f

        drawMetricLine(canvas, paint, col1, mY, s(R.string.pdf_engine), if (analysis.isDemo) s(R.string.pdf_simulation) else "ONNX Runtime int8 (CPU)")
        drawMetricLine(canvas, paint, col2, mY, s(R.string.pdf_time_a), "${analysis.clsTimeMs.toInt()} ms (480x480)")

        mY += 18f
        drawMetricLine(canvas, paint, col1, mY, s(R.string.pdf_arch), "YOLO26n-seg Dual Pipeline")
        drawMetricLine(canvas, paint, col2, mY, s(R.string.pdf_time_b), "${analysis.segTimeMs.toInt()} ms (640x640)")

        mY += 18f
        val totalMs = (analysis.clsTimeMs + analysis.segTimeMs).toInt()
        val fps = if (totalMs > 0) String.format(Locale.US, "%.1f FPS", 1000.0 / totalMs) else "—"
        drawMetricLine(canvas, paint, col1, mY, s(R.string.pdf_time_total), "$totalMs ms ($fps)")
        drawMetricLine(canvas, paint, col2, mY, s(R.string.pdf_conf_fruit), "${(analysis.leafConfidence * 100).toInt()} %")

        mY += 18f
        drawMetricLine(canvas, paint, col1, mY, s(R.string.pdf_conf_damage), "${(analysis.modelConfidence * 100).toInt()} %")
        drawMetricLine(canvas, paint, col2, mY, s(R.string.pdf_model_size), s(R.string.pdf_model_size_value))

        // ---------------------------------------------------------------------
        // SECCIÓN 4: DESGLOSE EPIDÉRMICO POR PATÓGENO
        // ---------------------------------------------------------------------
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_breakdown), 30f, 498f, paint)

        var tableY = 512f
        paint.color = Color.rgb(235, 230, 242)
        canvas.drawRect(30f, tableY - 14f, 565f, tableY + 8f, paint)

        paint.color = Color.rgb(60, 40, 60)
        paint.textSize = 10f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_col_damage), 40f, tableY, paint)
        canvas.drawText(s(R.string.pdf_col_agent), 180f, tableY, paint)
        canvas.drawText(s(R.string.pdf_col_level), 360f, tableY, paint)
        canvas.drawText(s(R.string.pdf_col_area), 485f, tableY, paint)

        tableY += 24f
        val rows = analysis.harmfulDamages + analysis.damages.filter { it.key == DamageCatalog.HEALTHY }

        rows.forEach { d ->
            val info = DamageCatalog.get(d.key)
            val dName = when (d.key) {
                "scab" -> s(R.string.pdf_scab)
                "anthracnose" -> s(R.string.pdf_anthracnose)
                "oil_spot" -> s(R.string.pdf_oil_spot)
                "fruit" -> s(R.string.pdf_fruit)
                "healthy" -> s(R.string.pdf_healthy_tissue)
                else -> d.key
            }
            val levelStr = if (d.key == DamageCatalog.HEALTHY) "0 / 10" else "${co.edu.unal.gulupascan.domain.Severity.level(d.pct)} / 10"

            paint.isFakeBoldText = false
            paint.color = Color.rgb(40, 40, 40)
            canvas.drawText(dName, 40f, tableY, paint)

            paint.color = Color.rgb(110, 110, 110)
            canvas.drawText(info.scientific ?: "—", 180f, tableY, paint)

            paint.color = Color.rgb(80, 80, 80)
            canvas.drawText(levelStr, 360f, tableY, paint)

            paint.isFakeBoldText = true
            paint.color = if (d.key == DamageCatalog.HEALTHY) Color.rgb(46, 158, 68) else Color.rgb(210, 50, 50)
            canvas.drawText(String.format(Locale.US, "%.2f %%", d.pct), 485f, tableY, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            paint.color = Color.rgb(230, 230, 230)
            canvas.drawLine(30f, tableY + 6f, 565f, tableY + 6f, paint)
            paint.style = Paint.Style.FILL

            tableY += 22f
        }

        // ---------------------------------------------------------------------
        // SECCIÓN 5: RECOMENDACIONES AGRONÓMICAS DE MANEJO
        // ---------------------------------------------------------------------
        tableY += 15f
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText(s(R.string.pdf_advice_t), 30f, tableY, paint)

        tableY += 10f
        val advRect = RectF(30f, tableY, 565f, tableY + 80f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(advRect, 8f, 8f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(220, 220, 220)
        canvas.drawRoundRect(advRect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        val adviceText = when (analysis.classification.name) {
            "HEALTHY" -> s(R.string.pdf_advice_healthy)
            "LIGHT" -> s(R.string.pdf_advice_light)
            "MODERATE" -> s(R.string.pdf_advice_moderate)
            else -> s(R.string.pdf_advice_severe)
        }

        paint.color = Color.rgb(55, 55, 55)
        paint.textSize = 9.5f
        paint.isFakeBoldText = false

        val words = adviceText.split(" ")
        var line = ""
        var textY = tableY + 22f
        for (w in words) {
            if (paint.measureText("$line $w") < 500f) {
                line = if (line.isEmpty()) w else "$line $w"
            } else {
                canvas.drawText(line, 45f, textY, paint)
                line = w
                textY += 15f
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 45f, textY, paint)
        }

        // Pie de página institucional
        paint.color = Color.GRAY
        paint.textSize = 8.5f
        paint.isFakeBoldText = false
        canvas.drawText(s(R.string.pdf_footer), 30f, 820f, paint)
        canvas.drawText(s(R.string.pdf_page), 420f, 820f, paint)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(dir, "GulupaScan_Informe_${analysis.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }

    private fun drawPhotoCard(canvas: android.graphics.Canvas, paint: Paint, title: String, filePath: String?, rect: RectF, unavailable: String) {
        // Fondo tarjeta foto
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(220, 220, 225)
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Título de la tarjeta
        paint.color = Color.rgb(80, 70, 80)
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText(title, rect.left + 10f, rect.top + 16f, paint)

        // Espacio para la imagen
        val imgArea = RectF(rect.left + 8f, rect.top + 24f, rect.right - 8f, rect.bottom - 8f)
        val bmp = filePath?.let { File(it) }?.takeIf { it.exists() }?.let {
            runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
        }

        if (bmp != null) {
            val src = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, src, imgArea, paint)
        } else {
            paint.color = Color.rgb(235, 235, 235)
            canvas.drawRect(imgArea, paint)
            paint.color = Color.GRAY
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText(unavailable, imgArea.left + 20f, imgArea.centerY(), paint)
        }
    }

    private fun drawMetricLine(canvas: android.graphics.Canvas, paint: Paint, x: Float, y: Float, label: String, value: String) {
        paint.color = Color.rgb(100, 95, 105)
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText(label, x, y, paint)

        val offset = paint.measureText(label) + 6f
        paint.color = Color.rgb(35, 25, 35)
        paint.isFakeBoldText = true
        canvas.drawText(value, x + offset, y, paint)
    }
}
