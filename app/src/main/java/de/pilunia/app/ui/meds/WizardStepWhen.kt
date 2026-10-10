package de.pilunia.app.ui.meds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import de.pilunia.app.data.Dose
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.PSwitch
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import java.time.DayOfWeek

private val ScheduleOptions = listOf(ScheduleType.DAILY, ScheduleType.WEEKDAYS, ScheduleType.AS_NEEDED)

/** 16 · Schritt 2 „Wann nimmst du es ein?“ */
@Composable
fun WizardStepWhen(
    draft: WizardDraft,
    onChange: (WizardDraft) -> Unit,
    repeatMinutes: Int,
    onCustomRhythm: () -> Unit,
    onEditDose: (Int) -> Unit,
    onAddDose: () -> Unit,
    onEditDates: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        WizardHeadline("Wann nimmst du es ein?")

        Column {
            WizardSegmented(
                options = listOf("Täglich", "Bestimmte Tage", "Bei Bedarf"),
                selected = ScheduleOptions.indexOf(draft.scheduleType),
                onSelect = { i ->
                    val type = ScheduleOptions[i]
                    val weekdays = if (type == ScheduleType.WEEKDAYS && (draft.weekdays == 0b1111111 || draft.weekdays == 0)) 0b0011111 else draft.weekdays
                    onChange(draft.copy(scheduleType = type, weekdays = weekdays))
                },
            )
            AnimatedVisibility(
                draft.scheduleType == ScheduleType.WEEKDAYS,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                WeekdayChips(draft.weekdays, { onChange(draft.copy(weekdays = it)) }, Modifier.padding(top = 12.dp))
            }
            AnimatedVisibility(
                draft.isAsNeeded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                AsNeededInfo(Modifier.padding(top = 20.dp))
            }
        }

        AnimatedVisibility(
            !draft.isAsNeeded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                RhythmGroup(draft, onChange, onCustomRhythm)
                DosesGroup(draft, onEditDose, onAddDose)
                ReminderGroup(draft, onChange, repeatMinutes, onEditDates)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Wochentage
// ---------------------------------------------------------------------

@Composable
private fun WeekdayChips(mask: Int, onMask: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { d ->
            val bit = 1 shl (d.value - 1)
            val on = mask and bit != 0
            Box(
                Modifier
                    .size(44.dp)
                    .pClickable(CircleShape, role = Role.Checkbox) {
                        val next = mask xor bit
                        if (next != 0) onMask(next)
                    }
                    .background(if (on) c.primaer else c.flaeche)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.feldrand, CircleShape))
                    .semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(Fmt.weekdayShort(d), style = atkinson(15, 700), color = if (on) FixedColors.Weiss else c.tinte)
            }
        }
    }
}

@Composable
private fun AsNeededInfo(modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.lavendelToenung)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LIcon(Lucide.Info, 20.dp, c.akzentText)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Einnahme nach Bedarf", style = atkinson(15, 700), color = c.tinte)
            Text(
                "Du trägst jede Einnahme selbst ein, wenn du sie brauchst. Pilunia erinnert dich dafür nicht, behält aber den Vorrat im Blick.",
                style = atkinson(14, lineHeight = 1.4f), color = c.tinteGedaempft,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Einnahmerhythmus
// ---------------------------------------------------------------------

@Composable
private fun RhythmGroup(draft: WizardDraft, onChange: (WizardDraft) -> Unit, onCustom: () -> Unit) {
    val c = Pilunia.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Einnahmerhythmus")
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RhythmTile(
                label = "Durchgehend", selected = !draft.isCycle, style = TileStyle.Solid,
                onClick = { onChange(draft.copy(intakeDays = 0, pauseDays = 0)) },
            ) { CycleBar(1, 0) }
            WizardCyclePresets.forEach { (i, p) ->
                RhythmTile(
                    label = "$i + $p", selected = draft.intakeDays == i && draft.pauseDays == p, style = TileStyle.Solid,
                    modifier = Modifier.weight(1f),
                    onClick = { onChange(draft.copy(intakeDays = i, pauseDays = p)) },
                ) { CycleBar(i, p) }
            }
            val custom = draft.isCustomCycle
            RhythmTile(
                label = if (custom) "${draft.intakeDays} + ${draft.pauseDays}" else "Eigener",
                selected = custom, style = TileStyle.Dashed,
                modifier = Modifier.weight(1f),
                onClick = onCustom,
            ) {
                if (custom) CycleBar(draft.intakeDays, draft.pauseDays)
                else Row(Modifier.width(56.dp).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.width(30.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.mint))
                    Box(Modifier.width(24.dp).fillMaxHeight().wizardDashedBorder(1.dp, c.feldrand, 3.dp, on = 2.dp, off = 2.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LIcon(Lucide.BellOff, 16.dp, c.tinteGedaempft)
            Text("Bei einer Pause ruhen auch die Erinnerungen.", style = atkinson(14), color = c.tinteGedaempft, modifier = Modifier.weight(1f))
        }
    }
}

private enum class TileStyle { Solid, Dashed }

@Composable
private fun RhythmTile(
    label: String,
    selected: Boolean,
    style: TileStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bar: @Composable () -> Unit,
) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxHeight()
            .then(if (selected) Modifier.softShadow(WizardSelectShadow, 16.dp) else Modifier)
            .clip(shape)
            .background(c.flaeche)
            .then(
                when {
                    selected -> Modifier.border(2.dp, c.primaer, shape)
                    style == TileStyle.Dashed -> Modifier.wizardDashedBorder(1.5.dp, c.feldrand, 16.dp)
                    else -> Modifier.border(1.5.dp, c.lavendelInaktiv, shape)
                }
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = atkinson(15, 700), color = if (selected) c.tinte else c.akzentText, maxLines = 1, softWrap = false)
        bar()
    }
}

