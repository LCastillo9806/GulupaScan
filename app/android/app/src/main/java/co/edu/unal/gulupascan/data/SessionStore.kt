package co.edu.unal.gulupascan.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.edu.unal.gulupascan.BuildConfig
import co.edu.unal.gulupascan.ui.theme.AppLanguage
import co.edu.unal.gulupascan.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "gulupascan")

data class UserProfile(
    val name: String = "",
    val email: String = "",
    val role: String = "",
    val location: String = "",
    val hectares: String = "",
    val phone: String = "",
)

data class AppSettings(
    val serverUrl: String = BuildConfig.DEFAULT_SERVER_URL,
    val demoMode: Boolean = false,
    val offlineMode: Boolean = true, // Por defecto inferencia local con ONNX
    val theme: AppTheme = AppTheme.GREEN,
    val language: AppLanguage = AppLanguage.SPANISH,
)

/**
 * Sesión y preferencias guardadas en el teléfono (DataStore).
 */
class SessionStore(private val context: Context) {

    private object K {
        val loggedIn = booleanPreferencesKey("logged_in")
        val name = stringPreferencesKey("name")
        val email = stringPreferencesKey("email")
        val pass = stringPreferencesKey("pass_hash")
        val role = stringPreferencesKey("role")
        val location = stringPreferencesKey("location")
        val hectares = stringPreferencesKey("hectares")
        val phone = stringPreferencesKey("phone")
        val server = stringPreferencesKey("server_url")
        val demo = booleanPreferencesKey("demo_mode")
        val offline = booleanPreferencesKey("offline_mode")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
    }

    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[K.loggedIn] ?: false }

    val profile: Flow<UserProfile> = context.dataStore.data.map { it.toProfile() }

    val settings: Flow<AppSettings> = context.dataStore.data.map {
        AppSettings(
            serverUrl = it[K.server]?.takeIf { s -> s.isNotBlank() } ?: BuildConfig.DEFAULT_SERVER_URL,
            demoMode = it[K.demo] ?: false,
            offlineMode = it[K.offline] ?: true,
            theme = AppTheme.from(it[K.theme]),
            language = AppLanguage.from(it[K.language]),
        )
    }

    suspend fun hasAccount(): Boolean = context.dataStore.data.first()[K.email] != null

    suspend fun register(name: String, email: String, password: String) {
        context.dataStore.edit {
            it[K.name] = name.trim()
            it[K.email] = email.trim().lowercase()
            it[K.pass] = hash(email.trim().lowercase(), password)
            it[K.loggedIn] = true
        }
    }

    /** true si las credenciales coinciden con la cuenta registrada en el teléfono. */
    suspend fun login(email: String, password: String): Boolean {
        val prefs = context.dataStore.data.first()
        val e = email.trim().lowercase()
        val ok = prefs[K.email] == e && prefs[K.pass] == hash(e, password)
        if (ok) context.dataStore.edit { it[K.loggedIn] = true }
        return ok
    }

    suspend fun logout() = context.dataStore.edit { it[K.loggedIn] = false }

    suspend fun updateProfile(p: UserProfile) = context.dataStore.edit {
        it[K.name] = p.name
        it[K.role] = p.role
        it[K.location] = p.location
        it[K.hectares] = p.hectares
        it[K.phone] = p.phone
    }

    suspend fun updateSettings(s: AppSettings) = context.dataStore.edit {
        it[K.server] = s.serverUrl.trim()
        it[K.demo] = s.demoMode
        it[K.offline] = s.offlineMode
        it[K.theme] = s.theme.id
        it[K.language] = s.language.code
    }

    suspend fun setTheme(t: AppTheme) = context.dataStore.edit { it[K.theme] = t.id }

    suspend fun setLanguage(l: AppLanguage) = context.dataStore.edit { it[K.language] = l.code }

    private fun Preferences.toProfile() = UserProfile(
        name = this[K.name].orEmpty(),
        email = this[K.email].orEmpty(),
        role = this[K.role].orEmpty(),
        location = this[K.location].orEmpty(),
        hectares = this[K.hectares].orEmpty(),
        phone = this[K.phone].orEmpty(),
    )

    private fun hash(email: String, password: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("gulupascan:$email:$password".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
