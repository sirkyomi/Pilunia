package de.pilunia.app

import android.app.Application
import android.content.Context
import de.pilunia.app.ui.theme.Fonts
import de.pilunia.app.data.PiluniaDatabase
import de.pilunia.app.data.PiluniaRepository
import de.pilunia.app.data.SettingsStore
import de.pilunia.app.reminder.Notifier
import de.pilunia.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Einfache Abhängigkeits-Sammlung der App. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db: PiluniaDatabase = PiluniaDatabase.create(app)
    val settings = SettingsStore(app)
    val scheduler = ReminderScheduler(app)
    val notifier = Notifier(app)
    val repository: PiluniaRepository = PiluniaRepository(db, settings) { reschedule() }

    suspend fun reschedule(): Unit = scheduler.rescheduleAll(repository)
}

class PiluniaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Fonts.init(assets)
        container = AppContainer(this)
        container.scope.launch {
            container.repository.cleanupOldSnoozes()
            container.reschedule()
        }
    }

    companion object {
        fun container(context: Context): AppContainer = (context.applicationContext as PiluniaApp).container
    }
}
