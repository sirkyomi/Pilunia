package de.pilunia.app.ui.meds

import de.pilunia.app.ui.components.rememberNativeTimePicker
import com.composables.icons.lucide.Clock
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarRange
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import de.pilunia.app.data.Dose
import de.pilunia.app.data.MedForm
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.cyclePhase
import de.pilunia.app.data.Medication
import de.pilunia.app.ui.components.ChoiceChip
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.PSwitch
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Anfrage für das Sheet „Eigener Rhythmus“. */
data class WizardRhythmRequest(val intake: Int, val pause: Int, val key: Long = System.nanoTime())

/** Anfrage für das Uhrzeit-Sheet; [index] = −1 für eine neue Uhrzeit. */
data class WizardDoseRequest(val index: Int, val dose: Dose, val key: Long = System.nanoTime())

/** Anfrage für das Sheet „Beginn und Ende“. */
data class WizardDatesRequest(val start: LocalDate, val end: LocalDate?, val minStart: LocalDate, val key: Long = System.nanoTime())

// =====================================================================
// 16a · Eigener Rhythmus
// =====================================================================

@Composable
fun WizardRhythmSheet(
    req: WizardRhythmRequest,
    form: MedForm,
    dailyAmount: Int,
    startDate: LocalDate,
    onApply: (Int, Int) -> Unit,
    onClose: () -> Unit,
) {
    val c = Pilunia.colors
    val today = Fmt.today()
    var intake by remember(req) { mutableIntStateOf(req.intake) }
    var pause by remember(req) { mutableIntStateOf(req.pause) }

    WizardSheetHeader(
        "Eigener Rhythmus",
        "Wähle selbst, wie viele Tage du ${form.wizardWithArticle()} nimmst und wie lange du pausierst. Tippe auf eine Zahl, um sie direkt einzugeben.",
        onClose,
    )

    WizardCard(padding = PaddingValues(start = 18.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)) {
        RhythmRow(c.mint, "Einnahmetage", form.wizardDailyLabel(dailyAmount), intake, { intake = it }, "Einnahmetag")
        RhythmRow(c.lavendelInaktiv, "Pausentage", form.wizardNoneLabel(), pause, { pause = it }, "Pausentag", last = true)
    }

    // Vorschau eines Zyklus
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Ein Zyklus · ${wizardDays(intake + pause)}")
        BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
            val total = maxWidth - 2.dp
            val p = (total * (pause.toFloat() / (intake + pause))).coerceIn(12.dp, total - 12.dp)
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c.mint))
                Box(Modifier.width(p).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c.lavendelInaktiv))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${wizardDays(intake)} Einnahme", style = atkinson(14), color = c.tinteGedaempft)
            Text("${wizardDays(pause)} Pause", style = atkinson(14), color = c.tinteGedaempft)
        }
    }

    // Erste bzw. nächste Pause, aus dem Beginn berechnet
    val probe = Medication(name = "", startDate = startDate.toEpochDay(), intakeDays = intake, pauseDays = pause)
    val future = startDate >= today
    val pauseStart: LocalDate
    val pauseEnd: LocalDate
    if (future) {
        pauseStart = startDate.plusDays(intake.toLong())
        pauseEnd = pauseStart.plusDays(pause - 1L)
    } else {
        val phase = probe.cyclePhase(today)!!
        pauseStart = if (phase.inPause) phase.phaseStart else phase.nextPauseStart
        pauseEnd = pauseStart.plusDays(pause - 1L)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.lavendelToenung)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(Lucide.CalendarRange, 20.dp, c.akzentText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "${if (future) "Erste" else "Nächste"} Pause: ${wizardRange(pauseStart, pauseEnd, today)}",
                style = atkinson(15, 700), color = c.tinte,
            )
            val begin = wizardDay(startDate, today)
            Text(
                "Bei Beginn am $begin${if (begin.endsWith('.')) "" else "."} In der Pause ruhen auch die Erinnerungen.",
                style = atkinson(14, lineHeight = 1.4f), color = c.tinteGedaempft,
            )
        }
    }

    PrimaryButton("Rhythmus übernehmen", { onApply(intake, pause) })
}

