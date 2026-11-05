package co.edu.unal.gulupascan.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.domain.Severity
import co.edu.unal.gulupascan.ui.theme.BorderColor
import co.edu.unal.gulupascan.ui.theme.Brand
import co.edu.unal.gulupascan.ui.theme.BrandDark
import co.edu.unal.gulupascan.ui.theme.TextSoft
import coil.compose.AsyncImage
import java.io.File

/** Encabezado de marca: logo + letrero "GulupaScan" + subtítulo. */
@Composable
fun BrandHeader(subtitle: String?, modifier: Modifier = Modifier, logoSize: Dp = 64.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GulupaLogo(size = logoSize)
        Spacer(Modifier.width(10.dp))
        Column {
            Wordmark(height = 30.dp, gulupaColor = BrandPalette.Plum, scanColor = BrandPalette.Green)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSoft)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GsTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BrandDark),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(26.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = BrandDark); Spacer(Modifier.width(8.dp))
        }
        Text(text, color = BrandDark, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Número dentro de un círculo verde, como los pasos 1-5 del mockup. */
@Composable
fun StepBadge(number: Int, size: Dp = 28.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Brand), contentAlignment = Alignment.Center) {
        Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, null, tint = BrandDark, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = TextSoft)
        }
    }
}

@Composable
fun GsCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth().border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = color,
    ) { content() }
}

/** Escala 1-10 de severidad con el nivel actual resaltado (pantalla "Detalle del daño"). */
@Composable
fun SeverityScale(level: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 1..10) {
            val active = i == level
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (active) Severity.levelColor(level) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$i",
                    color = if (active) Color.White else TextSoft,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PathImage(path: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, description: String? = null) {
    if (path == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        AsyncImage(model = File(path), contentDescription = description, modifier = modifier, contentScale = contentScale)
    }
}

/** Comparte texto y (opcionalmente) un archivo mediante el menú del sistema (WhatsApp, correo…). */
fun shareContent(context: Context, text: String, file: File?, mime: String = "image/*") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mime
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, null))
}

fun pct(v: Double): String = String.format(java.util.Locale.getDefault(), "%.1f %%", v)

/**
 * Formas orgánicas en las esquinas, como en las láminas de la presentación (tema morado).
 * No dibuja nada en el tema verde.
 */
@Composable
fun OrganicBlobs(modifier: Modifier = Modifier, color: Color = co.edu.unal.gulupascan.ui.theme.LocalBrand.current.accent.copy(alpha = 0.55f)) {
    if (!co.edu.unal.gulupascan.ui.theme.LocalBrand.current.decorated) return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Esquina superior izquierda
        drawPath(Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.34f, 0f)
            cubicTo(w * 0.30f, h * 0.06f, w * 0.20f, h * 0.05f, w * 0.17f, h * 0.10f)
            cubicTo(w * 0.13f, h * 0.16f, w * 0.06f, h * 0.17f, 0f, h * 0.15f)
            close()
        }, color)
        // Esquina superior derecha
        drawPath(Path().apply {
            moveTo(w, 0f)
            lineTo(w * 0.80f, 0f)
            cubicTo(w * 0.78f, h * 0.05f, w * 0.86f, h * 0.07f, w * 0.88f, h * 0.11f)
            cubicTo(w * 0.90f, h * 0.15f, w * 0.96f, h * 0.17f, w, h * 0.16f)
            close()
        }, color)
        // Esquina inferior izquierda
        drawPath(Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.82f)
            cubicTo(w * 0.10f, h * 0.80f, w * 0.18f, h * 0.86f, w * 0.20f, h * 0.91f)
            cubicTo(w * 0.22f, h * 0.96f, w * 0.30f, h * 0.97f, w * 0.32f, h)
            close()
        }, color)
        // Esquina inferior derecha
        drawPath(Path().apply {
            moveTo(w, h)
            lineTo(w * 0.70f, h)
            cubicTo(w * 0.74f, h * 0.95f, w * 0.82f, h * 0.95f, w * 0.85f, h * 0.90f)
            cubicTo(w * 0.88f, h * 0.85f, w * 0.95f, h * 0.84f, w, h * 0.86f)
            close()
        }, color)
    }
}
