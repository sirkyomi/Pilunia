package de.pilunia.app.reminder

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.pilunia.app.PiluniaApp
import de.pilunia.app.data.AppSettings
import de.pilunia.app.domain.Fmt
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.reminder.ReminderFlow
import de.pilunia.app.ui.theme.PiluniaTheme
import de.pilunia.app.ui.theme.isDarkMode
import java.time.LocalDate

/**
 * Vollbild-Erinnerung (08 / 08a / 08b), auch über dem Sperrbildschirm.
 * Wird von der Mitteilung als Full-Screen-Intent oder per Tippen geöffnet.
 */
class ReminderActivity : ComponentActivity() {

    private var slot by mutableStateOf(LocalDate.now() to 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        readSlot(intent)
        val container = PiluniaApp.container(this)
        setContent {
            val settings by container.settings.flow.collectAsState(initial = AppSettings(onboardingDone = true))
            PiluniaTheme(dark = isDarkMode(settings.appearance), largeText = settings.largeText) {
                CompositionLocalProvider(LocalContainer provides container) {
                    key(slot) {
                        ReminderFlow(day = slot.first, minute = slot.second, onFinish = { finishAndRemoveTask() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSlot(intent)
    }

    private fun readSlot(i: Intent) {
        slot = LocalDate.ofEpochDay(i.getLongExtra(EXTRA_DAY, Fmt.today().toEpochDay())) to i.getIntExtra(EXTRA_MINUTE, 0)
        PiluniaApp.container(this).notifier.cancelDoseReminder(slot.first, slot.second)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        const val EXTRA_DAY = "day"
        const val EXTRA_MINUTE = "minute"

        fun intent(context: Context, day: LocalDate, minute: Int): Intent =
            Intent(context, ReminderActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                .putExtra(EXTRA_DAY, day.toEpochDay())
                .putExtra(EXTRA_MINUTE, minute)
    }
}
