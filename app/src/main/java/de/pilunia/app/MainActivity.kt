package de.pilunia.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.PiluniaNavHost
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.components.MainTab
import de.pilunia.app.ui.theme.PiluniaTheme
import de.pilunia.app.ui.theme.isDarkMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /** Ziel aus einer Mitteilung (Vorrat niedrig, Erinnerung), das nach dem Start geöffnet wird. */
    private val pendingRoute = MutableStateFlow<String?>(null)
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        val container = PiluniaApp.container(this)
        handleIntent(intent)

        lifecycleScope.launch {
            val initial = container.settings.flow.first()
            ready = true
            val start = if (initial.onboardingDone) Routes.main() else Routes.WELCOME
            setContent {
                val settings by container.settings.flow.collectAsState(initial = initial)
                val dark = isDarkMode(settings.appearance)
                LaunchedEffect(dark) { applySystemBars(dark) }
                PiluniaTheme(dark = dark, largeText = settings.largeText) {
                    CompositionLocalProvider(LocalContainer provides container) {
                        val nav = rememberNavController()
                        PiluniaNavHost(nav, start)
                        val route by pendingRoute.collectAsState()
                        LaunchedEffect(route) {
                            val r = route ?: return@LaunchedEffect
                            if (settings.onboardingDone) nav.navigate(r) { launchSingleTop = true }
                            pendingRoute.value = null
                        }
                    }
                }
            }
        }
    }

    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Rechte können sich in den Systemeinstellungen geändert haben → neu planen.
        val c = PiluniaApp.container(this)
        c.scope.launch { c.reschedule() }
    }

    private fun handleIntent(intent: Intent?) {
        val i = intent ?: return
        when {
            i.hasExtra(EXTRA_STOCK_MED) -> pendingRoute.value = Routes.stockLow(i.getLongExtra(EXTRA_STOCK_MED, 0))
            i.hasExtra(EXTRA_REMINDER_DAY) -> pendingRoute.value = Routes.reminder(
                LocalDate.ofEpochDay(i.getLongExtra(EXTRA_REMINDER_DAY, 0)), i.getIntExtra(EXTRA_REMINDER_MINUTE, 0),
            )
            i.getStringExtra(EXTRA_TAB) != null -> pendingRoute.value =
                Routes.main(runCatching { MainTab.valueOf(i.getStringExtra(EXTRA_TAB)!!) }.getOrDefault(MainTab.HEUTE))
        }
    }

    companion object {
        const val EXTRA_STOCK_MED = "stockMed"
        const val EXTRA_REMINDER_DAY = "reminderDay"
        const val EXTRA_REMINDER_MINUTE = "reminderMinute"
        const val EXTRA_TAB = "tab"

        fun stockIntent(context: Context, medId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_STOCK_MED, medId)
    }
}
