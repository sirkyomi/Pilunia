package de.pilunia.app.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import de.pilunia.app.AppContainer
import de.pilunia.app.ui.components.MainTab
import java.time.LocalDate

/** Zugriff auf Datenbank, Einstellungen, Wecker und Mitteilungen in allen Screens. */
val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer fehlt") }

/** Alle Ziele der Haupt-Navigation. */
object Routes {
    /** 01 Splash / Willkommen. */
    const val WELCOME = "welcome"
    /** 02–04 Onboarding inkl. Namensschritt. */
    const val ONBOARDING = "onboarding"
    /** Tabs (06 Heute, 12 Medikamente, 18/19 Verlauf, 20 Mehr). */
    const val MAIN = "main?tab={tab}"
    fun main(tab: MainTab = MainTab.HEUTE) = "main?tab=${tab.name}"

    /** 13 / 13a Medikament-Detail. */
    const val MED_DETAIL = "med/{id}"
    fun medDetail(id: Long) = "med/$id"

    /** 14 Vorrat niedrig. */
    const val STOCK_LOW = "stock/{id}"
    fun stockLow(id: Long) = "stock/$id"

    /** 15–17 Neues Medikament (id = -1) bzw. Bearbeiten; [step] 0..2 springt direkt zu einem Schritt. */
    const val WIZARD = "wizard?id={id}&step={step}"
    fun wizard(id: Long = -1, step: Int = 0) = "wizard?id=$id&step=$step"

    /** 10/11 nach „Jetzt einnehmen“ auf Heute: Einnahme bestätigt bzw. Alles erledigt. */
    const val CONFIRMED = "confirmed/{day}/{minute}"
    fun confirmed(day: LocalDate, minute: Int) = "confirmed/${day.toEpochDay()}/$minute"
    const val ALL_DONE = "alldone"

    /** 08 Erinnerung im Hauptfenster (z. B. aus der Mitteilung ohne Vollbild). */
    const val REMINDER = "reminder/{day}/{minute}"
    fun reminder(day: LocalDate, minute: Int) = "reminder/${day.toEpochDay()}/$minute"

    /** 21 Benachrichtigungen. */
    const val NOTIFICATIONS = "settings/notifications"

    /** Vollbild-Beispiele mit lokalen Beispieldaten. */
    const val REMINDER_EXAMPLE = "settings/reminder-example/{example}"
    fun reminderExample(example: String) = "settings/reminder-example/$example"

    /** Weitere Einstellungsseiten aus „Mehr“ (siehe [SettingsPage]). */
    const val SETTINGS = "settings/page/{page}"
    fun settings(page: SettingsPage) = "settings/page/${page.name}"
}

/** Unterseiten von „Mehr“, die im Design nicht als eigener Screen gezeichnet sind. */
enum class SettingsPage { QUIET_HOURS, SOUND, BACKUP, EXPORT, APPEARANCE, ABOUT, PROFILE }

/** Zurück, oder – falls es kein Ziel gibt – zu den Tabs. */
fun NavController.backOrMain() {
    if (!popBackStack()) navigate(Routes.main()) { popUpTo(0) }
}
