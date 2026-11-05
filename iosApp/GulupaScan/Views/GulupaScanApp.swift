import SwiftUI
import PhotosUI
import UIKit

@main
struct GulupaScanApp: App {
    @StateObject private var vm = AppViewModel()

    var body: some Scene {
        WindowGroup {
            MainHostView()
                .environmentObject(vm)
        }
    }
}

// MARK: - ViewModel Principal (Idéntico a AppViewModel.kt en Android)

enum RootScreen: Equatable {
    case splash
    case auth
    case main
}

enum MainTab: String, CaseIterable {
    case scan
    case history
    case alerts
    case profile
}

enum ScanRoute: Equatable {
    case camera
    case processing
    case damageList
    case damageDetail(String)
    case result
    case techDetails
}

enum ProfileRoute: Equatable {
    case home
    case guide
    case techInfo
    case about
    case settings
}

@MainActor
final class AppViewModel: ObservableObject {
    @Published var rootScreen: RootScreen = .splash
    @Published var selectedTab: MainTab = .scan
    @Published var scanRoute: ScanRoute = .camera
    @Published var profileRoute: ProfileRoute = .home

    @Published var profile: UserProfile = UserProfile() {
        didSet { saveState() }
    }
    @Published var settings: AppSettings = AppSettings() {
        didSet { saveState() }
    }
    @Published var history: [Analysis] = []
    @Published var currentAnalysis: Analysis? = nil
    @Published var errorMessage: String? = nil
    @Published var toastMessage: String? = nil

    var brand: BrandColors { BrandColors.of(settings.theme) }
    var lang: AppLanguage { settings.language }

    init() {
        BundleImageLoader.registerHandFontIfNeeded()
        loadState()
        history = GulupaAnalyzer.shared.loadAllSavedAnalyses()
    }

    func t(_ es: String, _ en: String) -> String {
        lang == .english ? en : es
    }

    func showToast(_ msg: String) {
        toastMessage = msg
        Task {
            try? await Task.sleep(nanoseconds: 2_600_000_000)
            if toastMessage == msg { toastMessage = nil }
        }
    }

    func login(email: String, name: String? = nil, role: String? = nil, farm: String? = nil, municipality: String? = nil) {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.email = cleanEmail.isEmpty ? profile.email : cleanEmail
        if let name, !name.isEmpty { profile.name = name }
        else if !cleanEmail.isEmpty {
            profile.name = cleanEmail.components(separatedBy: "@").first?.replacingOccurrences(of: ".", with: " ").capitalized ?? profile.name
        }
        if let role, !role.isEmpty { profile.role = role }
        if let farm, !farm.isEmpty { profile.farm = farm }
        if let municipality, !municipality.isEmpty { profile.municipality = municipality }
        profile.loggedIn = true
        rootScreen = .main
    }

    func continueAsGuest() {
        profile.loggedIn = true
        rootScreen = .main
    }

    func logout() {
        profile.loggedIn = false
        rootScreen = .auth
    }

    func analyzePhoto(_ image: UIImage) {
        errorMessage = nil
        scanRoute = .processing
        Task {
            do {
                let res = try await GulupaAnalyzer.shared.analyze(image: image, settings: settings, forceDemo: false)
                currentAnalysis = res
                scanRoute = .damageList
            } catch let err as AnalyzerError {
                errorMessage = err.localizedMessage(lang)
                scanRoute = .camera
            } catch {
                errorMessage = error.localizedDescription
                scanRoute = .camera
            }
        }
    }

    func runDemoSample() {
        errorMessage = nil
        scanRoute = .processing
        Task {
            let sample = makeRealisticGulupaSampleImage()
            do {
                let res = try await GulupaAnalyzer.shared.analyze(image: sample, settings: settings, forceDemo: true)
                currentAnalysis = res
                scanRoute = .damageList
            } catch {
                errorMessage = error.localizedDescription
                scanRoute = .camera
            }
        }
    }

    func saveCurrentToHistory() {
        guard var cur = currentAnalysis else { return }
        cur.saved = true
        currentAnalysis = cur
        GulupaAnalyzer.shared.saveAnalysisJSON(cur)
        history = GulupaAnalyzer.shared.loadAllSavedAnalyses()
        showToast(t("Guardado en el historial", "Saved to history"))
    }

    func deleteHistoryItem(_ id: String) {
        GulupaAnalyzer.shared.deleteAnalysis(id: id)
        history = GulupaAnalyzer.shared.loadAllSavedAnalyses()
    }

    func clearHistory() {
        GulupaAnalyzer.shared.clearAllAnalyses()
        history = []
    }

    func exportCSV() -> URL? {
        var lines = ["id,fecha,fuente,clasificacion,dano_total_pct,sano_pct,sarna_pct,antracnosis_pct,mancha_aceite_pct,confianza_fruto,confianza_dano,tiempo_ms"]
        let df = ISO8601DateFormatter()
        for a in history {
            let scab = a.damages.first { $0.key == DamageCatalog.SCAB }?.pct ?? 0
            let anth = a.damages.first { $0.key == DamageCatalog.ANTHRACNOSE }?.pct ?? 0
            let oil  = a.damages.first { $0.key == DamageCatalog.OIL_SPOT }?.pct ?? 0
            let totalMs = Int(a.clsTimeMs + a.segTimeMs)
            lines.append("\(a.id),\(df.string(from: a.createdDate)),\(a.source),\(a.classification.rawValue),\(a.totalDamagePct),\(a.healthyPct),\(scab),\(anth),\(oil),\(a.leafConfidence),\(a.modelConfidence),\(totalMs)")
        }
        let csv = lines.joined(separator: "\n")
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("GulupaScan_Historial.csv")
        try? csv.data(using: .utf8)?.write(to: url)
        return url
    }

    func makeRealisticGulupaSampleImage() -> UIImage {
        let size = CGSize(width: 640, height: 640)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { ctx in
            let cg = ctx.cgContext
            UIColor(red: 0.90, green: 0.93, blue: 0.89, alpha: 1.0).setFill()
            cg.fill(CGRect(origin: .zero, size: size))

            // Fruto morado de gulupa en el centro
            let fruitRect = CGRect(x: 110, y: 110, width: 420, height: 420)
            UIColor(red: 0.34, green: 0.12, blue: 0.31, alpha: 1.0).setFill()
            cg.fillEllipse(in: fruitRect)

            // Brillo natural
            UIColor(red: 0.52, green: 0.24, blue: 0.48, alpha: 0.45).setFill()
            cg.fillEllipse(in: CGRect(x: 165, y: 155, width: 170, height: 130))

            // Lesión corchosa (Sarna / Roña)
            UIColor(red: 0.62, green: 0.44, blue: 0.25, alpha: 0.92).setFill()
            cg.fillEllipse(in: CGRect(x: 220, y: 240, width: 85, height: 72))

            // Lesión oscura (Antracnosis)
            UIColor(red: 0.20, green: 0.07, blue: 0.14, alpha: 0.92).setFill()
            cg.fillEllipse(in: CGRect(x: 345, y: 310, width: 64, height: 58))
        }
    }

    private func saveState() {
        if let pData = try? JSONEncoder().encode(profile) {
            UserDefaults.standard.set(pData, forKey: "gulupa_profile_v3")
        }
        if let sData = try? JSONEncoder().encode(settings) {
            UserDefaults.standard.set(sData, forKey: "gulupa_settings_v3")
        }
    }

    private func loadState() {
        if let pData = UserDefaults.standard.data(forKey: "gulupa_profile_v3"),
           let p = try? JSONDecoder().decode(UserProfile.self, from: pData) {
            profile = p
        }
        if let sData = UserDefaults.standard.data(forKey: "gulupa_settings_v3"),
           let s = try? JSONDecoder().decode(AppSettings.self, from: sData) {
            settings = s
        }
    }
}

// MARK: - Host Principal (Splash -> Auth -> Main con 4 Pestañas)

struct MainHostView: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        ZStack(alignment: .bottom) {
            switch vm.rootScreen {
            case .splash:
                SplashScreen {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        vm.rootScreen = vm.profile.loggedIn ? .main : .auth
                    }
                }
            case .auth:
                AuthScreen()
            case .main:
                MainTabsView()
            }

            if let toast = vm.toastMessage {
                Text(toast)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 11)
                    .background(Color.black.opacity(0.82))
                    .clipShape(Capsule())
                    .padding(.bottom, 76)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: vm.toastMessage)
        .preferredColorScheme(.light)
    }
}

// MARK: - 1. Pantalla Splash Animada + Componentes de Marca (Idénticos a Brand.kt y Components.kt)

struct OrganicBlobs: View {
    @EnvironmentObject var vm: AppViewModel
    var topRight: Bool = true
    var bottomLeft: Bool = true

    var body: some View {
        let b = vm.brand
        if b.decorated {
            Canvas { ctx, size in
                let w = size.width
                let h = size.height
                if topRight {
                    var yellow = Path()
                    yellow.move(to: CGPoint(x: w * 0.45, y: 0))
                    yellow.addCurve(
                        to: CGPoint(x: w, y: h * 0.24),
                        control1: CGPoint(x: w * 0.50, y: h * 0.19),
                        control2: CGPoint(x: w * 0.80, y: h * 0.14)
                    )
                    yellow.addLine(to: CGPoint(x: w, y: 0))
                    yellow.closeSubpath()
                    ctx.fill(yellow, with: .color(b.accent))

                    var green = Path()
                    green.move(to: CGPoint(x: w * 0.62, y: 0))
                    green.addCurve(
                        to: CGPoint(x: w, y: h * 0.17),
                        control1: CGPoint(x: w * 0.66, y: h * 0.14),
                        control2: CGPoint(x: w * 0.88, y: h * 0.10)
                    )
                    green.addLine(to: CGPoint(x: w, y: 0))
                    green.closeSubpath()
                    ctx.fill(green, with: .color(b.dark))
                }
                if bottomLeft {
                    var yellow = Path()
                    yellow.move(to: CGPoint(x: 0, y: h * 0.72))
                    yellow.addCurve(
                        to: CGPoint(x: w * 0.78, y: h),
                        control1: CGPoint(x: w * 0.35, y: h * 0.76),
                        control2: CGPoint(x: w * 0.52, y: h * 0.92)
                    )
                    yellow.addLine(to: CGPoint(x: 0, y: h))
                    yellow.closeSubpath()
                    ctx.fill(yellow, with: .color(b.accent))

                    var green = Path()
                    green.move(to: CGPoint(x: 0, y: h * 0.80))
                    green.addCurve(
                        to: CGPoint(x: w * 0.60, y: h),
                        control1: CGPoint(x: w * 0.28, y: h * 0.83),
                        control2: CGPoint(x: w * 0.42, y: h * 0.95)
                    )
                    green.addLine(to: CGPoint(x: 0, y: h))
                    green.closeSubpath()
                    ctx.fill(green, with: .color(b.dark))
                }
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
    }
}

struct Wordmark: View {
    var gulupaColor: Color
    var scanColor: Color
    var height: CGFloat = 38

    var body: some View {
        HStack(alignment: .center, spacing: height * 0.14) {
            if let wordG = BundleImageLoader.load("word_gulupa.png") {
                Image(uiImage: wordG)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(gulupaColor)
                    .frame(height: height)
            } else {
                Text("Gulupa")
                    .font(.system(size: height * 0.85, weight: .heavy, design: .rounded))
                    .foregroundStyle(gulupaColor)
            }

            if let wordS = BundleImageLoader.load("word_scan.png") {
                Image(uiImage: wordS)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(scanColor)
                    .frame(height: height * 0.92)
            } else {
                Text("Scan")
                    .font(.system(size: height * 0.82, weight: .bold, design: .rounded))
                    .foregroundStyle(scanColor)
            }
        }
    }
}

struct AnimatedBrandLockup: View {
    @EnvironmentObject var vm: AppViewModel
    var logoSize: CGFloat = 148
    var wordHeight: CGFloat = 40
    var showTagline: Bool = true
    var cycleInterval: TimeInterval = 0.70

    @State private var index: Int = 0
    @State private var pulse: Bool = false
    private let timer = Timer.publish(every: 0.70, on: .main, in: .common).autoconnect()

    var currentVariant: BrandVariant {
        let list = BrandVariant.cycle
        return list[index % list.count]
    }

    var body: some View {
        let v = currentVariant
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .fill(Color.white.opacity(0.18))
                    .frame(width: logoSize + 24, height: logoSize + 24)
                GulupaLogoIcon(size: logoSize * (pulse ? 1.04 : 0.96))
            }
            .animation(.easeInOut(duration: 0.55), value: v)

            Wordmark(gulupaColor: v.gulupa, scanColor: v.scan, height: wordHeight)
                .animation(.easeInOut(duration: 0.55), value: v)

            if showTagline {
                Text(vm.t("Detección de daños en gulupa", "Passion fruit damage detection"))
                    .font(BundleImageLoader.handFont(size: 19))
                    .foregroundStyle(v.tagline)
                    .multilineTextAlignment(.center)
                    .animation(.easeInOut(duration: 0.55), value: v)
            }
        }
        .padding(.horizontal, 28)
        .padding(.vertical, 22)
        .background(v.background)
        .clipShape(RoundedRectangle(cornerRadius: 28))
        .animation(.easeInOut(duration: 0.55), value: v)
        .onAppear {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulse = true
            }
        }
        .onReceive(timer) { _ in
            withAnimation(.easeInOut(duration: 0.55)) {
                index = (index + 1) % BrandVariant.cycle.count
            }
        }
    }
}

