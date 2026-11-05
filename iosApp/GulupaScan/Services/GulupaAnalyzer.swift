import Foundation
import UIKit

#if canImport(onnxruntime_objc)
import onnxruntime_objc
#endif

enum AnalyzerError: LocalizedError {
    case invalidImage
    case noFruitDetected
    case modelNotFound(String)
    case networkError(String)

    var isNoFruit: Bool {
        if case .noFruitDetected = self { return true }
        return false
    }

    func localizedMessage(_ lang: AppLanguage) -> String {
        switch self {
        case .invalidImage:
            return lang == .english
                ? "Could not process the selected image."
                : "No fue posible procesar la imagen seleccionada."
        case .noFruitDetected:
            return lang == .english
                ? "We couldn't find a passion fruit in the photo. Place a single fruit in the center of the frame, on a clear background and in good light, and take the photo again."
                : "No se detectó una gulupa en la foto. Coloca un solo fruto en el centro del recuadro, sobre un fondo despejado y con buena luz, y vuelve a tomar la foto."
        case .modelNotFound(let name):
            return lang == .english
                ? "ONNX model (\(name)) was not found in the app bundle."
                : "No se encontró el modelo ONNX (\(name)) en los recursos de la aplicación."
        case .networkError(let msg):
            return lang == .english
                ? "Connection error: \(msg)"
                : "Error de conexión: \(msg)"
        }
    }

    var errorDescription: String? {
        localizedMessage(.spanish)
    }
}

// MARK: - Parámetros y Estructuras Auxiliares (Idéntico a ConfigGulupa en OnnxAnalyzer.kt)

private enum ConfigGulupa {
    static let ladoFruto = 480
    static let ladoLesiones = 640
    static let confianza: Float = 0.25
    static let umbralMascara: Float = 0.5
    static let margenRecorte: Float = 0.10
    static let holguraFondo: Float = 0.004
    static let rellenoLetterbox: UInt8 = 114
    static let umbralSano: Double = 0.5
    static let idFruto = 0
    static let lesiones: [Int: String] = [
        1: DamageCatalog.SCAB,
        2: DamageCatalog.ANTHRACNOSE,
        3: DamageCatalog.OIL_SPOT
    ]
}

private struct Letterbox {
    let lado: Int
    let escala: Float
    let padX: Int
    let padY: Int
    let ancho: Int
    let alto: Int

    func indice(x: Int, y: Int) -> Int {
        let u = min(lado - 1, max(0, Int((Float(x) + 0.5) * escala + Float(padX))))
        let v = min(lado - 1, max(0, Int((Float(y) + 0.5) * escala + Float(padY))))
        return v * lado + u
    }
}

private struct Deteccion {
    let clase: Int
    let confianza: Float
    let cajaOriginal: CGRect
    let mascara: [Bool]
}

final class GulupaAnalyzer {
    static let shared = GulupaAnalyzer()

    #if canImport(onnxruntime_objc)
    private var ortEnv: ORTEnv?
    private var fruitSession: ORTSession?
    private var lesionSession: ORTSession?
    #endif

    private func ensureSessions() throws {
        guard let fruitPath = Bundle.main.path(forResource: "gulupa_fruto", ofType: "onnx") else {
            throw AnalyzerError.modelNotFound("gulupa_fruto.onnx")
        }
        guard let lesionPath = Bundle.main.path(forResource: "gulupa_lesiones", ofType: "onnx") else {
            throw AnalyzerError.modelNotFound("gulupa_lesiones.onnx")
        }
        #if canImport(onnxruntime_objc)
        if ortEnv == nil {
            ortEnv = try ORTEnv(loggingLevel: .warning)
        }
        if fruitSession == nil, let env = ortEnv {
            let opts = try ORTSessionOptions()
            try opts.setIntraOpNumThreads(4)
            fruitSession = try ORTSession(env: env, modelPath: fruitPath, sessionOptions: opts)
        }
        if lesionSession == nil, let env = ortEnv {
            let opts = try ORTSessionOptions()
            try opts.setIntraOpNumThreads(4)
            lesionSession = try ORTSession(env: env, modelPath: lesionPath, sessionOptions: opts)
        }
        #endif
    }

    func analyze(image: UIImage, settings: AppSettings, forceDemo: Bool = false) async throws -> Analysis {
        let normalizedImage = normalizeOrientation(image)
        let id = String(UUID().uuidString.prefix(8))
        let outDir = analysesDirectory().appendingPathComponent(id, isDirectory: true)
        try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

        if forceDemo {
            return try await runDemoAnalysis(image: normalizedImage, id: id, outDir: outDir, areaCm2: settings.averageFruitAreaCm2)
        }

        do {
            return try await runOnnxPipeline(
                image: normalizedImage,
                id: id,
                outDir: outDir,
                resolution: settings.resolution,
                areaCm2: settings.averageFruitAreaCm2
            )
        } catch let err as AnalyzerError where err.isNoFruit {
            try? FileManager.default.removeItem(at: outDir)
            throw err
        } catch {
            if settings.demoFallback {
                return try await runDemoAnalysis(image: normalizedImage, id: id, outDir: outDir, areaCm2: settings.averageFruitAreaCm2)
            }
            try? FileManager.default.removeItem(at: outDir)
            throw error
        }
    }

    // MARK: - Pipeline Dual YOLO26n-seg ONNX (Idéntico a OnnxAnalyzer.kt)

