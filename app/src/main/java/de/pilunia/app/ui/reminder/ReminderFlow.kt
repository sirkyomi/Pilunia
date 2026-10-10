package de.pilunia.app.ui.reminder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.streaks
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.theme.Pilunia
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Stage { REMIND, CONFIRMED, DONE }

/** Sanfter Übergang 08 → 10 → 11: Einblenden mit leichtem Heranzoomen. */
@Composable
private fun StageContent(stage: Stage, content: @Composable (Stage) -> Unit) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(380, delayMillis = 60)) + scaleIn(tween(380, delayMillis = 60), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(200)))
        },
        label = "reminderStage",
    ) { content(it) }
}

/** 08 / 08a / 08b → 09 (Sheet) / 10 → 11. */
@Composable
fun ReminderFlow(day: LocalDate, minute: Int, onFinish: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val data = rememberSlotData(day, minute)
    val finish by rememberUpdatedState(onFinish)

    var stage by rememberSaveable { mutableStateOf(Stage.REMIND) }
    /** In diesem Ablauf bestätigte Einnahmen (für 10 und „Rückgängig machen“). */
    var session by remember { mutableStateOf(emptySet<OccKey>()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var showSnooze by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    // Beim ersten Laden: nichts zu tun → schließen; schon alles genommen → direkt 10.
    LaunchedEffect(data != null) {
        val d = data ?: return@LaunchedEffect
        if (initialized) {
            if (session.isEmpty() && stage == Stage.CONFIRMED) session = d.slot.filter { it.isTaken }.map { it.key }.toSet()
            return@LaunchedEffect
        }
        initialized = true
        when {
            d.slot.isEmpty() -> finish()
            d.slot.all { it.isTaken } -> {
                session = d.slot.map { it.key }.toSet()
                stage = Stage.CONFIRMED
            }
        }
    }

    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        container.notifier.cancelDoseReminder(day, minute)
        scope.launch {
            try { block() } finally { busy = false }
        }
    }

    suspend fun nextAfterDone() {
        if (day == Fmt.today() && isDayDone(repo, day)) stage = Stage.DONE else finish()
    }

    val c = Pilunia.colors
    Box(Modifier.fillMaxSize().screenBackground(c, fadeEnd = 0.6f)) {
        if (data == null || !initialized) return@Box
        StageContent(stage) { s ->
            when (s) {
                Stage.REMIND -> RemindScreen(
                    data, minute, busy,
                    RemindActions(
                        onClose = { finish() },
                        onConfirmAll = {
                            val open = data.slot.filter { it.isOpenForReminder }
                            act {
                                if (open.isNotEmpty()) repo.takeAll(open)
                                session = session + open.map { it.key }
                                if (session.isEmpty()) session = data.slot.filter { it.isTaken }.map { it.key }.toSet()
                                stage = Stage.CONFIRMED
                            }
                        },
                        onTakeOne = { o ->
                            act {
                                repo.take(o, amount = 1)
                                session = session + o.key
                                stage = Stage.CONFIRMED
                            }
                        },
                        onToggle = { o ->
                            act {
                                if (o.isTaken) {
                                    repo.undo(o)
                                    session = session - o.key
                                } else {
                                    repo.take(o)
                                    session = session + o.key
                                }
                            }
                        },
                        onSnooze = { showSnooze = true },
                        onSkip = {
                            val open = data.slot.filter { it.isOpenForReminder }
                            act {
                                if (open.isNotEmpty()) repo.skip(open)
                                nextAfterDone()
                            }
                        },
                    ),
                )
                Stage.CONFIRMED -> {
                    val shown = data.slot.filter { it.isTaken && it.key in session }
                        .ifEmpty { data.slot.filter { it.isTaken } }
                    ConfirmedScreen(
                        data, shown, busy,
                        onClose = { finish() },
                        onDone = { act { nextAfterDone() } },
                        onUndo = {
                            val undo = data.slot.filter { it.key in session && it.intake != null }
                            act {
                                undo.forEach { repo.undo(it) }
                                session = emptySet()
                                stage = Stage.REMIND
                            }
                        },
                    )
                }
                Stage.DONE -> AllDone(data, day, onFinish = { finish() })
            }
        }
        if (showSnooze && stage == Stage.REMIND) {
            SnoozeSheet(day, minute, onDismiss = { showSnooze = false }, onSnoozed = {
                showSnooze = false
                finish()
            })
        }
    }
}

/** Startet bei 10 „Einnahme bestätigt“ (nach „Jetzt einnehmen“ auf Heute). */
@Composable
fun ConfirmedFlow(day: LocalDate, minute: Int, onFinish: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val data = rememberSlotData(day, minute)
    val finish by rememberUpdatedState(onFinish)

    var stage by rememberSaveable { mutableStateOf(Stage.CONFIRMED) }
    var busy by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val taken: List<Occurrence> = data?.slot?.filter { it.isTaken }.orEmpty()

    // Die Bestätigung auf Heute kann noch unterwegs sein – kurz warten, sonst schließen.
    LaunchedEffect(data != null, taken.isEmpty()) {
        if (data != null && taken.isEmpty() && stage == Stage.CONFIRMED && !finished) {
            delay(1500)
            finished = true
            finish()
        }
    }

    fun act(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        container.notifier.cancelDoseReminder(day, minute)
        scope.launch {
            try { block() } finally { busy = false }
        }
    }

    val c = Pilunia.colors
    Box(Modifier.fillMaxSize().screenBackground(c, fadeEnd = 0.6f, tint = androidx.compose.ui.graphics.Color(0xFF7FD3A6), alpha = 0x40 / 255f)) {
        if (data == null || (taken.isEmpty() && stage == Stage.CONFIRMED)) return@Box
        StageContent(stage) { s ->
            when (s) {
                Stage.DONE -> AllDone(data, day, onFinish = { finish() })
                else -> ConfirmedScreen(
                    data, taken, busy,
                    onClose = { finish() },
                    onDone = {
                        act {
                            if (day == Fmt.today() && isDayDone(repo, day)) stage = Stage.DONE
                            else { finished = true; finish() }
                        }
                    },
                    onUndo = {
                        val undo = taken
                        act {
                            finished = true
                            undo.forEach { repo.undo(it) }
                            finish()
                        }
                    },
                )
            }
        }
    }
}

/** 11 mit Name, Serie und Merker „heute gezeigt“. */
@Composable
private fun AllDone(data: SlotData, day: LocalDate, onFinish: () -> Unit) {
    val container = LocalContainer.current
    val settings by rememberSettings()
    val now by rememberNow()
    val streak = remember(data.meds, data.intakes) { streaks(data.meds, data.intakes).current }
    LaunchedEffect(day) {
        container.settings.update { it.copy(lastDayDoneShown = day.toEpochDay()) }
    }
    AllDoneScreen(data, settings.userName, streak, now, onClose = onFinish, onDone = onFinish)
}
