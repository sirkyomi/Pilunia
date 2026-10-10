package de.pilunia.app.ui.meds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CalendarX
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import de.pilunia.app.data.Intake
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.forecast
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson

/** 17 · Schritt 3 „Wie viel hast du da?“ */
@Composable
fun WizardStepStock(
    draft: WizardDraft,
    onChange: (WizardDraft) -> Unit,
    onStock: (Int) -> Unit,
    base: Medication?,
    intakes: List<Intake>,
) {
    val c = Pilunia.colors
    val form = draft.form
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WizardHeadline("Wie viel hast du da?")
            Text("So weiß Pilunia, wann du Nachschub brauchst.", style = atkinson(16), color = c.tinteGedaempft)
        }

        WizardCard(padding = PaddingValues(start = 18.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)) {
            StockRow(form.wizardPerContainer(), null) {
                WizardStepper(draft.unitsPerBlister, { onChange(draft.copy(unitsPerBlister = it)) }, 1..999, form.wizardPerContainer())
            }
            StockRow(form.wizardContainersPerPack(), null) {
                WizardStepper(draft.blistersPerPack, { onChange(draft.copy(blistersPerPack = it)) }, 1..99, form.wizardContainersPerPack())
            }
            StockRow("Davon noch übrig", form.wizardPackLabel(draft.packSize), last = true) {
                WizardStepper(draft.stock, onStock, 0..9999, "Davon noch übrig")
            }
        }

        AnimatedVisibility(
            !draft.isAsNeeded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WizardLabel("Warnen, wenn der Vorrat noch reicht für")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 14).forEach { d ->
                            WarnChip("$d Tage", draft.warnDays == d, Modifier.weight(1f)) { onChange(draft.copy(warnDays = d)) }
                        }
                    }
                }
                ForecastCard(draft, base, intakes)
            }
        }
    }
}

@Composable
private fun StockRow(title: String, subtitle: String?, last: Boolean = false, stepper: @Composable () -> Unit) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .wizardDivider(last, c.linie)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            if (subtitle != null) Text(subtitle, style = atkinson(14), color = c.tinteGedaempft)
        }
        stepper()
    }
}

/** Chip „3 / 7 / 14 Tage“: 40 hoch, Radius 20, gewählt „Primär“ mit Häkchen, Inhalt mittig. */
@Composable
private fun WarnChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(40.dp)
            .pClickable(shape, role = Role.RadioButton, onClick = onClick)
            .background(if (selected) c.primaer else c.flaeche)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.feldrand, shape))
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) LIcon(Lucide.Check, 18.dp, FixedColors.Weiss, strokeWidth = 2.5f)
        Text(text, style = atkinson(15, 700), color = if (selected) FixedColors.Weiss else c.tinte, maxLines = 1)
    }
}

/** Grüne Prognose „Reicht bis …“ (bei knappem oder fehlendem Vorrat in Warnfarben). */
@Composable
private fun ForecastCard(draft: WizardDraft, base: Medication?, intakes: List<Intake>) {
    val c = Pilunia.colors
    val today = Fmt.today()
    val med = draft.toMedication(base)
    val doses = draft.dosesForSave(med.id)
    val fc = forecast(MedWithDoses(med, doses), intakes, today)

    val title: String
    val subtitle: String
    var warn = false
    when {
        doses.isEmpty() -> return
        draft.stock <= 0 -> {
            warn = true
            title = "Noch kein Vorrat eingetragen"
            subtitle = "Trage ein, wie viel du noch da hast."
        }
        fc.lastCoveredDay == null -> {
            title = if (draft.endDate != null) "Reicht bis zum Ende am ${Fmt.dayShortWithYear(draft.endDate)}" else "Reicht mehr als 3 Jahre"
            subtitle = "Keine Vorrats-Erinnerung nötig"
        }
        else -> {
            val last = fc.lastCoveredDay
            val remind = last.minusDays(draft.warnDays.toLong())
            warn = fc.isLow(draft.warnDays)
            title = if (last < today) "Reicht nicht mehr für heute" else "Reicht bis ${Fmt.dayShortWithYear(last)}"
            subtitle = if (remind <= today) "Vorrats-Erinnerung schon heute" else "Vorrats-Erinnerung am ${Fmt.dayShort(remind)}"
        }
    }
    val bg = if (warn) c.warnungFlaeche else c.mintFlaeche
    val accent = if (warn) c.warnung else c.erfolg
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.flaeche), contentAlignment = Alignment.Center) {
            LIcon(if (warn) Lucide.CalendarX else Lucide.CalendarCheck, 22.dp, accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            Text(subtitle, style = atkinson(14), color = accent)
        }
    }
}