    private func runOnnxPipeline(
        image: UIImage,
        id: String,
        outDir: URL,
        resolution: AnalysisResolution,
        areaCm2: Double
    ) async throws -> Analysis {
        return try await Task.detached(priority: .userInitiated) {
            try self.ensureSessions()

            let foto = self.resizeMaxSide(image, maxSide: CGFloat(resolution.maxSidePx))
            guard let fotoRGBA = self.extractRGBA(foto) else {
                throw AnalyzerError.invalidImage
            }

            let origPath = outDir.appendingPathComponent("original.jpg").path
            self.saveJPEG(foto, path: origPath, quality: 0.92)

            #if canImport(onnxruntime_objc)
            guard let fruitSess = self.fruitSession, let lesionSess = self.lesionSession else {
                throw AnalyzerError.modelNotFound("ORT Session")
            }

            // 1. Fase A: Localización y recorte del fruto (480x480)
            let t0 = CFAbsoluteTimeGetCurrent()
            let (recorteRGBA, frutoOk, confFruto) = try self.recortarFruto(sess: fruitSess, foto: fotoRGBA)
            let tFruto = (CFAbsoluteTimeGetCurrent() - t0) * 1000.0
            if !frutoOk {
                throw AnalyzerError.noFruitDetected
            }

            // 2. Fase B: Segmentación de lesiones (640x640 + cuadrantes 2x2 en modo Ultra Alta)
            let t1 = CFAbsoluteTimeGetCurrent()
            let (detsBase, lb) = try self.predecir(
                sess: lesionSess,
                rgba: recorteRGBA,
                lado: ConfigGulupa.ladoLesiones,
                protoSide: 160
            )
            var detecciones = detsBase

            if resolution.multiTile && max(recorteRGBA.width, recorteRGBA.height) >= 720 {
                let cw = recorteRGBA.width
                let ch = recorteRGBA.height
                let tw = max(320, Int((Float(cw) * 0.60).rounded()))
                let th = max(320, Int((Float(ch) * 0.60).rounded()))
                let offsets = [
                    (0, 0),
                    (max(0, cw - tw), 0),
                    (0, max(0, ch - th)),
                    (max(0, cw - tw), max(0, ch - th))
                ]
                for (ox, oy) in offsets {
                    let subW = min(tw, cw - ox)
                    let subH = min(th, ch - oy)
                    let tileRGBA = self.cropSubBuffer(recorteRGBA, x: ox, y: oy, w: subW, h: subH)
                    let (tileDets, tileLb) = try self.predecir(
                        sess: lesionSess,
                        rgba: tileRGBA,
                        lado: ConfigGulupa.ladoLesiones,
                        protoSide: 160
                    )
                    for td in tileDets where td.clase != ConfigGulupa.idFruto {
                        var mapped = [Bool](repeating: false, count: lb.lado * lb.lado)
                        for ty in 0..<subH {
                            for tx in 0..<subW {
                                if td.mascara[tileLb.indice(x: tx, y: ty)] {
                                    mapped[lb.indice(x: ox + tx, y: oy + ty)] = true
                                }
                            }
                        }
                        detecciones.append(Deteccion(clase: td.clase, confianza: td.confianza, cajaOriginal: td.cajaOriginal, mascara: mapped))
                    }
                }
            }
            let tLesiones = (CFAbsoluteTimeGetCurrent() - t1) * 1000.0

            // 3. Calcular severidades en la rejilla Letterbox (640x640)
            let n = lb.lado * lb.lado
            var mascaraFruto = [Bool](repeating: false, count: n)
            var porLesion: [Int: [Bool]] = [
                1: [Bool](repeating: false, count: n),
                2: [Bool](repeating: false, count: n),
                3: [Bool](repeating: false, count: n)
            ]
            var maxConf: Float = 0

            for d in detecciones {
                if d.confianza > maxConf { maxConf = d.confianza }
                if d.clase == ConfigGulupa.idFruto {
                    for i in 0..<n where d.mascara[i] { mascaraFruto[i] = true }
                } else if var arr = porLesion[d.clase] {
                    for i in 0..<n where d.mascara[i] { arr[i] = true }
                    porLesion[d.clase] = arr
                }
            }

            var unionLesiones = [Bool](repeating: false, count: n)
            for m in porLesion.values {
                for i in 0..<n where m[i] {
                    unionLesiones[i] = true
                    mascaraFruto[i] = true
                }
            }

            var areaFruto = 0
            for i in 0..<n where mascaraFruto[i] { areaFruto += 1 }
            if areaFruto == 0 {
                throw AnalyzerError.noFruitDetected
            }

            func calcPct(_ m: [Bool]) -> Double {
                var c = 0
                for i in 0..<n where m[i] && mascaraFruto[i] { c += 1 }
                return self.round2((100.0 * Double(c)) / Double(areaFruto))
            }

            var severidades: [String: Double] = [:]
            for (clsId, key) in ConfigGulupa.lesiones {
                if let m = porLesion[clsId] {
                    severidades[key] = calcPct(m)
                }
            }
            let totalDano = calcPct(unionLesiones)
            let isHealthy = totalDano < ConfigGulupa.umbralSano

            var damagesList: [Damage] = []
            for (key, pctVal) in severidades.sorted(by: { $0.value > $1.value }) {
                if pctVal > 0.05 {
                    damagesList.append(Damage(key: key, pct: pctVal, areaCm2: self.round2(areaCm2 * pctVal / 100.0)))
                }
            }
            let pctSano = self.round2(max(0.0, min(100.0, 100.0 - totalDano)))
            damagesList.append(Damage(key: DamageCatalog.HEALTHY, pct: pctSano, areaCm2: self.round2(areaCm2 * pctSano / 100.0)))

            // 4. Generar imágenes derivadas (crop.jpg, result.jpg, mask.png, overlay.png y overlays por lesión)
            let images = self.saveDerivedImages(
                outDir: outDir,
                origPath: origPath,
                recorte: recorteRGBA,
                detecciones: detecciones,
                lb: lb,
                severidades: severidades
            )

            let topLesion = severidades.max { $0.value < $1.value }
            let diseaseName = isHealthy ? "Sano" : DamageCatalog.get(topLesion?.key ?? DamageCatalog.SCAB).nameEs

            let analysis = Analysis(
                id: id,
                createdAt: Int64(Date().timeIntervalSince1970 * 1000),
                source: "onnx_offline",
                modelClass: isHealthy ? "Healthy" : topLesion?.key.capitalized,
                modelConfidence: self.round2(Double(maxConf > 0 ? maxConf : confFruto)),
                leafConfidence: self.round2(Double(confFruto)),
                cropName: "Gulupa",
                diseaseName: diseaseName,
                isHealthy: isHealthy,
                damages: damagesList,
                images: images,
                clsTimeMs: self.round2(tFruto),
                segTimeMs: self.round2(tLesiones),
                saved: false,
                photoWidth: fotoRGBA.width,
                photoHeight: fotoRGBA.height,
                cropSidePx: max(recorteRGBA.width, recorteRGBA.height)
            )
            self.saveAnalysisJSON(analysis, outDir: outDir)
            return analysis
            #else
            throw AnalyzerError.modelNotFound("onnxruntime_objc")
            #endif
        }.value
    }