/** Mini-Balken 56 × 6: Einnahme „Mint“, Pause „Lavendel inaktiv“, Abstand 2. */
@Composable
private fun CycleBar(intake: Int, pause: Int, width: Dp = 56.dp) {
    val c = Pilunia.colors
    Row(Modifier.width(width).height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (pause <= 0) {
            Box(Modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.mint))
        } else {
            val total = (width - 2.dp).value
            val p = (total * pause / (intake + pause)).coerceIn(6f, total - 6f)
            Box(Modifier.width((total - p).dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.mint))
            Box(Modifier.width(p.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.lavendelInaktiv))
        }
    }
}

// ---------------------------------------------------------------------
// Uhrzeit und Menge
// ---------------------------------------------------------------------

@Composable
private fun DosesGroup(draft: WizardDraft, onEdit: (Int) -> Unit, onAdd: () -> Unit) {
    val c = Pilunia.colors
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Uhrzeit und Menge")
        draft.doses.forEachIndexed { i, d -> DoseCard(d, draft, onClick = { onEdit(i) }) }
        if (draft.doses.isEmpty()) {
            Text("Füge mindestens eine Uhrzeit hinzu.", style = atkinson(14), color = c.warnung)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .wizardDashedBorder(1.5.dp, c.feldrand, 20.dp)
                .pClickable(RoundedCornerShape(20.dp), onClick = onAdd),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LIcon(Lucide.Plus, 20.dp, c.akzentText, strokeWidth = 2.5f)
            Text("Weitere Uhrzeit", style = atkinson(16, 700), color = c.akzentText)
        }
    }
}

@Composable
private fun DoseCard(d: Dose, draft: WizardDraft, onClick: () -> Unit) {
    val c = Pilunia.colors
    WizardCard(padding = PaddingValues(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fmt.time(d.minuteOfDay), style = outfit(30, 600, letterSpacing = -0.5f), color = c.tinte)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(draft.form.amount(d.amount), style = atkinson(16, 700), color = c.tinte, maxLines = 1)
                Text(wizardDoseCaption(d), style = atkinson(14), color = c.tinteGedaempft, maxLines = 2)
            }
            Box(
                Modifier.size(44.dp).pClickable(CircleShape, onClick = onClick).background(c.lavendelToenung),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.Pencil, 20.dp, c.akzentText, contentDescription = "Uhrzeit bearbeiten")
            }
        }
    }
}

// ---------------------------------------------------------------------
// Erinnerung
// ---------------------------------------------------------------------

@Composable
private fun ReminderGroup(draft: WizardDraft, onChange: (WizardDraft) -> Unit, repeatMinutes: Int, onEditDates: () -> Unit) {
    val c = Pilunia.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizardLabel("Erinnerung")
        WizardCard(padding = PaddingValues(start = 18.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            OptionRow(
                "Zur Einnahmezeit erinnern", "Mitteilung mit Ton",
                onClick = { onChange(draft.copy(remindAtTime = !draft.remindAtTime)) },
            ) {
                PSwitch(draft.remindAtTime, { onChange(draft.copy(remindAtTime = it)) })
            }
            OptionRow(
                "Erneut erinnern", "Nach $repeatMinutes Min., wenn nicht bestätigt",
                enabled = draft.remindAtTime,
                onClick = { onChange(draft.copy(repeatReminder = !draft.repeatReminder)) },
            ) {
                PSwitch(draft.repeatReminder && draft.remindAtTime, { onChange(draft.copy(repeatReminder = it)) }, enabled = draft.remindAtTime)
            }
            OptionRow("Beginn", wizardBeginLabel(draft.startDate, draft.endDate), last = true, onClick = onEditDates) {
                LIcon(Lucide.ChevronRight, 22.dp, c.tinteGedaempft)
            }
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    last: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .wizardDivider(last, c.linie)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            Text(subtitle, style = atkinson(14), color = c.tinteGedaempft)
        }
        trailing()
    }
}