struct SplashScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let onDone: () -> Void
    @State private var appeared = false

    var body: some View {
        let b = vm.brand
        ZStack {
            (b.decorated ? Color.white : b.dark).ignoresSafeArea()
            OrganicBlobs()

            VStack(spacing: 16) {
                AnimatedBrandLockup(logoSize: 148, wordHeight: 40, showTagline: true)
                    .scaleEffect(appeared ? 1.0 : 0.85)
                    .opacity(appeared ? 1.0 : 0.0)

                Text("Passiflora edulis Sims · UNAL")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(b.decorated ? b.textSoft : .white.opacity(0.85))
                    .opacity(appeared ? 1.0 : 0.0)

                Button(action: onDone) {
                    Text(vm.t("COMENZAR", "START"))
                        .font(.headline.weight(.bold))
                        .tracking(1.5)
                        .foregroundStyle(b.decorated ? .white : Color(hex: 0x3A1628))
                        .padding(.horizontal, 44)
                        .padding(.vertical, 14)
                        .background(b.decorated ? b.dark : Color.pulp)
                        .clipShape(Capsule())
                        .shadow(color: .black.opacity(0.22), radius: 8, y: 4)
                }
                .padding(.top, 12)
                .scaleEffect(appeared ? 1.0 : 0.9)
                .opacity(appeared ? 1.0 : 0.0)
            }
            .padding(28)
        }
        .onAppear {
            withAnimation(.spring(response: 0.65, dampingFraction: 0.72)) {
                appeared = true
            }
            Task {
                try? await Task.sleep(nanoseconds: 2_600_000_000)
                if vm.rootScreen == .splash {
                    onDone()
                }
            }
        }
    }
}

struct BrandWordmarkView: View {
    @EnvironmentObject var vm: AppViewModel
    var centered: Bool = true
    var onDark: Bool = false

    var body: some View {
        let b = vm.brand
        let gulupaCol = onDark ? Color.white : (b.decorated ? BrandPalette.plum : b.main)
        let scanCol = onDark ? Color.pulp : (b.decorated ? BrandPalette.green : BrandPalette.yellow)

        VStack(alignment: centered ? .center : .leading, spacing: 8) {
            GulupaLogoIcon(size: 76)
            Wordmark(gulupaColor: gulupaCol, scanColor: scanCol, height: 34)
        }
    }
}

struct GulupaLogoIcon: View {
    var size: CGFloat = 44

    var body: some View {
        if let img = BundleImageLoader.load("logo_gulupa.png") {
            Image(uiImage: img)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            ZStack {
                Circle().fill(Color.gulupaPurple)
                Circle().fill(Color.pulp).padding(size * 0.18)
                Image(systemName: "camera.macro")
                    .font(.system(size: size * 0.36, weight: .bold))
                    .foregroundStyle(Color.gulupaPurple)
            }
            .frame(width: size, height: size)
        }
    }
}

// MARK: - 2. Pantalla de Autenticación (Login / Registro / Modo Campo)

struct AuthScreen: View {
    @EnvironmentObject var vm: AppViewModel
    @State private var isRegister = false
    @State private var email = ""
    @State private var password = ""
    @State private var name = ""
    @State private var role = "Productor / Asistente técnico"
    @State private var farm = ""
    @State private var municipality = ""

    var body: some View {
        let b = vm.brand
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Spacer()
                    BrandWordmarkView(centered: true)
                    Spacer()
                }
                .padding(.top, 12)

                Text(isRegister ? vm.t("Crear cuenta", "Create account") : vm.t("Bienvenido", "Welcome"))
                    .font(.title.bold())
                    .foregroundStyle(b.main)

                Text(isRegister
                     ? vm.t("Registra los datos de tu finca para asociarlos a los análisis.", "Register your farm details to link them to your analyses.")
                     : vm.t("Inicia sesión o continúa directamente en modo campo (sin internet).", "Log in or continue directly in field mode (offline)."))
                    .font(.subheadline)
                    .foregroundStyle(b.textSoft)

                VStack(spacing: 12) {
                    if isRegister {
                        AuthField(label: vm.t("Nombre completo", "Full name"), text: $name, icon: "person")
                        AuthField(label: vm.t("Rol (Productor / Agrónomo)", "Role (Grower / Agronomist)"), text: $role, icon: "briefcase")
                        AuthField(label: vm.t("Finca / Lote", "Farm / Plot"), text: $farm, icon: "leaf")
                        AuthField(label: vm.t("Municipio y departamento", "Municipality and state"), text: $municipality, icon: "mappin.and.ellipse")
                    }
                    AuthField(label: vm.t("Correo electrónico", "Email"), text: $email, icon: "envelope")
                    SecureAuthField(label: vm.t("Contraseña", "Password"), text: $password)
                }

                Button {
                    if isRegister {
                        vm.login(email: email, name: name, role: role, farm: farm, municipality: municipality)
                    } else {
                        vm.login(email: email)
                    }
                } label: {
                    Text(isRegister ? vm.t("REGISTRARME", "SIGN UP") : vm.t("INICIAR SESIÓN", "LOG IN"))
                        .font(.headline.bold())
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(b.dark)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                }
                .padding(.top, 4)

                Button {
                    vm.continueAsGuest()
                } label: {
                    HStack {
                        Image(systemName: "leaf.fill")
                        Text(vm.t("Continuar sin cuenta (modo campo)", "Continue without account (field mode)"))
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .foregroundStyle(b.dark)
                    .overlay(Capsule().stroke(b.dark, lineWidth: 1.4))
                }

                HStack {
                    Spacer()
                    Button {
                        withAnimation { isRegister.toggle() }
                    } label: {
                        Text(isRegister
                             ? vm.t("¿Ya tienes cuenta? Inicia sesión", "Already have an account? Log in")
                             : vm.t("¿No tienes cuenta? Regístrate", "Don't have an account? Sign up"))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(b.main)
                    }
                    Spacer()
                }

                Divider().padding(.vertical, 6)

                // Selector rápido de tema e idioma en el pie del login
                VStack(alignment: .leading, spacing: 8) {
                    Text(vm.t("Tema visual:", "Visual theme:"))
                        .font(.caption)
                        .foregroundStyle(b.textSoft)
                    HStack(spacing: 10) {
                        ThemeChipButton(
                            title: vm.t("Verde campo", "Field green"),
                            dotColor: Color(hex: 0x005828),
                            selected: vm.settings.theme == .green
                        ) { vm.settings.theme = .green }

                        ThemeChipButton(
                            title: vm.t("Morado gulupa", "Purple passion"),
                            dotColor: Color(hex: 0x9034BD),
                            selected: vm.settings.theme == .purple
                        ) { vm.settings.theme = .purple }
                    }

                    HStack(spacing: 10) {
                        Button("Español") { vm.settings.language = .spanish }
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(vm.lang == .spanish ? b.soft : Color.clear)
                            .clipShape(Capsule())
                        Button("English") { vm.settings.language = .english }
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(vm.lang == .english ? b.soft : Color.clear)
                            .clipShape(Capsule())
                    }
                    .padding(.top, 4)
                }
            }
            .padding(22)
        }
        .background(b.background.ignoresSafeArea())
    }
}

struct AuthField: View {
    let label: String
    @Binding var text: String
    let icon: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(.secondary)
                .frame(width: 22)
            TextField(label, text: $text)
                .textInputAutocapitalization(.never)
        }
        .padding(13)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.25), lineWidth: 1))
    }
}

struct SecureAuthField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "lock")
                .foregroundStyle(.secondary)
                .frame(width: 22)
            SecureField(label, text: $text)
        }
        .padding(13)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.gray.opacity(0.25), lineWidth: 1))
    }
}

struct ThemeChipButton: View {
    @EnvironmentObject var vm: AppViewModel
    let title: String
    let dotColor: Color
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Circle().fill(dotColor).frame(width: 10, height: 10)
                Text(title).font(.caption.weight(selected ? .bold : .regular))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(selected ? vm.brand.soft : Color.white)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(selected ? vm.brand.dark : vm.brand.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 3. Contenedor de 4 Pestañas Inferiores (Escanear, Historial, Alertas, Perfil)

struct MainTabsView: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        TabView(selection: $vm.selectedTab) {
            ScanFlowContainer()
                .tabItem {
                    Label(vm.t("Escanear", "Scan"), systemImage: "camera.viewfinder")
                }
                .tag(MainTab.scan)

            HistoryScreen()
                .tabItem {
                    Label(vm.t("Historial", "History"), systemImage: "clock.arrow.circlepath")
                }
                .tag(MainTab.history)

            AlertsScreen()
                .tabItem {
                    Label(vm.t("Alertas", "Alerts"), systemImage: "bell.badge")
                }
                .tag(MainTab.alerts)

            ProfileFlowContainer()
                .tabItem {
                    Label(vm.t("Perfil", "Profile"), systemImage: "person.crop.circle")
                }
                .tag(MainTab.profile)
        }
        .tint(vm.brand.dark)
    }
}

// MARK: - Barra Superior Institucional (TopBar)

struct GulupaTopBar: View {
    @EnvironmentObject var vm: AppViewModel
    let title: String
    var onBack: (() -> Void)? = nil
    var trailingIcon: String? = nil
    var onTrailing: (() -> Void)? = nil

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "arrow.left")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(b.main)
                            .frame(width: 34, height: 34)
                    }
                } else {
                    GulupaLogoIcon(size: 30)
                }

                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(b.main)
                    .lineLimit(1)

                Spacer()

                if let trailingIcon, let onTrailing {
                    Button(action: onTrailing) {
                        Image(systemName: trailingIcon)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(b.main)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.white)

            Divider()
        }
    }
}

// MARK: - 4. Flujo Completo de Escaneo (Cámara -> Procesando -> Lista Daños -> Detalle Escala 1-10 -> Informe -> Detalles Técnicos)

struct ScanFlowContainer: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        switch vm.scanRoute {
        case .camera:
            CameraCaptureScreen()
        case .processing:
            ProcessingScreen()
        case .damageList:
            if let a = vm.currentAnalysis {
                DamageListScreen(analysis: a)
            } else {
                CameraCaptureScreen()
            }
        case .damageDetail(let key):
            if let a = vm.currentAnalysis {
                DamageDetailScreen(analysis: a, damageKey: key)
            } else {
                CameraCaptureScreen()
            }
        case .result:
            if let a = vm.currentAnalysis {
                ResultScreen(analysis: a)
            } else {
                CameraCaptureScreen()
            }
        case .techDetails:
            if let a = vm.currentAnalysis {
                TechDetailsScreen(analysis: a)
            } else {
                CameraCaptureScreen()
            }
        }
    }
}

// MARK: - Paso 1: Cámara con Visor 4:3, Tap-to-Focus, Consejos y Galería (Idéntica a CameraScreen.kt)

struct CameraCaptureScreen: View {
    @EnvironmentObject var vm: AppViewModel
    @State private var selectedItem: PhotosPickerItem? = nil
    @State private var showTips = false
    @State private var showHardwareCamera = false
    @State private var focusPoint: CGPoint? = nil

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(
                title: vm.t("Fotografiar gulupa", "Photograph passion fruit"),
                trailingIcon: "questionmark.circle",
                onTrailing: { showTips = true }
            )

