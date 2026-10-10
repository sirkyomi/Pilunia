package de.pilunia.app.ui.meds

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import de.pilunia.app.data.Dose
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.bit
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.palette
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.PiluniaColors
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.delay
import java.time.DayOfWeek

// =====================================================================
// Texte rund um den Einnahmeplan
// =====================================================================

/** „Mo, Mi, Fr“, „Mo–Fr“ oder „Täglich“ (alle Tage). */
internal fun weekdaysText(mask: Int): String {
    val all = DayOfWeek.entries
    val sel = all.filter { mask and it.bit() != 0 }
    return when {
        sel.size == 7 || sel.isEmpty() -> "Täglich"
        sel == all.take(5) -> "Mo–Fr"
        else -> sel.joinToString(", ") { Fmt.weekdayShort(it) }
    }
}

/** Werden nur bestimmte Wochentage genommen (nicht alle sieben)? */
internal val Medication.hasWeekdaySubset: Boolean
    get() = scheduleType == ScheduleType.WEEKDAYS && weekdaysText(weekdays) != "Täglich"

/** Kurzer Rhythmus für die Liste: „Täglich“, „Mo, Mi, Fr“, „21 + 7“, „Bei Bedarf“. */
internal fun Medication.rhythmShort(): String = when {
    scheduleType == ScheduleType.AS_NEEDED -> "Bei Bedarf"
    isCycle -> "$intakeDays + $pauseDays"
    hasWeekdaySubset -> weekdaysText(weekdays)
    else -> "Täglich"
}

/** Präfix vor „um 08:00“: „Täglich“ bzw. „Mo, Mi, Fr“. */
internal fun Medication.dayPrefix(): String = if (hasWeekdaySubset) weekdaysText(weekdays) else "Täglich"

/** Plan-Zeile der Medikamentenkarte, z. B. „Täglich · 08:00 · 1 Tablette“. */
internal fun MedWithDoses.planLine(): String {
    val med = med
    val doses = sortedDoses
    if (med.scheduleType == ScheduleType.AS_NEEDED) {
        val detail = med.hint.trim().ifBlank { med.form.amount(doses.firstOrNull()?.amount ?: 1) }
        return "Bei Bedarf · $detail"
    }
    val parts = mutableListOf(med.rhythmShort())
    when (doses.size) {
        0 -> Unit
        1 -> { parts += Fmt.time(doses[0].minuteOfDay); parts += med.form.amount(doses[0].amount) }
        2 -> {
            parts += doses.joinToString(" & ") { Fmt.time(it.minuteOfDay) }
            if (doses[0].amount == doses[1].amount) parts += "je ${med.form.amount(doses[0].amount)}"
        }
        else -> {
            parts += "${doses.size}×"
            parts += "${Fmt.time(doses.first().minuteOfDay)}–${Fmt.time(doses.last().minuteOfDay)}"
        }
    }
    return parts.joinToString(" · ")
}

/** Untertitel der Detailansicht: „Blutdruck · 1 Tablette täglich“. */
internal fun MedWithDoses.detailSubtitle(): String {
    val med = med
    val doses = sortedDoses
    val rhythm = when {
        med.scheduleType == ScheduleType.AS_NEEDED -> "Bei Bedarf"
        doses.isEmpty() -> med.rhythmShort()
        med.hasWeekdaySubset ->
            (if (doses.size == 1) med.form.amount(doses[0].amount) else "${doses.size}×") + " · " + weekdaysText(med.weekdays)
        doses.size == 1 -> "${med.form.amount(doses[0].amount)} täglich"
        else -> "${doses.size}× täglich"
    }
    return listOf(med.purpose.trim(), rhythm).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** „1 Tablette · zum Frühstück“ */
internal fun doseSubtitle(form: MedForm, d: Dose): String =
    listOf(form.amount(d.amount), d.label.trim()).filter { it.isNotEmpty() }.joinToString(" · ")

/** „Tabletten übrig“ bzw. „Tablette übrig“. */
internal fun MedForm.leftLabel(n: Int): String = "${if (n == 1) singular else plural} übrig"

/** Wird diese Form als Blister gezählt (Mulden-Darstellung)? */
internal val Medication.usesBlister: Boolean
    get() = (form == MedForm.TABLETTE || form == MedForm.KAPSEL) && unitsPerBlister in 1..60

/** Großschreibung des ersten Buchstabens („heute“ → „Heute“). */
internal fun String.capitalizeFirst(): String = replaceFirstChar { it.uppercase() }

// =====================================================================
// Farben
// =====================================================================

/** Verlauf von oben in der Medikamentfarbe (Detailansicht: Rose 22 %, Lavendel 18 %, bis 40 %). */
internal fun Modifier.medBackground(colors: PiluniaColors, color: MedColor, fadeEnd: Float = 0.4f): Modifier {
    val tint = colors.palette(color).swatch
    val alpha = if (color == MedColor.LAVENDEL) 0x2E / 255f else 0x38 / 255f
    return background(colors.hintergrund).drawBehind {
        drawRect(Brush.verticalGradient(0f to tint.copy(alpha = alpha), fadeEnd to tint.copy(alpha = 0f), startY = 0f, endY = size.height))
    }
}

/** Gestrichelter Kreisrand (Mulden, offene Tage). */
internal fun Modifier.dashedCircle(color: Color, width: Dp, dash: Float, gap: Float): Modifier = drawBehind {
    val w = width.toPx()
    drawCircle(
        color, radius = (size.minDimension - w) / 2f,
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.dp.toPx(), gap.dp.toPx()))),
    )
}

