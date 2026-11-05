import Foundation
import SwiftUI
import UIKit
import CoreText

// MARK: - Paleta de Identidad Visual y Variantes Animadas (Idéntico a Brand.kt)

enum BrandPalette {
    static let plum   = Color(hex: 0x5D2741) // "Gulupa" y anillo del fruto
    static let green  = Color(hex: 0x005828) // "Scan" verde y tallo
    static let yellow = Color(hex: 0xFEDC7A) // "Scan" amarillo y pulpa
    static let purple = Color(hex: 0x9034BD) // fondo morado
    static let leaf   = Color(hex: 0x00A85A) // hoja
    static let light  = Color(hex: 0xF7F7F7) // fondo claro
    static let white  = Color.white
}

struct BrandVariant: Equatable {
    let background: Color
    let gulupa: Color
    let scan: Color
    let tagline: Color

    static let lightGreen   = BrandVariant(background: BrandPalette.light,  gulupa: BrandPalette.plum,  scan: BrandPalette.green,  tagline: BrandPalette.plum)
    static let lightYellow  = BrandVariant(background: BrandPalette.light,  gulupa: BrandPalette.plum,  scan: BrandPalette.yellow, tagline: BrandPalette.plum)
    static let purpleYellow = BrandVariant(background: BrandPalette.purple, gulupa: BrandPalette.white, scan: BrandPalette.yellow, tagline: BrandPalette.white)
    static let purpleGreen  = BrandVariant(background: BrandPalette.purple, gulupa: BrandPalette.white, scan: BrandPalette.green,  tagline: BrandPalette.white)
    static let greenYellow  = BrandVariant(background: BrandPalette.green,  gulupa: BrandPalette.white, scan: BrandPalette.yellow, tagline: BrandPalette.white)

    static let cycle: [BrandVariant] = [lightGreen, lightYellow, purpleGreen, purpleYellow]
}

// MARK: - Tema, Idioma y Resolución de Procesamiento (Idéntico a Theme.kt y SessionStore.kt)

enum AppTheme: String, Codable, CaseIterable {
    case green = "green"
    case purple = "purple"
}

enum AppLanguage: String, Codable, CaseIterable {
    case spanish = "es"
    case english = "en"
}

enum AnalysisResolution: String, Codable, CaseIterable {
    case low      = "low"      // 640 px · Baja / VGA (ultrarrápida para equipos básicos)
    case medium   = "medium"   // 1024 px · Media / HD (rápida, 1 pasada)
    case high     = "high"     // 2048 px · Alta / 2K (estándar recomendado)
    case original = "original" // 0 (Nativa) · Resolución original del sensor de la cámara sin reescalar
    case ultra    = "ultra"    // 4096 px · Ultra Alta (Original + cuadrantes 2x2 HD = 1280x1280 efectivo)

    var maxSidePx: Int {
        switch self {
        case .low:      return 640
        case .medium:   return 1024
        case .high:     return 2048
        case .original: return 0
        case .ultra:    return 4096
        }
    }

    var multiTile: Bool {
        self == .ultra
    }

    func title(_ lang: AppLanguage) -> String {
        switch self {
        case .low:      return lang == .english ? "Low" : "Baja"
        case .medium:   return lang == .english ? "Medium" : "Media"
        case .high:     return lang == .english ? "High" : "Alta"
        case .original: return lang == .english ? "Original" : "Original"
        case .ultra:    return lang == .english ? "Ultra 2×2" : "Ultra 2×2"
        }
    }

    func subtitle(_ lang: AppLanguage) -> String {
        switch self {
        case .low:      return lang == .english ? "Fastest · VGA 640px" : "Ultrarrápida · VGA 640px"
        case .medium:   return lang == .english ? "Fast · HD 1024px" : "Rápida · HD 1024px"
        case .high:     return lang == .english ? "Standard · 2K 2048px" : "Estándar · 2K 2048px"
        case .original: return lang == .english ? "Native camera sensor (100%)" : "Sensor nativo del equipo (100%)"
        case .ultra:    return lang == .english ? "Native + 2×2 Tiles (1280×1280 eff.)" : "Nativa + 2×2 Tiles (1280×1280 ef.)"
        }
    }
}

struct BrandColors {
    let dark: Color
    let main: Color
    let mid: Color
    let soft: Color
    let background: Color
    let card: Color
    let border: Color
    let surfaceVariant: Color
    let text: Color
    let textSoft: Color
    let accent: Color
    let decorated: Bool

