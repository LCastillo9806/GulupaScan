package co.edu.unal.gulupascan

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import co.edu.unal.gulupascan.ui.screens.AboutScreen
import co.edu.unal.gulupascan.ui.screens.AlertsScreen
import co.edu.unal.gulupascan.ui.screens.AuthScreen
import co.edu.unal.gulupascan.ui.screens.CameraScreen
import co.edu.unal.gulupascan.ui.screens.DamageDetailScreen
import co.edu.unal.gulupascan.ui.screens.DamageListScreen
import co.edu.unal.gulupascan.ui.screens.HistoryScreen
import co.edu.unal.gulupascan.ui.screens.ProcessingScreen
import co.edu.unal.gulupascan.ui.screens.ProfileScreen
import co.edu.unal.gulupascan.ui.screens.ResultScreen
import co.edu.unal.gulupascan.ui.screens.SettingsScreen
import co.edu.unal.gulupascan.ui.screens.SplashScreen
import co.edu.unal.gulupascan.ui.screens.TechDetailsScreen
import co.edu.unal.gulupascan.ui.theme.AppLanguage
import co.edu.unal.gulupascan.ui.theme.BrandDark
import co.edu.unal.gulupascan.ui.theme.BrandSoft
import co.edu.unal.gulupascan.ui.theme.GulupaScanTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val theme by vm.theme.collectAsStateWithLifecycle()
            val language by vm.language.collectAsStateWithLifecycle()

            val t = theme
            if (t == null) {
                Box(Modifier.fillMaxSize())
            } else {
                val localizedContext = rememberLocalizedContext(LocalContext.current, language)
                CompositionLocalProvider(LocalContext provides localizedContext) {
                    GulupaScanTheme(t) {
                        val bar = BrandDark.toArgb()
                        SideEffect {
                            window.statusBarColor = bar
                            window.navigationBarColor = bar
                        }
                        GulupaScanApp(vm)
                    }
                }
            }
        }
    }

    private fun rememberLocalizedContext(base: Context, language: AppLanguage): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}

private object Routes {
    const val SPLASH = "splash"
    const val AUTH = "auth"
    const val SCAN = "scan"
    const val PROCESSING = "processing"
    const val DAMAGES = "damages"
    const val DAMAGE = "damage/{key}"
    const val RESULT = "result"
    const val TECH = "tech"
    const val HISTORY = "history"
    const val ALERTS = "alerts"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    fun damage(key: String) = "damage/$key"
}

private data class Tab(val route: String, val icon: ImageVector, val label: Int)

private val tabs = listOf(
    Tab(Routes.SCAN, Icons.Outlined.PhotoCamera, R.string.tab_scan),
    Tab(Routes.HISTORY, Icons.Outlined.History, R.string.tab_history),
    Tab(Routes.ALERTS, Icons.Outlined.Notifications, R.string.tab_alerts),
    Tab(Routes.PROFILE, Icons.Outlined.Person, R.string.tab_profile),
)

@Composable
fun GulupaScanApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    LaunchedEffect(scan, currentRoute) {
        if (scan is ScanState.Done && currentRoute == Routes.PROCESSING) {
            nav.navigate(Routes.DAMAGES) { popUpTo(Routes.PROCESSING) { inclusive = true } }
        }
    }

    val current = (scan as? ScanState.Done)?.analysis
    fun openAnalysis(a: co.edu.unal.gulupascan.domain.Analysis) {
        vm.open(a); nav.navigate(Routes.RESULT)
    }

    Scaffold(
        bottomBar = { if (showBar) BottomBar(nav, currentRoute) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = Routes.SPLASH) {
                composable(Routes.SPLASH) {
                    SplashScreen(loggedIn) { ok ->
                        nav.navigate(if (ok) Routes.SCAN else Routes.AUTH) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                }
                composable(Routes.AUTH) {
                    AuthScreen(vm) { nav.navigate(Routes.SCAN) { popUpTo(Routes.AUTH) { inclusive = true } } }
                }
                composable(Routes.SCAN) {
                    CameraScreen { uri -> vm.analyze(uri); nav.navigate(Routes.PROCESSING) }
                }
                composable(Routes.PROCESSING) {
                    ProcessingScreen(
                        state = scan,
                        onCancel = { vm.cancel(); nav.popBackStack() },
                        onRetry = { vm.reset(); nav.popBackStack() },
                        onTryDemo = { vm.retryAsDemo() },
                    )
                }
                composable(Routes.DAMAGES) {
                    current?.let { a ->
                        DamageListScreen(a, onBack = { nav.popBackStack() },
                            onDamage = { nav.navigate(Routes.damage(it)) },
                            onResult = { nav.navigate(Routes.RESULT) })
                    } ?: MissingAnalysis(nav)
                }
                composable(Routes.DAMAGE, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                    current?.let { a ->
                        DamageDetailScreen(a, entry.arguments?.getString("key").orEmpty()) { nav.popBackStack() }
                    } ?: MissingAnalysis(nav)
                }
                composable(Routes.RESULT) {
                    current?.let { a ->
                        ResultScreen(
                            a,
                            onBack = { nav.popBackStack() },
                            onDamage = { nav.navigate(Routes.damage(it)) },
                            onSave = { vm.saveCurrent() },
                            onTech = { nav.navigate(Routes.TECH) },
                            onExportPdf = { vm.exportPdf(a) },
                            onNewScan = {
                                vm.reset()
                                if (!nav.popBackStack(Routes.SCAN, inclusive = false)) {
                                    nav.navigate(Routes.SCAN) { popUpTo(nav.graph.id) { inclusive = true } }
                                }
                            },
                        )
                    } ?: MissingAnalysis(nav)
                }
                composable(Routes.TECH) {
                    current?.let { TechDetailsScreen(it) { nav.popBackStack() } } ?: MissingAnalysis(nav)
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(vm, history, onOpen = ::openAnalysis, onBack = null)
                }
                composable(Routes.ALERTS) {
                    AlertsScreen(history, onOpen = ::openAnalysis, onBack = null)
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        profile,
                        onHistory = { nav.navigate(Routes.HISTORY) },
                        onAlerts = { nav.navigate(Routes.ALERTS) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                        onAbout = { nav.navigate(Routes.ABOUT) },
                        onLogout = {
                            vm.logout()
                            nav.navigate(Routes.AUTH) { popUpTo(nav.graph.id) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        profile = profile,
                        settings = settings,
                        onTheme = { vm.setTheme(it) },
                        onLanguage = { vm.setLanguage(it) },
                        onSave = { p, s -> vm.updateProfile(p); vm.updateSettings(s); nav.popBackStack() },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.ABOUT) { AboutScreen { nav.popBackStack() } }
            }
        }
    }
}

@Composable
private fun MissingAnalysis(nav: NavHostController) {
    LaunchedEffect(Unit) { nav.navigate(Routes.SCAN) { popUpTo(nav.graph.id) { inclusive = true } } }
}

@Composable
private fun BottomBar(nav: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) nav.navigate(tab.route) {
                        popUpTo(Routes.SCAN) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandDark,
                    selectedTextColor = BrandDark,
                    indicatorColor = BrandSoft,
                ),
            )
        }
    }
}