    // MARK: - Inferencia YOLO26n-seg con Letterbox (Idéntico a ModeloSegmentacion en OnnxAnalyzer.kt)

    private struct RGBABuffer {
        var pixels: [UInt8]
        let width: Int
        let height: Int
        let image: UIImage
    }

    #if canImport(onnxruntime_objc)
    private func predecir(
        sess: ORTSession,
        rgba: RGBABuffer,
        lado: Int,
        protoSide: Int,
        confianza: Float = ConfigGulupa.confianza
    ) throws -> ([Deteccion], Letterbox) {
        let escala = min(Float(lado) / Float(rgba.height), Float(lado) / Float(rgba.width))
        let nuevoW = max(1, Int((Float(rgba.width) * escala).rounded()))
        let nuevoH = max(1, Int((Float(rgba.height) * escala).rounded()))
        let padX = (lado - nuevoW) / 2
        let padY = (lado - nuevoH) / 2
        let lb = Letterbox(lado: lado, escala: escala, padX: padX, padY: padY, ancho: rgba.width, alto: rgba.height)

        // Preparar imagen letterbox con relleno gris 114
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let size = CGSize(width: lado, height: lado)
        let lbImage = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let g = CGFloat(ConfigGulupa.rellenoLetterbox) / 255.0
            UIColor(red: g, green: g, blue: g, alpha: 1.0).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            rgba.image.draw(in: CGRect(x: padX, y: padY, width: nuevoW, height: nuevoH))
        }

        guard let lbRGBA = extractRGBA(lbImage) else {
            throw AnalyzerError.invalidImage
        }

        let plano = lado * lado
        var floats = [Float](repeating: 0, count: 3 * plano)
        for i in 0..<plano {
            let p = i * 4
            floats[i]             = Float(lbRGBA.pixels[p]) / 255.0
            floats[plano + i]     = Float(lbRGBA.pixels[p + 1]) / 255.0
            floats[2 * plano + i] = Float(lbRGBA.pixels[p + 2]) / 255.0
        }

        let tensorData = NSMutableData(bytes: &floats, length: floats.count * MemoryLayout<Float>.size)
        let shape: [NSNumber] = [1, 3, NSNumber(value: lado), NSNumber(value: lado)]
        let inputVal = try ORTValue(tensorData: tensorData, elementType: .float, shape: shape)
        let inputNames = try sess.inputNames()
        let outputNames = try sess.outputNames()
        let outputs = try sess.run(withInputs: [inputNames[0]: inputVal], outputNames: Set(outputNames), runOptions: nil)

        guard let out0 = outputs[outputNames[0]],
              let out1 = outputs[outputNames.count > 1 ? outputNames[1] : outputNames[0]],
              let detFloats = floatArray(from: out0),
              let protoFloats = floatArray(from: out1) else {
            return ([], lb)
        }

        let ph = protoSide
        let pw = protoSide
        let numRows = min(300, detFloats.count / 38)
        var detecciones: [Deteccion] = []

        for r in 0..<numRows {
            let base = r * 38
            let score = detFloats[base + 4]
            if score < confianza { continue }
            let cls = Int(detFloats[base + 5])
            let x1 = detFloats[base + 0]
            let y1 = detFloats[base + 1]
            let x2 = detFloats[base + 2]
            let y2 = detFloats[base + 3]

            var baja = [Float](repeating: 0, count: ph * pw)
            for y in 0..<ph {
                for x in 0..<pw {
                    let pIdx = y * pw + x
                    var s: Float = 0
                    for k in 0..<32 {
                        s += detFloats[base + 6 + k] * protoFloats[k * ph * pw + pIdx]
                    }
                    baja[pIdx] = 1.0 / (1.0 + exp(-s))
                }
            }

            let ox1 = CGFloat((x1 - Float(lb.padX)) / lb.escala)
            let oy1 = CGFloat((y1 - Float(lb.padY)) / lb.escala)
            let ox2 = CGFloat((x2 - Float(lb.padX)) / lb.escala)
            let oy2 = CGFloat((y2 - Float(lb.padY)) / lb.escala)

            let mascara = ampliarYRecortar(m: baja, ph: ph, pw: pw, x1: x1, y1: y1, x2: x2, y2: y2, lb: lb)
            detecciones.append(
                Deteccion(
                    clase: cls,
                    confianza: score,
                    cajaOriginal: CGRect(x: ox1, y: oy1, width: max(0, ox2 - ox1), height: max(0, oy2 - oy1)),
                    mascara: mascara
                )
            )
        }