    static let green = BrandColors(
        dark: Color(hex: 0x005828),
        main: Color(hex: 0x005828),
        mid: Color(hex: 0x0B7A3E),
        soft: Color(hex: 0xE1F2E7),
        background: Color(hex: 0xF7F7F7),
        card: Color.white,
        border: Color(hex: 0xE4E2E3),
        surfaceVariant: Color(hex: 0xEFEDEE),
        text: Color(hex: 0x2A1A22),
        textSoft: Color(hex: 0x6A5E64),
        accent: Color(hex: 0x00A85A),
        decorated: false
    )

    static let purple = BrandColors(
        dark: Color(hex: 0x9034BD),
        main: Color(hex: 0x5D2741),
        mid: Color(hex: 0x9034BD),
        soft: Color(hex: 0xF3E6F9),
        background: Color(hex: 0xF7F7F7),
        card: Color.white,
        border: Color(hex: 0xEADBF1),
        surfaceVariant: Color(hex: 0xF2EAF5),
        text: Color(hex: 0x2A1A22),
        textSoft: Color(hex: 0x6E6178),
        accent: Color(hex: 0xD9B8EC),
        decorated: true
    )

    static func of(_ theme: AppTheme) -> BrandColors {
        theme == .purple ? .purple : .green
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1.0) {
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(.sRGB, red: r, green: g, blue: b, opacity: alpha)
    }

    static let pulp = Color(hex: 0xFEDC7A)
    static let seed = Color(hex: 0x1A1A1A)
    static let leafGreen = Color(hex: 0x00A85A)
    static let warnBg = Color(hex: 0xFFF4D6)
    static let warnText = Color(hex: 0xD35400)
    static let gulupaPurple = Color(hex: 0x9034BD)
    static let neutralGray = Color(hex: 0x7A7A7A)

    static let damageFruit = Color(hex: 0x4CAF50)
    static let damageScab = Color(hex: 0xE53935)
    static let damageAnthracnose = Color(hex: 0x8E24AA)
    static let damageOilSpot = Color(hex: 0xFB8C00)
    static let damageMechanical = Color(hex: 0x1E88E5)
    static let damageThrips = Color(hex: 0xFFB300)
    static let damageMildew = Color(hex: 0x7E57C2)
    static let healthyGreen = Color(hex: 0x2E9E44)
}

// MARK: - Catálogo de Daños (Idéntico a DamageCatalog.kt)

enum DamageCatalog {
    static let FRUIT = "fruit"
    static let SCAB = "scab"
    static let ANTHRACNOSE = "anthracnose"
    static let OIL_SPOT = "oil_spot"
    static let MECHANICAL = "mechanical"
    static let THRIPS = "thrips"
    static let MILDEW = "mildew"
    static let HEALTHY = "healthy"

    struct DamageInfo {
        let key: String
        let scientific: String?
        let color: Color
        let nameEs: String
        let nameEn: String
        let descEs: String
        let descEn: String
        let treatEs: String
        let treatEn: String

        func name(_ lang: AppLanguage) -> String { lang == .english ? nameEn : nameEs }
        func description(_ lang: AppLanguage) -> String { lang == .english ? descEn : descEs }
        func treatment(_ lang: AppLanguage) -> String { lang == .english ? treatEn : treatEs }
    }