            ScrollView {
                VStack(spacing: 14) {
                    if let err = vm.errorMessage {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(Color.warnText)
                            Text(err)
                                .font(.caption.weight(.medium))
                                .foregroundStyle(Color.warnText)
                            Spacer()
                            Button { vm.errorMessage = nil } label: {
                                Image(systemName: "xmark")
                                    .font(.caption.bold())
                                    .foregroundStyle(Color.warnText)
                            }
                        }
                        .padding(12)
                        .background(Color.warnBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    // Visor 4:3 con esquinas de encuadre y anillo de enfoque al tocar
                    GeometryReader { geo in
                        let w = geo.size.width
                        let h = geo.size.height
                        ZStack {
                            LinearGradient(
                                colors: [Color(hex: 0x1E141C), Color(hex: 0x351B31), Color(hex: 0x181216)],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )

                            // Vista previa ilustrativa del fruto en el visor
                            VStack(spacing: 10) {
                                GulupaLogoIcon(size: 86)
                                Text(vm.t("Toca para enfocar · Encuadra un solo fruto", "Tap to focus · Frame a single fruit"))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.white.opacity(0.9))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 5)
                                    .background(Color.black.opacity(0.45))
                                    .clipShape(Capsule())
                            }

                            // Esquinas amarillas del marco de captura
                            FrameCornersOverlay()
                                .padding(24)

                            if let pt = focusPoint {
                                Circle()
                                    .stroke(Color.pulp, lineWidth: 2.5)
                                    .frame(width: 56, height: 56)
                                    .position(pt)
                                    .transition(.scale.combined(with: .opacity))
                            }

                            VStack {
                                HStack {
                                    Text(vm.t("Encuadra el fruto de gulupa en el centro", "Frame the purple passion fruit in the center"))
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(.white)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(Color.black.opacity(0.55))
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                    Spacer()
                                }
                                .padding(12)
                                Spacer()
                            }
                        }
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 0)
                                .onEnded { val in
                                    withAnimation(.easeOut(duration: 0.15)) {
                                        focusPoint = CGPoint(
                                            x: min(max(30, val.location.x), w - 30),
                                            y: min(max(30, val.location.y), h - 30)
                                        )
                                    }
                                    Task {
                                        try? await Task.sleep(nanoseconds: 900_000_000)
                                        withAnimation { focusPoint = nil }
                                    }
                                }
                        )
                    }
                    .aspectRatio(3.0 / 4.0, contentMode: .fit)
                    .clipShape(RoundedRectangle(cornerRadius: 20))
                    .shadow(color: .black.opacity(0.12), radius: 6, y: 3)

                    // Selector rápido de resolución de entrada (Baja 640 / Media 1024 / Alta 2048 / Original / Ultra 2×2)
                    VStack(spacing: 6) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(AnalysisResolution.allCases, id: \.self) { res in
                                    let isSel = vm.settings.resolution == res
                                    Button {
                                        vm.settings.resolution = res
                                    } label: {
                                        Text(res.title(vm.lang))
                                            .font(.caption2.weight(isSel ? .bold : .semibold))
                                            .foregroundStyle(isSel ? .white : b.main)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(isSel ? b.dark : Color.white)
                                            .clipShape(Capsule())
                                            .overlay(Capsule().stroke(isSel ? b.dark : b.border, lineWidth: 1))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.horizontal, 4)
                        }
                        Text(vm.settings.resolution.subtitle(vm.lang))
                            .font(.caption2)
                            .foregroundStyle(b.textSoft)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 2)

                    // Controles inferiores: Galería · Obturador · Guía
                    HStack(spacing: 28) {
                        PhotosPicker(selection: $selectedItem, matching: .images) {
                            VStack(spacing: 4) {
                                ZStack {
                                    Circle()
                                        .stroke(b.dark, lineWidth: 1.5)
                                        .frame(width: 50, height: 50)
                                    Image(systemName: "photo.on.rectangle")
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundStyle(b.dark)
                                }
                                Text(vm.t("Galería", "Gallery"))
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(b.text)
                            }
                        }
                        .onChange(of: selectedItem) { newItem in
                            guard let newItem else { return }
                            Task {
                                if let data = try? await newItem.loadTransferable(type: Data.self),
                                   let uiImage = UIImage(data: data) {
                                    vm.analyzePhoto(uiImage)
                                }
                            }
                        }

                        // Botón obturador circular grande
                        Button {
                            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                showHardwareCamera = true
                            } else {
                                vm.runDemoSample()
                            }
                        } label: {
                            ZStack {
                                Circle()
                                    .fill(b.dark)
                                    .frame(width: 74, height: 74)
                                Circle()
                                    .stroke(Color.pulp, lineWidth: 3)
                                    .frame(width: 62, height: 62)
                                Image(systemName: "camera.fill")
                                    .font(.system(size: 26, weight: .bold))
                                    .foregroundStyle(.white)
                            }
                        }

                        Button {
                            showTips = true
                        } label: {
                            VStack(spacing: 4) {
                                ZStack {
                                    Circle()
                                        .stroke(b.dark, lineWidth: 1.5)
                                        .frame(width: 50, height: 50)
                                    Image(systemName: "info.circle")
                                        .font(.system(size: 20, weight: .semibold))
                                        .foregroundStyle(b.dark)
                                }
                                Text(vm.t("Consejos", "Tips"))
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(b.text)
                            }
                        }
                    }
                    .padding(.top, 4)

                    Button {
                        vm.runDemoSample()
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "sparkles")
                            Text(vm.t("Ver demostración con ejemplo", "Run demo with sample fruit"))
                                .font(.subheadline.weight(.semibold))
                        }
                        .foregroundStyle(b.dark)
                        .padding(.vertical, 6)
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
        .alert(vm.t("Consejos de captura", "Capture tips"), isPresented: $showTips) {
            Button(vm.t("Ver guía paso a paso", "View step-by-step guide")) {
                vm.selectedTab = .profile
                vm.profileRoute = .guide
            }
            Button(vm.t("Entendido", "Got it"), role: .cancel) {}
        } message: {
            Text(vm.t(
                "• Coloca un solo fruto de gulupa en el centro del recuadro (15–25 cm).\n• Toca el fruto en la pantalla para enfocar antes de disparar.\n• Prefiere luz natural difusa; evita sombras duras o reflejos directos.\n• Mantén el teléfono firme para no desenfocar las lesiones.",
                "• Place a single passion fruit in the center of the frame (15–25 cm).\n• Tap the fruit on screen to focus before shooting.\n• Prefer diffuse natural light; avoid harsh shadows or glare.\n• Hold the phone steady to keep lesions sharp."
            ))
        }
        .sheet(isPresented: $showHardwareCamera) {
            HardwareCameraPicker { captured in
                vm.analyzePhoto(captured)
            }
        }
    }
}

struct FrameCornersOverlay: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let s = min(w, h) * 0.72
            let left = (w - s) / 2
            let top = (h - s) / 2
            let right = left + s
            let bottom = top + s
            let arm: CGFloat = 28

            Path { p in
                p.move(to: CGPoint(x: left, y: top + arm))
                p.addLine(to: CGPoint(x: left, y: top))
                p.addLine(to: CGPoint(x: left + arm, y: top))

                p.move(to: CGPoint(x: right - arm, y: top))
                p.addLine(to: CGPoint(x: right, y: top))
                p.addLine(to: CGPoint(x: right, y: top + arm))

                p.move(to: CGPoint(x: left, y: bottom - arm))
                p.addLine(to: CGPoint(x: left, y: bottom))
                p.addLine(to: CGPoint(x: left + arm, y: bottom))

                p.move(to: CGPoint(x: right - arm, y: bottom))
                p.addLine(to: CGPoint(x: right, y: bottom))
                p.addLine(to: CGPoint(x: right, y: bottom - arm))
            }
            .stroke(Color.pulp, style: StrokeStyle(lineWidth: 4, lineCap: .round))
        }
    }
}

struct HardwareCameraPicker: UIViewControllerRepresentable {
    let onCapture: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: HardwareCameraPicker
        init(_ parent: HardwareCameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let img = info[.originalImage] as? UIImage {
                parent.onCapture(img)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

// MARK: - Paso 2: Pantalla Procesando (Idéntica a ProcessingScreen en ScanFlowScreens.kt)

struct ProcessingScreen: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        let b = vm.brand
        ZStack {
            Color.white.ignoresSafeArea()
            OrganicBlobs()

            VStack(spacing: 18) {
                Spacer()

                AnimatedBrandLockup(logoSize: 120, wordHeight: 34, showTagline: true)

                ProgressView()
                    .scaleEffect(1.4)
                    .tint(b.dark)
                    .padding(.top, 4)

                Text(vm.t("Analizando fruto de gulupa…", "Analyzing passion fruit…"))
                    .font(BundleImageLoader.handFont(size: 24))
                    .foregroundStyle(b.main)

                Text(vm.settings.resolution.title(vm.lang))
                    .font(.caption.bold())
                    .foregroundStyle(b.dark)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 5)
                    .background(b.soft)
                    .clipShape(Capsule())

                VStack(alignment: .leading, spacing: 8) {
                    Text(vm.t("1. Recortando fruto y aislando el fondo (480×480)", "1. Cropping fruit and isolating background (480×480)"))
                    Text(vm.t("2. Segmentando sarna, antracnosis y bacteriosis (640×640)", "2. Segmenting scab, anthracnose, and bacteriosis (640×640)"))
                    if vm.settings.resolution.multiTile {
                        Text(vm.t("3. Inferencia multi-escala 2×2 en cuadrantes de ultra resolución", "3. 2×2 multi-scale tile inference at ultra resolution"))
                    } else {
                        Text(vm.t("3. Calculando área afectada (%) y escala 1–10", "3. Calculating affected area (%) and 1–10 scale"))
                    }
                }
                .font(.caption)
                .foregroundStyle(b.textSoft)
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.white.opacity(0.95))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
                .padding(.horizontal, 24)

                Spacer()
            }
        }
    }
}

// MARK: - Paso 3: Lista de Daños Detectados (Idéntica a DamageListScreen en ScanFlowScreens.kt)

struct DamageListScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let analysis: Analysis

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(
                title: vm.t("Daños detectados", "Detected damages"),
                onBack: { vm.scanRoute = .camera }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if analysis.isDemo {
                        DemoBannerView()
                    }

                    // Foto del fruto con etiqueta de porcentaje total
                    ZStack(alignment: .bottomLeading) {
                        if let uiImg = BundleImageLoader.loadFile(analysis.images.result ?? analysis.images.crop ?? analysis.images.original) {
                            Image(uiImage: uiImg)
                                .resizable()
                                .scaledToFill()
                                .frame(height: 225)
                                .frame(maxWidth: .infinity)
                                .clipped()
                        } else {
                            Rectangle().fill(b.soft).frame(height: 225)
                        }

                        LinearGradient(colors: [.clear, .black.opacity(0.65)], startPoint: .top, endPoint: .bottom)
                            .frame(height: 90)

                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Gulupa · Passiflora edulis")
                                    .font(.subheadline.bold())
                                    .foregroundStyle(.white)
                                Text(analysis.classification.title(vm.lang))
                                    .font(.caption)
                                    .foregroundStyle(.white.opacity(0.9))
                            }
                            Spacer()
                            Text(String(format: "%.1f%% %@", analysis.totalDamagePct, vm.t("daño", "damage")))
                                .font(.caption.bold())
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(analysis.classification.color)
                                .clipShape(Capsule())
                        }
                        .padding(14)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                    // Indicador de calidad de captura / resolución del recorte
                    if analysis.cropSidePx > 0 {
                        HStack(spacing: 10) {
                            Image(systemName: analysis.lowDetail ? "exclamationmark.triangle.fill" : "checkmark.seal.fill")
                                .foregroundStyle(analysis.lowDetail ? Color.warnText : Color.healthyGreen)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(analysis.lowDetail
                                     ? vm.t("Fruto lejano o pequeño (\(analysis.cropSidePx) px)", "Distant or small fruit (\(analysis.cropSidePx) px)")
                                     : vm.t("Calidad de captura óptima (\(analysis.cropSidePx) px)", "Optimal capture quality (\(analysis.cropSidePx) px)"))
                                    .font(.caption.bold())
                                    .foregroundStyle(analysis.lowDetail ? Color.warnText : Color.healthyGreen)
                                Text(analysis.lowDetail
                                     ? vm.t("Acerca la cámara a 15–25 cm del fruto para captar lesiones pequeñas (< 640 px).",
                                            "Move the camera closer (15–25 cm) to capture small lesions (< 640 px).")
                                     : vm.t("Foto: \(analysis.photoWidth)×\(analysis.photoHeight) px · Recorte nativo ≥ 640 px.",
                                            "Photo: \(analysis.photoWidth)×\(analysis.photoHeight) px · Native crop ≥ 640 px."))
                                    .font(.caption2)
                                    .foregroundStyle(b.textSoft)
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(analysis.lowDetail ? Color.warnBg : Color.healthyGreen.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    Text(vm.t("Toca un daño para ver su máscara y nivel en la escala 1–10:", "Tap a damage item to view its mask and 1–10 severity level:"))
                        .font(.footnote)
                        .foregroundStyle(b.textSoft)

                    if analysis.harmfulDamages.isEmpty {
                        HStack(spacing: 12) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.title2)
                                .foregroundStyle(Color.healthyGreen)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(vm.t("Sin daños significativos (< 1 %)", "No significant damage (< 1 %)"))
                                    .font(.subheadline.bold())
                                Text(vm.t("El fruto cumple criterios visuales de exportación.", "The fruit meets visual export criteria."))
                                    .font(.caption)
                                    .foregroundStyle(b.textSoft)
                            }
                        }
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.healthyGreen.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    } else {
                        ForEach(analysis.harmfulDamages) { d in
                            DamageRowCard(damage: d) {
                                vm.scanRoute = .damageDetail(d.key)
                            }
                        }
                    }

                    if let healthy = analysis.damages.first(where: { $0.key == DamageCatalog.HEALTHY }) {
                        DamageRowCard(damage: healthy, isHealthy: true) {
                            vm.scanRoute = .damageDetail(healthy.key)
                        }
                    }

                    HStack(spacing: 10) {
                        Button {
                            vm.scanRoute = .camera
                        } label: {
                            Label(vm.t("Nuevo escaneo", "New scan"), systemImage: "camera")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .foregroundStyle(b.dark)
                                .overlay(Capsule().stroke(b.dark, lineWidth: 1.4))
                        }

                        Button {
                            vm.scanRoute = .result
                        } label: {
                            Label(vm.t("Ver informe completo", "Full report"), systemImage: "doc.text.magnifyingglass")
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(b.dark)
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                        }
                    }
                    .padding(.top, 6)
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

