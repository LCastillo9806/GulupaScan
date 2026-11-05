package co.edu.unal.gulupascan.domain

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.ui.theme.DamageScab
import co.edu.unal.gulupascan.ui.theme.DamageThrips
import co.edu.unal.gulupascan.ui.theme.Healthy
import co.edu.unal.gulupascan.ui.theme.WarnText

enum class Classification(@StringRes val title: Int, @StringRes val advice: Int, val color: Color) {
    HEALTHY(R.string.class_healthy, R.string.advice_healthy, Healthy),
    LIGHT(R.string.class_light, R.string.advice_light, DamageThrips),
    MODERATE(R.string.class_moderate, R.string.advice_moderate, WarnText),
    SEVERE(R.string.class_severe, R.string.advice_severe, DamageScab),
}

/**
 * Umbrales de decisión (canvas: "Umbrales de decisión").
 * Convierte el % de área afectada en una escala 1-10 y en una clasificación general.
 * Son valores iniciales para la presentación; se deben calibrar con agrónomos.
 */
object Severity {
    /** Límite superior (exclusivo) de % de área para cada nivel 1..9; >= último -> 10. */
    private val levelLimits = doubleArrayOf(1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 25.0, 40.0, 60.0)

    fun level(pct: Double): Int {
        if (pct <= 0.0) return 0
        val idx = levelLimits.indexOfFirst { pct < it }
        return if (idx == -1) 10 else idx + 1
    }

    @StringRes
    fun levelLabel(level: Int): Int = when (level) {
        0 -> R.string.sev_none
        1, 2 -> R.string.sev_low
        3, 4 -> R.string.sev_moderate_low
        5 -> R.string.sev_moderate
        6, 7 -> R.string.sev_moderate_high
        else -> R.string.sev_high
    }

    fun levelColor(level: Int): Color = when {
        level <= 2 -> Healthy
        level <= 5 -> DamageThrips
        else -> DamageScab
    }

    fun classify(a: Analysis): Classification {
        if (a.isHealthy || a.harmfulDamages.isEmpty()) return Classification.HEALTHY
        val total = a.totalDamagePct
        return when {
            // Si el clasificador dijo "enfermo" no lo reportamos como sano aunque el área sea mínima.
            total < 2.0 -> if (a.diseaseName != null) Classification.LIGHT else Classification.HEALTHY
            total < 10.0 -> Classification.LIGHT
            total < 30.0 -> Classification.MODERATE
            else -> Classification.SEVERE
        }
    }
}
