package co.edu.unal.gulupascan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import co.edu.unal.gulupascan.AppViewModel
import co.edu.unal.gulupascan.R
import co.edu.unal.gulupascan.ui.components.AnimatedBrandLockup
import co.edu.unal.gulupascan.ui.components.BrandPalette
import co.edu.unal.gulupascan.ui.components.BrandVariants
import co.edu.unal.gulupascan.ui.components.GulupaLogo
import co.edu.unal.gulupascan.ui.components.OrganicBlobs
import co.edu.unal.gulupascan.ui.components.PrimaryButton
import co.edu.unal.gulupascan.ui.components.Wordmark
import co.edu.unal.gulupascan.ui.theme.Brand
import co.edu.unal.gulupascan.ui.theme.LocalBrand
import co.edu.unal.gulupascan.ui.theme.TextSoft
import kotlinx.coroutines.launch

/**
 * Paso 1 "Abrir app": transición de colores de la identidad (las imágenes del logo una tras otra)
 * y termina en la versión del tema activo.
 */
@Composable
fun SplashScreen(loggedIn: Boolean?, onFinish: (loggedIn: Boolean) -> Unit) {
    var animationDone by remember { mutableStateOf(false) }
    val final = if (LocalBrand.current.decorated) BrandVariants.PurpleYellow else BrandVariants.GreenYellow
    val sequence = remember(final) {
        listOf(BrandVariants.LightGreen, BrandVariants.LightYellow, BrandVariants.PurpleGreen, final).distinct()
    }
    LaunchedEffect(animationDone, loggedIn) {
        if (animationDone && loggedIn != null) onFinish(loggedIn)
    }
    AnimatedBrandLockup(
        Modifier.fillMaxSize(),
        sequence = sequence,
        loop = false,
        stepMillis = 650,
        logoSize = 150.dp,
        wordHeight = 44.dp,
        onFinished = { animationDone = true },
    )
}

/** Pantalla "Iniciar sesión" y "Regístrate" (lámina 2 del mockup). */
@Composable
fun AuthScreen(vm: AppViewModel, onLoggedIn: () -> Unit) {
    var registerMode by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgot by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val errFill = stringResource(R.string.auth_err_fill)
    val errEmail = stringResource(R.string.auth_err_email)
    val errPass = stringResource(R.string.auth_err_pass_len)
    val errLogin = stringResource(R.string.auth_err_login)
    val errNoAccount = stringResource(R.string.auth_err_no_account)

    LaunchedEffect(Unit) { if (!vm.hasAccount()) registerMode = true }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        OrganicBlobs(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState()).imePadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            GulupaLogo(size = 96.dp)
            Spacer(Modifier.height(8.dp))
            Wordmark(height = 34.dp, gulupaColor = BrandPalette.Plum, scanColor = BrandPalette.Green)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (registerMode) R.string.auth_register_sub else R.string.auth_login_sub),
                color = TextSoft, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            val fieldShape = RoundedCornerShape(10.dp)
            if (registerMode) {
                Label(stringResource(R.string.auth_name))
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape,
                    placeholder = { Text(stringResource(R.string.auth_name)) })
                Spacer(Modifier.height(14.dp))
            }
            Label(stringResource(R.string.auth_email))
            OutlinedTextField(
                email, { email = it }, Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape,
                placeholder = { Text(stringResource(R.string.auth_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Spacer(Modifier.height(14.dp))
            Label(stringResource(R.string.auth_password))
            OutlinedTextField(
                pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, shape = fieldShape,
                placeholder = { Text(stringResource(R.string.auth_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))

            PrimaryButton(
                stringResource(if (registerMode) R.string.auth_register_btn else R.string.auth_login_btn),
                onClick = {
                    error = when {
                        email.isBlank() || pass.isBlank() || (registerMode && name.isBlank()) -> errFill
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> errEmail
                        registerMode && pass.length < 6 -> errPass
                        else -> null
                    }
                    if (error == null) scope.launch {
                        if (registerMode) {
                            vm.register(name, email, pass); onLoggedIn()
                        } else if (!vm.hasAccount()) {
                            error = errNoAccount
                        } else if (vm.login(email, pass)) {
                            onLoggedIn()
                        } else {
                            error = errLogin
                        }
                    }
                },
            )
            if (!registerMode) {
                TextButton(onClick = { showForgot = true }) {
                    Text(stringResource(R.string.auth_forgot), color = Brand, textDecoration = TextDecoration.Underline)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(stringResource(if (registerMode) R.string.auth_have_account else R.string.auth_no_account), color = TextSoft)
                TextButton(onClick = { registerMode = !registerMode; error = null }) {
                    Text(
                        stringResource(if (registerMode) R.string.auth_login_btn else R.string.auth_register_link),
                        color = Brand, textDecoration = TextDecoration.Underline,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.auth_local_note), style = MaterialTheme.typography.bodySmall, color = TextSoft, textAlign = TextAlign.Center)
        }
    }

    if (showForgot) {
        AlertDialog(
            onDismissRequest = { showForgot = false },
            confirmButton = { TextButton(onClick = { showForgot = false }) { Text(stringResource(R.string.ok)) } },
            title = { Text(stringResource(R.string.auth_forgot)) },
            text = { Text(stringResource(R.string.auth_forgot_body)) },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, Modifier.fillMaxWidth().padding(bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground,
    )
}