        return (detecciones, lb)
    }

    private func ampliarYRecortar(
        m: [Float], ph: Int, pw: Int,
        x1: Float, y1: Float, x2: Float, y2: Float,
        lb: Letterbox
    ) -> [Bool] {
        let lado = lb.lado
        var salida = [Bool](repeating: false, count: lado * lado)
        let sx = Float(pw) / Float(lado)
        let sy = Float(ph) / Float(lado)
        let cx0 = max(max(0, Int(x1)), lb.padX)
        let cy0 = max(max(0, Int(y1)), lb.padY)
        let cx1 = min(min(lado, Int(ceil(x2))), lado - lb.padX)
        let cy1 = min(min(lado, Int(ceil(y2))), lado - lb.padY)
        guard cy0 < cy1 && cx0 < cx1 else { return salida }

        for v in cy0..<cy1 {
            let fy = max(0, (Float(v) + 0.5) * sy - 0.5)
            let y0 = min(ph - 1, Int(floor(fy)))
            let yb = min(ph - 1, y0 + 1)
            let dy = fy - Float(y0)
            for u in cx0..<cx1 {
                let fx = max(0, (Float(u) + 0.5) * sx - 0.5)
                let x0 = min(pw - 1, Int(floor(fx)))
                let xb = min(pw - 1, x0 + 1)
                let dx = fx - Float(x0)
                let valTop = (1 - dx) * m[y0 * pw + x0] + dx * m[y0 * pw + xb]
                let valBot = (1 - dx) * m[yb * pw + x0] + dx * m[yb * pw + xb]
                let valor = (1 - dy) * valTop + dy * valBot
                salida[v * lado + u] = valor > ConfigGulupa.umbralMascara
            }
        }
        return salida
    }

    private func recortarFruto(sess: ORTSession, foto: RGBABuffer) throws -> (RGBABuffer, Bool, Float) {
        let (dets, lb) = try predecir(sess: sess, rgba: foto, lado: ConfigGulupa.ladoFruto, protoSide: 120)
        guard let fruto = dets.filter({ $0.clase == ConfigGulupa.idFruto }).max(by: { $0.confianza < $1.confianza }) else {
            return (foto, false, 0)
        }

        let c = fruto.cajaOriginal
        let margen = Int((ConfigGulupa.margenRecorte * Float(max(c.width, c.height))).rounded())
        let left = max(0, Int(c.minX) - margen)
        let top = max(0, Int(c.minY) - margen)
        let right = min(foto.width, Int(c.maxX) + margen)
        let bottom = min(foto.height, Int(c.maxY) + margen)
        let w = max(1, right - left)
        let h = max(1, bottom - top)

        var dentro = [Bool](repeating: false, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                dentro[y * w + x] = fruto.mascara[lb.indice(x: left + x, y: top + y)]
            }
        }
        let radio = max(1, Int((ConfigGulupa.holguraFondo * Float(max(w, h))).rounded()))
        dentro = dilatar(m: dentro, w: w, h: h, radio: radio)

        var cropPixels = [UInt8](repeating: 128, count: w * h * 4)
        for y in 0..<h {
            for x in 0..<w {
                let ci = y * w + x
                let cp = ci * 4
                if dentro[ci] {
                    let op = ((top + y) * foto.width + (left + x)) * 4
                    cropPixels[cp]     = foto.pixels[op]
                    cropPixels[cp + 1] = foto.pixels[op + 1]
                    cropPixels[cp + 2] = foto.pixels[op + 2]
                    cropPixels[cp + 3] = 255
                } else {
                    cropPixels[cp]     = 128
                    cropPixels[cp + 1] = 128
                    cropPixels[cp + 2] = 128
                    cropPixels[cp + 3] = 255
                }
            }
        }

        let cropImg = makeImage(from: cropPixels, width: w, height: h) ?? foto.image
        return (RGBABuffer(pixels: cropPixels, width: w, height: h, image: cropImg), true, fruto.confianza)
    }
    #endif

    private func dilatar(m: [Bool], w: Int, h: Int, radio: Int) -> [Bool] {
        var horizontal = [Bool](repeating: false, count: w * h)
        for y in 0..<h {
            var ultimo = -radio - 1
            for x in 0..<(w + radio) {
                if x < w && m[y * w + x] { ultimo = x }
                let destino = x - radio
                if destino >= 0 && destino < w && (x - ultimo) <= 2 * radio {
                    horizontal[y * w + destino] = true
                }
            }
        }
        var salida = [Bool](repeating: false, count: w * h)
        for x in 0..<w {
            var ultimo = -radio - 1
            for y in 0..<(h + radio) {
                if y < h && horizontal[y * w + x] { ultimo = y }
                let destino = y - radio
                if destino >= 0 && destino < h && (y - ultimo) <= 2 * radio {
                    salida[destino * w + x] = true
                }
            }
        }
        return salida
    }

    private func saveDerivedImages(
        outDir: URL,
        origPath: String,
        recorte: RGBABuffer,
        detecciones: [Deteccion],
        lb: Letterbox,
        severidades: [String: Double]
    ) -> AnalysisImages {
        let w = recorte.width
        let h = recorte.height
        var resPixels = recorte.pixels
        var maskPixels = [UInt8](repeating: 0, count: w * h * 4)
        var overlayAll = [UInt8](repeating: 0, count: w * h * 4)
        var overlayScab = [UInt8](repeating: 0, count: w * h * 4)
        var overlayAnth = [UInt8](repeating: 0, count: w * h * 4)
        var overlayOil  = [UInt8](repeating: 0, count: w * h * 4)

        for i in 0..<(w * h) { maskPixels[i * 4 + 3] = 255 }

        let colores: [Int: (UInt8, UInt8, UInt8)] = [
            1: (229, 57, 53),   // Scab (Rojo)
            2: (142, 36, 170),  // Anthracnose (Morado)
            3: (251, 140, 0)    // Oil spot (Naranja)
        ]

        for d in detecciones {
            if d.clase == ConfigGulupa.idFruto { continue }
            guard let (cr, cg, cb) = colores[d.clase] else { continue }

            for y in 0..<h {
                for x in 0..<w {
                    let idxRejilla = lb.indice(x: x, y: y)
                    if d.mascara[idxRejilla] {
                        let pos = (y * w + x) * 4
                        resPixels[pos]     = UInt8((Int(resPixels[pos]) + Int(cr)) / 2)
                        resPixels[pos + 1] = UInt8((Int(resPixels[pos + 1]) + Int(cg)) / 2)
                        resPixels[pos + 2] = UInt8((Int(resPixels[pos + 2]) + Int(cb)) / 2)

                        maskPixels[pos] = 255; maskPixels[pos + 1] = 255; maskPixels[pos + 2] = 255
                        setPremul(&overlayAll, pos, r: cr, g: cg, b: cb, a: 170)
                        if d.clase == 1 { setPremul(&overlayScab, pos, r: cr, g: cg, b: cb, a: 170) }
                        if d.clase == 2 { setPremul(&overlayAnth, pos, r: cr, g: cg, b: cb, a: 170) }
                        if d.clase == 3 { setPremul(&overlayOil,  pos, r: cr, g: cg, b: cb, a: 170) }
                    }
                }
            }
        }

        let cropPath = outDir.appendingPathComponent("crop.jpg").path
        let resultPath = outDir.appendingPathComponent("result.jpg").path
        let maskPath = outDir.appendingPathComponent("mask.png").path
        let overlayPath = outDir.appendingPathComponent("overlay.png").path

        saveJPEG(recorte.image, path: cropPath, quality: 0.90)
        if let resImg = makeImage(from: resPixels, width: w, height: h) { saveJPEG(resImg, path: resultPath, quality: 0.92) }
        if let mImg = makeImage(from: maskPixels, width: w, height: h) { savePNG(mImg, path: maskPath) }
        if let ovImg = makeImage(from: overlayAll, width: w, height: h) { savePNG(ovImg, path: overlayPath) }

        var overlaysMap: [String: String] = [:]
        if (severidades[DamageCatalog.SCAB] ?? 0) > 0.05, let img = makeImage(from: overlayScab, width: w, height: h) {
            let p = outDir.appendingPathComponent("overlay_scab.png").path
            savePNG(img, path: p); overlaysMap[DamageCatalog.SCAB] = p
        }
        if (severidades[DamageCatalog.ANTHRACNOSE] ?? 0) > 0.05, let img = makeImage(from: overlayAnth, width: w, height: h) {
            let p = outDir.appendingPathComponent("overlay_anthracnose.png").path
            savePNG(img, path: p); overlaysMap[DamageCatalog.ANTHRACNOSE] = p
        }
        if (severidades[DamageCatalog.OIL_SPOT] ?? 0) > 0.05, let img = makeImage(from: overlayOil, width: w, height: h) {
            let p = outDir.appendingPathComponent("overlay_oil_spot.png").path
            savePNG(img, path: p); overlaysMap[DamageCatalog.OIL_SPOT] = p
        }

        return AnalysisImages(
            original: origPath,
            crop: cropPath,
            leaf: cropPath,
            mask: maskPath,
            overlay: overlayPath,
            overlays: overlaysMap,
            result: resultPath
        )
    }

    private func cropSubBuffer(_ src: RGBABuffer, x: Int, y: Int, w: Int, h: Int) -> RGBABuffer {
        var sub = [UInt8](repeating: 0, count: w * h * 4)
        for row in 0..<h {
            for col in 0..<w {
                let sp = ((y + row) * src.width + (x + col)) * 4
                let dp = (row * w + col) * 4
                sub[dp]     = src.pixels[sp]
                sub[dp + 1] = src.pixels[sp + 1]
                sub[dp + 2] = src.pixels[sp + 2]
                sub[dp + 3] = src.pixels[sp + 3]
            }
        }
        let img = makeImage(from: sub, width: w, height: h) ?? src.image
        return RGBABuffer(pixels: sub, width: w, height: h, image: img)
    }

    // MARK: - Modo Demostración (Idéntico a DemoAnalyzer en Analyzers.kt)

    private func runDemoAnalysis(image: UIImage, id: String, outDir: URL, areaCm2: Double) async throws -> Analysis {
        try await Task.sleep(nanoseconds: 1_800_000_000)
        let resized = resizeMaxSide(image, maxSide: 640)
        let origPath = outDir.appendingPathComponent("original.jpg").path
        saveJPEG(resized, path: origPath, quality: 0.92)

        return Analysis(
            id: id,
            createdAt: Int64(Date().timeIntervalSince1970 * 1000),
            source: Analysis.SOURCE_DEMO,
            modelClass: "Scab",
            modelConfidence: 0.92,
            leafConfidence: 0.96,
            cropName: "Gulupa",
            diseaseName: "Sarna (Roña)",
            isHealthy: false,
            damages: [
                Damage(key: DamageCatalog.SCAB, pct: 12.5, areaCm2: 4.8),
                Damage(key: DamageCatalog.ANTHRACNOSE, pct: 3.2, areaCm2: 1.2),
                Damage(key: DamageCatalog.OIL_SPOT, pct: 1.8, areaCm2: 0.7),
                Damage(key: DamageCatalog.HEALTHY, pct: 82.5, areaCm2: 31.7)
            ],
            images: AnalysisImages(original: origPath, crop: origPath, result: origPath),
            clsTimeMs: 42.0,
            segTimeMs: 68.0,
            saved: false,
            photoWidth: Int(resized.size.width),
            photoHeight: Int(resized.size.height),
            cropSidePx: Int(max(resized.size.width, resized.size.height))
        )
    }

    // MARK: - Utilidades de Imagen y Persistencia

    private func setPremul(_ buf: inout [UInt8], _ p: Int, r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
        let af = Int(a)
        buf[p]     = UInt8((Int(r) * af) / 255)
        buf[p + 1] = UInt8((Int(g) * af) / 255)
        buf[p + 2] = UInt8((Int(b) * af) / 255)
        buf[p + 3] = a
    }

    #if canImport(onnxruntime_objc)
    private func floatArray(from value: ORTValue) -> [Float]? {
        guard let data = try? value.tensorData() as Data else { return nil }
        let count = data.count / MemoryLayout<Float>.size
        return data.withUnsafeBytes { raw in
            guard let base = raw.baseAddress?.assumingMemoryBound(to: Float.self) else { return nil }
            return Array(UnsafeBufferPointer(start: base, count: count))
        }
    }
    #endif

    private func normalizeOrientation(_ image: UIImage) -> UIImage {
        if image.imageOrientation == .up { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }

    private func resizeMaxSide(_ image: UIImage, maxSide: CGFloat) -> UIImage {
        let w = image.size.width
        let h = image.size.height
        let longest = max(w, h)
        if longest <= maxSide { return image }
        let scale = maxSide / longest
        let newSize = CGSize(width: max(1, round(w * scale)), height: max(1, round(h * scale)))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    private func extractRGBA(_ image: UIImage) -> RGBABuffer? {
        guard let cg = image.cgImage else { return nil }
        let w = cg.width
        let h = cg.height
        var pixels = [UInt8](repeating: 0, count: w * h * 4)
        let cs = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &pixels,
            width: w,
            height: h,
            bitsPerComponent: 8,
            bytesPerRow: w * 4,
            space: cs,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: w, height: h))
        return RGBABuffer(pixels: pixels, width: w, height: h, image: image)
    }

    private func makeImage(from rgba: [UInt8], width: Int, height: Int) -> UIImage? {
        var copy = rgba
        let cs = CGColorSpaceCreateDeviceRGB()
        guard let ctx = CGContext(
            data: &copy,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: cs,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ), let cg = ctx.makeImage() else { return nil }
        return UIImage(cgImage: cg)
    }

    private func saveJPEG(_ image: UIImage, path: String, quality: CGFloat) {
        if let data = image.jpegData(compressionQuality: quality) {
            try? data.write(to: URL(fileURLWithPath: path))
        }
    }

    private func savePNG(_ image: UIImage, path: String) {
        if let data = image.pngData() {
            try? data.write(to: URL(fileURLWithPath: path))
        }
    }

    private func round2(_ v: Double) -> Double {
        (v * 100.0).rounded() / 100.0
    }

    func analysesDirectory() -> URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("analyses", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func saveAnalysisJSON(_ analysis: Analysis, outDir: URL? = nil) {
        let dir = outDir ?? analysesDirectory().appendingPathComponent(analysis.id, isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("analysis.json")
        if let data = try? JSONEncoder().encode(analysis) {
            try? data.write(to: file)
        }
    }

    func loadAllSavedAnalyses() -> [Analysis] {
        let root = analysesDirectory()
        guard let folders = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil) else {
            return []
        }
        var list: [Analysis] = []
        for folder in folders {
            let jsonFile = folder.appendingPathComponent("analysis.json")
            if let data = try? Data(contentsOf: jsonFile),
               let item = try? JSONDecoder().decode(Analysis.self, from: data),
               item.saved {
                list.append(item)
            }
        }
        return list.sorted { $0.createdAt > $1.createdAt }
    }

    func deleteAnalysis(id: String) {
        let dir = analysesDirectory().appendingPathComponent(id, isDirectory: true)
        try? FileManager.default.removeItem(at: dir)
    }

    func clearAllAnalyses() {
        let root = analysesDirectory()
        try? FileManager.default.removeItem(at: root)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }
}