struct DamageRowCard: View {
    @EnvironmentObject var vm: AppViewModel
    let damage: Damage
    var isHealthy: Bool = false
    let onTap: () -> Void

    var body: some View {
        let info = DamageCatalog.get(damage.key)
        let lvl = isHealthy ? 0 : Severity.level(damage.pct)

        Button(action: onTap) {
            HStack(spacing: 12) {
                Circle()
                    .fill(info.color)
                    .frame(width: 14, height: 14)

                VStack(alignment: .leading, spacing: 3) {
                    Text(info.name(vm.lang))
                        .font(.subheadline.bold())
                        .foregroundStyle(vm.brand.text)
                    if let sci = info.scientific {
                        Text(sci)
                            .font(.caption2.italic())
                            .foregroundStyle(vm.brand.textSoft)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Capsule().fill(vm.brand.surfaceVariant).frame(height: 6)
                            Capsule()
                                .fill(info.color)
                                .frame(width: geo.size.width * CGFloat(min(1.0, max(0.0, damage.pct / 100.0))), height: 6)
                        }
                    }
                    .frame(height: 6)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(String(format: "%.1f%%", damage.pct))
                        .font(.subheadline.bold())
                        .foregroundStyle(info.color)
                    if !isHealthy {
                        Text(vm.t("Nivel \(lvl)/10", "Level \(lvl)/10"))
                            .font(.caption2)
                            .foregroundStyle(vm.brand.textSoft)
                    }
                }

                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(vm.brand.textSoft)
            }
            .padding(14)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(vm.brand.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Paso 4: Detalle del Daño + Escala 1-10 (Idéntico a DamageDetailScreen en ScanFlowScreens.kt)

struct DamageDetailScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let analysis: Analysis
    let damageKey: String
    @State private var showOverlay = true

    var body: some View {
        let b = vm.brand
        let info = DamageCatalog.get(damageKey)
        let dmg = analysis.damages.first { $0.key == damageKey } ?? Damage(key: damageKey, pct: 0.0)
        let level = damageKey == DamageCatalog.HEALTHY ? 0 : Severity.level(dmg.pct)
        let overlayPath = analysis.images.overlayFor(damageKey)

        VStack(spacing: 0) {
            GulupaTopBar(
                title: info.name(vm.lang),
                onBack: { vm.scanRoute = .damageList }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Imagen del fruto con máscara superpuesta conmutable
                    ZStack(alignment: .topTrailing) {
                        ZStack {
                            if let baseImg = BundleImageLoader.loadFile(analysis.images.crop ?? analysis.images.original) {
                                Image(uiImage: baseImg)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(maxHeight: 260)
                            }
                            if showOverlay, damageKey != DamageCatalog.HEALTHY,
                               let ovImg = BundleImageLoader.loadFile(overlayPath) {
                                Image(uiImage: ovImg)
                                    .resizable()
                                    .scaledToFit()
                                    .frame(maxHeight: 260)
                                    .opacity(0.75)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .background(Color.black)
                        .clipShape(RoundedRectangle(cornerRadius: 18))

                        if overlayPath != nil && damageKey != DamageCatalog.HEALTHY {
                            Button {
                                showOverlay.toggle()
                            } label: {
                                Label(
                                    showOverlay ? vm.t("Ocultar máscara", "Hide mask") : vm.t("Ver máscara", "Show mask"),
                                    systemImage: showOverlay ? "eye.slash" : "eye"
                                )
                                .font(.caption.bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Color.white.opacity(0.92))
                                .foregroundStyle(b.main)
                                .clipShape(Capsule())
                            }
                            .padding(10)
                        }
                    }

                    // Tarjeta de porcentaje y área en cm²
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Circle().fill(info.color).frame(width: 12, height: 12)
                            Text(info.name(vm.lang))
                                .font(.headline)
                            Spacer()
                            Text(String(format: "%.2f %%", dmg.pct))
                                .font(.title2.bold())
                                .foregroundStyle(info.color)
                        }
                        if let sci = info.scientific {
                            Text(sci)
                                .font(.caption.italic())
                                .foregroundStyle(b.textSoft)
                        }
                        if let cm2 = dmg.areaCm2 {
                            Text(vm.t(
                                String(format: "Área estimada: %.2f cm² (sobre %.0f cm² de fruto)", cm2, vm.settings.averageFruitAreaCm2),
                                String(format: "Estimated area: %.2f cm² (out of %.0f cm² fruit)", cm2, vm.settings.averageFruitAreaCm2)
                            ))
                            .font(.caption)
                            .foregroundStyle(b.textSoft)
                        }
                    }
                    .padding(16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))

                    // Escala de severidad 1–10
                    if damageKey != DamageCatalog.HEALTHY {
                        SeverityScaleCard(activeLevel: level, pct: dmg.pct)
                    }

                    // Descripción y manejo agronómico
                    VStack(alignment: .leading, spacing: 8) {
                        Text(vm.t("Descripción de síntomas", "Symptom description"))
                            .font(.subheadline.bold())
                            .foregroundStyle(b.main)
                        Text(info.description(vm.lang))
                            .font(.footnote)
                            .foregroundStyle(b.textSoft)

                        Divider().padding(.vertical, 4)

                        Text(vm.t("Manejo agronómico recomendado", "Recommended agronomic management"))
                            .font(.subheadline.bold())
                            .foregroundStyle(b.main)
                        Text(info.treatment(vm.lang))
                            .font(.footnote)
                            .foregroundStyle(b.textSoft)
                    }
                    .padding(16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))

                    Button {
                        vm.scanRoute = .result
                    } label: {
                        Text(vm.t("VER INFORME DEL FRUTO", "VIEW FRUIT REPORT"))
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(b.dark)
                            .foregroundStyle(.white)
                            .clipShape(Capsule())
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

struct SeverityScaleCard: View {
    @EnvironmentObject var vm: AppViewModel
    let activeLevel: Int
    let pct: Double

    var body: some View {
        let b = vm.brand
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(vm.t("Escala de severidad (1 – 10)", "Severity scale (1 – 10)"))
                    .font(.subheadline.bold())
                Spacer()
                Text(vm.t("Nivel \(activeLevel)/10 · \(Severity.rangeText(activeLevel))", "Level \(activeLevel)/10 · \(Severity.rangeText(activeLevel))"))
                    .font(.caption.bold())
                    .foregroundStyle(Severity.levelColor(activeLevel))
            }

            HStack(spacing: 4) {
                ForEach(1...10, id: \.self) { lv in
                    let isSelected = lv == activeLevel
                    let c = Severity.levelColor(lv)
                    Text("\(lv)")
                        .font(.caption.weight(isSelected ? .heavy : .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: isSelected ? 36 : 28)
                        .background(isSelected ? c : c.opacity(0.20))
                        .foregroundStyle(isSelected ? .white : c)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(isSelected ? b.text : .clear, lineWidth: 1.5)
                        )
                }
            }

            HStack {
                Text(vm.t("1: < 1% (leve)", "1: < 1% (light)"))
                Spacer()
                Text(vm.t("5: 10%", "5: 10%"))
                Spacer()
                Text(vm.t("10: ≥ 60% (severo)", "10: ≥ 60% (severe)"))
            }
            .font(.caption2)
            .foregroundStyle(b.textSoft)
        }
        .padding(16)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
    }
}

// MARK: - Paso 5: Informe Final del Fruto + PDF + Guardar Fotos + Compartir (Idéntico a ResultScreen en ScanFlowScreens.kt)

struct ResultScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let analysis: Analysis
    @State private var shareItems: [Any] = []
    @State private var showShareSheet = false

    var body: some View {
        let b = vm.brand
        let cls = analysis.classification

        VStack(spacing: 0) {
            GulupaTopBar(
                title: vm.t("Informe del fruto", "Fruit report"),
                onBack: { vm.scanRoute = .damageList }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if analysis.isDemo {
                        DemoBannerView()
                    }

                    // Encabezado con miniatura y clasificación comercial
                    HStack(spacing: 14) {
                        if let thumb = BundleImageLoader.loadFile(analysis.thumbnail) {
                            Image(uiImage: thumb)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 92, height: 92)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(cls.title(vm.lang))
                                .font(.caption.bold())
                                .foregroundStyle(cls.color)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(cls.color.opacity(0.14))
                                .clipShape(Capsule())

                            Text(cls.subtitle(vm.lang))
                                .font(.caption)
                                .foregroundStyle(b.textSoft)

                            Text(String(format: "%@: %.1f%% · %@: %.1f%%",
                                        vm.t("Daño total", "Total damage"), analysis.totalDamagePct,
                                        vm.t("Sano", "Healthy"), analysis.healthyPct))
                                .font(.subheadline.bold())
                                .foregroundStyle(b.text)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))

                    // Barra apilada de composición de la superficie
                    VStack(alignment: .leading, spacing: 10) {
                        Text(vm.t("Distribución de la superficie del fruto", "Fruit surface breakdown"))
                            .font(.subheadline.bold())

                        GeometryReader { geo in
                            HStack(spacing: 0) {
                                ForEach(analysis.damages.filter { $0.pct > 0.1 }) { d in
                                    Rectangle()
                                        .fill(DamageCatalog.get(d.key).color)
                                        .frame(width: max(2, geo.size.width * CGFloat(d.pct / 100.0)))
                                }
                            }
                        }
                        .frame(height: 16)
                        .clipShape(Capsule())

                        ForEach(analysis.damages) { d in
                            let info = DamageCatalog.get(d.key)
                            let lv = d.key == DamageCatalog.HEALTHY ? 0 : Severity.level(d.pct)
                            HStack {
                                Circle().fill(info.color).frame(width: 10, height: 10)
                                Text(info.name(vm.lang))
                                    .font(.footnote)
                                Spacer()
                                if lv > 0 {
                                    Text(vm.t("Nivel \(lv)/10", "Level \(lv)/10"))
                                        .font(.caption2)
                                        .foregroundStyle(b.textSoft)
                                }
                                Text(String(format: "%.1f%%", d.pct))
                                    .font(.footnote.bold())
                            }
                        }
                    }
                    .padding(16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))

                    // Botón principal: Generar Informe Técnico PDF
                    Button {
                        if let pdfURL = PdfReportGenerator.generateReport(analysis: analysis, lang: vm.lang) {
                            shareItems = [pdfURL]
                            showShareSheet = true
                        }
                    } label: {
                        HStack {
                            Image(systemName: "doc.richtext.fill")
                            Text(vm.t("Generar Informe Técnico (PDF)", "Generate Technical Report (PDF)"))
                                .font(.subheadline.bold())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.gulupaPurple)
                        .foregroundStyle(.white)
                        .clipShape(Capsule())
                    }

                    // Guardar foto original / procesada en Fotos
                    HStack(spacing: 10) {
                        Button {
                            if let img = BundleImageLoader.loadFile(analysis.images.original) {
                                UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                                vm.showToast(vm.t("Foto original guardada en Fotos", "Original photo saved to Photos"))
                            }
                        } label: {
                            Label(vm.t("Guardar Original", "Save Original"), systemImage: "square.and.arrow.down")
                                .font(.caption.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .foregroundStyle(b.dark)
                                .overlay(Capsule().stroke(b.dark, lineWidth: 1.2))
                        }

                        Button {
                            if let img = BundleImageLoader.loadFile(analysis.images.result ?? analysis.images.crop) {
                                UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                                vm.showToast(vm.t("Foto segmentada guardada en Fotos", "Segmented photo saved to Photos"))
                            }
                        } label: {
                            Label(vm.t("Guardar Procesada", "Save Processed"), systemImage: "photo.badge.checkmark")
                                .font(.caption.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .foregroundStyle(b.dark)
                                .overlay(Capsule().stroke(b.dark, lineWidth: 1.2))
                        }
                    }

                    // Guardar en historial + Compartir texto
                    HStack(spacing: 10) {
                        Button {
                            vm.saveCurrentToHistory()
                        } label: {
                            Label(
                                analysis.saved ? vm.t("Guardado", "Saved") : vm.t("Guardar en historial", "Save to history"),
                                systemImage: analysis.saved ? "checkmark.circle.fill" : "tray.and.arrow.down.fill"
                            )
                            .font(.subheadline.bold())
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(analysis.saved ? b.soft : b.dark)
                            .foregroundStyle(analysis.saved ? b.dark : .white)
                            .clipShape(Capsule())
                        }
                        .disabled(analysis.saved)

                        Button {
                            let summary = """
                            GulupaScan · UNAL
                            \(cls.title(vm.lang))
                            \(vm.t("Daño total", "Total damage")): \(String(format: "%.1f%%", analysis.totalDamagePct))
                            \(vm.t("Tejido sano", "Healthy tissue")): \(String(format: "%.1f%%", analysis.healthyPct))
                            """
                            shareItems = [summary]
                            showShareSheet = true
                        } label: {
                            Label(vm.t("Compartir", "Share"), systemImage: "square.and.arrow.up")
                                .font(.subheadline.bold())
                                .padding(.horizontal, 18)
                                .padding(.vertical, 13)
                                .foregroundStyle(b.dark)
                                .overlay(Capsule().stroke(b.dark, lineWidth: 1.4))
                        }
                    }

                    Button {
                        vm.scanRoute = .techDetails
                    } label: {
                        HStack {
                            Image(systemName: "cpu")
                            Text(vm.t("Ver detalles técnicos del modelo (máscaras, tiempos)", "View model technical details (masks, timings)"))
                                .font(.caption.bold())
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .foregroundStyle(b.main)
                    }

                    Button {
                        vm.scanRoute = .camera
                    } label: {
                        Label(vm.t("Analizar otro fruto", "Analyze another fruit"), systemImage: "camera.fill")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .foregroundStyle(b.dark)
                            .overlay(Capsule().stroke(b.border, lineWidth: 1))
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
        .sheet(isPresented: $showShareSheet) {
            ActivityShareSheet(items: shareItems)
        }
    }
}

struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

struct DemoBannerView: View {
    @EnvironmentObject var vm: AppViewModel
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(Color.warnText)
            Text(vm.t(
                "Resultado de demostración. Fotografía un fruto real para ejecutar los modelos ONNX.",
                "Demo result. Photograph a real fruit to run the ONNX models."
            ))
            .font(.caption)
            .foregroundStyle(Color.warnText)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.warnBg)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Paso 6: Detalles Técnicos del Modelo (Idéntico a TechDetailsScreen en ScanFlowScreens.kt)

struct TechDetailsScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let analysis: Analysis
    @State private var selectedImgTab = 0

    var body: some View {
        let b = vm.brand
        let tabs: [(String, String?)] = [
            (vm.t("Resultado", "Result"), analysis.images.result),
            (vm.t("Sin fondo", "No background"), analysis.images.leaf),
            (vm.t("Máscara", "Mask"), analysis.images.mask),
            (vm.t("Original", "Original"), analysis.images.original)
        ]

        VStack(spacing: 0) {
            GulupaTopBar(
                title: vm.t("Detalles técnicos", "Technical details"),
                onBack: { vm.scanRoute = .result }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Picker("", selection: $selectedImgTab) {
                        ForEach(0..<tabs.count, id: \.self) { i in
                            Text(tabs[i].0).tag(i)
                        }
                    }
                    .pickerStyle(.segmented)

                    if let path = tabs[selectedImgTab].1, let img = BundleImageLoader.loadFile(path) {
                        Image(uiImage: img)
                            .resizable()
                            .scaledToFit()
                            .frame(height: 250)
                            .frame(maxWidth: .infinity)
                            .background(Color.black)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(vm.t("Pipeline de visión por computador", "Computer vision pipeline"))
                            .font(.subheadline.bold())
                            .foregroundStyle(b.main)

                        TechKVRow(vm.t("Origen de datos", "Data source"), analysis.isDemo ? vm.t("Demostración", "Demo") : "ONNX Runtime (CPU)")
                        TechKVRow(vm.t("Arquitectura", "Architecture"), "YOLO26n-seg Dual Pipeline")
                        TechKVRow(vm.t("Confianza fruto (Modelo A)", "Fruit confidence (Model A)"), String(format: "%.1f %%", analysis.leafConfidence * 100))
                        TechKVRow(vm.t("Confianza lesiones (Modelo B)", "Lesion confidence (Model B)"), String(format: "%.1f %%", analysis.modelConfidence * 100))
                        TechKVRow(vm.t("Tiempo Modelo A (480×480)", "Model A time (480×480)"), String(format: "%.0f ms", analysis.clsTimeMs))
                        TechKVRow(vm.t("Tiempo Modelo B (640×640)", "Model B time (640×640)"), String(format: "%.0f ms", analysis.segTimeMs))
                        TechKVRow(vm.t("Resolución foto / recorte", "Photo / crop resolution"), "\(analysis.photoWidth)×\(analysis.photoHeight) px / \(analysis.cropSidePx) px")
                    }
                    .padding(16)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

struct TechKVRow: View {
    let k: String
    let v: String
    init(_ k: String, _ v: String) { self.k = k; self.v = v }

    var body: some View {
        HStack {
            Text(k).font(.caption).foregroundStyle(.secondary)
            Spacer()
            Text(v).font(.caption.bold())
        }
        .padding(.vertical, 2)
    }
}

// MARK: - 5. Pestaña Historial con Exportación CSV (Idéntica a HistoryScreen en ManagementScreens.kt)

struct HistoryScreen: View {
    @EnvironmentObject var vm: AppViewModel
    @State private var csvURL: URL? = nil
    @State private var showCSVShare = false

    var body: some View {
        let b = vm.brand
        let total = vm.history.count
        let healthyCount = vm.history.filter { $0.classification == .HEALTHY }.count
        let damagedCount = total - healthyCount

        VStack(spacing: 0) {
            HStack {
                GulupaLogoIcon(size: 30)
                Text(vm.t("Historial de análisis", "Analysis history"))
                    .font(.headline.bold())
                    .foregroundStyle(b.main)
                Spacer()
                if !vm.history.isEmpty {
                    Button {
                        if let url = vm.exportCSV() {
                            csvURL = url
                            showCSVShare = true
                        }
                    } label: {
                        Label("CSV", systemImage: "square.and.arrow.up")
                            .font(.caption.bold())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(b.soft)
                            .foregroundStyle(b.dark)
                            .clipShape(Capsule())
                    }

                    Button {
                        vm.clearHistory()
                    } label: {
                        Image(systemName: "trash")
                            .font(.subheadline)
                            .foregroundStyle(.red)
                            .padding(6)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Color.white)
            Divider()

            ScrollView {
                VStack(spacing: 12) {
                    if !vm.history.isEmpty {
                        HStack(spacing: 10) {
                            SummaryStatBox(label: vm.t("Total", "Total"), value: "\(total)", color: b.dark)
                            SummaryStatBox(label: vm.t("Sanos", "Healthy"), value: "\(healthyCount)", color: .healthyGreen)
                            SummaryStatBox(label: vm.t("Con daño", "Damaged"), value: "\(damagedCount)", color: .damageScab)
                        }
                    }

                    if vm.history.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 46))
                                .foregroundStyle(b.textSoft)
                            Text(vm.t("Aún no hay análisis guardados", "No saved analyses yet"))
                                .font(.headline)
                            Text(vm.t(
                                "Cuando analices un fruto y pulses «Guardar en historial», aparecerá aquí.",
                                "When you analyze a fruit and tap 'Save to history', it will appear here."
                            ))
                            .font(.caption)
                            .foregroundStyle(b.textSoft)
                            .multilineTextAlignment(.center)
                        }
                        .padding(32)
                        .frame(maxWidth: .infinity)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                    } else {
                        ForEach(vm.history) { item in
                            Button {
                                vm.currentAnalysis = item
                                vm.selectedTab = .scan
                                vm.scanRoute = .result
                            } label: {
                                HStack(spacing: 12) {
                                    if let img = BundleImageLoader.loadFile(item.thumbnail) {
                                        Image(uiImage: img)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 64, height: 64)
                                            .clipShape(RoundedRectangle(cornerRadius: 12))
                                    } else {
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(b.soft)
                                            .frame(width: 64, height: 64)
                                    }

                                    VStack(alignment: .leading, spacing: 4) {
                                        HStack {
                                            Text(item.classification.title(vm.lang))
                                                .font(.subheadline.bold())
                                                .foregroundStyle(item.classification.color)
                                            Spacer()
                                            Text(String(format: "%.1f%%", item.totalDamagePct))
                                                .font(.subheadline.bold())
                                                .foregroundStyle(item.classification.color)
                                        }
                                        Text(item.createdDate.formatted(date: .abbreviated, time: .shortened))
                                            .font(.caption2)
                                            .foregroundStyle(b.textSoft)
                                    }

                                    Button {
                                        vm.deleteHistoryItem(item.id)
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.caption)
                                            .foregroundStyle(b.textSoft)
                                            .padding(6)
                                    }
                                }
                                .padding(12)
                                .background(Color.white)
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                                .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
        .sheet(isPresented: $showCSVShare) {
            if let csvURL {
                ActivityShareSheet(items: [csvURL])
            }
        }
    }
}

struct SummaryStatBox: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.title2.bold())
                .foregroundStyle(color)
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - 6. Pestaña Alertas Fitosanitarias (Idéntica a AlertsScreen en ManagementScreens.kt)

struct AlertsScreen: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        let b = vm.brand
        let regions: [(String, String, Int, Double)] = [
            ("Sonsón, Antioquia", DamageCatalog.get(DamageCatalog.SCAB).name(vm.lang), 28, 12.4),
            ("Rionegro, Antioquia", DamageCatalog.get(DamageCatalog.ANTHRACNOSE).name(vm.lang), 19, 8.7),
            ("La Unión, Antioquia", DamageCatalog.get(DamageCatalog.OIL_SPOT).name(vm.lang), 14, 6.2),
            ("San Bernardo, Cundinamarca", DamageCatalog.get(DamageCatalog.SCAB).name(vm.lang), 22, 10.1),
            ("Jardín, Antioquia", DamageCatalog.get(DamageCatalog.ANTHRACNOSE).name(vm.lang), 11, 4.5)
        ]

        VStack(spacing: 0) {
            GulupaTopBar(title: vm.t("Alertas fitosanitarias", "Phytosanitary alerts"))

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.title3)
                            .foregroundStyle(Color.warnText)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(vm.t("Vigilancia fitosanitaria regional", "Regional phytosanitary surveillance"))
                                .font(.subheadline.bold())
                                .foregroundStyle(Color.warnText)
                            Text(vm.t(
                                "Condiciones de alta humedad relativa (> 80%) favorecen brotes de roña (Cladosporium) y antracnosis en cultivos entre 1.800 y 2.400 m s. n. m.",
                                "High relative humidity (> 80%) favors scab (Cladosporium) and anthracnose outbreaks in crops between 1,800 and 2,400 m a.s.l."
                            ))
                            .font(.caption)
                            .foregroundStyle(b.text)
                        }
                    }
                    .padding(14)
                    .background(Color.warnBg)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    Text(vm.t("Incidencia reportada por zona productora", "Reported incidence by growing region"))
                        .font(.subheadline.bold())
                        .foregroundStyle(b.main)

                    VStack(spacing: 0) {
                        ForEach(0..<regions.count, id: \.self) { i in
                            let r = regions[i]
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.0).font(.footnote.bold()).foregroundStyle(b.text)
                                    Text(r.1).font(.caption2).foregroundStyle(b.textSoft)
                                }
                                Spacer()
                                Text("\(r.2) \(vm.t("casos", "cases"))")
                                    .font(.caption)
                                    .foregroundStyle(b.textSoft)
                                Text(String(format: "%.1f%%", r.3))
                                    .font(.footnote.bold())
                                    .foregroundStyle(r.3 >= 10 ? Color.damageScab : Color.warnText)
                                    .frame(width: 54, alignment: .trailing)
                            }
                            .padding(12)
                            if i < regions.count - 1 { Divider() }
                        }
                    }
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

// MARK: - 7. Pestaña Perfil + Instrucciones de Uso + Información Técnica + Acerca de Nosotros + Configuración

struct ProfileFlowContainer: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        switch vm.profileRoute {
        case .home:
            ProfileHomeScreen()
        case .guide:
            GuideScreen { vm.profileRoute = .home }
        case .techInfo:
            TechInfoScreen { vm.profileRoute = .home }
        case .about:
            AboutScreen { vm.profileRoute = .home }
        case .settings:
            SettingsScreen { vm.profileRoute = .home }
        }
    }
}

struct ProfileHomeScreen: View {
    @EnvironmentObject var vm: AppViewModel

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(
                title: vm.t("Perfil del productor", "Grower profile"),
                trailingIcon: "gearshape",
                onTrailing: { vm.profileRoute = .settings }
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    // Cabecera morada/verde con avatar y traducción automática de valores por defecto
                    HStack(spacing: 14) {
                        GulupaLogoIcon(size: 64)
                            .padding(4)
                            .background(Color.white.opacity(0.2))
                            .clipShape(Circle())

                        VStack(alignment: .leading, spacing: 3) {
                            Text(vm.profile.displayName(vm.lang))
                                .font(.title3.bold())
                                .foregroundStyle(.white)
                            Text(vm.profile.displayRole(vm.lang))
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.9))
                            Text("\(vm.profile.displayFarm(vm.lang)) · \(vm.profile.displayMunicipality(vm.lang))")
                                .font(.caption2)
                                .foregroundStyle(.white.opacity(0.8))
                        }
                        Spacer()
                    }
                    .padding(18)
                    .background(
                        LinearGradient(colors: [b.dark, b.mid], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                    // Menú de Información y Ayuda (Idéntico a ManagementScreens.kt + InfoScreens.kt)
                    Text(vm.t("Ayuda, documentación y equipo", "Help, documentation, and team"))
                        .font(.subheadline.bold())
                        .foregroundStyle(b.main)

                    VStack(spacing: 10) {
                        ProfileMenuButton(
                            icon: "book.fill",
                            title: vm.t("Instrucciones de uso", "How to use"),
                            subtitle: vm.t("Guía interactiva de 7 pasos para capturar y analizar gulupa", "7-step interactive guide to capture and analyze passion fruit")
                        ) { vm.profileRoute = .guide }

                        ProfileMenuButton(
                            icon: "cpu.fill",
                            title: vm.t("Información técnica", "Technical information"),
                            subtitle: vm.t("Arquitectura YOLO26n-seg, modelos ONNX, métricas mAP y escala 1–10", "YOLO26n-seg architecture, ONNX models, mAP metrics & 1–10 scale")
                        ) { vm.profileRoute = .techInfo }

                        ProfileMenuButton(
                            icon: "person.3.fill",
                            title: vm.t("Acerca de nosotros", "About us"),
                            subtitle: vm.t("Equipo de desarrollo · Universidad Nacional de Colombia", "Development team · National University of Colombia")
                        ) { vm.profileRoute = .about }

                        ProfileMenuButton(
                            icon: "slider.horizontal.3",
                            title: vm.t("Configuración", "Settings"),
                            subtitle: vm.t("Idioma (ES/EN), tema visual, modo offline ONNX y datos de finca", "Language (ES/EN), visual theme, offline ONNX mode & farm info")
                        ) { vm.profileRoute = .settings }
                    }

                    Button {
                        vm.logout()
                    } label: {
                        Label(vm.t("Cambiar cuenta / Volver al inicio", "Switch account / Back to login"), systemImage: "rectangle.portrait.and.arrow.right")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(Color.white)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
                    }
                    .padding(.top, 6)
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

struct ProfileMenuButton: View {
    @EnvironmentObject var vm: AppViewModel
    let icon: String
    let title: String
    let subtitle: String
    let action: () -> Void

    var body: some View {
        let b = vm.brand
        Button(action: action) {
            HStack(spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(b.soft)
                        .frame(width: 44, height: 44)
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(b.dark)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.subheadline.bold())
                        .foregroundStyle(b.text)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(b.textSoft)
                        .lineLimit(2)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(b.textSoft)
            }
            .padding(14)
            .background(Color.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - 8. Instrucciones de Uso: Guía Interactiva de 7 Pasos (Idéntica a GuideScreen en InfoScreens.kt)

struct GuideScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let onBack: () -> Void
    @State private var currentStep = 0

    struct StepData {
        let icon: String
        let title: String
        let desc: String
        let tip: String
    }

    var steps: [StepData] {
        [
            StepData(
                icon: "hand.Point.up.left.fill",
                title: vm.t("Paso 1: Posición y encuadre del fruto", "Step 1: Fruit position and framing"),
                desc: vm.t(
                    "Sostén el teléfono a una distancia de entre 15 y 25 cm del fruto de gulupa. Ubica un único fruto dentro de las esquinas amarillas del visor para que ocupe al menos el 50 % de la imagen.",
                    "Hold the phone 15 to 25 cm away from the passion fruit. Place a single fruit inside the yellow framing corners so it fills at least 50% of the image."
                ),
                tip: vm.t(
                    "Evita incluir varios frutos a la vez; el Modelo A selecciona automáticamente el fruto principal más confiable.",
                    "Avoid including multiple fruits at once; Model A automatically selects the most confident main fruit."
                )
            ),
            StepData(
                icon: "sun.max.fill",
                title: vm.t("Paso 2: Iluminación y enfoque nítido", "Step 2: Lighting and sharp focus"),
                desc: vm.t(
                    "Prefiere luz natural difusa (día nublado o sombra suave). Toca el fruto en la pantalla para activar el anillo de enfoque automático antes de capturar la fotografía.",
                    "Prefer diffuse natural light (cloudy day or soft shade). Tap the fruit on screen to trigger the autofocus ring before taking the photo."
                ),
                tip: vm.t(
                    "Evita el flash directo sobre la corteza lisa de la gulupa, ya que el reflejo blanco puede confundirse con lesiones.",
                    "Avoid direct flash on the smooth passion fruit rind, as white glare may obscure lesions."
                )
            ),
            StepData(
                icon: "camera.aperture",
                title: vm.t("Paso 3: Captura o selección desde galería", "Step 3: Capture or pick from gallery"),
                desc: vm.t(
                    "Pulsa el botón obturador central para tomar la foto o elige una imagen existente desde la Galería de tu dispositivo.",
                    "Press the center shutter button to take the photo or pick an existing image from your device's Gallery."
                ),
                tip: vm.t(
                    "También puedes pulsar «Ver demostración con ejemplo» para explorar todo el flujo con un fruto de prueba.",
                    "You can also tap 'Run demo with sample fruit' to explore the full flow with a sample fruit."
                )
            ),
            StepData(
                icon: "cpu",
                title: vm.t("Paso 4: Inferencia dual ONNX en el dispositivo", "Step 4: Dual on-device ONNX inference"),
                desc: vm.t(
                    "Sin necesidad de internet, el Modelo A (480×480) segmenta el fruto y aísla el fondo en gris neutro; luego el Modelo B (640×640) segmenta píxel a píxel las lesiones de Sarna, Antracnosis y Mancha de aceite.",
                    "With no internet required, Model A (480×480) segments the fruit and isolates the background in neutral gray; then Model B (640×640) segments Scab, Anthracnose, and Oil Spot lesions pixel by pixel."
                ),
                tip: vm.t(
                    "Si en la imagen no aparece una gulupa, el sistema te avisará para repetir la captura.",
                    "If no passion fruit is detected in the image, the system will prompt you to retake the photo."
                )
            ),
            StepData(
                icon: "chart.bar.doc.horizontal",
                title: vm.t("Paso 5: Revisión de daños y escala 1–10", "Step 5: Damage review and 1–10 scale"),
                desc: vm.t(
                    "En la lista de daños detectados, toca cualquier patógeno para inspeccionar su máscara coloreada sobre el fruto, el porcentaje exacto de área afectada y su grado en la escala agronómica del 1 al 10.",
                    "In the detected damages list, tap any pathogen to inspect its colored mask over the fruit, the exact affected area percentage, and its grade on the 1-to-10 agronomic scale."
                ),
                tip: vm.t(
                    "Usa el botón «Ocultar/Ver máscara» para comparar la corteza original con la segmentación de la red neuronal.",
                    "Use the 'Hide/Show mask' button to compare the original rind with the neural network segmentation."
                )
            ),
            StepData(
                icon: "doc.richtext",
                title: vm.t("Paso 6: Informe técnico PDF y guardado", "Step 6: PDF technical report and saving"),
                desc: vm.t(
                    "Desde «Informe del fruto» puedes generar un documento PDF A4 listo para imprimir o enviar por WhatsApp/correo con las fotos comparativas, tiempos de inferencia, tabla de patógenos y recomendación comercial.",
                    "From 'Fruit report' you can generate an A4 PDF document ready to print or share via WhatsApp/email with side-by-side photos, inference timings, pathogen table, and commercial recommendation."
                ),
                tip: vm.t(
                    "Pulsa «Guardar en historial» para conservar el registro en tu teléfono y exportarlo luego como tabla CSV.",
                    "Tap 'Save to history' to keep the record on your phone and export it later as a CSV spreadsheet."
                )
            ),
            StepData(
                icon: "checkmark.shield.fill",
                title: vm.t("Paso 7: Toma de decisiones en cosecha", "Step 7: Harvest decision-making"),
                desc: vm.t(
                    "Utiliza la clasificación automática: Sano (< 1 % daño, apto exportación), Leve (1–5 %, selección), Moderado (5–15 %, mercado nacional/pulpa) o Severo (> 15 %, descarte fitosanitario).",
                    "Use the automatic classification: Healthy (< 1% damage, export suitable), Light (1–5%, sorting), Moderate (5–15%, domestic/pulp), or Severe (> 15%, phytosanitary discard)."
                ),
                tip: vm.t(
                    "¡Listo! Ya puedes iniciar tu monitoreo fitosanitario en campo.",
                    "All set! You are ready to start your field phytosanitary monitoring."
                )
            )
        ]
    }

    var body: some View {
        let b = vm.brand
        let list = steps
        let step = list[currentStep]

        VStack(spacing: 0) {
            GulupaTopBar(title: vm.t("Instrucciones de uso", "How to use"), onBack: onBack)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Barra de progreso de los 7 pasos
                    HStack(spacing: 6) {
                        ForEach(0..<list.count, id: \.self) { i in
                            Capsule()
                                .fill(i <= currentStep ? b.dark : b.border)
                                .frame(height: 6)
                                .onTapGesture { withAnimation { currentStep = i } }
                        }
                    }

                    Text(vm.t("Paso \(currentStep + 1) de \(list.count)", "Step \(currentStep + 1) of \(list.count)"))
                        .font(.caption.bold())
                        .foregroundStyle(b.dark)

                    VStack(alignment: .leading, spacing: 14) {
                        GuideStepCanvasIllustration(stepIndex: currentStep)
                            .frame(height: 150)
                            .frame(maxWidth: .infinity)
                            .background(b.soft.opacity(0.65))
                            .clipShape(RoundedRectangle(cornerRadius: 16))

                        Text(step.title)
                            .font(.title3.bold())
                            .foregroundStyle(b.main)

                        Text(step.desc)
                            .font(.body)
                            .foregroundStyle(b.text)

                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "lightbulb.fill")
                                .foregroundStyle(Color.warnText)
                            Text(step.tip)
                                .font(.caption)
                                .foregroundStyle(b.text)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.warnBg)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .padding(18)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(b.border, lineWidth: 1))

                    HStack(spacing: 12) {
                        if currentStep > 0 {
                            Button {
                                withAnimation { currentStep -= 1 }
                            } label: {
                                Label(vm.t("Anterior", "Previous"), systemImage: "chevron.left")
                                    .font(.subheadline.bold())
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 13)
                                    .foregroundStyle(b.dark)
                                    .overlay(Capsule().stroke(b.dark, lineWidth: 1.4))
                            }
                        }

                        Button {
                            if currentStep < list.count - 1 {
                                withAnimation { currentStep += 1 }
                            } else {
                                vm.profileRoute = .home
                                vm.selectedTab = .scan
                                vm.scanRoute = .camera
                            }
                        } label: {
                            Text(currentStep < list.count - 1
                                 ? vm.t("Siguiente", "Next")
                                 : vm.t("Ir a escanear", "Go to scanner"))
                                .font(.subheadline.bold())
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(b.dark)
                                .foregroundStyle(.white)
                                .clipShape(Capsule())
                        }
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

struct GuideStepCanvasIllustration: View {
    let stepIndex: Int

    var body: some View {
        Canvas { ctx, size in
            let w = size.width
            let h = size.height
            let cx = w / 2
            let cy = h / 2
            let plum = Color(hex: 0x5D2741)
            let purple = Color(hex: 0x9034BD)
            let green = Color(hex: 0x005828)
            let yellow = Color(hex: 0xFEDC7A)
            let scab = Color(hex: 0xC62828)

            switch stepIndex {
            case 0:
                // Visor con esquinas amarillas y fruto centrado
                let r: CGFloat = 38
                ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2)), with: .color(plum))
                let box: CGFloat = 96
                let rect = CGRect(x: cx - box / 2, y: cy - box / 2, width: box, height: box)
                ctx.stroke(Path(roundedRect: rect, cornerRadius: 12), with: .color(yellow), style: StrokeStyle(lineWidth: 3, dash: [10, 6]))
            case 1:
                // Sol difuso + anillo de enfoque
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 34, y: cy - 34, width: 68, height: 68)), with: .color(plum))
                ctx.stroke(Path(ellipseIn: CGRect(x: cx - 46, y: cy - 46, width: 92, height: 92)), with: .color(yellow), lineWidth: 3)
                ctx.fill(Path(ellipseIn: CGRect(x: cx + 48, y: cy - 54, width: 26, height: 26)), with: .color(yellow))
            case 2:
                // Botón obturador y tarjeta de galería
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 30, y: cy - 30, width: 60, height: 60)), with: .color(green))
                ctx.stroke(Path(ellipseIn: CGRect(x: cx - 38, y: cy - 38, width: 76, height: 76)), with: .color(plum), lineWidth: 3)
                ctx.fill(Path(roundedRect: CGRect(x: cx - 95, y: cy - 20, width: 40, height: 40), cornerRadius: 8), with: .color(purple.opacity(0.35)))
            case 3:
                // Dos etapas ONNX: Fruto -> Lesiones
                ctx.fill(Path(roundedRect: CGRect(x: cx - 105, y: cy - 32, width: 76, height: 64), cornerRadius: 12), with: .color(plum.opacity(0.18)))
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 87, y: cy - 20, width: 40, height: 40)), with: .color(plum))
                ctx.fill(Path(roundedRect: CGRect(x: cx + 29, y: cy - 32, width: 76, height: 64), cornerRadius: 12), with: .color(green.opacity(0.18)))
                ctx.fill(Path(ellipseIn: CGRect(x: cx + 47, y: cy - 20, width: 40, height: 40)), with: .color(plum))
                ctx.fill(Path(ellipseIn: CGRect(x: cx + 58, y: cy - 10, width: 14, height: 12)), with: .color(scab))
            case 4:
                // Escala 1-10 de barras
                let barW: CGFloat = 14
                for i in 0..<10 {
                    let bh = CGFloat(18 + i * 8)
                    let bx = cx - 88 + CGFloat(i) * 18
                    let by = cy + 42 - bh
                    let col = i < 3 ? green : (i < 6 ? Color(hex: 0xEF6C00) : scab)
                    ctx.fill(Path(roundedRect: CGRect(x: bx, y: by, width: barW, height: bh), cornerRadius: 4), with: .color(col))
                }
            case 5:
                // Documento PDF A4
                let doc = CGRect(x: cx - 38, y: cy - 50, width: 76, height: 100)
                ctx.fill(Path(roundedRect: doc, cornerRadius: 10), with: .color(.white))
                ctx.stroke(Path(roundedRect: doc, cornerRadius: 10), with: .color(plum), lineWidth: 2.5)
                ctx.fill(Path(roundedRect: CGRect(x: cx - 26, y: cy - 34, width: 52, height: 12), cornerRadius: 3), with: .color(plum))
                ctx.fill(Path(roundedRect: CGRect(x: cx - 26, y: cy - 14, width: 52, height: 8), cornerRadius: 2), with: .color(green.opacity(0.5)))
                ctx.fill(Path(roundedRect: CGRect(x: cx - 26, y: cy + 2, width: 38, height: 8), cornerRadius: 2), with: .color(scab.opacity(0.6)))
            default:
                // Escudo de calidad de exportación
                ctx.fill(Path(ellipseIn: CGRect(x: cx - 42, y: cy - 42, width: 84, height: 84)), with: .color(green))
                ctx.stroke(Path(ellipseIn: CGRect(x: cx - 48, y: cy - 48, width: 96, height: 96)), with: .color(yellow), lineWidth: 3)
            }
        }
    }
}

