package co.edu.unal.gulupascan.domain

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.ui.theme.DamageAnthracnose
import co.edu.unal.gulupascan.ui.theme.DamageFruit
import co.edu.unal.gulupascan.ui.theme.DamageMechanical
import co.edu.unal.gulupascan.ui.theme.DamageMildew
import co.edu.unal.gulupascan.ui.theme.DamageOilSpot
import co.edu.unal.gulupascan.ui.theme.DamageScab
import co.edu.unal.gulupascan.ui.theme.DamageThrips
import co.edu.unal.gulupascan.ui.theme.Healthy
import co.edu.unal.gulupascan.ui.theme.Neutral

/**
 * Catálogo de tipos de daño. Se asigna clave técnica, nombre traducible,
 * agente causal / nombre científico, descripción y color representativo.
 */
data class DamageInfo(
    val key: String,
    @StringRes val name: Int,
    val scientific: String?,
    @StringRes val description: Int,
    val color: Color,
)

object DamageCatalog {
    const val HEALTHY = "healthy"
    const val FRUIT = "fruit"
    const val SCAB = "scab"
    const val ANTHRACNOSE = "anthracnose"
    const val OIL_SPOT = "oil_spot"

    private val items = listOf(
        // Daños reales del modelo Gulupa YOLO26n-seg
        DamageInfo(SCAB, R.string.dmg_scab, "Cladosporium spp.", R.string.dmg_scab_desc, DamageScab),
        DamageInfo(ANTHRACNOSE, R.string.dmg_anthracnose, "Colletotrichum gloeosporioides", R.string.dmg_anthracnose_desc, DamageAnthracnose),
        DamageInfo(OIL_SPOT, R.string.dmg_oil_spot, "Xanthomonas axonopodis", R.string.dmg_oil_spot_desc, DamageOilSpot),
        DamageInfo(FRUIT, R.string.dmg_fruit, "Passiflora edulis f. edulis", R.string.dmg_fruit_desc, DamageFruit),
        DamageInfo(HEALTHY, R.string.dmg_healthy, null, R.string.dmg_healthy_desc, Healthy),

        // Daños legados o complementarios
        DamageInfo("mechanical", R.string.dmg_mechanical, null, R.string.dmg_mechanical_desc, DamageMechanical),
        DamageInfo("thrips", R.string.dmg_thrips, "Thysanoptera", R.string.dmg_thrips_desc, DamageThrips),
        DamageInfo("powdery_mildew", R.string.dmg_mildew, "Erysiphales", R.string.dmg_mildew_desc, DamageMildew),
    ).associateBy { it.key }

    fun get(key: String): DamageInfo =
        items[key] ?: DamageInfo(key, R.string.dmg_unknown, null, R.string.dmg_unknown_desc, Neutral)
}
