package co.edu.unal.gulupascan

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unal.gulupascan.data.AnalysisException
import co.edu.unal.gulupascan.data.AnalysisRepository
import co.edu.unal.gulupascan.data.AppSettings
import co.edu.unal.gulupascan.data.DemoAnalyzer
import co.edu.unal.gulupascan.data.ImageUtils
import co.edu.unal.gulupascan.data.NoFruitException
import co.edu.unal.gulupascan.data.OnnxLocalAnalyzer
import co.edu.unal.gulupascan.data.PdfReportGenerator
import co.edu.unal.gulupascan.data.RemoteAnalyzer
import co.edu.unal.gulupascan.data.SessionStore
import co.edu.unal.gulupascan.data.UserProfile
import co.edu.unal.gulupascan.domain.Analysis
import co.edu.unal.gulupascan.ui.theme.AppLanguage
import co.edu.unal.gulupascan.ui.theme.AppTheme
import co.edu.unal.gulupascan.ui.theme.withLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

sealed interface ScanState {
    data object Idle : ScanState
    data class Processing(val previewPath: String?) : ScanState
    data class Done(val analysis: Analysis) : ScanState
    data class Failed(val previewPath: String?, val message: String, val canTryDemo: Boolean) : ScanState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val session = SessionStore(app)
    private val repo = AnalysisRepository(app)
    private val onnxAnalyzer by lazy { OnnxLocalAnalyzer(app) }

    val loggedIn: StateFlow<Boolean?> = session.loggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile: StateFlow<UserProfile> = session.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserProfile())
    val settings: StateFlow<AppSettings> = session.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Tema guardado */
    val theme: StateFlow<AppTheme?> = session.settings
        .map { it.theme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Idioma seleccionado */
    val language: StateFlow<AppLanguage> = session.settings
        .map { it.language }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppLanguage.SPANISH)

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _history = MutableStateFlow<List<Analysis>>(emptyList())
    val history: StateFlow<List<Analysis>> = _history.asStateFlow()

    private var job: Job? = null
    private var lastJpeg: Pair<String, ByteArray>? = null

    init {
        viewModelScope.launch {
            repo.purgeUnsaved()
            refreshHistory()
        }
    }

    /** Analiza una foto de la cámara o de la galería. */
    fun analyze(uri: Uri, forceDemo: Boolean = false) {
        job?.cancel()
        val id = UUID.randomUUID().toString().take(8)
        _scan.value = ScanState.Processing(null)
        job = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val workDir = repo.newWorkDir(id)
            val original = File(workDir, "original.jpg")
            try {
                val jpeg = withContext(Dispatchers.IO) {
                    ImageUtils.loadAsJpeg(ctx, uri).also { original.writeBytes(it) }
                }
                lastJpeg = id to jpeg
                runAnalysis(id, jpeg, workDir, forceDemo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(original, e, forceDemo)
            }
        }
    }

    /** Reintenta la última foto en modo demostración. */
    fun retryAsDemo() {
        val (id, jpeg) = lastJpeg ?: return
        job?.cancel()
        job = viewModelScope.launch {
            val workDir = repo.newWorkDir(id)
            try {
                runAnalysis(id, jpeg, workDir, forceDemo = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(File(workDir, "original.jpg"), e, true)
            }
        }
    }

    private suspend fun runAnalysis(id: String, jpeg: ByteArray, workDir: File, forceDemo: Boolean) {
        _scan.value = ScanState.Processing(File(workDir, "original.jpg").absolutePath)
        val s = session.settings.first()
        onnxAnalyzer.resolution = s.resolution
        val analyzer = when {
            forceDemo || s.demoMode -> DemoAnalyzer()
            s.offlineMode -> onnxAnalyzer
            else -> RemoteAnalyzer(s.serverUrl)
        }
        val result = analyzer.analyze(id, jpeg, workDir)
        repo.write(result)
        _scan.value = ScanState.Done(result)
    }

    private fun fail(original: File, e: Exception, wasDemo: Boolean) {
        val ctx = localizedContext()
        val msg = when (e) {
            is NoFruitException -> ctx.getString(R.string.err_no_fruit)
            is AnalysisException -> e.message.orEmpty()
            is IOException -> ctx.getString(R.string.err_network)
            else -> ctx.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
        }
        _scan.value = ScanState.Failed(
            previewPath = original.takeIf { it.exists() }?.absolutePath,
            message = msg,
            canTryDemo = !wasDemo && lastJpeg != null && e !is NoFruitException,
        )
    }

    /** Contexto con el idioma elegido en la app, para textos generados fuera de la interfaz. */
    private fun localizedContext() = getApplication<Application>().withLanguage(language.value)

    fun cancel() {
        job?.cancel()
        _scan.value = ScanState.Idle
    }

    fun reset() {
        if (_scan.value !is ScanState.Processing) _scan.value = ScanState.Idle
    }

    fun open(analysis: Analysis) {
        job?.cancel()
        _scan.value = ScanState.Done(analysis)
    }

    fun saveCurrent() {
        val current = (_scan.value as? ScanState.Done)?.analysis ?: return
        if (current.saved) return
        val saved = current.copy(saved = true)
        _scan.value = ScanState.Done(saved)
        viewModelScope.launch {
            repo.write(saved)
            refreshHistory()
        }
    }

    fun delete(a: Analysis) = viewModelScope.launch {
        repo.delete(a.id)
        val cur = (_scan.value as? ScanState.Done)?.analysis
        if (cur?.id == a.id) _scan.value = ScanState.Idle
        refreshHistory()
    }

    suspend fun exportCsv(): File = repo.exportCsv(_history.value)

    suspend fun exportPdf(analysis: Analysis): File = PdfReportGenerator.generateReport(localizedContext(), analysis)

    fun saveImageToGallery(filePath: String?, displayName: String): Boolean {
        if (filePath == null) return false
        val file = File(filePath)
        return ImageUtils.saveImageToGallery(getApplication(), file, displayName)
    }

    private suspend fun refreshHistory() {
        _history.value = repo.saved()
    }

    // ---- Sesión y Configuración ----
    suspend fun login(email: String, pass: String) = session.login(email, pass)
    suspend fun register(name: String, email: String, pass: String) = session.register(name, email, pass)
    suspend fun hasAccount() = session.hasAccount()
    fun logout() = viewModelScope.launch { session.logout() }
    fun updateProfile(p: UserProfile) = viewModelScope.launch { session.updateProfile(p) }
    fun updateSettings(s: AppSettings) = viewModelScope.launch { session.updateSettings(s) }
    fun setTheme(t: AppTheme) = viewModelScope.launch { session.setTheme(t) }
    fun setLanguage(l: AppLanguage) = viewModelScope.launch { session.setLanguage(l) }

    override fun onCleared() {
        super.onCleared()
        onnxAnalyzer.close()
    }
}
