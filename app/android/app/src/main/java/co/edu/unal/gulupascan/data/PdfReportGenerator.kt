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
 * Generador de Reporte Técnico en formato PDF con el diseño institucional y
 * estructurado de presentación (lámina de resultados, severidad, diagnóstico y recomendaciones).
 */
object PdfReportGenerator {

    suspend fun generateReport(context: Context, analysis: Analysis): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        // Página A4 estándar: 595 x 842 puntos
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fondo y cabecera
        paint.color = Color.rgb(247, 247, 247)
        canvas.drawRect(0f, 0f, 595f, 842f, paint)

        // Barra superior decorativa (Morado UNAL)
        paint.color = Color.rgb(144, 52, 189)
        canvas.drawRect(0f, 0f, 595f, 75f, paint)

        // Acento verde
        paint.color = Color.rgb(0, 88, 40)
        canvas.drawRect(0f, 75f, 595f, 80f, paint)

        // Título del reporte
        paint.color = Color.WHITE
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText("GULUPASCAN · REPORTE TÉCNICO", 35f, 45f, paint)

        paint.textSize = 10f
        paint.isFakeBoldText = false
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        canvas.drawText("Fecha: ${dateFormat.format(Date(analysis.createdAt))}  |  ID: #${analysis.id}", 35f, 63f, paint)

        // Tarjeta resumen principal
        paint.color = Color.WHITE
        val cardRect = RectF(35f, 95f, 560f, 290f)
        canvas.drawRoundRect(cardRect, 12f, 12f, paint)