// MARK: - 9. Información Técnica Expandible (12 Secciones Idénticas a TechInfoScreen en InfoScreens.kt)

struct TechInfoScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let onBack: () -> Void
    @State private var expanded: Set<Int> = [0, 1]

    struct SectionItem {
        let icon: String
        let title: String
        let body: String
    }

    var sections: [SectionItem] {
        [
            SectionItem(
                icon: "square.stack.3d.up.fill",
                title: vm.t("1. Arquitectura de dos etapas (Cascade Segmentation)", "1. Two-stage cascade segmentation architecture"),
                body: vm.t(
                    "GulupaScan desacopla el problema en dos redes secuenciales basadas en YOLO26n-seg:\n• Etapa A (gulupa_fruto.onnx · 480×480 · 3.1 MB): localiza el fruto, genera una máscara prototipo de 120×120 interpolada bilinealmente, recorta el fruto con margen del 10 % y sustituye el fondo por gris neutro RGB(128,128,128).\n• Etapa B (gulupa_lesiones.onnx · 640×640 · 3.2 MB): recibe únicamente el fruto aislado y segmenta a alta resolución tres patologías epidérmicas.",
                    "GulupaScan decouples the problem into two sequential YOLO26n-seg networks:\n• Stage A (gulupa_fruto.onnx · 480×480 · 3.1 MB): locates the fruit, generates a bilinearly upsampled 120×120 prototype mask, crops the fruit with a 10% margin, and replaces the background with neutral gray RGB(128,128,128).\n• Stage B (gulupa_lesiones.onnx · 640×640 · 3.2 MB): receives only the isolated fruit and segments three epidermal pathologies at high resolution."
                )
            ),
            SectionItem(
                icon: "aspectratio.fill",
                title: vm.t("2. Preprocesamiento Letterbox y normalización", "2. Letterbox preprocessing and normalization"),
                body: vm.t(
                    "Cada imagen se redimensiona manteniendo su relación de aspecto original sobre un lienzo cuadrado con bordes en gris RGB(114,114,114). Los canales RGB se convierten a formato planar NCHW float32 normalizado en [0.0, 1.0].",
                    "Each image is resized preserving its original aspect ratio onto a square canvas padded with RGB(114,114,114). RGB channels are converted to planar NCHW float32 format normalized in [0.0, 1.0]."
                )
            ),
            SectionItem(
                icon: "crop",
                title: vm.t("3. Aislamiento morfológico del fruto (Etapa A)", "3. Morphological fruit isolation (Stage A)"),
                body: vm.t(
                    "La máscara del fruto se proyecta mediante producto punto de 32 coeficientes por los prototipos 120×120 seguido de función sigmoide (umbral ≥ 0.50) y dilatación de contorno (0.4 % del lado mayor) para preservar bordes con daño perimetral.",
                    "The fruit mask is projected via dot product of 32 coefficients with 120×120 prototypes followed by a sigmoid activation (threshold ≥ 0.50) and contour dilation (0.4% of max side) to preserve perimeter lesions."
                )
            ),
            SectionItem(
                icon: "microbe.fill",
                title: vm.t("4. Patologías detectadas y agentes causales (Etapa B)", "4. Detected pathologies and causal agents (Stage B)"),
                body: vm.t(
                    "• Clase 0 — fruit: superficie epidérmica total del fruto.\n• Clase 1 — Sarna o Roña (Cladosporium cladosporioides): lesiones suberizadas corchosas pardas.\n• Clase 2 — Antracnosis (Colletotrichum gloeosporioides): manchas necróticas oscuras hundidas.\n• Clase 3 — Mancha de aceite / Bacteriosis (Xanthomonas axonopodis pv. passiflorae): lesiones translúcidas acuosas.",
                    "• Class 0 — fruit: total epidermal fruit surface.\n• Class 1 — Scab (Cladosporium cladosporioides): brownish corky suberized lesions.\n• Class 2 — Anthracnose (Colletotrichum gloeosporioides): sunken dark necrotic spots.\n• Class 3 — Oil spot / Bacteriosis (Xanthomonas axonopodis pv. passiflorae): water-soaked translucent lesions."
                )
            ),
            SectionItem(
                icon: "square.grid.2x2.fill",
                title: vm.t("5. Modos de resolución: Media, Alta y Ultra Alta (Tiling 2×2)", "5. Resolution modes: Medium, High, and Ultra High (2×2 Tiling)"),
                body: vm.t(
                    "Sin necesidad de reentrenar los modelos ONNX (cuya ventana interna es 480×480 y 640×640), GulupaScan permite elegir entre:\n• Media (1024 px): inferencia rápida en una pasada.\n• Alta (2048 px): conserva mayor nitidez en el recorte del fruto.\n• Ultra Alta (4096 px + Tiling 2×2): además de la inferencia global, subdivide el fruto en 4 cuadrantes solapados al 65 % y ejecuta el Modelo B en cada cuadrante para detectar micro-lesiones.",
                    "Without retraining the ONNX models (whose internal window is 480×480 and 640×640), GulupaScan lets you choose between:\n• Medium (1024 px): fast single-pass inference.\n• High (2048 px): preserves higher sharpness in the fruit crop.\n• Ultra High (4096 px + 2×2 Tiling): in addition to global inference, subdivides the fruit into 4 overlapping 65% tiles and runs Model B on each quadrant to detect micro-lesions."
                )
            ),
            SectionItem(
                icon: "function",
                title: vm.t("6. Cálculo matemático de severidad y área en cm²", "6. Mathematical severity calculation and area in cm²"),
                body: vm.t(
                    "La severidad epidérmica de cada daño se calcula como el cociente exacto de píxeles:\nSeveridad (%) = ( |Máscara_Lesión ∩ Máscara_Fruto| / |Máscara_Fruto| ) × 100.\nEl área física afectada se estima sobre la superficie media configurable del fruto (78.5 cm² por defecto).",
                    "Epidermal severity for each damage type is computed as the exact pixel ratio:\nSeverity (%) = ( |Lesion_Mask ∩ Fruit_Mask| / |Fruit_Mask| ) × 100.\nPhysical affected area is estimated from the configurable average fruit surface (78.5 cm² default)."
                )
            ),
            SectionItem(
                icon: "chart.bar.fill",
                title: vm.t("7. Escala agronómica de severidad (Grados 1 al 10)", "7. Agronomic severity scale (Grades 1 to 10)"),
                body: vm.t(
                    "Los porcentajes continuos se mapean a 10 niveles discretos con límites superiores en: [1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 25.0, 40.0, 60.0, 100.0] %, facilitando el registro fitosanitario estandarizado en campo.",
                    "Continuous percentages map to 10 discrete grades with upper bounds at: [1.0, 2.5, 5.0, 7.5, 10.0, 15.0, 25.0, 40.0, 60.0, 100.0] %, enabling standardized field phytosanitary scoring."
                )
            ),
            SectionItem(
                icon: "shippingbox.fill",
                title: vm.t("8. Criterios de clasificación comercial y exportación", "8. Commercial and export classification criteria"),
                body: vm.t(
                    "• Fruto Sano (< 1.0 % daño): Calidad Extra apta para exportación.\n• Daño Leve (1.0 – 5.0 %): Categoría I, requiere selección en poscosecha.\n• Daño Moderado (5.0 – 15.0 %): Categoría II, mercado nacional o agroindustria (pulpa).\n• Daño Severo (> 15.0 %): Descarte fitosanitario en campo.",
                    "• Healthy Fruit (< 1.0% damage): Extra Quality suitable for export.\n• Light Damage (1.0 – 5.0%): Category I, requires post-harvest sorting.\n• Moderate Damage (5.0 – 15.0%): Category II, domestic market or pulp processing.\n• Severe Damage (> 15.0%): Field phytosanitary discard."
                )
            ),
            SectionItem(
                icon: "cpu.fill",
                title: vm.t("9. Motor de inferencia ONNX Runtime On-Device", "9. On-device ONNX Runtime inference engine"),
                body: vm.t(
                    "Los modelos se ejecutan localmente en la CPU del dispositivo móvil mediante Microsoft ONNX Runtime (opset 17), garantizando operación 100 % offline en fincas sin cobertura celular.",
                    "Models run locally on the mobile device CPU via Microsoft ONNX Runtime (opset 17), ensuring 100% offline operation on farms without cellular coverage."
                )
            ),
            SectionItem(
                icon: "doc.richtext.fill",
                title: vm.t("10. Generación de informes PDF A4 y exportación CSV", "10. A4 PDF report generation and CSV export"),
                body: vm.t(
                    "Genera informes técnicos PDF vectoriales con fotografías comparativas (original vs. segmentada), desglose por patógeno, nivel 1–10 y recomendación agronómica, además de exportación masiva del historial en formato CSV.",
                    "Generates vector A4 PDF technical reports with side-by-side photographs (original vs. segmented), pathogen breakdown, 1–10 grade, and agronomic recommendation, plus bulk CSV history export."
                )
            )
        ]
    }

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(title: vm.t("Información técnica", "Technical information"), onBack: onBack)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ForEach(0..<sections.count, id: \.self) { idx in
                        let item = sections[idx]
                        let isExp = expanded.contains(idx)
                        VStack(alignment: .leading, spacing: 10) {
                            Button {
                                withAnimation {
                                    if isExp { expanded.remove(idx) } else { expanded.insert(idx) }
                                }
                            } label: {
                                HStack(spacing: 12) {
                                    Image(systemName: item.icon)
                                        .foregroundStyle(b.dark)
                                        .frame(width: 24)
                                    Text(item.title)
                                        .font(.subheadline.bold())
                                        .foregroundStyle(b.main)
                                        .multilineTextAlignment(.leading)
                                    Spacer()
                                    Image(systemName: isExp ? "chevron.up" : "chevron.down")
                                        .font(.caption.bold())
                                        .foregroundStyle(b.textSoft)
                                }
                            }
                            .buttonStyle(.plain)

                            if isExp {
                                Divider()
                                Text(item.body)
                                    .font(.footnote)
                                    .foregroundStyle(b.text)
                            }
                        }
                        .padding(16)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(b.border, lineWidth: 1))
                    }
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

