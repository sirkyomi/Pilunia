package de.pilunia.app.ui.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.AlarmClock
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pill
import de.pilunia.app.R
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.ui.components.icon
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.MedIcon
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.SmallChip
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit

/** Aktionen der Screens 08 / 08a / 08b. */
internal class RemindActions(
    val onClose: () -> Unit,
    /** Alle offenen Einnahmen bestätigen. */
    val onConfirmAll: () -> Unit,
    /** 08a „Nur 1 genommen“. */
    val onTakeOne: (Occurrence) -> Unit,
    /** 08b: einzelne Zeile abhaken bzw. zurücknehmen. */
    val onToggle: (Occurrence) -> Unit,
    val onSnooze: () -> Unit,
    val onSkip: () -> Unit,
)

/**
 * 08 Erinnerung (1 Medikament, 1 Einheit), 08a (1 Medikament, Menge ≥ 2)
 * und 08b (mehrere Medikamente) – Variante je nach Inhalt des Zeitpunkts.
 */
@Composable
internal fun RemindScreen(data: SlotData, minute: Int, busy: Boolean, actions: RemindActions, preview: Boolean = false) {
    val c = Pilunia.colors
    val slot = data.slot
    ReminderScaffold(Modifier.screenBackground(c, fadeEnd = 0.6f), actions.onClose, preview = preview) { scale ->
        when {
            slot.size > 1 -> MultiContent(data, minute, scale, busy, actions)
            slot.size == 1 && slot[0].dose.amount >= 2 -> DoubleContent(data, slot[0], minute, scale, busy, actions)
            slot.size == 1 -> SingleContent(data, slot[0], minute, scale, busy, actions)
        }
    }
}

// ---------------------------------------------------------------------
// 08 – ein Medikament, eine Einheit
// ---------------------------------------------------------------------

@Composable
private fun SingleContent(data: SlotData, o: Occurrence, minute: Int, scale: Float, busy: Boolean, a: RemindActions) {
    val c = Pilunia.colors
    Head(minute, singleHeadline(o.med.form))
    Figure(260.dp * scale, 245.dp * scale)
    Box(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
        PCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedIcon(o.med.color, o.med.form, size = 52.dp, radius = 16.dp, iconSize = 26.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(o.med.displayName, style = atkinson(18, 700), color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(doseLine(o), style = atkinson(15), color = c.tinteGedaempft)
                }
                if (data.isLow(o)) StockChip(o)
            }
        }
    }
    Actions(
        primary = "Einnahme bestätigen", primaryIcon = Lucide.Check, busy = busy, a = a,
    ) { TextButtonP("Heute auslassen", { if (!busy) a.onSkip() }, Modifier.fillMaxWidth()) }
}

// ---------------------------------------------------------------------
// 08a – ein Medikament, mehrere Einheiten
// ---------------------------------------------------------------------

@Composable
private fun DoubleContent(data: SlotData, o: Occurrence, minute: Int, scale: Float, busy: Boolean, a: RemindActions) {
    val c = Pilunia.colors
    val form = o.med.form
    val n = o.dose.amount
    Head(minute, "Zeit für ${form.amount(n)}!")
    Figure(220.dp * scale, 208.dp * scale)
    Box(Modifier.padding(top = 6.dp, bottom = 12.dp)) {
        PCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MedIcon(o.med.color, o.med.form, size = 52.dp, radius = 16.dp, iconSize = 26.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(o.med.displayName, style = atkinson(18, 700), color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val hint = doseHint(o).ifBlank { o.med.purpose }
                        if (hint.isNotBlank()) Text(hint, style = atkinson(15), color = c.tinteGedaempft)
                    }
                    if (data.isLow(o)) StockChip(o)
                }
                // Dosis-Box: „2×“, Menge, Gesamtstärke, Tabletten-Symbole
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.lavendelToenung)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$n×", style = outfit(40, 800, lineHeight = 1f), color = c.akzentText)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(form.amount(n), style = atkinson(17, 700), color = c.tinte)
                        totalStrength(o)?.let { Text(it, style = atkinson(15), color = c.tinteGedaempft) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(n.coerceAtMost(3)) {
                            Box(Modifier.size(40.dp).background(c.flaeche, CircleShape), contentAlignment = Alignment.Center) {
                                LIcon(form.icon, 22.dp, c.auswahl)
                            }
                        }
                    }
                }
            }
        }
    }
    Actions(
        primary = "${form.amount(n)} bestätigen", primaryIcon = Lucide.Check, busy = busy, a = a,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButtonP("Nur 1 genommen", { if (!busy) a.onTakeOne(o) }, Modifier.weight(1f), color = c.akzentText)
            TextButtonP("Heute auslassen", { if (!busy) a.onSkip() }, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------
// 08b – mehrere Medikamente
// ---------------------------------------------------------------------

@Composable
private fun MultiContent(data: SlotData, minute: Int, scale: Float, busy: Boolean, a: RemindActions) {
    val c = Pilunia.colors
    val slot = data.slot
    val done = slot.count { it.isTaken }
    val open = slot.size - done
    Head(minute, multiHeadline(slot))
    Figure(168.dp * scale, 160.dp * scale)
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${slot.size} Medikamente", style = atkinson(16, 700), color = c.tinte)
            Text("$done von ${slot.size} erledigt", style = atkinson(14), color = c.tinteGedaempft)
        }
        PCard(Modifier.fillMaxWidth(), shadow = CardShadow, padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            slot.forEachIndexed { i, o ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.linie))
                MultiRow(data, o, busy, a)
            }
        }
    }
    val label = when {
        open >= 2 -> "Alle $open bestätigen"
        open == 1 -> "Einnahme bestätigen"
        else -> "Weiter"
    }
    Actions(
        primary = label, primaryIcon = if (open >= 2) Lucide.CheckCheck else Lucide.Check, busy = busy, a = a,
    ) { TextButtonP("Heute auslassen", { if (!busy) a.onSkip() }, Modifier.fillMaxWidth()) }
}

