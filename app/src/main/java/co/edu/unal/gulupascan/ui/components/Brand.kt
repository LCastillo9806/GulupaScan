package co.edu.unal.gulupascan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.gulupascan.R
import kotlinx.coroutines.delay

// ---- Paleta de la identidad visual (logos entregados por el equipo) ----
object BrandPalette {
    val Plum = Color(0xFF5D2741)      // "Gulupa" y anillo del fruto
    val Green = Color(0xFF005828)     // "Scan" verde y tallo
    val Yellow = Color(0xFFFEDC7A)    // "Scan" amarillo y pulpa
    val Purple = Color(0xFF9034BD)    // fondo morado
    val Leaf = Color(0xFF00A85A)      // hoja
    val Light = Color(0xFFF7F7F7)     // fondo claro
    val White = Color.White
}

/** Tipografía manuscrita del lema ("Detecta tu roña en la gulupa"). Licencia OFL. */
val HandFont = FontFamily(Font(R.font.patrick_hand))

// Proporciones de las imágenes de texto (ancho / alto) y separación entre palabras.
private const val GULUPA_RATIO = 672f / 194f
private const val SCAN_RATIO = 515f / 194f
private const val WORD_GAP = 0.17f

/** Logo del fruto (imagen con fondo transparente). */
@Composable
fun GulupaLogo(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Image(
        painterResource(R.drawable.logo_gulupa),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.size(size),
    )
}

/** Letrero "Gulupa" + "Scan" con colores intercambiables. [height] = alto de las letras. */
@Composable
fun Wordmark(
    height: Dp,
    gulupaColor: Color = BrandPalette.Plum,
    scanColor: Color = BrandPalette.Green,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.word_gulupa), contentDescription = "Gulupa",
            modifier = Modifier.height(height).aspectRatio(GULUPA_RATIO),
            colorFilter = ColorFilter.tint(gulupaColor),
        )
        Spacer(Modifier.width(height * WORD_GAP))
        Image(
            painterResource(R.drawable.word_scan), contentDescription = "Scan",
            modifier = Modifier.height(height).aspectRatio(SCAN_RATIO),
            colorFilter = ColorFilter.tint(scanColor),
        )
    }
}

/** Una combinación de colores de la identidad (fondo + "Gulupa" + "Scan" + lema). */
data class BrandVariant(val background: Color, val gulupa: Color, val scan: Color, val tagline: Color)

object BrandVariants {
    val LightGreen = BrandVariant(BrandPalette.Light, BrandPalette.Plum, BrandPalette.Green, BrandPalette.Plum)
    val LightYellow = BrandVariant(BrandPalette.Light, BrandPalette.Plum, BrandPalette.Yellow, BrandPalette.Plum)
    val PurpleYellow = BrandVariant(BrandPalette.Purple, BrandPalette.White, BrandPalette.Yellow, BrandPalette.White)
    val PurpleGreen = BrandVariant(BrandPalette.Purple, BrandPalette.White, BrandPalette.Green, BrandPalette.White)
    val GreenYellow = BrandVariant(BrandPalette.Green, BrandPalette.White, BrandPalette.Yellow, BrandPalette.White)

    /** Recorrido de la transición (las imágenes entregadas, una tras otra). */
    val cycle = listOf(LightGreen, LightYellow, PurpleGreen, PurpleYellow)
}

/**
 * Logo + letrero + lema que van cambiando de color, como una transición entre las imágenes
 * de la identidad. Con [loop] = true se repite mientras dure (p. ej. durante la inferencia);
 * con [loop] = false recorre [sequence] una vez, se queda en el último y llama a [onFinished].
 */
@Composable
fun AnimatedBrandLockup(
    modifier: Modifier = Modifier,
    sequence: List<BrandVariant> = BrandVariants.cycle,
    loop: Boolean = true,
    stepMillis: Long = 750,
    logoSize: Dp = 120.dp,
    wordHeight: Dp = 40.dp,
    tagline: String? = stringResource(R.string.splash_tagline),
    taglineSize: TextUnit = 22.sp,
    onFinished: () -> Unit = {},
) {
    var index by remember(sequence) { mutableIntStateOf(0) }
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(sequence, loop) {
        if (loop) {
            while (true) {
                delay(stepMillis)
                index = (index + 1) % sequence.size
            }
        } else {
            for (i in 1 until sequence.size) {
                delay(stepMillis)
                index = i
            }
            delay(stepMillis)
            finished()
        }
    }

    val v = sequence[index.coerceIn(0, sequence.lastIndex)]
    val spec = tween<Color>(durationMillis = (stepMillis * 0.6).toInt(), easing = FastOutSlowInEasing)
    val bg by animateColorAsState(v.background, spec, label = "bg")
    val g by animateColorAsState(v.gulupa, spec, label = "gulupa")
    val s by animateColorAsState(v.scan, spec, label = "scan")
    val t by animateColorAsState(v.tagline, spec, label = "tagline")

    // Pequeño "latido" del logo para que se note que está trabajando.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        0.96f, 1.04f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Box(modifier.background(bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GulupaLogo(Modifier.scale(scale), size = logoSize)
            Spacer(Modifier.height(logoSize * 0.08f))
            Wordmark(wordHeight, g, s)
            if (tagline != null) {
                Spacer(Modifier.height(wordHeight * 0.15f))
                Text(tagline, color = t, fontFamily = HandFont, fontSize = taglineSize, textAlign = TextAlign.Center)
            }
        }
    }
}