    static let all: [String: DamageInfo] = [
        FRUIT: DamageInfo(
            key: FRUIT,
            scientific: "Passiflora edulis f. edulis",
            color: .damageFruit,
            nameEs: "Fruta / Fruto detectado",
            nameEn: "Fruit / Detected fruit",
            descEs: "Contorno y superficie del fruto de gulupa localizado automáticamente en la imagen.",
            descEn: "Contour and surface of the purple passion fruit automatically located in the image.",
            treatEs: "Mantener el monitoreo fitosanitario periódico del lote y asegurar cosecha en el grado de madurez óptimo.",
            treatEn: "Maintain periodic phytosanitary monitoring of the plot and ensure harvesting at the optimal ripeness stage."
        ),
        SCAB: DamageInfo(
            key: SCAB,
            scientific: "Cladosporium cladosporioides",
            color: .damageScab,
            nameEs: "Sarna (Roña)",
            nameEn: "Scab (Cladosporium)",
            descEs: "Lesiones circulares de aspecto corchoso, hundidas o ligeramente levantadas, de color pardo que deprecian la calidad visual del fruto para exportación.",
            descEn: "Circular corky-looking lesions, sunken or slightly raised, brownish in color that depreciate the visual quality of the fruit for export.",
            treatEs: "Realizar podas de ventilación para reducir la humedad relativa, eliminar frutos afectados y aplicar fungicidas protectantes autorizados en rotación.",
            treatEn: "Perform ventilation pruning to reduce relative humidity, remove affected fruits, and apply authorized protectant fungicides in rotation."
        ),
        ANTHRACNOSE: DamageInfo(
            key: ANTHRACNOSE,
            scientific: "Colletotrichum gloeosporioides",
            color: .damageAnthracnose,
            nameEs: "Antracnosis",
            nameEn: "Anthracnose",
            descEs: "Manchas necróticas oscuras y hundidas en la epidermis del fruto, que en condiciones de alta humedad pueden presentar acérvulos de color salmón.",
            descEn: "Dark sunken necrotic spots on the fruit epidermis, which under high humidity conditions may show salmon-colored acervuli.",
            treatEs: "Mejorar la aireación del cultivo, recolectar y retirar frutos enfermos del suelo, desinfectar herramientas de poda y aplicar manejo fungicida preventivo.",
            treatEn: "Improve crop aeration, collect and remove diseased fruits from the ground, disinfect pruning tools, and apply preventive fungicide management."
        ),
        OIL_SPOT: DamageInfo(
            key: OIL_SPOT,
            scientific: "Xanthomonas axonopodis pv. passiflorae",
            color: .damageOilSpot,
            nameEs: "Mancha de aceite (Bacteriosis)",
            nameEn: "Oil spot (Bacteriosis)",
            descEs: "Lesiones translúcidas de aspecto aceitoso o acuoso en la corteza del fruto que evolucionan a manchas necróticas irregulares.",
            descEn: "Translucent oily or water-soaked lesions on the fruit rind that evolve into irregular necrotic spots.",
            treatEs: "Desinfectar tijeras de poda entre plantas, evitar labores con follaje húmedo, retirar tejidos infectados y aplicar productos cúpricos preventivos.",
            treatEn: "Disinfect pruning shears between plants, avoid working with wet foliage, remove infected tissues, and apply preventive copper-based products."
        ),
        HEALTHY: DamageInfo(
            key: HEALTHY,
            scientific: nil,
            color: .healthyGreen,
            nameEs: "Tejido sano",
            nameEn: "Healthy tissue",
            descEs: "Superficie del fruto de gulupa sin lesiones epidérmicas visibles.",
            descEn: "Purple passion fruit surface without visible epidermal lesions.",
            treatEs: "Mantener el monitoreo periódico y las buenas prácticas agrícolas de cosecha y poscosecha.",
            treatEn: "Maintain periodic monitoring and good agricultural harvesting and post-harvest practices."
        )
    ]

    static func get(_ key: String) -> DamageInfo {
        if let info = all[key] { return info }
        return DamageInfo(
            key: key,
            scientific: nil,
            color: .damageScab,
            nameEs: key.replacingOccurrences(of: "_", with: " ").capitalized,
            nameEn: key.replacingOccurrences(of: "_", with: " ").capitalized,
            descEs: "Daño fitosanitario detectado sobre la superficie del fruto.",
            descEn: "Phytosanitary damage detected on the fruit surface.",
            treatEs: "Consultar con el asistente técnico del cultivo.",
            treatEn: "Consult with the crop technical advisor."
        )
    }
}

// MARK: - Escala de Severidad 1-10 y Clasificación (Idéntico a Severity.kt)

enum Classification: String, Codable {
    case HEALTHY
    case LIGHT
    case MODERATE
    case SEVERE

    func title(_ lang: AppLanguage) -> String {
        switch self {
        case .HEALTHY: return lang == .english ? "Healthy fruit" : "Fruto sano"
        case .LIGHT: return lang == .english ? "Light damage" : "Daño leve"
        case .MODERATE: return lang == .english ? "Moderate damage" : "Daño moderado"
        case .SEVERE: return lang == .english ? "Severe damage" : "Daño severo"
        }
    }