@Composable
private fun MultiRow(data: SlotData, o: Occurrence, busy: Boolean, a: RemindActions) {
    val c = Pilunia.colors
    val taken = o.isTaken
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(o.med.color, o.med.form, size = 44.dp, radius = 16.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    o.med.displayName, style = atkinson(17, 700), color = c.tinte,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (data.isLow(o)) StockChip(o, height = 24.dp, small = true)
            }
            Text(doseLine(o), style = atkinson(15), color = c.tinteGedaempft)
        }
        Box(
            Modifier
                .size(48.dp)
                .pClickable(CircleShape, enabled = !busy, role = Role.Checkbox) { a.onToggle(o) }
                .semantics { contentDescription = "${o.med.name} ${if (taken) "zurücknehmen" else "abhaken"}" },
            contentAlignment = Alignment.Center,
        ) { CheckCircle(taken) }
    }
}

// ---------------------------------------------------------------------
// Gemeinsame Teile
// ---------------------------------------------------------------------

/** Zeit-Chip + Überschrift (Gruppe „Kopf“, Abstand 12). */
@Composable
private fun Head(minute: Int, headline: String) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeChip(minute)
        Text(
            headline, style = outfit(30, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** „08:00 · Morgens“ mit Tageszeit-Symbol. */
@Composable
internal fun TimeChip(minute: Int) {
    val c = Pilunia.colors
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.flaeche)
            .padding(start = 12.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(daypartIcon(minute), 18.dp, c.pflaume)
        Text("${Fmt.time(minute)} · ${Fmt.daypart(minute)}", style = outfit(16, 600), color = c.pflaume, maxLines = 1)
    }
}

/** Pilunia mit Wecker im Rahmen der Designgröße. */
@Composable
private fun Figure(width: Dp, height: Dp) {
    Box(Modifier.padding(top = 12.dp, bottom = 6.dp).size(width, height)) {
        PiluniaImage(R.drawable.pilunia_wecker, "Pilunia mit Wecker", Modifier.fillMaxSize())
    }
}

/** „6 übrig“ in Warnfarben (28 hoch / Rand 10; in der Liste 24 hoch / Rand 8). */
@Composable
private fun StockChip(o: Occurrence, height: Dp = 28.dp, small: Boolean = false) {
    val c = Pilunia.colors
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(c.warnungFlaeche)
            .padding(horizontal = if (small) 8.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("${o.med.stock.coerceAtLeast(0)} übrig", style = atkinson(13, 700), color = c.warnung, maxLines = 1)
    }
}

/** Aktionen: Primär, „Später erinnern“ und Text-Button(s), Abstand 8. */
@Composable
private fun Actions(
    primary: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    a: RemindActions,
    textButtons: @Composable () -> Unit,
) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PillButton(primary, { if (!busy) a.onConfirmAll() }, c.primaer, OnPrimary, icon = primaryIcon, iconStroke = 2.5f)
        PillButton("Später erinnern", { if (!busy) a.onSnooze() }, c.lavendelToenungKraeftig, c.akzentText, icon = Lucide.AlarmClock, iconStroke = 2f)
        textButtons()
    }
}