// MARK: - Generador de Informe Técnico PDF (Idéntico a PdfReportGenerator.kt)

enum PdfReportGenerator {
    static func generateReport(analysis: Analysis, lang: AppLanguage) -> URL? {
        let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)

        let data = renderer.pdfData { context in
            context.beginPage()
            let cg = context.cgContext

            cg.setFillColor(UIColor(red: 248/255, green: 247/255, blue: 249/255, alpha: 1).cgColor)
            cg.fill(pageRect)

            cg.setFillColor(UIColor(red: 144/255, green: 52/255, blue: 189/255, alpha: 1).cgColor)
            cg.fill(CGRect(x: 0, y: 0, width: 595, height: 72))

            cg.setFillColor(UIColor(red: 0/255, green: 88/255, blue: 40/255, alpha: 1).cgColor)
            cg.fill(CGRect(x: 0, y: 72, width: 595, height: 5))

            let title = lang == .english
                ? "GULUPASCAN · TECHNICAL PHYTOSANITARY REPORT"
                : "GULUPASCAN · INFORME TÉCNICO FITOSANITARIO"
            drawText(title, x: 30, y: 24, size: 18, bold: true, color: .white)

            let df = DateFormatter()
            df.dateFormat = "dd/MM/yyyy HH:mm:ss"
            let dateStr = df.string(from: analysis.createdDate)
            let subtitle = lang == .english
                ? "Date: \(dateStr)   |   Sample ID: #\(analysis.id)   |   Crop: Purple Passion Fruit (Passiflora edulis)"
                : "Fecha: \(dateStr)   |   Muestra ID: #\(analysis.id)   |   Cultivo: Gulupa (Passiflora edulis)"
            drawText(subtitle, x: 30, y: 50, size: 9.5, bold: false, color: .white)

            let sec1 = lang == .english
                ? "PHOTOGRAPHIC EVIDENCE AND ONNX SEGMENTATION"
                : "EVIDENCIA FOTOGRÁFICA Y SEGMENTACIÓN ONNX"
            drawText(sec1, x: 30, y: 86, size: 11.5, bold: true, color: UIColor(red: 42/255, green: 26/255, blue: 34/255, alpha: 1))

            drawPhotoCard(
                cg: cg,
                title: lang == .english ? "1. ORIGINAL PHOTOGRAPH" : "1. FOTOGRAFÍA ORIGINAL",
                path: analysis.images.original,
                rect: CGRect(x: 30, y: 106, width: 260, height: 150),
                noImgText: lang == .english ? "Image unavailable" : "Imagen no disponible"
            )
            drawPhotoCard(
                cg: cg,
                title: lang == .english ? "2. SEGMENTATION AND MASKS" : "2. SEGMENTACIÓN Y MÁSCARAS",
                path: analysis.images.result ?? analysis.images.crop ?? analysis.images.original,
                rect: CGRect(x: 305, y: 106, width: 260, height: 150),
                noImgText: lang == .english ? "Image unavailable" : "Imagen no disponible"
            )

            let diagRect = CGRect(x: 30, y: 268, width: 535, height: 82)
            drawRoundCard(cg: cg, rect: diagRect)
            drawText(
                lang == .english ? "PHYTOSANITARY DIAGNOSIS AND COMMERCIAL DESTINATION" : "DIAGNÓSTICO FITOSANITARIO Y DESTINO COMERCIAL",
                x: 45, y: 278, size: 11, bold: true, color: UIColor(red: 42/255, green: 26/255, blue: 34/255, alpha: 1)
            )
            drawText(
                lang == .english ? "Classification by severity:" : "Clasificación según severidad:",
                x: 45, y: 298, size: 9.5, bold: false, color: .darkGray
            )

            let clsTitle: String
            let clsColor: UIColor
            switch analysis.classification {
            case .HEALTHY:
                clsTitle = lang == .english ? "Healthy Fruit (Extra Quality · Export Suitable)" : "Fruto Sano (Calidad Extra · Exportación Apta)"
                clsColor = UIColor(red: 46/255, green: 158/255, blue: 68/255, alpha: 1)
            case .LIGHT:
                clsTitle = lang == .english ? "Light Damage (Category I · Fresh Market / Selected Export)" : "Daño Leve (Categoría I · Mercado Fresco / Exportación con Selección)"
                clsColor = UIColor(red: 230/255, green: 120/255, blue: 0, alpha: 1)
            case .MODERATE:
                clsTitle = lang == .english ? "Moderate Damage (Category II · Agro-industrial / Pulp)" : "Daño Moderado (Categoría II · Destino Agroindustrial / Pulpa)"
                clsColor = UIColor(red: 210/255, green: 70/255, blue: 0, alpha: 1)
            case .SEVERE:
                clsTitle = lang == .english ? "Severe Damage (Phytosanitary Discard · Unfit for Fresh Market)" : "Daño Severo (Descarte Fitosanitario · No Apto Consumo Fresco)"
                clsColor = UIColor(red: 220/255, green: 40/255, blue: 40/255, alpha: 1)
            }
            drawText(clsTitle, x: 45, y: 316, size: 10.5, bold: true, color: clsColor)

            drawText(
                lang == .english ? "Total Epidermal Severity:" : "Severidad Epidérmica Total:",
                x: 380, y: 298, size: 9.5, bold: false, color: .darkGray
            )
            drawText(
                String(format: "%.2f %%", analysis.totalDamagePct),
                x: 380, y: 314, size: 17, bold: true, color: UIColor(red: 144/255, green: 52/255, blue: 189/255, alpha: 1)
            )

            drawText(
                lang == .english ? "TECHNICAL INFERENCE METRICS (ON-DEVICE RUNTIME)" : "MÉTRICAS TÉCNICAS DE INFERENCIA (ON-DEVICE RUNTIME)",
                x: 30, y: 360, size: 11.5, bold: true, color: UIColor(red: 42/255, green: 26/255, blue: 34/255, alpha: 1)
            )
            let metricsRect = CGRect(x: 30, y: 380, width: 535, height: 95)
            drawRoundCard(cg: cg, rect: metricsRect)

            let totalMs = Int(analysis.clsTimeMs + analysis.segTimeMs)
            let fps = totalMs > 0 ? String(format: "%.1f FPS", 1000.0 / Double(totalMs)) : "—"
            drawMetric(x: 45, y: 392, label: lang == .english ? "Inference Engine:" : "Motor de Inferencia:", value: analysis.isDemo ? (lang == .english ? "Simulation (Demo)" : "Simulación (Demo)") : "ONNX Runtime int8 (CPU)")
            drawMetric(x: 300, y: 392, label: lang == .english ? "Model A Time (Fruit):" : "Tiempo Modelo A (Fruto):", value: "\(Int(analysis.clsTimeMs)) ms (480x480)")
            drawMetric(x: 45, y: 412, label: lang == .english ? "Architecture:" : "Arquitectura:", value: "YOLO26n-seg Dual Pipeline")
            drawMetric(x: 300, y: 412, label: lang == .english ? "Model B Time (Lesions):" : "Tiempo Modelo B (Lesiones):", value: "\(Int(analysis.segTimeMs)) ms (640x640)")
            drawMetric(x: 45, y: 432, label: lang == .english ? "Total Execution Time:" : "Tiempo Total de Ejecución:", value: "\(totalMs) ms (\(fps))")
            drawMetric(x: 300, y: 432, label: lang == .english ? "Fruit Localization Conf.:" : "Confianza Localización Fruto:", value: "\(Int(analysis.leafConfidence * 100)) %")
            drawMetric(x: 45, y: 452, label: lang == .english ? "Damage Detection Conf.:" : "Confianza Detección Daño:", value: "\(Int(analysis.modelConfidence * 100)) %")
            drawMetric(x: 300, y: 452, label: lang == .english ? "In-App Model Size:" : "Tamaño Modelos en App:", value: lang == .english ? "Fruit: 3.1 MB | Lesions: 3.2 MB" : "Fruto: 3.1 MB | Lesiones: 3.2 MB")

            drawText(
                lang == .english ? "QUANTIFIED BREAKDOWN BY LESION TYPE" : "DESGLOSE CUANTIFICADO POR TIPO DE LESIÓN",
                x: 30, y: 488, size: 11.5, bold: true, color: UIColor(red: 42/255, green: 26/255, blue: 34/255, alpha: 1)
            )
            var tableY: CGFloat = 510
            cg.setFillColor(UIColor(red: 235/255, green: 230/255, blue: 242/255, alpha: 1).cgColor)
            cg.fill(CGRect(x: 30, y: tableY - 4, width: 535, height: 22))
            drawText(lang == .english ? "Pathogen / Damage" : "Patógeno / Daño", x: 40, y: tableY, size: 9.5, bold: true, color: .black)
            drawText(lang == .english ? "Causal Agent" : "Agente Causal", x: 180, y: tableY, size: 9.5, bold: true, color: .black)
            drawText(lang == .english ? "Severity Level" : "Nivel Severidad", x: 360, y: tableY, size: 9.5, bold: true, color: .black)
            drawText(lang == .english ? "Area (%)" : "Área (%)", x: 485, y: tableY, size: 9.5, bold: true, color: .black)

            tableY += 24
            let rows = analysis.harmfulDamages + analysis.damages.filter { $0.key == DamageCatalog.HEALTHY }
            for d in rows {
                let info = DamageCatalog.get(d.key)
                let lvl = d.key == DamageCatalog.HEALTHY ? "0 / 10" : "\(Severity.level(d.pct)) / 10"
                drawText(info.name(lang), x: 40, y: tableY, size: 9.5, bold: false, color: .darkGray)
                drawText(info.scientific ?? "—", x: 180, y: tableY, size: 9.5, bold: false, color: .gray)
                drawText(lvl, x: 360, y: tableY, size: 9.5, bold: false, color: .darkGray)
                let valColor = d.key == DamageCatalog.HEALTHY
                    ? UIColor(red: 46/255, green: 158/255, blue: 68/255, alpha: 1)
                    : UIColor(red: 210/255, green: 50/255, blue: 50/255, alpha: 1)
                drawText(String(format: "%.2f %%", d.pct), x: 485, y: tableY, size: 9.5, bold: true, color: valColor)
                tableY += 22
            }

            tableY += 12
            drawText(
                lang == .english ? "AGRONOMIC RECOMMENDATIONS AND FIELD MANAGEMENT" : "RECOMENDACIONES AGRONÓMICAS Y MANEJO EN CAMPO",
                x: 30, y: tableY, size: 11.5, bold: true, color: UIColor(red: 42/255, green: 26/255, blue: 34/255, alpha: 1)
            )
            tableY += 18
            let advRect = CGRect(x: 30, y: tableY, width: 535, height: 74)
            drawRoundCard(cg: cg, rect: advRect)

            let advice: String
            switch analysis.classification {
            case .HEALTHY:
                advice = lang == .english
                    ? "Batch in excellent phytosanitary condition. Maintain desuckering, aeration pruning, and continuous weekly monitoring to sustain export qualification."
                    : "Lote en excelente estado fitosanitario. Mantener las labores de deschuponado, podas de aireación y monitoreo continuo semanal para sostener la calificación de exportación."
            case .LIGHT:
                advice = lang == .english
                    ? "Incipient damage foci identified (< 5%). Perform selective sanitary pruning of affected leaves and tendrils. Apply preventive copper-based disinfectants or biofungicides."
                    : "Se identifican focos incipientes de daño (< 5%). Realizar podas sanitarias selectivas de hojas y zarcillos afectados. Aplicar desinfectantes preventivos a base de cobre o biofungicidas."
            case .MODERATE:
                advice = lang == .english
                    ? "Moderate severity (5-20%). Sorting for domestic market or processed pulp is suggested. Evaluate relative humidity conditions and apply curative fungicide rotation."
                    : "Severidad moderada (5-20%). Se sugiere clasificar para mercado interno o pulpa procesada. Evaluar condiciones de humedad relativa y aplicar rotación de fungicidas con acción curativa."
            case .SEVERE:
                advice = lang == .english
                    ? "Fruit with severe damage (> 20%). Phytosanitary discard for export. Remove diseased fruits from the plantation to prevent inoculum spread and consult an agronomist immediately."
                    : "Fruto con daño severo (> 20%). Descarte fitosanitario para exportación. Retirar frutos enfermos de la plantación para evitar diseminación de inóculo y consultar de inmediato a un agrónomo."
            }
            let attr: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 9.5),
                .foregroundColor: UIColor.darkGray
            ]
            (advice as NSString).draw(in: CGRect(x: 42, y: tableY + 12, width: 510, height: 54), withAttributes: attr)

            drawText(
                lang == .english
                    ? "National University of Colombia · Faculty of Agricultural Sciences · GulupaScan Edge Inference"
                    : "Universidad Nacional de Colombia · Facultad de Ciencias Agrarias · GulupaScan Inferencia Edge",
                x: 30, y: 814, size: 8.5, bold: false, color: .gray
            )
            drawText(
                lang == .english ? "Page 1 of 1 · Export Report" : "Página 1 de 1 · Reporte de Exportación",
                x: 420, y: 814, size: 8.5, bold: false, color: .gray
            )
        }

        let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("GulupaScan_Informe_\(analysis.id).pdf")
        try? data.write(to: outURL)
        return outURL
    }

    private static func drawText(_ text: String, x: CGFloat, y: CGFloat, size: CGFloat, bold: Bool, color: UIColor) {
        let font = bold ? UIFont.boldSystemFont(ofSize: size) : UIFont.systemFont(ofSize: size)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        (text as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: attrs)
    }

    private static func drawRoundCard(cg: CGContext, rect: CGRect) {
        let path = UIBezierPath(roundedRect: rect, cornerRadius: 8)
        cg.setFillColor(UIColor.white.cgColor)
        cg.addPath(path.cgPath)
        cg.fillPath()
        cg.setStrokeColor(UIColor(white: 0.88, alpha: 1).cgColor)
        cg.setLineWidth(1)
        cg.addPath(path.cgPath)
        cg.strokePath()
    }

    private static func drawPhotoCard(cg: CGContext, title: String, path: String?, rect: CGRect, noImgText: String) {
        drawRoundCard(cg: cg, rect: rect)
        drawText(title, x: rect.minX + 10, y: rect.minY + 6, size: 8.5, bold: true, color: .darkGray)
        let imgRect = CGRect(x: rect.minX + 8, y: rect.minY + 22, width: rect.width - 16, height: rect.height - 30)
        if let img = BundleImageLoader.loadFile(path) {
            img.draw(in: imgRect)
        } else {
            cg.setFillColor(UIColor(white: 0.92, alpha: 1).cgColor)
            cg.fill(imgRect)
            drawText(noImgText, x: imgRect.minX + 20, y: imgRect.midY - 6, size: 9, bold: false, color: .gray)
        }
    }

    private static func drawMetric(x: CGFloat, y: CGFloat, label: String, value: String) {
        let lFont = UIFont.systemFont(ofSize: 9)
        let lAttr: [NSAttributedString.Key: Any] = [.font: lFont, .foregroundColor: UIColor.gray]
        let lSize = (label as NSString).size(withAttributes: lAttr)
        (label as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: lAttr)

        let vFont = UIFont.boldSystemFont(ofSize: 9)
        let vAttr: [NSAttributedString.Key: Any] = [.font: vFont, .foregroundColor: UIColor.black]
        (value as NSString).draw(at: CGPoint(x: x + lSize.width + 5, y: y), withAttributes: vAttr)
    }
}
