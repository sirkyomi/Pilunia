package de.pilunia.app.ui.reminder

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Utensils
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.toMillis
import de.pilunia.app.ui.components.rememberNativeTimePicker
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val CUSTOM = "custom"

private class SnoozeOption(val id: String, val icon: ImageVector, val label: String, val target: LocalDateTime)

/** Mahlzeit-Option passend zur Uhrzeit (nur, wenn sie noch mindestens 15 Min. entfernt ist). */
private fun mealOption(now: LocalDateTime): SnoozeOption? {
    val today = now.toLocalDate()
    val candidates = listOf(
        Triple("Zum Mittagessen", 12 * 60, Lucide.Utensils),
        Triple("Zum Abendessen", 18 * 60, Lucide.Utensils),
        Triple("Vor dem Schlafen", 21 * 60, Lucide.Moon),
    )
    return candidates.firstNotNullOfOrNull { (label, min, icon) ->
        val t = today.atTime(min / 60, min % 60)
        if (t.isAfter(now.plusMinutes(15))) SnoozeOption("meal", icon, label, t) else null
    }
}

/**
 * 09 „Später erinnern“ als Bottom-Sheet über dem abgedunkelten aktuellen Screen.
 * [onSnoozed] wird nach dem Speichern aufgerufen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(day: LocalDate, minute: Int, onDismiss: () -> Unit, onSnoozed: () -> Unit) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val pickTime = rememberNativeTimePicker()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val data = rememberSlotData(day, minute)
    val now = rememberNow().value.truncatedTo(ChronoUnit.MINUTES)

    var selected by rememberSaveable { mutableStateOf("m30") }
    var customMinute by rememberSaveable {
        // Vorschlag: in 45 Minuten, auf 5 Minuten aufgerundet
        val t = LocalDateTime.now().plusMinutes(45)
        mutableIntStateOf(((t.hour * 60 + t.minute + 4) / 5 * 5) % (24 * 60))
    }
    var saving by remember { mutableStateOf(false) }

    val options = buildList {
        add(SnoozeOption("m10", Lucide.Timer, "In 10 Minuten", now.plusMinutes(10)))
        add(SnoozeOption("m30", Lucide.Timer, "In 30 Minuten", now.plusMinutes(30)))
        add(SnoozeOption("h1", Lucide.Timer, "In 1 Stunde", now.plusHours(1)))
        mealOption(now)?.let { add(it) }
    }
    val customTarget = now.toLocalDate().atTime(customMinute / 60, customMinute % 60)
        .let { if (it.isAfter(now)) it else it.plusDays(1) }
    val target = if (selected == CUSTOM) customTarget else (options.firstOrNull { it.id == selected } ?: options[1]).target
    val buttonLabel = if (target.toLocalDate() == now.toLocalDate()) "Um ${Fmt.time(target.toLocalTime())} erinnern"
    else "Morgen um ${Fmt.time(target.toLocalTime())} erinnern"

    val open = data?.slot?.filter { !it.isTaken }.orEmpty()
    val subtitle = when {
        open.size == 1 -> "${open[0].med.displayName} · geplant um ${Fmt.time(minute)}"
        open.size > 1 -> "${open.size} Medikamente · geplant um ${Fmt.time(minute)}"
        else -> "Geplant um ${Fmt.time(minute)}"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.flaeche,
        contentColor = c.tinte,
        scrimColor = ScrimColor,
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.feldrand))
        },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Später erinnern", style = outfit(22, 600), color = c.tinte)
                Text(subtitle, style = atkinson(14), color = c.tinteGedaempft, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { o ->
                    OptionRow(o.icon, o.label, selected == o.id, onClick = { selected = o.id }) {
                        Text(Fmt.time(o.target.toLocalTime()), style = outfit(15, 500), color = c.tinteGedaempft, maxLines = 1)
                        Radio(selected == o.id)
                    }
                }
                val customOn = selected == CUSTOM
                OptionRow(Lucide.Clock, "Eigene Uhrzeit wählen", customOn, onClick = {
                    pickTime(customMinute) { m -> customMinute = m; selected = CUSTOM }
                }) {
                    if (customOn) {
                        Text(Fmt.time(customMinute), style = outfit(15, 500), color = c.tinteGedaempft, maxLines = 1)
                        Radio(true)
                    } else LIcon(Lucide.ChevronRight, 22.dp, c.tinteGedaempft)
                }
            }
            PillButton(
                buttonLabel,
                onClick = {
                    if (saving) return@PillButton
                    saving = true
                    val until = target.toMillis()
                    scope.launch {
                        container.repository.snooze(day, minute, until)
                        container.notifier.cancelDoseReminder(day, minute)
                        sheetState.hide()
                        onSnoozed()
                    }
                },
                container = c.primaer, content = OnPrimary,
            )
        }
        Spacer(Modifier.height(16.dp))
        NavBarSpace()
    }
}

/** Option 56 hoch, Radius 16; gewählt mit „Lavendel Tönung“. Symbol 22 „Pflaume“, Text 16/700. */
@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pClickable(RoundedCornerShape(16.dp), role = Role.RadioButton, onClick = onClick)
            .background(if (selected) c.lavendelToenung else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icon, 22.dp, c.pflaume)
        Text(label, style = atkinson(16, 700), color = c.tinte, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Radio 24: aus = 2er Rand „Feldrand“, an = 2er Rand + Punkt 12 „Auswahl“. */
@Composable
private fun Radio(on: Boolean) {
    val c = Pilunia.colors
    Box(
        Modifier.size(24.dp).border(2.dp, if (on) c.auswahl else c.feldrand, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Box(Modifier.size(12.dp).background(c.auswahl, CircleShape))
    }
}