// MARK: - 10. Acerca de Nosotros con Fotos Reales del Equipo (Idéntica a AboutScreen en InfoScreens.kt)

struct AboutScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let onBack: () -> Void

    struct Member {
        let photo: String
        let name: String
        let role: String
        let org: String
        let bio: String
        let email: String
        let phone: String
    }

    var team: [Member] {
        [
            Member(
                photo: "team_jesus.jpg",
                name: "Jesús Enrique Flores-Riera",
                role: vm.t("Investigador Principal · Maestría en Ciencias Agrarias", "Lead Researcher · M.Sc. Agricultural Sciences"),
                org: vm.t("Facultad de Ciencias Agrarias · UNAL", "Faculty of Agricultural Sciences · UNAL"),
                bio: vm.t(
                    "Entrenamiento de redes YOLO26n-seg de segmentación en dos etapas, cuantización ONNX y validación fitopatológica.",
                    "Two-stage YOLO26n-seg segmentation network training, ONNX quantization, and phytopathological validation."
                ),
                email: "jffloresr@unal.edu.co",
                phone: "+57 300 000 0000"
            ),
            Member(
                photo: "team_luis.jpg",
                name: "Luis Alberto Castillo Sanabria",
                role: vm.t("Co-investigador · Ciencias Agrarias y Fitopatología", "Co-researcher · Agricultural Sciences & Plant Pathology"),
                org: vm.t("Facultad de Ciencias Agrarias · UNAL", "Faculty of Agricultural Sciences · UNAL"),
                bio: vm.t(
                    "Caracterización de sintomatología de Cladosporium, Colletotrichum y Xanthomonas en cultivos de gulupa de exportación.",
                    "Symptom characterization of Cladosporium, Colletotrichum, and Xanthomonas in export passion fruit crops."
                ),
                email: "lacastillos@unal.edu.co",
                phone: "+57 310 000 0000"
            ),
            Member(
                photo: "team_diego.jpg",
                name: "Diego Humberto Lavado González",
                role: vm.t("Co-investigador · Ingeniería de Sistemas y Computación", "Co-researcher · Systems and Computing Engineering"),
                org: vm.t("Facultad de Ingeniería · UNAL", "Faculty of Engineering · UNAL"),
                bio: vm.t(
                    "Arquitectura de software móvil, integración de inferencia en el borde (Edge AI) y experiencia de usuario en campo.",
                    "Mobile software architecture, Edge AI inference integration, and field user experience."
                ),
                email: "dlavadog@unal.edu.co",
                phone: "+57 315 000 0000"
            ),
            Member(
                photo: "team_daniel.jpg",
                name: "Daniel Felipe Ahumada Hernández",
                role: vm.t("Co-investigador · Ingeniería de Sistemas y Computación", "Co-researcher · Systems and Computing Engineering"),
                org: vm.t("Facultad de Ingeniería · UNAL", "Faculty of Engineering · UNAL"),
                bio: vm.t(
                    "Curaduría del conjunto de datos anotados en campo, calibración de escalas de severidad 1–10 y pruebas de inferencia.",
                    "Field annotated dataset curation, 1–10 severity scale calibration, and inference benchmarking."
                ),
                email: "dahumadah@unal.edu.co",
                phone: "+57 320 000 0000"
            )
        ]
    }

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(title: vm.t("Acerca de nosotros", "About us"), onBack: onBack)

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Cabecera institucional
                    VStack(spacing: 10) {
                        BrandWordmarkView(centered: true)
                        Text(vm.t(
                            "Herramienta móvil de diagnóstico fitosanitario asistido por inteligencia artificial para el cultivo de gulupa (Passiflora edulis f. edulis).",
                            "AI-assisted mobile phytosanitary diagnostic tool for purple passion fruit (Passiflora edulis f. edulis)."
                        ))
                        .font(.footnote)
                        .foregroundStyle(b.textSoft)
                        .multilineTextAlignment(.center)
                    }
                    .padding(18)
                    .frame(maxWidth: .infinity)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(b.border, lineWidth: 1))

                    Text(vm.t("Equipo de investigación y desarrollo", "Research and development team"))
                        .font(.subheadline.bold())
                        .foregroundStyle(b.main)

                    ForEach(team, id: \.name) { m in
                        HStack(alignment: .top, spacing: 14) {
                            if let uiImg = BundleImageLoader.load(m.photo) {
                                Image(uiImage: uiImg)
                                    .resizable()
                                    .scaledToFill()
                                    .frame(width: 72, height: 72)
                                    .clipShape(Circle())
                                    .overlay(Circle().stroke(b.dark, lineWidth: 2))
                            } else {
                                Circle()
                                    .fill(b.soft)
                                    .frame(width: 72, height: 72)
                                    .overlay(
                                        Image(systemName: "person.fill")
                                            .font(.title2)
                                            .foregroundStyle(b.dark)
                                    )
                            }

                            VStack(alignment: .leading, spacing: 4) {
                                Text(m.name)
                                    .font(.subheadline.bold())
                                    .foregroundStyle(b.text)
                                Text(m.role)
                                    .font(.caption.bold())
                                    .foregroundStyle(b.dark)
                                Text(m.org)
                                    .font(.caption2)
                                    .foregroundStyle(b.textSoft)
                                Text(m.bio)
                                    .font(.caption)
                                    .foregroundStyle(b.textSoft)
                                    .padding(.top, 2)
                                Text(m.email)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(b.main)
                                    .padding(.top, 2)
                            }
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))
                    }

                    // Proyecto académico
                    VStack(alignment: .leading, spacing: 6) {
                        Text(vm.t("Proyecto Académico · UNAL", "Academic Project · UNAL"))
                            .font(.subheadline.bold())
                            .foregroundStyle(b.main)
                        Text(vm.t(
                            "Desarrollado en la Universidad Nacional de Colombia como aporte tecnológico para productores y exportadores de pasifloras de Colombia. Versión 0.3.0.",
                            "Developed at the National University of Colombia as a technological contribution for Colombian passion fruit growers and exporters. Version 0.3.0."
                        ))
                        .font(.footnote)
                        .foregroundStyle(b.textSoft)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(b.border, lineWidth: 1))
                }
                .padding(16)
            }
            .background(b.background)
        }
    }
}

