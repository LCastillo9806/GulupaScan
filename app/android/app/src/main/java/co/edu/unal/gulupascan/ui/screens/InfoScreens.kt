package co.edu.unal.gulupascan.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unal.gulupascan.BuildConfig
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.domain.Classification
import co.edu.unal.gulupascan.domain.DamageCatalog
import co.edu.unal.gulupascan.ui.components.BrandHeader
import co.edu.unal.gulupascan.ui.components.BrandPalette
import co.edu.unal.gulupascan.ui.components.ColorDot
import co.edu.unal.gulupascan.ui.components.GsCard
import co.edu.unal.gulupascan.ui.components.GsTopBar
import co.edu.unal.gulupascan.ui.components.InfoNote
import co.edu.unal.gulupascan.ui.components.PrimaryButton
import co.edu.unal.gulupascan.ui.components.SecondaryButton
import co.edu.unal.gulupascan.ui.components.StepBadge
import co.edu.unal.gulupascan.ui.theme.Brand
import co.edu.unal.gulupascan.ui.theme.BrandDark
import co.edu.unal.gulupascan.ui.theme.BrandSoft
import co.edu.unal.gulupascan.ui.theme.DamageAnthracnose
import co.edu.unal.gulupascan.ui.theme.DamageOilSpot
import co.edu.unal.gulupascan.ui.theme.DamageScab
import co.edu.unal.gulupascan.ui.theme.Healthy
import co.edu.unal.gulupascan.ui.theme.LocalBrand
import co.edu.unal.gulupascan.ui.theme.Pulp
import co.edu.unal.gulupascan.ui.theme.TextSoft
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Acerca de nosotros
// ---------------------------------------------------------------------------
private data class TeamMember(
    val name: String,
    @StringRes val role: Int,
    @StringRes val faculty: Int,
    @DrawableRes val photo: Int?, // null = avatar genérico
)