        // Borde tarjeta
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.rgb(230, 225, 235)
        canvas.drawRoundRect(cardRect, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        // Imagen analizada en el PDF
        val imgPath = analysis.images.result ?: analysis.images.crop ?: analysis.images.original
        val bmp = imgPath?.let { File(it) }?.takeIf { it.exists() }?.let {
            runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
        }

        val imgRect = RectF(50f, 110f, 220f, 275f)
        if (bmp != null) {
            val src = Rect(0, 0, bmp.width, bmp.height)
            canvas.drawBitmap(bmp, src, imgRect, paint)
        } else {
            paint.color = Color.LTGRAY
            canvas.drawRect(imgRect, paint)
        }

        // Datos del diagnóstico al lado de la imagen
        val leftText = 235f
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText("DIAGNÓSTICO FITOSANITARIO", leftText, 128f, paint)

        paint.textSize = 12f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(100, 90, 100)
        canvas.drawText("Clasificación comercial:", leftText, 150f, paint)

        paint.textSize = 13f
        paint.isFakeBoldText = true
        val clsTitle = when (analysis.classification.name) {
            "HEALTHY" -> "Fruto Sano (Calidad Extra)"
            "LIGHT" -> "Daño Leve (Categoría I)"
            "MODERATE" -> "Daño Moderado (Categoría II)"
            else -> "Daño Severo (Descarte / Industria)"
        }
        val clsColor = when (analysis.classification.name) {
            "HEALTHY" -> Color.rgb(46, 158, 68)
            "LIGHT" -> Color.rgb(255, 152, 0)
            "MODERATE" -> Color.rgb(230, 81, 0)
            else -> Color.rgb(229, 57, 53)
        }
        paint.color = clsColor
        canvas.drawText(clsTitle, leftText, 168f, paint)

        paint.color = Color.rgb(100, 90, 100)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Severidad total:", leftText, 195f, paint)

        paint.textSize = 18f
        paint.isFakeBoldText = true
        paint.color = Color.rgb(144, 52, 189)
        canvas.drawText(String.format(Locale.US, "%.1f %%", analysis.totalDamagePct), leftText, 218f, paint)

        paint.color = Color.rgb(100, 90, 100)
        paint.textSize = 11f
        paint.isFakeBoldText = false
        val motor = if (analysis.isDemo) "Modo Demostración" else "YOLO26n-seg On-Device (Offline)"
        canvas.drawText("Motor: $motor", leftText, 245f, paint)
        canvas.drawText("Confianza: ${(analysis.modelConfidence * 100).toInt()}%", leftText, 262f, paint)

        // Tabla de desglose de lesiones
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("DESGLOSE EPIDÉRMICO POR PATÓGENO", 35f, 320f, paint)

        var tableY = 345f
        // Encabezado de la tabla
        paint.color = Color.rgb(235, 230, 240)
        canvas.drawRect(35f, tableY - 18f, 560f, tableY + 8f, paint)

        paint.color = Color.rgb(60, 40, 60)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText("Lesión / Daño", 45f, tableY, paint)
        canvas.drawText("Agente Causal", 200f, tableY, paint)
        canvas.drawText("Área (%)", 460f, tableY, paint)

        tableY += 28f
        val rows = analysis.harmfulDamages + analysis.damages.filter { it.key == DamageCatalog.HEALTHY }

        rows.forEach { d ->
            val info = DamageCatalog.get(d.key)
            val dName = when (d.key) {
                "scab" -> "Sarna (Roña)"
                "anthracnose" -> "Antracnosis"
                "oil_spot" -> "Mancha de aceite"
                "fruit" -> "Fruta / Fruto sano"
                "healthy" -> "Tejido sano"
                else -> d.key
            }
            paint.isFakeBoldText = false
            paint.color = Color.rgb(40, 40, 40)
            canvas.drawText(dName, 45f, tableY, paint)

            paint.color = Color.GRAY
            canvas.drawText(info.scientific ?: "—", 200f, tableY, paint)

            paint.isFakeBoldText = true
            paint.color = if (d.key == DamageCatalog.HEALTHY) Color.rgb(46, 158, 68) else Color.rgb(200, 50, 50)
            canvas.drawText(String.format(Locale.US, "%.1f %%", d.pct), 460f, tableY, paint)

            // Línea divisoria
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.5f
            paint.color = Color.rgb(230, 230, 230)
            canvas.drawLine(35f, tableY + 8f, 560f, tableY + 8f, paint)
            paint.style = Paint.Style.FILL

            tableY += 26f
        }

        // Recomendaciones agronómicas
        tableY += 25f
        paint.color = Color.rgb(42, 26, 34)
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("RECOMENDACIONES Y MANEJO EN CULTIVO", 35f, tableY, paint)

        tableY += 18f
        paint.color = Color.WHITE
        val advRect = RectF(35f, tableY, 560f, tableY + 110f)
        canvas.drawRoundRect(advRect, 10f, 10f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.rgb(220, 220, 220)
        canvas.drawRoundRect(advRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        val adviceText = when (analysis.classification.name) {
            "HEALTHY" -> "Lote óptimo para exportación. Mantener el programa regular de fertilización y monitoreo periódico de follaje y fruto."
            "LIGHT" -> "Aparición incipiente de lesiones (< 5%). Realizar monitoreo focalizado en el lote, podas sanitarias y desinfección preventiva."
            "MODERATE" -> "Severidad moderada (5-20%). Se sugiere evaluar destino agroindustrial local y aplicar control fitosanitario específico con rotación de fungicidas."
            else -> "Severidad alta (> 20%). Descarte fitosanitario. Aislar frutos afectados y consultar asesoría agronómica para frenar el foco de infección."
        }

        paint.color = Color.rgb(50, 50, 50)
        paint.textSize = 11f
        paint.isFakeBoldText = false

        // Dividir texto largo en líneas
        val words = adviceText.split(" ")
        var line = ""
        var textY = tableY + 28f
        for (w in words) {
            if (paint.measureText("$line $w") < 490f) {
                line = if (line.isEmpty()) w else "$line $w"
            } else {
                canvas.drawText(line, 50f, textY, paint)
                line = w
                textY += 18f
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, 50f, textY, paint)
        }

        // Pie de página institucional
        paint.color = Color.GRAY
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Universidad Nacional de Colombia · Facultad de Ciencias Agrarias · Proyecto GulupaScan", 35f, 810f, paint)
        canvas.drawText("Página 1 de 1", 500f, 810f, paint)

        doc.finishPage(page)

        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val outFile = File(dir, "GulupaScan_Reporte_${analysis.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        outFile
    }
}