// MARK: - 11. Configuración Completa (Idioma ES/EN, Tema Morado/Verde, Resolución Media/Alta/Ultra, Offline ONNX, Finca)

struct SettingsScreen: View {
    @EnvironmentObject var vm: AppViewModel
    let onBack: () -> Void

    var body: some View {
        let b = vm.brand
        VStack(spacing: 0) {
            GulupaTopBar(title: vm.t("Configuración", "Settings"), onBack: onBack)

            Form {
                Section(vm.t("Idioma / Language", "Language / Idioma")) {
                    Picker(vm.t("Idioma de la aplicación", "App language"), selection: $vm.settings.language) {
                        Text("Español").tag(AppLanguage.spanish)
                        Text("English").tag(AppLanguage.english)
                    }
                    .pickerStyle(.segmented)
                }

                Section(vm.t("Tema visual", "Visual theme")) {
                    Picker(vm.t("Paleta de colores", "Color palette"), selection: $vm.settings.theme) {
                        Text(vm.t("Morado gulupa", "Purple passion")).tag(AppTheme.purple)
                        Text(vm.t("Verde campo", "Field green")).tag(AppTheme.green)
                    }
                    .pickerStyle(.segmented)
                }

                Section(vm.t("Resolución de procesamiento IA", "AI processing resolution")) {
                    ForEach(AnalysisResolution.allCases, id: \.self) { res in
                        let selected = vm.settings.resolution == res
                        Button {
                            vm.settings.resolution = res
                        } label: {
                            HStack(alignment: .top, spacing: 10) {
                                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                                    .foregroundStyle(selected ? b.dark : .secondary)
                                    .padding(.top, 2)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(res.title(vm.lang))
                                        .font(.subheadline.weight(selected ? .bold : .semibold))
                                        .foregroundStyle(b.text)
                                    Text(res.subtitle(vm.lang))
                                        .font(.caption)
                                        .foregroundStyle(b.textSoft)
                                }
                                Spacer()
                            }
                            .padding(.vertical, 4)
                        }
                        .buttonStyle(.plain)
                    }
                }

                Section(vm.t("Motor de inferencia", "Inference engine")) {
                    Toggle(vm.t("Modo Offline en el teléfono (ONNX)", "On-device Offline Mode (ONNX)"), isOn: $vm.settings.offlineMode)
                    Toggle(vm.t("Respaldo con demostración si falla", "Demo fallback on error"), isOn: $vm.settings.demoFallback)
                    if !vm.settings.offlineMode {
                        TextField(vm.t("URL del servidor", "Server URL"), text: $vm.settings.serverUrl)
                            .textInputAutocapitalization(.never)
                    }
                }

                Section(vm.t("Datos del productor y finca", "Grower and farm info")) {
                    TextField(vm.t("Nombre", "Name"), text: $vm.profile.name)
                    TextField(vm.t("Rol", "Role"), text: $vm.profile.role)
                    TextField(vm.t("Finca", "Farm"), text: $vm.profile.farm)
                    TextField(vm.t("Municipio", "Municipality"), text: $vm.profile.municipality)
                }

                Section(vm.t("Modelos ONNX integrados y resolución activa", "Bundled ONNX models & active resolution")) {
                    LabeledContent(
                        vm.t("Entrada de cámara", "Camera input"),
                        value: "\(vm.settings.resolution.title(vm.lang)) (\(vm.settings.resolution.subtitle(vm.lang)))"
                    )
                    LabeledContent(
                        vm.t("Etapa A (Fruto)", "Stage A (Fruit)"),
                        value: "gulupa_fruto.onnx (Tensor 480×480 INT8)"
                    )
                    LabeledContent(
                        vm.t("Etapa B (Lesiones)", "Stage B (Lesions)"),
                        value: vm.settings.resolution.multiTile
                            ? "gulupa_lesiones.onnx (640×640 × 5 = 1280×1280 ef.)"
                            : "gulupa_lesiones.onnx (Tensor 640×640 INT8)"
                    )
                    LabeledContent(vm.t("Precisión INT8 (IoU)", "INT8 Accuracy (IoU)"), value: "98.1% (A) · 90.2% (B)")
                    LabeledContent(vm.t("Versión", "Version"), value: "0.3.0")
                }
            }
            .scrollContentBackground(.hidden)
            .background(b.background)
        }
    }
}
