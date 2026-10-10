package de.pilunia.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class AppearanceMode(val label: String) { SYSTEM("Wie das System"), LIGHT("Hell"), DARK("Dunkel") }

/** Erinnerungstöne (Systemklänge werden zusätzlich über den Kanal-Dialog von Android angeboten). */
@Serializable
enum class ReminderSound(val label: String) { GLOECKCHEN("Glöckchen"), SYSTEM("Systemton"), LEISE("Lautlos") }

@Serializable
data class AppSettings(
    val onboardingDone: Boolean = false,
    val userName: String = "",
    /** „Einnahme-Erinnerungen“ – zur geplanten Uhrzeit. */
    val remindersEnabled: Boolean = true,
    /** „Vollbild-Erinnerung“ – auch auf dem Sperrbildschirm. */
    val fullScreenEnabled: Boolean = true,
    /** „Erneut erinnern“ – Abstand in Minuten. */
    val repeatIntervalMin: Int = 15,
    /** „Erneut erinnern“ – höchstens so oft. */
    val repeatMaxCount: Int = 3,
    val sound: ReminderSound = ReminderSound.GLOECKCHEN,
    val vibration: Boolean = true,
    /** Ruhezeiten: nur leise Hinweise. */
    val quietEnabled: Boolean = true,
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 6 * 60,
    /** „Vorratswarnung“ global. */
    val stockWarningEnabled: Boolean = true,
    /** „Tagesabschluss“ – täglich nach der letzten Einnahme. */
    val dailySummary: Boolean = true,
    /** „Wochenbericht“ – sonntags um 18:00. */
    val weeklyReport: Boolean = false,
    val appearance: AppearanceMode = AppearanceMode.SYSTEM,
    val largeText: Boolean = false,
    /** Zeitpunkt der letzten Sicherung (epoch millis) oder 0. */
    val lastBackupAt: Long = 0,
    /** Tag (epochDay), an dem „Alles erledigt“ zuletzt gezeigt wurde. */
    val lastDayDoneShown: Long = 0,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val onboardingDone = booleanPreferencesKey("onboardingDone")
        val userName = stringPreferencesKey("userName")
        val remindersEnabled = booleanPreferencesKey("remindersEnabled")
        val fullScreenEnabled = booleanPreferencesKey("fullScreenEnabled")
        val repeatIntervalMin = intPreferencesKey("repeatIntervalMin")
        val repeatMaxCount = intPreferencesKey("repeatMaxCount")
        val sound = stringPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val quietEnabled = booleanPreferencesKey("quietEnabled")
        val quietStartMin = intPreferencesKey("quietStartMin")
        val quietEndMin = intPreferencesKey("quietEndMin")
        val stockWarningEnabled = booleanPreferencesKey("stockWarningEnabled")
        val dailySummary = booleanPreferencesKey("dailySummary")
        val weeklyReport = booleanPreferencesKey("weeklyReport")
        val appearance = stringPreferencesKey("appearance")
        val largeText = booleanPreferencesKey("largeText")
        val lastBackupAt = longPreferencesKey("lastBackupAt")
        val lastDayDoneShown = longPreferencesKey("lastDayDoneShown")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> read(p) }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val next = transform(read(p))
            write(p, next)
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[K.onboardingDone] ?: d.onboardingDone,
            userName = p[K.userName] ?: d.userName,
            remindersEnabled = p[K.remindersEnabled] ?: d.remindersEnabled,
            fullScreenEnabled = p[K.fullScreenEnabled] ?: d.fullScreenEnabled,
            repeatIntervalMin = p[K.repeatIntervalMin] ?: d.repeatIntervalMin,
            repeatMaxCount = p[K.repeatMaxCount] ?: d.repeatMaxCount,
            sound = p[K.sound]?.let { runCatching { ReminderSound.valueOf(it) }.getOrNull() } ?: d.sound,
            vibration = p[K.vibration] ?: d.vibration,
            quietEnabled = p[K.quietEnabled] ?: d.quietEnabled,
            quietStartMin = p[K.quietStartMin] ?: d.quietStartMin,
            quietEndMin = p[K.quietEndMin] ?: d.quietEndMin,
            stockWarningEnabled = p[K.stockWarningEnabled] ?: d.stockWarningEnabled,
            dailySummary = p[K.dailySummary] ?: d.dailySummary,
            weeklyReport = p[K.weeklyReport] ?: d.weeklyReport,
            appearance = p[K.appearance]?.let { runCatching { AppearanceMode.valueOf(it) }.getOrNull() } ?: d.appearance,
            largeText = p[K.largeText] ?: d.largeText,
            lastBackupAt = p[K.lastBackupAt] ?: d.lastBackupAt,
            lastDayDoneShown = p[K.lastDayDoneShown] ?: d.lastDayDoneShown,
        )
    }

    private fun write(p: MutablePreferences, s: AppSettings) {
        p[K.onboardingDone] = s.onboardingDone
        p[K.userName] = s.userName
        p[K.remindersEnabled] = s.remindersEnabled
        p[K.fullScreenEnabled] = s.fullScreenEnabled
        p[K.repeatIntervalMin] = s.repeatIntervalMin
        p[K.repeatMaxCount] = s.repeatMaxCount
        p[K.sound] = s.sound.name
        p[K.vibration] = s.vibration
        p[K.quietEnabled] = s.quietEnabled
        p[K.quietStartMin] = s.quietStartMin
        p[K.quietEndMin] = s.quietEndMin
        p[K.stockWarningEnabled] = s.stockWarningEnabled
        p[K.dailySummary] = s.dailySummary
        p[K.weeklyReport] = s.weeklyReport
        p[K.appearance] = s.appearance.name
        p[K.largeText] = s.largeText
        p[K.lastBackupAt] = s.lastBackupAt
        p[K.lastDayDoneShown] = s.lastDayDoneShown
    }
}