@Composable
private fun RhythmRow(
    dot: Color,
    title: String,
    subtitle: String,
    value: Int,
    onValue: (Int) -> Unit,
    unitName: String,
    last: Boolean = false,
) {
    val c = Pilunia.colors
    val range = 1..365
    Row(
        Modifier
            .fillMaxWidth()
            .wizardDivider(last, c.linie)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            Text(subtitle, style = atkinson(14), color = c.tinteGedaempft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            WizardStepButton(
                Lucide.Minus, "Einen $unitName weniger", { onValue((value - 1).coerceIn(range)) },
                enabled = value > range.first, background = c.lavendelToenung,
            )
            WizardNumberField(
                value = value, onValue = onValue, range = range,
                textStyle = outfit(26, 600, letterSpacing = -0.4f),
                modifier = Modifier.size(56.dp, 44.dp),
                boxShape = RoundedCornerShape(12.dp),
                contentDescription = title,
            )
            WizardStepButton(
                Lucide.Plus, "Einen $unitName mehr", { onValue((value + 1).coerceIn(range)) },
                enabled = value < range.last, background = c.lavendelToenung,
            )
        }
    }
}

// =====================================================================
// Uhrzeit und Menge
// =====================================================================

@Composable
fun WizardDoseSheet(
    req: WizardDoseRequest,
    form: MedForm,
    canDelete: Boolean,
    onApply: (Dose) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Pilunia.colors
    var minute by remember(req) { mutableIntStateOf(req.dose.minuteOfDay) }
    var amount by remember(req) { mutableIntStateOf(req.dose.amount) }
    var label by remember(req) { mutableStateOf(req.dose.label) }
    val isNew = req.index < 0

    WizardSheetHeader(if (isNew) "Weitere Uhrzeit" else "Uhrzeit und Menge", null, onClose)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Uhrzeit")
        WizardTimePicker(minute, { minute = it }, openImmediately = isNew)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Menge")
        WizardCard(padding = PaddingValues(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(form.amount(amount), style = atkinson(16, 700), color = c.tinte, modifier = Modifier.weight(1f))
                WizardStepper(amount, { amount = it }, 1..20, "Menge")
            }
        }
    }

    WizardFieldGroup("Beschreibung (optional)") {
        WizardTextField(label, { label = it.take(40) }, placeholder = "z. B. zum Frühstück", imeAction = ImeAction.Done)
    }

    Column {
        PrimaryButton(
            if (isNew) "Uhrzeit hinzufügen" else "Übernehmen",
            { onApply(req.dose.copy(minuteOfDay = minute, amount = amount, label = label.trim())) },
            icon = Lucide.Check,
        )
        if (!isNew && canDelete) {
            TextButtonP("Uhrzeit löschen", onDelete, Modifier.fillMaxWidth().padding(top = 8.dp), color = c.warnung, icon = Lucide.Trash2)
        }
    }
}

/**
 * Uhrzeit als Karte im Stil der Menge-Zeile: links die Uhrzeit, rechts ein runder Uhr-Knopf.
 * Tippen öffnet den nativen Android-Zeitdialog (minutengenau); bei einer neuen Uhrzeit sofort.
 */
@Composable
fun WizardTimePicker(minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, openImmediately: Boolean = false) {
    val c = Pilunia.colors
    val pickTime = rememberNativeTimePicker()
    LaunchedEffect(Unit) { if (openImmediately) pickTime(minuteOfDay, onChange) }
    WizardCard(
        modifier,
        padding = PaddingValues(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        onClick = { pickTime(minuteOfDay, onChange) },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.time(minuteOfDay), style = outfit(28, 600, letterSpacing = -0.5f), color = c.tinte,
                modifier = Modifier.weight(1f),
            )
            WizardStepButton(Lucide.Clock, "Uhrzeit ändern", { pickTime(minuteOfDay, onChange) })
        }
    }
}