private val team = listOf(
    TeamMember("Jesús Enrique Flores-Riera", R.string.team_role_jesus, R.string.team_fac_agrarias, R.drawable.team_jesus),
    TeamMember("Luis Alberto Castillo Sanabria", R.string.team_role_luis, R.string.team_fac_agrarias, R.drawable.team_luis),
    // Para poner la foto de Diego: copiar team_diego.jpg a res/drawable-nodpi y cambiar null por
    // R.drawable.team_diego (instrucciones completas en app/README.md).
    TeamMember("Diego Humberto Lavado González", R.string.team_role_systems, R.string.team_fac_engineering, null),
    TeamMember("Daniel Felipe Ahumada Hernández", R.string.team_role_systems, R.string.team_fac_engineering, R.drawable.team_daniel),
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { GsTopBar(stringResource(R.string.about_title), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BrandHeader(stringResource(R.string.about_commitment))
            Column {
                Text(stringResource(R.string.about_mission_t), style = MaterialTheme.typography.titleSmall, color = Brand)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_mission), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.about_team_t), style = MaterialTheme.typography.titleSmall, color = Brand)
            team.forEachIndexed { i, member -> TeamCard(member, i) }
            Text(stringResource(R.string.team_university), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.about_course), style = MaterialTheme.typography.bodySmall, color = TextSoft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TeamCard(member: TeamMember, index: Int) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(90L * index)
        shown = true
    }
    AnimatedVisibility(shown, enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) {
        GsCard {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val avatar = Modifier.size(84.dp).clip(CircleShape).border(2.dp, BrandDark, CircleShape)
                if (member.photo != null) {
                    Image(painterResource(member.photo), member.name, avatar, contentScale = ContentScale.Crop)
                } else {
                    Box(avatar.background(BrandSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Person, member.name, Modifier.size(48.dp), tint = BrandDark)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(member.name, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(member.role), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(member.faculty), style = MaterialTheme.typography.bodySmall, color = TextSoft)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Información técnica
// ---------------------------------------------------------------------------
@Composable
fun TechInfoScreen(onBack: () -> Unit) {
    Scaffold(topBar = { GsTopBar(stringResource(R.string.ti_title), onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandHeader(stringResource(R.string.ti_subtitle))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("2", stringResource(R.string.ti_stat_models), Modifier.weight(1f))
                StatTile("3", stringResource(R.string.ti_stat_lesions), Modifier.weight(1f))
                StatTile("${num(6.5, 1)} MB", stringResource(R.string.ti_stat_weight), Modifier.weight(1f))
            }
            PipelineDiagram()

            TechSection(R.string.ti_s1_t, Icons.Outlined.PhotoCamera, initiallyOpen = true) {
                Para(R.string.ti_s1_a)
                Para(R.string.ti_s1_b)
                Para(R.string.ti_s1_c)
                LetterboxDiagram()
            }
            TechSection(R.string.ti_s2_t, Icons.Outlined.TravelExplore) {
                Para(R.string.ti_s2_a)
                Spec(R.string.ti_spec_input, "[1, 3, 480, 480] float32")
                Spec(R.string.ti_spec_dets, "[1, 300, 38]")
                Spec(R.string.ti_spec_protos, "[1, 32, 120, 120]")
                Spec(R.string.ti_spec_classes, "fruit")
                Spec(R.string.ti_spec_file, "gulupa_fruto.onnx · ${num(3.2, 1)} MB")
            }
            TechSection(R.string.ti_s3_t, Icons.Outlined.CropFree) {
                Para(R.string.ti_s3_a)
                CropDiagram()
                Para(R.string.ti_s3_b)
            }
            TechSection(R.string.ti_s4_t, Icons.Outlined.Layers) {
                Para(R.string.ti_s4_a)
                listOf(DamageCatalog.SCAB, DamageCatalog.ANTHRACNOSE, DamageCatalog.OIL_SPOT).forEach { key ->
                    val info = DamageCatalog.get(key)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(info.color)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(info.name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text(info.scientific.orEmpty(), style = MaterialTheme.typography.bodySmall, color = TextSoft)
                    }
                }
                Spec(R.string.ti_spec_input, "[1, 3, 640, 640] float32")
                Spec(R.string.ti_spec_dets, "[1, 300, 38]")
                Spec(R.string.ti_spec_protos, "[1, 32, 160, 160]")
                Spec(R.string.ti_spec_classes, "fruit, scab, anthracnose, oil_spot")
                Spec(R.string.ti_spec_file, "gulupa_lesiones.onnx · ${num(3.3, 1)} MB")
            }
            TechSection(R.string.ti_s5_t, Icons.Outlined.GridOn) {
                Para(R.string.ti_s5_a)
                Formula(stringResource(R.string.ti_s5_formula))
                Para(R.string.ti_s5_b)
            }
            TechSection(R.string.ti_s6_t, Icons.Outlined.Calculate) {
                Formula(stringResource(R.string.ti_s6_formula))
                SeverityDiagram()
                Para(R.string.ti_s6_a)
                ClassBand(Classification.HEALTHY, "< ${num(0.5, 1)} %")
                ClassBand(Classification.LIGHT, "${num(0.5, 1)} – 10 %")
                ClassBand(Classification.MODERATE, "10 – 30 %")
                ClassBand(Classification.SEVERE, "≥ 30 %")
                Para(R.string.ti_s6_b)
                Formula("1: <1 · 2: <${num(2.5, 1)} · 3: <5 · 4: <${num(7.5, 1)} · 5: <10\n6: <15 · 7: <25 · 8: <40 · 9: <60 · 10: ≥60")
                InfoNote(stringResource(R.string.ti_s6_note))
            }
            TechSection(R.string.ti_s7_t, Icons.Outlined.BarChart) {
                Para(R.string.ti_s7_a)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("${num(80.3, 1)} %", stringResource(R.string.ti_stat_accuracy), Modifier.weight(1f))
                    StatTile(num(0.81, 2), stringResource(R.string.ti_stat_macro_f1), Modifier.weight(1f))
                }
                Text(stringResource(R.string.ti_s7_chart), style = MaterialTheme.typography.titleSmall)
                MetricRow(stringResource(R.string.dmg_scab), 0.973f, 0.947, 1.0, 36)
                MetricRow(stringResource(R.string.dmg_anthracnose), 0.8846f, 1.0, 0.793, 29)
                MetricRow(stringResource(R.string.dmg_healthy), 0.7021f, 0.559, 0.943, 35)
                MetricRow(stringResource(R.string.dmg_oil_spot), 0.6875f, 1.0, 0.524, 42)
                InfoNote(stringResource(R.string.ti_s7_note))
                Para(R.string.ti_s7_b)
            }
            TechSection(R.string.ti_s8_t, Icons.Outlined.Memory) {
                Para(R.string.ti_s8_a)
                Text(stringResource(R.string.ti_s8_check), style = MaterialTheme.typography.titleSmall)
                Spec(R.string.ti_s8_fruit, "IoU ${num(0.98, 2)} · 100 %")
                Spec(R.string.ti_s8_lesions, "IoU ${num(0.90, 2)} · 100 %")
                Para(R.string.ti_s8_b)
            }
            TechSection(R.string.ti_s9_t, Icons.Outlined.Dataset) {
                Para(R.string.ti_s9_a)
                Spec(R.string.ti_s9_total, "1039")
                Spec(R.string.ti_s9_split, "728 / 156 / 155")
                Para(R.string.ti_s9_b)
            }
            TechSection(R.string.ti_s10_t, Icons.Outlined.PrivacyTip) {
                Para(R.string.ti_s10_a)
                Para(R.string.ti_s10_b)
                Para(R.string.ti_s10_c)
            }
            TechSection(R.string.ti_s11_t, Icons.Outlined.WarningAmber) {
                listOf(R.string.ti_s11_1, R.string.ti_s11_2, R.string.ti_s11_3, R.string.ti_s11_4).forEach { Bullet(it) }
            }
            TechSection(R.string.ti_s12_t, Icons.Outlined.AccountTree) {
                Spec(R.string.ti_spec_version, BuildConfig.VERSION_NAME)
                Spec(R.string.ti_spec_ui, "Kotlin · Jetpack Compose · Material 3")
                Spec(R.string.ti_spec_camera, "CameraX 1.3.4")
                Spec(R.string.ti_spec_runtime, "ONNX Runtime Android 1.20.0")
                Spec(R.string.ti_spec_android, "Android 8.0 (API 26)")
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = BrandSoft) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = BrandDark)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextSoft, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TechSection(
    @StringRes title: Int,
    icon: ImageVector,
    initiallyOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    val angle by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron")
    GsCard {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = BrandDark, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null, tint = TextSoft, modifier = Modifier.rotate(angle))
            }
            AnimatedVisibility(open, enter = expandVertically(tween(280)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut()) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
    }
}

@Composable
private fun Para(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(@StringRes text: Int) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(BrandDark))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Spec(@StringRes label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = TextSoft, modifier = Modifier.weight(0.45f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun Formula(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text,
            Modifier.padding(12.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ClassBand(cls: Classification, range: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(cls.color)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(cls.title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(range, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Fila de desempeño: la barra representa el F1 (escala 0-1) y el resto va en texto. */
@Composable
private fun MetricRow(name: String, f1: Float, precision: Double, recall: Double, n: Int) {
    var target by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) { target = f1 }
    val fill by animateFloatAsState(target, tween(700), label = "f1")
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(num(f1.toDouble(), 2), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fill).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(BrandDark))
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.ti_metric_line, num(precision, 2), num(recall, 2), n), style = MaterialTheme.typography.bodySmall, color = TextSoft)
    }
}

/** Flujo foto → modelo A → recorte → modelo B → severidad, con el paso activo resaltado. */
@Composable
private fun PipelineDiagram() {
    val steps = listOf(
        Triple(Icons.Outlined.PhotoCamera, R.string.ti_flow1_t, R.string.ti_flow1),
        Triple(Icons.Outlined.TravelExplore, R.string.ti_flow2_t, R.string.ti_flow2),
        Triple(Icons.Outlined.CropFree, R.string.ti_flow3_t, R.string.ti_flow3),
        Triple(Icons.Outlined.Layers, R.string.ti_flow4_t, R.string.ti_flow4),
        Triple(Icons.Outlined.Calculate, R.string.ti_flow5_t, R.string.ti_flow5),
    )
    val t = loop(steps.size * 1400)
    val active = (t * steps.size).toInt().coerceAtMost(steps.lastIndex)
    val phase = t * steps.size - active

    GsCard {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.ti_flow_t), style = MaterialTheme.typography.titleSmall, color = Brand)
            Spacer(Modifier.height(10.dp))
            steps.forEachIndexed { i, (icon, title, desc) ->
                val on = i == active
                val bg by animateColorAsState(if (on) BrandDark else BrandSoft, tween(300), label = "bg")
                val fg by animateColorAsState(if (on) Color.White else BrandDark, tween(300), label = "fg")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = TextSoft)
                    }
                }
                if (i < steps.lastIndex) {
                    val line = MaterialTheme.colorScheme.outline
                    val dot = BrandDark
                    Canvas(Modifier.padding(start = 20.dp).width(2.dp).height(18.dp)) {
                        drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 2.dp.toPx())
                        if (on) drawCircle(dot, 4.dp.toPx(), Offset(0f, size.height * phase))
                    }
                }
            }
        }
    }
}