    func subtitle(_ lang: AppLanguage) -> String {
        switch self {
        case .HEALTHY: return lang == .english ? "Export suitable (< 1% damage)" : "Apto para exportación (< 1% de daño)"
        case .LIGHT: return lang == .english ? "Check sorting criteria (1 – 5%)" : "Revisar criterios de selección (1 – 5%)"
        case .MODERATE: return lang == .english ? "National market or processing (5 – 15%)" : "Mercado nacional o agroindustria (5 – 15%)"
        case .SEVERE: return lang == .english ? "Discard for fresh market (> 15%)" : "Descarte para consumo en fresco (> 15%)"
        }
    }

    var color: Color {
        switch self {
        case .HEALTHY: return .healthyGreen
        case .LIGHT: return Color(hex: 0xE67E22)
        case .MODERATE: return .warnText
        case .SEVERE: return .damageScab
        }
    }
}

enum Severity {
    static let limits: [Double] = [1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 25.0, 40.0, 60.0]

    static func level(_ pct: Double) -> Int {
        if pct <= 0.05 { return 0 }
        let idx = limits.firstIndex { pct < $0 } ?? limits.count
        return idx + 1
    }

    static func rangeText(_ level: Int) -> String {
        switch level {
        case ..<1: return "0 %"
        case 1: return "< \(fmt(limits[0])) %"
        case 10...: return "≥ \(fmt(limits.last!)) %"
        default: return "\(fmt(limits[level - 2])) – \(fmt(limits[level - 1])) %"
        }
    }

    static func levelColor(_ level: Int) -> Color {
        switch level {
        case ..<1: return .healthyGreen
        case 1...2: return Color(hex: 0x2E9E44)
        case 3...4: return Color(hex: 0xF39C12)
        case 5...6: return Color(hex: 0xE67E22)
        default: return .damageScab
        }
    }

    static func classify(_ analysis: Analysis) -> Classification {
        let total = analysis.totalDamagePct
        if analysis.isHealthy || total < 1.0 { return .HEALTHY }
        if total < 5.0 { return .LIGHT }
        if total < 15.0 { return .MODERATE }
        return .SEVERE
    }

    private static func fmt(_ v: Double) -> String {
        v.truncatingRemainder(dividingBy: 1.0) == 0.0 ? "\(Int(v))" : String(format: "%.1f", v)
    }
}

// MARK: - Modelo de Análisis e Imágenes (Idéntico a Analysis.kt)

struct Damage: Identifiable, Codable, Equatable {
    var id: String { key }
    let key: String
    let pct: Double
    var areaCm2: Double? = nil
}

struct AnalysisImages: Codable, Equatable {
    var original: String? = nil
    var crop: String? = nil
    var leaf: String? = nil
    var mask: String? = nil
    var overlay: String? = nil
    var overlays: [String: String] = [:]
    var result: String? = nil

    func overlayFor(_ key: String) -> String? {
        overlays[key] ?? overlay
    }
}

struct Analysis: Identifiable, Codable, Equatable {
    let id: String
    let createdAt: Int64
    let source: String
    var modelClass: String? = nil
    var modelConfidence: Double = 0.0
    var leafConfidence: Double = 0.0
    var cropName: String? = nil
    var diseaseName: String? = nil
    var isHealthy: BooleanLiteralType = false
    var damages: [Damage] = []
    var images: AnalysisImages = AnalysisImages()
    var clsTimeMs: Double = 0.0
    var segTimeMs: Double = 0.0
    var saved: Bool = false
    var photoWidth: Int = 0
    var photoHeight: Int = 0
    var cropSidePx: Int = 0

    static let SOURCE_SERVER = "server"
    static let SOURCE_DEMO = "demo"

    var isDemo: Bool { source == Self.SOURCE_DEMO }
    var lowDetail: Bool { cropSidePx >= 1 && cropSidePx < 640 }

    var harmfulDamages: [Damage] {
        damages.filter { $0.key != DamageCatalog.HEALTHY }.sorted { $0.pct > $1.pct }
    }

    var totalDamagePct: Double {
        min(100.0, max(0.0, harmfulDamages.reduce(0.0) { $0 + $1.pct }))
    }

    var healthyPct: Double {
        damages.first { $0.key == DamageCatalog.HEALTHY }?.pct ?? max(0.0, 100.0 - totalDamagePct)
    }

    var classification: Classification {
        Severity.classify(self)
    }

    var thumbnail: String? {
        images.result ?? images.crop ?? images.original
    }