// =====================================================================
// Bausteine
// =====================================================================

/** Kartenzeile wie im Design: Kachel 44 (Radius 14), Titel 16/700, Untertitel 14, Abstand 14, innen 14. */
@Composable
internal fun DetailRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    subtitleColor: Color = Pilunia.colors.tinteGedaempft,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = { LIcon(Lucide.ChevronRight, 22.dp, Pilunia.colors.tinteGedaempft) },
) {
    val c = Pilunia.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (divider) Modifier.drawBehind {
                drawLine(c.linie, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
            } else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.lavendelToenung), contentAlignment = Alignment.Center) {
            LIcon(icon, 22.dp, c.pflaume)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = atkinson(14), color = subtitleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke(this)
    }
}

/** Weiße Karte mit Zeilen (innen oben/unten 4, seitlich 16; bei nur einer Zeile ohne Zusatzabstand). */
@Composable
internal fun RowsCard(rows: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Pilunia.colors
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.flaeche)
            .padding(horizontal = 16.dp, vertical = if (rows > 1) 4.dp else 0.dp),
        content = content,
    )
}

/** Abschnittsbeschriftung über einer Kartengruppe (Atkinson 14/700, Abstand 8). */
@Composable
internal fun CardGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = atkinson(14, 700), color = Pilunia.colors.tinteGedaempft)
        content()
    }
}

/** Primär-Button wie in den Aktionsleisten des Designs (Symbol 22, Strich 2). */
@Composable
internal fun ActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pClickable(RoundedCornerShape(28.dp), enabled, onClick = onClick)
            .background(Pilunia.colors.primaer.copy(alpha = alpha)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 22.dp, FixedColors.Weiss.copy(alpha = alpha))
        Text(text, style = atkinson(17, 700), color = FixedColors.Weiss.copy(alpha = alpha), maxLines = 1)
    }
}

// =====================================================================
// Sheet, Dialog, Bestätigung
// =====================================================================

private fun scrimColor(c: PiluniaColors) = if (c.isDark) Color.Black.copy(alpha = 0.6f) else Color(0xFF2E2440).copy(alpha = 0.5f)

/** Bottom Sheet im App-Stil („Später erinnern“): Fläche, Radius 28 oben, Griff 36 × 4, Titel Outfit 22/600. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PSheet(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pilunia.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.flaeche,
        contentColor = c.tinte,
        tonalElevation = 0.dp,
        scrimColor = scrimColor(c),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.feldrand))
        },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = outfit(22, 600), color = c.tinte)
                if (subtitle != null) Text(subtitle, style = atkinson(14), color = c.tinteGedaempft)
            }
            content()
        }
    }
}

/** Eingabefeld wie im Assistenten: 56 hoch (mehrzeilig höher), Radius 16, Rand „Feldrand“ bzw. 2er „Primär“ im Fokus. */
@Composable
internal fun PTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    textStyle: TextStyle = atkinson(17),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Pilunia.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .onFocusChanged { focused = it.isFocused },
        singleLine = singleLine,
        textStyle = textStyle.copy(color = c.tinte),
        cursorBrush = SolidColor(c.primaer),
        keyboardOptions = keyboardOptions,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(shape)
                    .background(c.flaeche)
                    .border(if (focused) 2.dp else 1.dp, if (focused) c.primaer else c.feldrand, shape)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.tinteGedaempft)
                inner()
            }
        },
    )
}

/** Bestätigungsdialog im App-Stil (Karte Radius 28, Symbolkachel, Outfit-Titel, zwei Buttons). */
@Composable
internal fun ConfirmDialog(
    icon: ImageVector,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    val c = Pilunia.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .softShadow(CardShadow, 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.flaeche)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(if (destructive) c.warnungFlaeche else c.lavendelToenung),
                contentAlignment = Alignment.Center,
            ) { LIcon(icon, 26.dp, if (destructive) c.warnung else c.pflaume) }
            Text(title, style = outfit(22, 700, letterSpacing = -0.3f), color = c.tinte, textAlign = TextAlign.Center)
            Text(text, style = atkinson(16, lineHeight = 1.45f), color = c.tinteGedaempft, textAlign = TextAlign.Center)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    confirmLabel, onClick = onConfirm,
                    container = if (destructive) c.warnungKraeftig else c.primaer,
                )
                PrimaryButton(
                    "Abbrechen", onClick = onDismiss,
                    container = c.lavendelToenungKraeftig, content = c.akzentText,
                )
            }
        }
    }
}

