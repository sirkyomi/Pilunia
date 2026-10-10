package de.pilunia.app.ui.reminder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Sunset
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.PiluniaRepository
import de.pilunia.app.data.Snooze
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.ui.components.AnimatedCheckCircle
import de.pilunia.app.ui.components.CloseBar
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberMeds
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

// =====================================================================
// Daten eines Erinnerungszeitpunkts
// =====================================================================

/** Schlüssel einer geplanten Einnahme (Medikament + Dosis). */
internal typealias OccKey = Pair<Long, Long>

internal val Occurrence.key: OccKey get() = med.id to dose.id
internal val Occurrence.isTaken: Boolean get() = intake?.status == IntakeStatus.TAKEN
/** Noch nicht genommen (offen, verpasst oder ausgelassen). */
internal val Occurrence.isOpenForReminder: Boolean get() = !isTaken

/** Alles, was die Erinnerungs-Screens brauchen; null, solange geladen wird. */
internal class SlotData(
    val day: LocalDate,
    val meds: List<MedWithDoses>,
    val intakes: List<Intake>,
    /** Alle geplanten Einnahmen des Tages. */
    val dayOcc: List<Occurrence>,
    /** Die Einnahmen dieses Zeitpunkts. */
    val slot: List<Occurrence>,
) {
    fun medWithDoses(id: Long): MedWithDoses? = meds.firstOrNull { it.med.id == id }

    /** Vorrat knapp (Warnschwelle des Medikaments erreicht)? */
    fun isLow(o: Occurrence): Boolean {
        val m = medWithDoses(o.med.id) ?: return false
        return forecast(m, intakes).isLow(m.med.warnDays)
    }
}

@Composable
private fun rememberIntakesOrNull(): State<List<Intake>?> {
    val repo = LocalContainer.current.repository
    val flow = remember(repo) { repo.observeIntakes() }
    return flow.collectAsState(initial = null)
}

@Composable
private fun rememberSnoozesOrNull(): State<List<Snooze>?> {
    val repo = LocalContainer.current.repository
    val flow = remember(repo) { repo.observeSnoozes() }
    return flow.collectAsState(initial = null)
}

/** Reaktive Einnahmen eines Zeitpunkts (Tag + Uhrzeit). */
@Composable
internal fun rememberSlotData(day: LocalDate, minute: Int): SlotData? {
    val meds by rememberMeds()
    val intakes by rememberIntakesOrNull()
    val snoozes by rememberSnoozesOrNull()
    return remember(meds, intakes, snoozes, day, minute) {
        val m = meds ?: return@remember null
        val i = intakes ?: return@remember null
        val s = snoozes ?: return@remember null
        val dayOcc = occurrencesOn(day, m, i, s)
        SlotData(day, m, i, dayOcc, dayOcc.filter { it.minute == minute })
    }
}

/** Sind mit dem aktuellen Stand der Datenbank alle Einnahmen des Tages erledigt (genommen oder ausgelassen)? */
internal suspend fun isDayDone(repo: PiluniaRepository, day: LocalDate): Boolean {
    val occ = occurrencesOn(day, repo.meds(), repo.intakes(day, day))
    return occ.isNotEmpty() && occ.all { it.intake != null }
}

// =====================================================================
// Texte
// =====================================================================

/** „1 Tablette · zum Frühstück“ */
internal fun doseLine(o: Occurrence): String =
    listOf(o.med.form.amount(o.dose.amount), o.dose.label.ifBlank { o.med.hint })
        .filter { it.isNotBlank() }.joinToString(" · ")

/** „zum Frühstück“ (Hinweis der Dosis bzw. des Medikaments) */
internal fun doseHint(o: Occurrence): String = o.dose.label.ifBlank { o.med.hint }

/** „Zeit für deine Tablette!“ – mit passendem Artikel je Form. */
internal fun singleHeadline(form: MedForm): String = when (form) {
    MedForm.TROPFEN, MedForm.SPRAY -> "Zeit für deinen ${form.singular}!"
    else -> "Zeit für deine ${form.singular}!"
}

/** Überschrift für mehrere Medikamente: „Zeit für 3 Tabletten!“ bzw. „Zeit für deine Medikamente!“ */
internal fun multiHeadline(slot: List<Occurrence>): String {
    val forms = slot.map { it.med.form }.distinct()
    return if (forms.size == 1) "Zeit für ${forms[0].amount(slot.sumOf { it.dose.amount })}!"
    else "Zeit für deine Medikamente!"
}

/** „zusammen 10 mg“, wenn die Stärke eine Zahl ist. */
internal fun totalStrength(o: Occurrence): String? {
    val raw = o.med.strength.trim()
    if (raw.isBlank()) return null
    val v = raw.replace(".", "").replace(',', '.').toDoubleOrNull() ?: return null
    val nf = NumberFormat.getNumberInstance(Locale.GERMANY).apply { maximumFractionDigits = 2 }
    return "zusammen ${nf.format(v * o.dose.amount)} ${o.med.unit}".trim()
}

/** Symbol zur Tageszeit (morgens/mittags Sonne, abends Sonnenuntergang, nachts Mond). */
internal fun daypartIcon(minuteOfDay: Int): ImageVector = when (minuteOfDay / 60) {
    in 5..17 -> Lucide.Sun
    in 18..22 -> Lucide.Sunset
    else -> Lucide.Moon
}

// =====================================================================
// Bausteine
// =====================================================================

/** Feste Farbe der Abdunklung hinter dem Sheet (Tinte 50 %, in beiden Modi). */
internal val ScrimColor = Color(0xFF2E2440).copy(alpha = 0.5f)

/**
 * Grundgerüst der Erinnerungs-Screens: Statusleiste, Schließen-Kopfzeile, Inhalt (20 seitlich, 16 unten)
 * mit „spaceBetween“ wie im Design. Ist der Bildschirm zu klein, wird der Inhalt scrollbar.
 * [content] bekommt einen Faktor (0,6–1), um Figuren auf kleinen Bildschirmen zu verkleinern.
 */
@Composable
internal fun ReminderScaffold(
    background: Modifier,
    onClose: () -> Unit,
    content: @Composable ColumnScope.(scale: Float) -> Unit,
) {
    Column(Modifier.fillMaxSize().then(background)) {
        StatusBarSpace()
        CloseBar(onClose)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scale = (maxHeight / 795.dp).coerceIn(0.6f, 1f)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content(scale) }
        }
        NavBarSpace()
    }
}

/** Pill-Button 56 hoch wie im Design (Symbol mit eigener Strichstärke). */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconStroke: Float = 2.5f,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pClickable(RoundedCornerShape(28.dp), enabled, onClick = onClick)
            .background(container),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 20.dp, content, strokeWidth = iconStroke)
        Text(text, style = atkinson(17, 700), color = content, maxLines = 1)
    }
}

/** Abhak-Kreis: aus = 2er Rand „Feldrand“, an = „Mint“ mit Haken „Erfolg“ (Strich 3). */
@Composable
internal fun CheckCircle(checked: Boolean, size: Dp = 28.dp, iconSize: Dp = 16.dp) {
    AnimatedCheckCircle(checked = checked, size = size, iconSize = iconSize)
}

/** Weiß im Design auf farbigen Buttons. */
internal val OnPrimary = FixedColors.Weiss
