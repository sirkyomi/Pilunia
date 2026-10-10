package de.pilunia.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import de.pilunia.app.data.AppSettings
import de.pilunia.app.data.Intake
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Snooze
import de.pilunia.app.ui.nav.LocalContainer
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Aktuelle Uhrzeit, die sich zum Minutenwechsel aktualisiert (für „in 8 Min.“, Tageswechsel usw.). */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val now = LocalDateTime.now()
        value = now
        delay(((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(500))
    }
}

/** Alle Medikamente mit Dosen (null, solange noch geladen wird). */
@Composable
fun rememberMeds(): State<List<MedWithDoses>?> {
    val repo = LocalContainer.current.repository
    val flow = remember(repo) { repo.observeMeds() }
    return flow.collectAsState(initial = null)
}

/** Alle Einnahmen (der Verlauf ist klein genug, um ihn komplett zu beobachten). */
@Composable
fun rememberIntakes(): State<List<Intake>> {
    val repo = LocalContainer.current.repository
    val flow = remember(repo) { repo.observeIntakes() }
    return flow.collectAsState(initial = emptyList())
}

@Composable
fun rememberSnoozes(): State<List<Snooze>> {
    val repo = LocalContainer.current.repository
    val flow = remember(repo) { repo.observeSnoozes() }
    return flow.collectAsState(initial = emptyList())
}

@Composable
fun rememberSettings(): State<AppSettings> {
    val store = LocalContainer.current.settings
    return store.flow.collectAsState(initial = AppSettings(onboardingDone = true))
}