/** Kurze Bestätigung (z. B. „Neue Packung eingetragen“) mit optionalem „Rückgängig“. */
data class ToastData(val message: String, val actionLabel: String? = null, val id: Long = System.nanoTime(), val onAction: (() -> Unit)? = null)

@Composable
internal fun PToastHost(toast: ToastData?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(5000)
            onDismiss()
        }
    }
    // Letzte Meldung merken, damit sie beim Ausblenden sichtbar bleibt.
    val holder = remember { arrayOfNulls<ToastData>(1) }
    if (toast != null) holder[0] = toast
    val last = holder[0]
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
    ) {
        val t = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .softShadow(CardShadow, 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.tinte)
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).background(if (c.isDark) Color(0xFF1F7A4D) else c.mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LIcon(Lucide.Check, 14.dp, if (c.isDark) FixedColors.Weiss else Color(0xFF1F7A4D), strokeWidth = 3f) }
            Text(
                t.message, style = atkinson(15, 700), color = c.hintergrund,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            )
            if (t.actionLabel != null && t.onAction != null) {
                TextButtonP(
                    t.actionLabel,
                    onClick = { t.onAction.invoke(); onDismiss() },
                    color = if (c.isDark) c.primaer else c.lavendel,
                    style = atkinson(15, 700),
                )
            }
        }
    }
}

// =====================================================================
// Vorrat korrigieren / Rezept-Notiz
// =====================================================================

/** Sheet zum Korrigieren des Vorrats (−/+ und direkte Eingabe). */
@Composable
internal fun StockSheet(med: Medication, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val c = Pilunia.colors
    var text by remember(med.id) { mutableStateOf(med.stock.coerceAtLeast(0).toString()) }
    val value = text.toIntOrNull()
    PSheet(
        onDismiss = onDismiss,
        title = "Vorrat korrigieren",
        subtitle = "${med.displayName} · aktuell ${med.form.amount(med.stock.coerceAtLeast(0))}",
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepButton(Lucide.Minus, "Weniger") { text = ((value ?: 0) - 1).coerceAtLeast(0).toString() }
            BasicTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                singleLine = true,
                textStyle = outfit(56, 800, lineHeight = 1f, letterSpacing = -1.5f).copy(color = c.tinte, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(c.primaer),
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.weight(1f, fill = false).size(width = 140.dp, height = 72.dp)
                    .clip(RoundedCornerShape(20.dp)).background(c.lavendelToenung).padding(top = 6.dp),
            )
            StepButton(Lucide.Plus, "Mehr") { text = ((value ?: 0) + 1).coerceAtMost(9999).toString() }
        }
        if (med.usesBlister) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                QuickChip("+ 1 Blister") { text = ((value ?: 0) + med.unitsPerBlister).coerceAtMost(9999).toString() }
                QuickChip("+ 1 Packung") { text = ((value ?: 0) + med.packSize).coerceAtMost(9999).toString() }
            }
        }
        PrimaryButton("Vorrat speichern", onClick = { onSave(value ?: 0) }, enabled = value != null)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = Pilunia.colors
    Box(
        Modifier.size(56.dp).pClickable(CircleShape, onClick = onClick).background(c.lavendelToenungKraeftig),
        contentAlignment = Alignment.Center,
    ) { LIcon(icon, 24.dp, c.akzentText, strokeWidth = 2.5f, contentDescription = description) }
}

@Composable
private fun QuickChip(text: String, onClick: () -> Unit) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .height(36.dp)
            .pClickable(shape, onClick = onClick)
            .background(c.flaeche)
            .border(1.dp, c.feldrand, shape)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = atkinson(14, 700), color = c.tinte) }
}

/** Sheet für die Rezept-Notiz. */
@Composable
internal fun NoteSheet(med: Medication, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Pilunia.colors
    var text by remember(med.id) { mutableStateOf(med.prescriptionNote) }
    PSheet(onDismiss = onDismiss, title = "Rezept-Notiz", subtitle = med.displayName) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Notiz", style = atkinson(14, 700), color = c.tinte)
            PTextField(
                value = text,
                onValueChange = { text = it.take(300) },
                placeholder = "z. B. Folgerezept telefonisch in der Praxis",
                singleLine = false,
                minHeight = 112.dp,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton("Notiz speichern", onClick = { onSave(text.trim()) })
            if (med.prescriptionNote.isNotBlank()) {
                TextButtonP(
                    "Notiz entfernen", onClick = { onSave("") },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = c.warnung,
                )
            }
        }
    }
}
