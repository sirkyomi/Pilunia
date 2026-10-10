package de.pilunia.app.ui.meds

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import de.pilunia.app.data.Dose
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.nav.backOrMain
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch

/**
 * 15–17 Assistent „Neues Medikament“ (id = −1) bzw. „Medikament bearbeiten“.
 * [step] 0..2 springt direkt zu einem Schritt.
 */
@Composable
fun MedWizardScreen(nav: NavController, id: Long, step: Int) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val repo = container.repository
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settings by rememberSettings()
    val intakes by rememberIntakes()
    val isNew = id == -1L

    var current by rememberSaveable { mutableIntStateOf(step.coerceIn(0, 2)) }
    var base by rememberSaveable(stateSaver = WizardMedSaver) { mutableStateOf<Medication?>(null) }
    var initial by rememberSaveable(stateSaver = WizardDraftSaver) { mutableStateOf<WizardDraft?>(null) }
    var draft by rememberSaveable(stateSaver = WizardDraftSaver) { mutableStateOf<WizardDraft?>(null) }
    /** Solange der Vorrat nicht angefasst wurde, folgt er bei neuen Medikamenten der Packungsgröße. */
    var stockTouched by rememberSaveable { mutableStateOf(!isNew) }
    var saving by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }

    var rhythmReq by remember { mutableStateOf<WizardRhythmRequest?>(null) }
    var doseReq by remember { mutableStateOf<WizardDoseRequest?>(null) }
    var datesReq by remember { mutableStateOf<WizardDatesRequest?>(null) }

    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(id) {
        if (draft != null) return@LaunchedEffect // nach Konfigurationswechsel wiederhergestellt
        if (isNew) {
            // Neue Medikamente beginnen morgen, mit einer Dose um 08:00 und einer noch nicht vergebenen Farbe.
            val used = repo.meds().map { it.med.color }.toSet()
            val d = WizardDraft(
                startDate = Fmt.today().plusDays(1),
                color = MedColor.entries.firstOrNull { it !in used } ?: MedColor.ROSE,
            )
            initial = d
            draft = d
        } else {
            val m = repo.med(id)
            if (m == null) {
                nav.backOrMain()
                return@LaunchedEffect
            }
            base = m.med
            val d = WizardDraft.from(m)
            initial = d
            draft = d
        }
    }

    fun update(d: WizardDraft) {
        draft = if (!stockTouched) d.copy(stock = d.packSize) else d
    }

    fun close() {
        focusManager.clearFocus()
        if (draft != null && draft != initial) confirmClose = true else nav.backOrMain()
    }

    fun back() {
        focusManager.clearFocus()
        if (current > 0) current-- else close()
    }

    fun save() {
        val d = draft ?: return
        if (saving) return
        saving = true
        scope.launch {
            val med = d.toMedication(base)
            val newId = repo.saveMedication(med, d.dosesForSave(med.id))
            if (isNew) {
                nav.navigate(Routes.medDetail(newId)) { popUpTo(Routes.WIZARD) { inclusive = true } }
            } else {
                nav.backOrMain()
            }
        }
    }

    BackHandler { back() }

    val d = draft
    val canContinue = when {
        d == null -> false
        current == 0 -> d.name.isNotBlank()
        current == 1 -> d.isAsNeeded || (d.doses.isNotEmpty() && (d.scheduleType != ScheduleType.WEEKDAYS || d.weekdays != 0))
        else -> !saving
    }

    Box(Modifier.fillMaxSize().background(c.hintergrund)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
        ) {
            StatusBarSpace()
            WizardHeader(
                title = if (isNew) "Neues Medikament" else "Medikament bearbeiten",
                step = current,
                onNav = { back() },
            )
            AnimatedContent(
                targetState = current,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(280)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(160)))
                        .using(SizeTransform(clip = false))
                },
                label = "wizardStep",
            ) { s ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)
                ) {
                    if (d != null) when (s) {
                        0 -> WizardStepWhat(
                            draft = d, onChange = ::update, nameFocus = nameFocus,
                            onNameDone = { focusManager.clearFocus(); keyboard?.hide() },
                        )
                        1 -> WizardStepWhen(
                            draft = d, onChange = ::update,
                            repeatMinutes = settings.repeatIntervalMin,
                            onCustomRhythm = {
                                rhythmReq = if (d.isCycle) WizardRhythmRequest(d.intakeDays, d.pauseDays) else WizardRhythmRequest(21, 7)
                            },
                            onEditDose = { i -> doseReq = WizardDoseRequest(i, d.doses[i]) },
                            onAddDose = {
                                val last = d.doses.lastOrNull()
                                doseReq = WizardDoseRequest(-1, Dose(medId = base?.id ?: 0, minuteOfDay = wizardNextDoseMinute(d.doses), amount = last?.amount ?: 1))
                            },
                            onEditDates = {
                                val today = Fmt.today()
                                val min = if (isNew) today else minOf(initial?.startDate ?: today, today)
                                datesReq = WizardDatesRequest(d.startDate, d.endDate, min)
                            },
                        )
                        else -> WizardStepStock(
                            draft = d, onChange = ::update,
                            onStock = { stockTouched = true; draft = d.copy(stock = it) },
                            base = base, intakes = intakes,
                        )
                    }
                }
            }
            PrimaryButton(
                text = if (current < 2) "Weiter" else "Medikament speichern",
                onClick = {
                    if (current < 2) {
                        focusManager.clearFocus()
                        current++
                    } else save()
                },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                icon = if (current < 2) null else Lucide.Check,
                enabled = canContinue,
            )
            TextButtonP(
                "Abbrechen", onClick = { close() },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 8.dp),
            )
        }

        // Sheets
        WizardSheetHost(rhythmReq, onDismiss = { rhythmReq = null }) { req ->
            val dd = draft ?: return@WizardSheetHost
            WizardRhythmSheet(
                req = req, form = dd.form,
                dailyAmount = dd.doses.sumOf { it.amount }.coerceAtLeast(1),
                startDate = dd.startDate,
                onApply = { i, p -> update(dd.copy(intakeDays = i, pauseDays = p)); rhythmReq = null },
                onClose = { rhythmReq = null },
            )
        }
        WizardSheetHost(doseReq, onDismiss = { doseReq = null }) { req ->
            val dd = draft ?: return@WizardSheetHost
            WizardDoseSheet(
                req = req, form = dd.form,
                canDelete = true,
                onApply = { dose ->
                    val list = dd.doses.toMutableList()
                    if (req.index in list.indices) list[req.index] = dose else list += dose
                    update(dd.copy(doses = list.sortedBy { it.minuteOfDay }))
                    doseReq = null
                },
                onDelete = {
                    update(dd.copy(doses = dd.doses.filterIndexed { i, _ -> i != req.index }))
                    doseReq = null
                },
                onClose = { doseReq = null },
            )
        }
        WizardSheetHost(datesReq, onDismiss = { datesReq = null }) { req ->
            val dd = draft ?: return@WizardSheetHost
            WizardDatesSheet(
                req = req,
                onApply = { s, e -> update(dd.copy(startDate = s, endDate = e)); datesReq = null },
                onClose = { datesReq = null },
            )
        }
    }

    // Autofokus auf „Name“ bei einem neuen Medikament.
    LaunchedEffect(d != null) {
        if (d != null && isNew && current == 0) runCatching { nameFocus.requestFocus() }
    }

    if (confirmClose) {
        WizardConfirmDialog(
            title = if (isNew) "Eingaben verwerfen?" else "Änderungen verwerfen?",
            text = if (isNew) "Das Medikament wird nicht angelegt." else "Deine Änderungen an diesem Medikament gehen verloren.",
            confirm = "Verwerfen",
            dismiss = "Weiter bearbeiten",
            onConfirm = { confirmClose = false; nav.backOrMain() },
            onDismiss = { confirmClose = false },
        )
    }
}

/** Kopfzeile: X bzw. Zurück, Titel Outfit 18/600, „1 von 3“ und Fortschritt (3 Segmente, 6 hoch). */
@Composable
private fun WizardHeader(title: String, step: Int, onNav: () -> Unit) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step == 0) IconButtonP(Lucide.X, "Schließen", onNav)
            else IconButtonP(Lucide.ArrowLeft, "Zurück", onNav)
            Text(
                title, style = outfit(18, 600), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${step + 1} von 3", style = atkinson(14), color = c.tinteGedaempft)
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                val color by animateColorAsState(if (i <= step) c.primaer else c.lavendelInaktiv, tween(300), label = "progress")
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
            }
        }
    }
}