/** Foto rectangular escalada dentro del cuadrado de entrada, con las bandas de relleno. */
@Composable
private fun LetterboxDiagram() {
    val t = loop(3600)
    val s = smooth(t, 0.1f, 0.45f) - smooth(t, 0.85f, 1f)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val side = size.height * 0.92f
        val square = Rect(Offset(center.x - side / 2, center.y - side / 2), Size(side, side))
        val photoW = side * (1.25f - 0.25f * s)
        val photoH = photoW * 0.75f
        val photo = Rect(Offset(center.x - photoW / 2, center.y - photoH / 2), Size(photoW, photoH))
        drawRect(Color(0xFF727272), square.topLeft, square.size, alpha = s)
        drawRect(Color(0xFFCFE3C8), photo.topLeft, photo.size)
        drawGulupa(photo.center, photoH * 0.32f)
        drawRect(Color(0xFF444444), square.topLeft, square.size, style = Stroke(1.5.dp.toPx()))
    }
}

/** Foto con fondo → caja con margen → fondo gris: lo que recibe el Modelo B. */
@Composable
private fun CropDiagram() {
    val t = loop(4200)
    val box = smooth(t, 0.1f, 0.35f)
    val gray = smooth(t, 0.45f, 0.7f)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val r = size.height * 0.34f
        val frame = Rect(Offset(center.x - size.height * 0.62f, 0f), Size(size.height * 1.24f, size.height))
        drawRect(Color(0xFFB9D7A8), frame.topLeft, frame.size)
        drawCircle(Color(0xFF8DB37A), r * 0.7f, frame.topLeft + Offset(r * 0.5f, r * 0.6f))
        drawRect(Color(0xFF808080), frame.topLeft, frame.size, alpha = gray)
        drawGulupa(center, r)
        val m = r * 1.28f
        if (box > 0f) {
            drawRect(
                Color.White.copy(alpha = box),
                Offset(center.x - m, center.y - m),
                Size(m * 2, m * 2),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

/** Máscara del fruto con las lesiones que crecen sobre ella. */
@Composable
private fun SeverityDiagram() {
    val t = loop(4000)
    val grow = smooth(t, 0.1f, 0.6f) - smooth(t, 0.9f, 1f)
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val r = size.height * 0.40f
        drawCircle(Healthy.copy(alpha = 0.25f), r, center)
        drawCircle(Healthy, r, center, style = Stroke(2.dp.toPx()))
        drawCircle(DamageScab.copy(alpha = 0.8f), r * 0.30f * grow, center + Offset(-r * 0.35f, -r * 0.25f))
        drawCircle(DamageAnthracnose.copy(alpha = 0.8f), r * 0.22f * grow, center + Offset(r * 0.40f, r * 0.05f))
        drawCircle(DamageOilSpot.copy(alpha = 0.8f), r * 0.16f * grow, center + Offset(-r * 0.05f, r * 0.50f))
    }
}

// ---------------------------------------------------------------------------
// Instrucciones de uso
// ---------------------------------------------------------------------------
private data class GuideStep(
    @StringRes val title: Int,
    @StringRes val desc: Int,
    val tips: List<Int>,
    val scene: @Composable () -> Unit,
)

@Composable
fun GuideScreen(onBack: () -> Unit, onFinish: () -> Unit) {
    val steps = remember {
        listOf(
            GuideStep(R.string.g1_t, R.string.g1_d, listOf(R.string.g1_1, R.string.g1_2, R.string.g1_3)) { ScenePrepare() },
            GuideStep(R.string.g2_t, R.string.g2_d, listOf(R.string.g2_1, R.string.g2_2, R.string.g2_3)) { SceneFrame() },
            GuideStep(R.string.g3_t, R.string.g3_d, listOf(R.string.g3_1, R.string.g3_2, R.string.g3_3)) { SceneFocus() },
            GuideStep(R.string.g4_t, R.string.g4_d, listOf(R.string.g4_1, R.string.g4_2, R.string.g4_3)) { SceneCapture() },
            GuideStep(R.string.g5_t, R.string.g5_d, listOf(R.string.g5_1, R.string.g5_2, R.string.g5_3)) { SceneAnalyze() },
            GuideStep(R.string.g6_t, R.string.g6_d, listOf(R.string.g6_1, R.string.g6_2, R.string.g6_3)) { SceneResult() },
            GuideStep(R.string.g7_t, R.string.g7_d, listOf(R.string.g7_1, R.string.g7_2, R.string.g7_3)) { SceneShare() },
        )
    }
    val pager = rememberPagerState { steps.size }
    val scope = rememberCoroutineScope()
    val progress by animateFloatAsState((pager.currentPage + 1f) / steps.size, tween(400), label = "progress")

    Scaffold(topBar = { GsTopBar(stringResource(R.string.prof_guide), onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(BrandDark))
            }
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                val step = steps[page]
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StepBadge(page + 1)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(step.title), style = MaterialTheme.typography.titleLarge, color = Brand)
                    }
                    Text(stringResource(step.desc), style = MaterialTheme.typography.bodyMedium, color = TextSoft)
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = BrandSoft) {
                        Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { step.scene() }
                    }
                    step.tips.forEach { tip ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Check, null, tint = BrandDark, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                repeat(steps.size) { i ->
                    val on = i == pager.currentPage
                    val dot by animateFloatAsState(if (on) 1.35f else 1f, tween(250), label = "dot")
                    Box(
                        Modifier.padding(horizontal = 5.dp).size(9.dp).scale(dot).clip(CircleShape)
                            .background(if (on) BrandDark else MaterialTheme.colorScheme.outline)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pager.currentPage > 0) {
                    SecondaryButton(
                        stringResource(R.string.g_prev),
                        { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        Modifier.weight(1f),
                        Icons.AutoMirrored.Outlined.ArrowBack,
                    )
                }
                if (pager.currentPage < steps.lastIndex) {
                    PrimaryButton(
                        stringResource(R.string.g_next),
                        { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Outlined.ArrowForward,
                    )
                } else {
                    PrimaryButton(stringResource(R.string.g_finish), onFinish, Modifier.weight(1f), icon = Icons.Outlined.PhotoCamera)
                }
            }
        }
    }
}

private val SceneHeight: Dp = 190.dp

@Composable
private fun ScenePrepare() {
    val t = loop(2400)
    val shake = sin(t * 2 * PI.toFloat() * 3) * 3f * (1f - smooth(t, 0.3f, 0.5f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExampleCard(R.string.g1_ok, true, 0f, Modifier.weight(1f)) { c, r -> drawGulupa(c, r) }
        ExampleCard(R.string.g1_many, false, shake, Modifier.weight(1f)) { c, r ->
            drawGulupa(c + Offset(-r * 0.55f, r * 0.2f), r * 0.7f)
            drawGulupa(c + Offset(r * 0.55f, -r * 0.1f), r * 0.7f)
        }
        ExampleCard(R.string.g1_shadow, false, -shake, Modifier.weight(1f)) { c, r ->
            drawGulupa(c, r)
            drawCircle(Color.Black.copy(alpha = 0.55f), r, c + Offset(r * 0.55f, r * 0.2f))
        }
    }
}

@Composable
private fun ExampleCard(
    @StringRes label: Int,
    ok: Boolean,
    shake: Float,
    modifier: Modifier,
    drawing: DrawScope.(Offset, Float) -> Unit,
) {
    Surface(
        modifier.graphicsLayer { translationX = shake * density },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.TopEnd) {
                Canvas(Modifier.fillMaxWidth().height(96.dp)) { drawing(center, size.height * 0.36f) }
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(if (ok) Healthy else DamageScab),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (ok) Icons.Outlined.Check else Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(label), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SceneFrame() {
    val t = loop(3400)
    val grow = smooth(t, 0.1f, 0.55f) - smooth(t, 0.92f, 1f)
    val good = Healthy
    Canvas(Modifier.fillMaxWidth().height(SceneHeight)) {
        val screen = drawPhone(center)
        clipRect(screen.left, screen.top, screen.right, screen.bottom) {
            drawGulupa(screen.center, screen.width * (0.14f + 0.26f * grow))
        }
        val inner = screen.deflate(screen.width * 0.08f)
        drawCorners(inner, if (grow > 0.9f) good else Color.White)
    }
}

@Composable
private fun SceneFocus() {
    val t = loop(3000)
    val sharp = smooth(t, 0.3f, 0.5f) - smooth(t, 0.92f, 1f)
    val ring = smooth(t, 0.18f, 0.45f)
    val ringAlpha = smooth(t, 0.14f, 0.2f) - smooth(t, 0.5f, 0.65f)
    Canvas(Modifier.fillMaxWidth().height(SceneHeight)) {
        val screen = drawPhone(center)
        val r = screen.width * 0.36f
        for (i in 3 downTo 1) {
            drawCircle(BrandPalette.Plum.copy(alpha = (1f - sharp) * 0.22f), r * (1f + 0.07f * i), screen.center)
        }
        drawGulupa(screen.center, r, lesions = sharp)
        drawCircle(
            Pulp.copy(alpha = ringAlpha.coerceIn(0f, 1f)),
            r * (1.2f - 0.55f * ring),
            screen.center,
            style = Stroke(2.5.dp.toPx()),
        )
    }
}

@Composable
private fun SceneCapture() {
    val t = loop(2600)
    val flash = smooth(t, 0.30f, 0.34f) - smooth(t, 0.38f, 0.5f)
    val press = 1f - 0.12f * (smooth(t, 0.2f, 0.3f) - smooth(t, 0.34f, 0.42f))
    val pick = 1f + 0.10f * (smooth(t, 0.62f, 0.72f) - smooth(t, 0.8f, 0.92f))
    Row(Modifier.fillMaxWidth().height(SceneHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        CaptureOption(Icons.Outlined.PhotoCamera, R.string.g4_camera, press, flash)
        Text(stringResource(R.string.g4_or), style = MaterialTheme.typography.titleSmall, color = TextSoft)
        CaptureOption(Icons.Outlined.PhotoLibrary, R.string.g4_gallery, pick, 0f)
    }
}

@Composable
private fun CaptureOption(icon: ImageVector, @StringRes label: Int, scale: Float, flash: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(92.dp).scale(scale).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = BrandDark, modifier = Modifier.size(44.dp))
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.coerceIn(0f, 1f) * 0.9f)))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SceneAnalyze() {
    val t = loop(4200)
    val gray = smooth(t, 0.12f, 0.32f)
    val scan = smooth(t, 0.35f, 0.75f)
    val lesions = smooth(t, 0.45f, 0.8f) - smooth(t, 0.95f, 1f)
    val line = Pulp
    Canvas(Modifier.fillMaxWidth().height(SceneHeight)) {
        val side = minOf(size.height * 0.9f, size.width * 0.8f)
        val frame = Rect(Offset(center.x - side / 2, center.y - side / 2), Size(side, side))
        drawRoundRect(Color(0xFFB9D7A8), frame.topLeft, frame.size, CornerRadius(12.dp.toPx()))
        drawRoundRect(Color(0xFF808080).copy(alpha = gray), frame.topLeft, frame.size, CornerRadius(12.dp.toPx()))
        drawGulupa(center, side * 0.32f)
        val r = side * 0.32f
        drawCircle(DamageScab.copy(alpha = 0.75f * lesions), r * 0.26f, center + Offset(-r * 0.38f, -r * 0.2f))
        drawCircle(DamageAnthracnose.copy(alpha = 0.75f * lesions), r * 0.2f, center + Offset(r * 0.4f, r * 0.1f))
        drawCircle(DamageOilSpot.copy(alpha = 0.75f * lesions), r * 0.15f, center + Offset(-r * 0.05f, r * 0.52f))
        if (scan in 0.001f..0.999f) {
            val y = frame.top + frame.height * scan
            drawLine(line, Offset(frame.left, y), Offset(frame.right, y), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun SceneResult() {
    val t = loop(4000)
    val fill = smooth(t, 0.05f, 0.4f) - smooth(t, 0.94f, 1f)
    Column(Modifier.fillMaxWidth().height(SceneHeight), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.g6_example), style = MaterialTheme.typography.bodySmall, color = TextSoft)
        ExampleBar(R.string.dmg_scab, DamageScab, 12.5f, fill)
        ExampleBar(R.string.dmg_anthracnose, DamageAnthracnose, 3.2f, fill)
        ExampleBar(R.string.dmg_oil_spot, DamageOilSpot, 1.8f, fill)
        Text(
            stringResource(R.string.g6_total, stringResource(Classification.MODERATE.title)),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.graphicsLayer { alpha = smooth(t, 0.35f, 0.5f) - smooth(t, 0.94f, 1f) },
        )
    }
}

@Composable
private fun ExampleBar(@StringRes name: Int, color: Color, value: Float, fill: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("${num(value.toDouble(), 1)} %", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.fillMaxWidth((value / 25f * fill).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

@Composable
private fun SceneShare() {
    val t = loop(3600)
    val actions = listOf(
        Icons.Outlined.BookmarkAdd to R.string.g7_save,
        Icons.Outlined.PictureAsPdf to R.string.g7_pdf,
        Icons.Outlined.Share to R.string.g7_share,
    )
    Row(Modifier.fillMaxWidth().height(SceneHeight), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        actions.forEachIndexed { i, (icon, label) ->
            val start = i / 3f
            val pulse = smooth(t, start, start + 0.12f) - smooth(t, start + 0.2f, start + 0.32f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).scale(1f + 0.14f * pulse).clip(CircleShape)
                        .background(lerpColor(MaterialTheme.colorScheme.surface, BrandDark, pulse)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = lerpColor(BrandDark, Color.White, pulse), modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Utilidades de animación y dibujo
// ---------------------------------------------------------------------------

/** Número con el separador decimal del idioma activo. */
private fun num(v: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", v)

/** Progreso lineal 0-1 que se repite sin fin cada [durationMs]. */
@Composable
private fun loop(durationMs: Int): Float {
    val transition = rememberInfiniteTransition(label = "loop")
    val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(durationMs, easing = LinearEasing)), label = "loop")
    return value
}

/** Rampa suave de 0 a 1 mientras [t] va de [from] a [to]. */
private fun smooth(t: Float, from: Float, to: Float): Float {
    val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpColor(a: Color, b: Color, f: Float): Color =
    androidx.compose.ui.graphics.lerp(a, b, f.coerceIn(0f, 1f))

/** Gulupa dibujada: fruto ciruela con brillo, tallo y lesiones opcionales. */
private fun DrawScope.drawGulupa(c: Offset, r: Float, lesions: Float = 0f) {
    drawLine(
        BrandPalette.Green,
        c + Offset(0f, -r * 0.95f),
        c + Offset(r * 0.18f, -r * 1.28f),
        r * 0.1f,
        StrokeCap.Round,
    )
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFF9A5A7C), BrandPalette.Plum),
            center = c + Offset(-r * 0.3f, -r * 0.35f),
            radius = r * 1.5f,
        ),
        r,
        c,
    )
    drawCircle(Color.White.copy(alpha = 0.18f), r * 0.2f, c + Offset(-r * 0.42f, -r * 0.46f))
    if (lesions > 0f) {
        val spot = Color(0xFF2B1A12).copy(alpha = lesions.coerceIn(0f, 1f) * 0.8f)
        drawCircle(spot, r * 0.15f, c + Offset(r * 0.28f, -r * 0.1f))
        drawCircle(spot, r * 0.1f, c + Offset(-r * 0.2f, r * 0.34f))
        drawCircle(spot, r * 0.07f, c + Offset(r * 0.1f, r * 0.5f))
    }
}

/** Dibuja un teléfono centrado en [c] y devuelve el rectángulo de su pantalla. */
private fun DrawScope.drawPhone(c: Offset): Rect {
    val h = size.height * 0.98f
    val w = h * 0.56f
    val body = Rect(Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
    drawRoundRect(Color(0xFF1F1F1F), body.topLeft, body.size, CornerRadius(w * 0.12f))
    val screen = body.deflate(w * 0.05f)
    drawRoundRect(Color(0xFFC9DDBC), screen.topLeft, screen.size, CornerRadius(w * 0.08f))
    return screen
}

private fun DrawScope.drawCorners(r: Rect, color: Color) {
    val len = r.width * 0.16f
    val sw = 2.5.dp.toPx()
    fun l(a: Offset, b: Offset) = drawLine(color, a, b, sw, StrokeCap.Round)
    l(r.topLeft, r.topLeft + Offset(len, 0f)); l(r.topLeft, r.topLeft + Offset(0f, len))
    l(r.topRight, r.topRight + Offset(-len, 0f)); l(r.topRight, r.topRight + Offset(0f, len))
    l(r.bottomLeft, r.bottomLeft + Offset(len, 0f)); l(r.bottomLeft, r.bottomLeft + Offset(0f, -len))
    l(r.bottomRight, r.bottomRight + Offset(-len, 0f)); l(r.bottomRight, r.bottomRight + Offset(0f, -len))
}