// =====================================================================
// Beginn und Ende
// =====================================================================

@Composable
fun WizardDatesSheet(
    req: WizardDatesRequest,
    onApply: (LocalDate, LocalDate?) -> Unit,
    onClose: () -> Unit,
) {
    val c = Pilunia.colors
    val today = Fmt.today()
    var tab by remember(req) { mutableIntStateOf(0) }
    var start by remember(req) { mutableStateOf(req.start) }
    var end by remember(req) { mutableStateOf(req.end) }
    var lastEnd by remember(req) { mutableStateOf(req.end ?: req.start.plusDays(27)) }

    WizardSheetHeader("Beginn und Ende", wizardBeginLabel(start, end, today), onClose)

    WizardSegmented(listOf("Beginn", "Ende"), tab, { tab = it })

    AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "datesTab") { t ->
        if (t == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (req.minStart <= today) ChoiceChip("Heute", start == today, { start = today })
                    ChoiceChip("Morgen", start == today.plusDays(1), { start = today.plusDays(1) })
                }
                WizardCard(padding = PaddingValues(12.dp)) {
                    WizardCalendar(start, req.minStart, { d ->
                        start = d
                        end?.let { if (it < d) { end = d; lastEnd = d } }
                    })
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WizardCard(padding = PaddingValues(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Ohne Ende", style = atkinson(16, 700), color = c.tinte)
                            Text("Läuft weiter, bis du es beendest", style = atkinson(14), color = c.tinteGedaempft)
                        }
                        PSwitch(end == null, { on ->
                            end = if (on) null else maxOf(lastEnd, start)
                        })
                    }
                }
                AnimatedVisibility(end != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    WizardCard(padding = PaddingValues(12.dp)) {
                        WizardCalendar(end ?: lastEnd, start, { d -> end = d; lastEnd = d })
                    }
                }
            }
        }
    }

    PrimaryButton("Übernehmen", { onApply(start, end) }, icon = Lucide.Check)
}

/** Monatskalender im App-Stil: Kopf mit Monat und Pfeilen, Wochentage Mo–So, Tage als Kreise (40). */
@Composable
fun WizardCalendar(selected: LocalDate?, min: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val c = Pilunia.colors
    val today = Fmt.today()
    var month by remember { mutableStateOf(YearMonth.from(selected ?: min ?: today)) }
    val minMonth = min?.let { YearMonth.from(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Fmt.monthYear(month.atDay(1)), style = outfit(18, 600), color = c.tinte, modifier = Modifier.weight(1f))
            WizardStepButton(
                Lucide.ChevronLeft, "Vorheriger Monat", { month = month.minusMonths(1) },
                enabled = minMonth == null || month > minMonth, background = c.lavendelToenung, size = 40.dp,
            )
            WizardStepButton(Lucide.ChevronRight, "Nächster Monat", { month = month.plusMonths(1) }, background = c.lavendelToenung, size = 40.dp)
        }
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { d ->
                Text(
                    Fmt.weekdayShort(d), style = atkinson(13, 700), color = c.tinteGedaempft,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val length = month.lengthOfMonth()
        for (row in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = row * 7 + col - offset + 1
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..length) {
                            val day = month.atDay(n)
                            val isSel = day == selected
                            val enabled = min == null || day >= min
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .pClickable(CircleShape, enabled = enabled) { onSelect(day) }
                                    .background(if (isSel) c.primaer else Color.Transparent)
                                    .then(if (day == today && !isSel) Modifier.border(1.5.dp, c.primaer, CircleShape) else Modifier)
                                    .semantics {
                                        this.selected = isSel
                                        contentDescription = Fmt.dayLong(day)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$n",
                                    style = atkinson(15, if (isSel || day == today) 700 else 400),
                                    color = when {
                                        isSel -> FixedColors.Weiss
                                        !enabled -> c.tinteGedaempft.copy(alpha = 0.45f)
                                        else -> c.tinte
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