    var createdDate: Date {
        Date(timeIntervalSince1970: TimeInterval(createdAt) / 1000.0)
    }

    func localizedDisease(_ lang: AppLanguage) -> String {
        if isHealthy || harmfulDamages.isEmpty {
            return lang == .english ? "Healthy" : "Sano"
        }
        if let top = harmfulDamages.first {
            return DamageCatalog.get(top.key).name(lang)
        }
        return diseaseName ?? (lang == .english ? "Mixed lesions" : "Lesiones mixtas")
    }

    func localizedCrop(_ lang: AppLanguage) -> String {
        lang == .english ? "Purple passion fruit (Passiflora edulis)" : "Gulupa (Passiflora edulis)"
    }
}

struct UserProfile: Codable, Equatable {
    var name: String = "Productor de Gulupa"
    var email: String = ""
    var role: String = "Productor / Asistente técnico"
    var farm: String = "Finca La Esperanza"
    var municipality: String = "Antioquia, Colombia"
    var location: String = ""
    var hectares: String = ""
    var phone: String = ""
    var loggedIn: Bool = false

    func displayName(_ lang: AppLanguage) -> String {
        if name.isEmpty || name == "Productor de Gulupa" || name == "Passion Fruit Grower" {
            return lang == .english ? "Passion Fruit Grower" : "Productor de Gulupa"
        }
        return name
    }

    func displayRole(_ lang: AppLanguage) -> String {
        if role.isEmpty || role == "Agricultor de gulupa" || role == "Productor / Asistente técnico" || role == "Passion fruit grower" || role == "Grower / Technical advisor" {
            return lang == .english ? "Grower / Technical advisor" : "Productor / Asistente técnico"
        }
        return role
    }

    func displayFarm(_ lang: AppLanguage) -> String {
        if farm.isEmpty || farm == "Finca La Esperanza" || farm == "La Esperanza Farm" {
            return lang == .english ? "La Esperanza Farm" : "Finca La Esperanza"
        }
        return farm
    }

    func displayMunicipality(_ lang: AppLanguage) -> String {
        if municipality.isEmpty {
            return lang == .english ? "Antioquia, Colombia" : "Antioquia, Colombia"
        }
        return municipality
    }

    func displayLocation(_ lang: AppLanguage) -> String {
        if location.isEmpty {
            return lang == .english ? "Not set" : "Sin definir"
        }
        return location
    }

    func displayHectares(_ lang: AppLanguage) -> String {
        if hectares.isEmpty {
            return lang == .english ? "Not set" : "Sin definir"
        }
        return hectares
    }

    func displayPhone(_ lang: AppLanguage) -> String {
        if phone.isEmpty {
            return lang == .english ? "Not set" : "Sin definir"
        }
        return phone
    }
}

struct AppSettings: Codable, Equatable {
    var serverUrl: String = "https://jf-floresriera-powderyvision7.hf.space"
    var offlineMode: Bool = true
    var demoFallback: Bool = false
    var averageFruitAreaCm2: Double = 28.0
    var theme: AppTheme = .green
    var language: AppLanguage = .spanish
    var resolution: AnalysisResolution = .high
}

// MARK: - Carga de Imágenes y Fuente Manuscrita (Patrick Hand) desde Bundle

enum BundleImageLoader {
    private static var fontRegistered = false
    private static var customFontName: String = "PatrickHand-Regular"

    static func registerHandFontIfNeeded() {
        guard !fontRegistered else { return }
        fontRegistered = true
        if let url = Bundle.main.url(forResource: "patrick_hand", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            if let descriptors = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
               let first = descriptors.first,
               let postScriptName = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String {
                customFontName = postScriptName
            }
        }
    }

    static func handFont(size: CGFloat) -> Font {
        registerHandFontIfNeeded()
        return Font.custom(customFontName, size: size)
    }

    static func load(_ nameWithExt: String) -> UIImage? {
        let parts = nameWithExt.split(separator: ".", maxSplits: 1).map(String.init)
        if parts.count == 2,
           let path = Bundle.main.path(forResource: parts[0], ofType: parts[1]),
           let img = UIImage(contentsOfFile: path) {
            return img
        }
        return UIImage(named: nameWithExt) ?? UIImage(named: parts.first ?? nameWithExt)
    }

    static func loadFile(_ path: String?) -> UIImage? {
        guard let path, !path.isEmpty else { return nil }
        return UIImage(contentsOfFile: path)
    }
}
