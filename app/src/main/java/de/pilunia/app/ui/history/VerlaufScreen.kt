package de.pilunia.app.ui.history

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trophy
import de.pilunia.app.R
import de.pilunia.app.data.Intake
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.data.Snooze
import de.pilunia.app.domain.Adherence
import de.pilunia.app.domain.DayState
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.Streaks
import de.pilunia.app.domain.adherence
import de.pilunia.app.domain.asNeededIntakesOn
import de.pilunia.app.domain.atMinute
import de.pilunia.app.domain.cyclePhase
import de.pilunia.app.domain.dayState
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.isActiveOn
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.streaks
import de.pilunia.app.domain.toMillis
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.MedIcon
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.SecondaryButton
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.SmallChip
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberMeds
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.rememberSnoozes
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.more.PSheet
import de.pilunia.app.ui.more.SheetTitle
import de.pilunia.app.ui.more.rememberSheetCloser
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Balken „verpasst“ in Statistik (im Design fest, in beiden Modi gleich). */
private val RoseBalken = Color(0xFFF48FAA)

@Composable
fun VerlaufScreen(nav: NavController, contentPadding: PaddingValues) {
    val c = Pilunia.colors
    val medsState by rememberMeds()
    val intakes by rememberIntakes()
    val snoozes by rememberSnoozes()
    val now by rememberNow()
    var showStats by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().screenBackground(c)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarSpace()
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp + contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Verlauf", style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte)
                val meds = medsState ?: return@Column
                if (meds.isEmpty()) {
                    EmptyHistory(onAdd = { nav.navigate(Routes.wizard()) })
                    return@Column
                }
                Segments(showStats) { showStats = it }
                Crossfade(showStats, animationSpec = tween(180), label = "verlauf") { stats ->
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (stats) StatsContent(meds, intakes, now)
                        else CalendarContent(meds, intakes, snoozes, now, onOpenStock = { nav.navigate(Routes.stockLow(it)) })
                    }
                }
            }
        }
    }
}

// =====================================================================
// Segment-Schalter
// =====================================================================

@Composable
private fun Segments(stats: Boolean, onChange: (Boolean) -> Unit) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.lavendelToenungKraeftig, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Segment("Kalender", !stats, Modifier.weight(1f)) { onChange(false) }
        Segment("Statistik", stats, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxHeight()
            .then(
                if (selected) Modifier.softShadow(listOf(ShadowLayer(1.dp, 3.dp, FixedColors.Schatten.copy(alpha = 0x26 / 255f))), 20.dp)
                else Modifier
            )
            .clip(shape)
            .background(if (selected) c.flaeche else Color.Transparent)
            .pClickable(shape, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = atkinson(15, 700), color = if (selected) c.tinte else c.akzentText, maxLines = 1)
    }
}

// =====================================================================
// Kalender
// =====================================================================

@Composable
private fun CalendarContent(
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    snoozes: List<Snooze>,
    now: LocalDateTime,
    onOpenStock: (Long) -> Unit,
) {
    val today = now.toLocalDate()
    var monthEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val month = LocalDate.ofEpochDay(monthEpoch)
    val selected = LocalDate.ofEpochDay(selectedEpoch)

    val byDay = remember(intakes) { intakes.groupBy { it.epochDay } }
    val gridStart = month.minusDays((month.dayOfWeek.value - 1).toLong())
    val monthEnd = month.plusMonths(1).minusDays(1)
    val gridEnd = monthEnd.plusDays((7 - monthEnd.dayOfWeek.value).toLong())
    val days = remember(monthEpoch) {
        generateSequence(gridStart) { it.plusDays(1) }.takeWhile { it <= gridEnd }.toList()
    }
    val states = remember(meds, byDay, monthEpoch, now) {
        days.filter { it.month == month.month }.associateWith { d -> dayState(d, meds, byDay[d.toEpochDay()].orEmpty(), now) }
    }
    val stockEnds = remember(meds, intakes, today) { stockEndDays(meds, intakes, today) }

    PCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthHeader(
                month,
                onPrev = { monthEpoch = month.minusMonths(1).toEpochDay() },
                onNext = { monthEpoch = month.plusMonths(1).toEpochDay() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        Text(
                            Fmt.weekdayShort(d), style = atkinson(13), color = Pilunia.colors.tinteGedaempft,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        )
                    }
                }
                days.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { d ->
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                DayCell(
                                    day = d,
                                    inMonth = d.month == month.month,
                                    state = states[d],
                                    isToday = d == today,
                                    selected = d == selected,
                                    stockEnd = stockEnds[d]?.let { ends -> if (ends.any { !it.future }) StockMark.PAST else StockMark.FUTURE },
                                ) {
                                    selectedEpoch = d.toEpochDay()
                                    if (d.month != month.month) monthEpoch = d.withDayOfMonth(1).toEpochDay()
                                }
                            }
                        }
                    }
                }
            }
            Legend()
        }
    }

    DayDetails(selected, meds, byDay[selected.toEpochDay()].orEmpty(), snoozes, now, stockEnds[selected].orEmpty(), onOpenStock)
}

/** Ein Medikament, dessen Vorrat an einem Tag endet. [future] = aus der Vorschau (gestrichelt). */
private data class StockEnd(val med: Medication, val future: Boolean, val lastCoveredDay: LocalDate?)

/** Tage, an denen ein Vorrat endet, mit den betroffenen Medikamenten. */
private fun stockEndDays(meds: List<MedWithDoses>, intakes: List<Intake>, today: LocalDate): Map<LocalDate, List<StockEnd>> {
    val out = HashMap<LocalDate, MutableList<StockEnd>>()
    for (m in meds) {
        if (m.med.scheduleType == ScheduleType.AS_NEEDED || m.doses.isEmpty()) continue
        val f = forecast(m, intakes, today)
        f.firstEmptyDay?.let { d -> out.getOrPut(d) { mutableListOf() } += StockEnd(m.med, d > today, f.lastCoveredDay) }
        // Bereits leer: erster Tag nach der letzten Einnahme.
        if (m.med.stock <= 0) {
            val lastTaken = intakes.filter { it.medId == m.med.id && it.takenAt != null }.maxOfOrNull { it.epochDay }
            val emptyDay = lastTaken?.let { LocalDate.ofEpochDay(it + 1) }
            if (emptyDay != null && emptyDay <= today && out[emptyDay]?.none { it.med.id == m.med.id } != false) {
                out.getOrPut(emptyDay) { mutableListOf() } += StockEnd(m.med, false, emptyDay.minusDays(1))
            }
        }
    }
    return out
}

/**
 * Hinweis über der Tagesliste, wenn an diesem Tag ein Vorrat endet: welches Medikament,
 * seit/ab wann leer und wie viel noch da ist. Tippen öffnet „Vorrat niedrig“.
 */
@Composable
private fun StockEndCard(ends: List<StockEnd>, day: LocalDate, onOpen: (Long) -> Unit) {
    val c = Pilunia.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.warnungFlaeche),
    ) {
        ends.forEachIndexed { i, e ->
            val med = e.med
            val title = if (e.future) "Vorrat von ${med.name} endet" else "Vorrat von ${med.name} war leer"
            val sub = buildString {
                if (e.future) {
                    append("Noch ${med.form.amount(med.stock)}")
                    e.lastCoveredDay?.let { append(" – reicht bis ${Fmt.dayShort(it)}") }
                } else {
                    append("Ab diesem Tag war der Vorrat aufgebraucht")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .pClickable(onClick = { onOpen(med.id) })
                    .then(if (i < ends.lastIndex) Modifier.drawBehind {
                        val y = size.height - 0.5.dp.toPx()
                        drawLine(c.warnungGedaempft, Offset(16.dp.toPx(), y), Offset(size.width - 16.dp.toPx(), y), 1.dp.toPx())
                    } else Modifier)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedIcon(med.color, med.form, size = 40.dp, radius = 14.dp, iconSize = 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = atkinson(16, 700), color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(sub, style = atkinson(14, lineHeight = 1.35f), color = c.warnung)
                }
                LIcon(Lucide.ChevronRight, 22.dp, c.warnung)
            }
        }
    }
}

@Composable
private fun MonthHeader(month: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    val c = Pilunia.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButtonP(Lucide.ChevronLeft, "Vorheriger Monat", onPrev, size = 44.dp, iconSize = 22.dp)
        Text(Fmt.monthYear(month), style = outfit(18, 600), color = c.tinte)
        IconButtonP(Lucide.ChevronRight, "Nächster Monat", onNext, size = 44.dp, iconSize = 22.dp)
    }
}

private enum class StockMark { PAST, FUTURE }

/** Tag im Kalender: 40 × 40, Kreis, Zustandsfarben wie im Design. */
@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    state: DayState?,
    isToday: Boolean,
    selected: Boolean,
    stockEnd: StockMark?,
    onClick: () -> Unit,
) {
    val c = Pilunia.colors
    var bg = Color.Transparent
    var fg = c.tinte
    var weight = 500
    when {
        !inMonth -> fg = c.tinteGedaempft
        isToday -> { bg = c.primaer; fg = FixedColors.Weiss; weight = 700 }
        state == DayState.ALL_TAKEN -> { bg = c.mintFlaeche; fg = c.erfolg; weight = 600 }
        state == DayState.MISSED || state == DayState.PARTIAL -> { bg = c.warnungFlaeche; fg = c.warnung; weight = 600 }
        stockEnd != null -> { fg = c.warnung; weight = 600 }
    }
    val ring: Color? = when {
        selected && !isToday -> c.tinte
        stockEnd != null && inMonth && !isToday -> c.warnungKraeftig
        else -> null
    }
    val dashed = ring != null && !selected && stockEnd == StockMark.FUTURE
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .pClickable(CircleShape, onClick = onClick)
            .then(
                if (ring != null) Modifier.drawBehind {
                    val w = 2.dp.toPx()
                    drawCircle(
                        ring, radius = size.minDimension / 2 - w / 2,
                        style = Stroke(w, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null),
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text("${day.dayOfMonth}", style = outfit(16, weight), color = fg)
    }
}

@Composable
private fun Legend() {
    val c = Pilunia.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("Alles genommen") { Box(Modifier.size(12.dp).background(c.mint, CircleShape)) }
        LegendItem("Verpasst") { Box(Modifier.size(12.dp).background(c.rose, CircleShape)) }
        LegendItem("Vorrat endet") { Box(Modifier.size(12.dp).border(2.dp, c.warnungKraeftig, CircleShape)) }
    }
}

@Composable
private fun LegendItem(text: String, dot: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        dot()
        Text(text, style = atkinson(13), color = Pilunia.colors.tinteGedaempft, maxLines = 1)
    }
}

// =====================================================================
// Tagesdetails
// =====================================================================

/** Eintrag der Tagesliste: geplante Einnahme oder Bei-Bedarf-Einnahme. */
private sealed interface DayItem {
    val minute: Int
    data class Planned(val occ: Occurrence) : DayItem { override val minute get() = occ.minute }
    data class AsNeeded(val intake: Intake, val med: Medication) : DayItem { override val minute get() = intake.minuteOfDay }
}

@Composable
private fun DayDetails(
    day: LocalDate,
    meds: List<MedWithDoses>,
    dayIntakes: List<Intake>,
    snoozes: List<Snooze>,
    now: LocalDateTime,
    stockEnds: List<StockEnd>,
    onOpenStock: (Long) -> Unit,
) {
    val c = Pilunia.colors
    val occ = remember(day, meds, dayIntakes, snoozes) { occurrencesOn(day, meds, dayIntakes, snoozes) }
    val asNeeded = remember(day, meds, dayIntakes) {
        asNeededIntakesOn(day, dayIntakes).mapNotNull { i -> meds.find { it.med.id == i.medId }?.let { DayItem.AsNeeded(i, it.med) } }
    }
    val items: List<DayItem> = (occ.map { DayItem.Planned(it) } + asNeeded).sortedBy { it.minute }
    var edit by remember { mutableStateOf<DayItem?>(null) }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.dayLong(day), style = outfit(20, 600), color = c.tinte,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            val count = when {
                occ.isNotEmpty() -> "${occ.count { it.status(now) == OccStatus.TAKEN }} von ${occ.size}"
                asNeeded.isNotEmpty() -> "${asNeeded.size} bei Bedarf"
                else -> null
            }
            if (count != null) Text(count, style = atkinson(14), color = c.tinteGedaempft, modifier = Modifier.padding(start = 12.dp))
        }
        if (stockEnds.isNotEmpty()) StockEndCard(stockEnds, day, onOpenStock)
        PCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (items.isEmpty()) {
                Text(
                    emptyDayText(day, meds, now.toLocalDate()), style = atkinson(15, lineHeight = 1.4f), color = c.tinteGedaempft,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
            items.forEachIndexed { i, item ->
                val last = i == items.lastIndex
                when (item) {
                    is DayItem.Planned -> PlannedRow(item.occ, now, last) { edit = item }
                    is DayItem.AsNeeded -> AsNeededRow(item, last) { edit = item }
                }
            }
        }
    }

    edit?.let { EditSheet(it, now) { edit = null } }
}

private fun emptyDayText(day: LocalDate, meds: List<MedWithDoses>, today: LocalDate): String {
    val pause = meds.firstNotNullOfOrNull { m ->
        m.med.takeIf { it.isActiveOn(day) }?.cyclePhase(day)?.takeIf { it.inPause }?.let { m.med.name to it }
    }
    return when {
        pause != null -> "Einnahmepause bei ${pause.first} (Tag ${pause.second.dayInPhase} von ${pause.second.phaseLength})."
        day > today -> "Für diesen Tag ist nichts geplant."
        else -> "An diesem Tag war nichts geplant."
    }
}

@Composable
private fun EntryRow(
    med: Medication,
    subtitle: String,
    last: Boolean,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pClickable(onClick = onClick) else Modifier)
            .then(if (!last) Modifier.drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            } else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(med.color, med.form, size = 40.dp, radius = 14.dp, iconSize = 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(med.displayName, style = atkinson(16, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = atkinson(14), color = c.tinteGedaempft)
        }
        trailing()
    }
}

@Composable
private fun CheckBadge() {
    val c = Pilunia.colors
    Box(Modifier.size(28.dp).background(c.mint, CircleShape), contentAlignment = Alignment.Center) {
        LIcon(Lucide.Check, 16.dp, c.erfolg, strokeWidth = 3f, contentDescription = "Eingenommen")
    }
}

@Composable
private fun PlannedRow(o: Occurrence, now: LocalDateTime, last: Boolean, onClick: () -> Unit) {
    val c = Pilunia.colors
    val status = o.status(now)
    val planned = "${Fmt.time(o.minute)} geplant"
    val amount = if (o.dose.amount != 1) " · ${o.med.form.amount(o.dose.amount)}" else ""
    val subtitle = when (status) {
        OccStatus.TAKEN -> {
            val t = o.intake?.takenAt?.let { " · ${Fmt.time(it)} genommen" } ?: " · genommen"
            val part = o.intake?.amount?.takeIf { it in 1 until o.dose.amount }?.let { " ($it von ${o.dose.amount})" }.orEmpty()
            planned + t + part
        }
        OccStatus.SNOOZED -> planned + (o.snoozedUntil?.let { " · später um ${Fmt.time(it)}" } ?: "")
        else -> planned + amount
    }
    EntryRow(o.med, subtitle, last, onClick.takeIf { status != OccStatus.UPCOMING }) {
        when (status) {
            OccStatus.TAKEN -> CheckBadge()
            OccStatus.MISSED -> SmallChip("Verpasst", c.warnungFlaeche, c.warnung)
            OccStatus.SKIPPED -> SmallChip("Ausgelassen", c.lavendelToenung, c.tinteGedaempft)
            OccStatus.OPEN -> SmallChip("Offen", c.lavendelToenungKraeftig, c.akzentText)
            OccStatus.SNOOZED -> SmallChip("Später", c.gelbFlaeche, c.gelb)
            OccStatus.UPCOMING -> Box(Modifier.size(28.dp).border(2.dp, c.lavendelInaktiv, CircleShape))
        }
    }
}

@Composable
private fun AsNeededRow(item: DayItem.AsNeeded, last: Boolean, onClick: () -> Unit) {
    val i = item.intake
    val time = i.takenAt?.let { Fmt.time(it) } ?: Fmt.time(i.minuteOfDay)
    val amount = if (i.amount != 1) " · ${item.med.form.amount(i.amount)}" else ""
    EntryRow(item.med, "Bei Bedarf · $time genommen$amount", last, onClick) { CheckBadge() }
}

/** Sheet zum nachträglichen Ändern einer Einnahme. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(item: DayItem, now: LocalDateTime, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.repository
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(state, onDismiss)
    fun run(block: suspend () -> Unit) = close { container.scope.launch { block() } }

    PSheet(onDismiss, state) {
        when (item) {
            is DayItem.Planned -> {
                val o = item.occ
                val status = o.status(now)
                SheetTitle(o.med.displayName, "${Fmt.dayLong(o.day)} · ${Fmt.time(o.minute)} geplant · ${o.med.form.amount(o.dose.amount)}")
                if (status != OccStatus.TAKEN) {
                    PrimaryButton("Als genommen markieren", icon = Lucide.Check, onClick = {
                        // Vergangene Tage: zur geplanten Zeit; heute: jetzt.
                        val at = if (o.day < now.toLocalDate()) o.day.atMinute(o.minute).toMillis() else System.currentTimeMillis()
                        run { repo.take(o, at = at) }
                    })
                }
                if (status == OccStatus.MISSED || status == OccStatus.OPEN || status == OccStatus.SNOOZED) {
                    SecondaryButton("Als ausgelassen markieren", onClick = { run { repo.skip(listOf(o)) } })
                }
                if (status == OccStatus.TAKEN || status == OccStatus.SKIPPED) {
                    SecondaryButton(
                        if (status == OccStatus.TAKEN) "Zurücknehmen" else "Auslassen zurücknehmen",
                        onClick = { run { repo.undo(o) } },
                    )
                }
            }
            is DayItem.AsNeeded -> {
                val i = item.intake
                SheetTitle(
                    item.med.displayName,
                    "${Fmt.dayLong(LocalDate.ofEpochDay(i.epochDay))} · bei Bedarf um ${i.takenAt?.let { Fmt.time(it) } ?: Fmt.time(i.minuteOfDay)} genommen",
                )
                SecondaryButton("Zurücknehmen", onClick = { run { repo.undoIntake(i) } })
            }
        }
        TextButtonP("Abbrechen", onClick = { close {} }, modifier = Modifier.fillMaxWidth())
    }
}

// =====================================================================
// Statistik
// =====================================================================

private data class WeekBar(val day: LocalDate, val fraction: Float, val missed: Boolean, val isToday: Boolean, val future: Boolean)
private data class MedStat(val name: String, val percent: Int, val missed: Int)
private data class Stats(val adherence: Adherence, val streaks: Streaks, val week: List<WeekBar>, val perMed: List<MedStat>)

@Composable
private fun StatsContent(meds: List<MedWithDoses>, intakes: List<Intake>, now: LocalDateTime) {
    val stats by produceState<Stats?>(null, meds, intakes, now) {
        value = withContext(Dispatchers.Default) { computeStats(meds, intakes, now) }
    }
    val s = stats ?: return
    AdherenceCard(s.adherence)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val c = Pilunia.colors
        StatTile(Lucide.Heart, c.warnungFlaeche, c.warnungKraeftig, days(s.streaks.current), "Aktuelle Serie", Modifier.weight(1f), strokeWidth = 2.5f)
        StatTile(Lucide.Trophy, c.gelbFlaeche, c.gelb, days(s.streaks.longest), "Längste Serie", Modifier.weight(1f))
        StatTile(Lucide.CircleX, c.lavendelToenung, c.pflaume, "${s.adherence.missed}", "Verpasst", Modifier.weight(1f))
    }
    WeekCard(s.week)
    if (s.perMed.isNotEmpty()) PerMedCard(s.perMed)
}

private fun days(n: Int) = if (n == 1) "1 Tag" else "$n Tage"

private fun computeStats(meds: List<MedWithDoses>, intakes: List<Intake>, now: LocalDateTime): Stats {
    val today = now.toLocalDate()
    val from = today.minusDays(29)
    val planned = meds.filter { it.med.scheduleType != ScheduleType.AS_NEEDED }
    val byDay = intakes.groupBy { it.epochDay }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val week = (0..6).map { i ->
        val d = monday.plusDays(i.toLong())
        if (d > today) WeekBar(d, 0f, missed = false, isToday = false, future = true)
        else {
            val occ = occurrencesOn(d, planned, byDay[d.toEpochDay()].orEmpty())
            val st = occ.map { it.status(now) }
            val frac = if (occ.isEmpty()) 0f else st.count { it == OccStatus.TAKEN } / occ.size.toFloat()
            WeekBar(d, frac, st.any { it == OccStatus.MISSED || it == OccStatus.SKIPPED }, d == today, future = false)
        }
    }
    val perMed = planned.mapNotNull { m ->
        val a = adherence(from, today, listOf(m), intakes, now)
        if (a.due == 0) null else MedStat(m.med.name, a.percent, a.missed)
    }.sortedWith(compareBy({ it.percent }, { it.name.lowercase() }))
    return Stats(adherence(from, today, planned, intakes, now), streaks(planned, intakes, now), week, perMed)
}

@Composable
private fun AdherenceCard(a: Adherence) {
    val c = Pilunia.colors
    PCard(Modifier.fillMaxWidth(), padding = PaddingValues(20.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                val track = c.lavendelToenungKraeftig
                val value = c.primaer
                val sweep = if (a.due == 0) 0f else 360f * a.taken / a.due
                Canvas(Modifier.size(136.dp)) {
                    val w = 14.dp.toPx()
                    val inset = w / 2
                    val arcSize = Size(size.width - w, size.height - w)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
                    if (sweep > 0f) drawArc(value, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(w))
                }
                Text(
                    if (a.due == 0) "–" else "${a.percent} %",
                    style = outfit(32, 800, letterSpacing = -0.8f), color = c.tinte, textAlign = TextAlign.Center,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Einnahmetreue", style = outfit(20, 600), color = c.tinte)
                Text(
                    if (a.due == 0) "Noch keine fälligen Einnahmen in den letzten 30 Tagen"
                    else "${a.taken} von ${a.due} Einnahmen in den letzten 30 Tagen",
                    style = atkinson(14, lineHeight = 1.4f), color = c.tinteGedaempft,
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tile: Color,
    tint: Color,
    value: String,
    label: String,
    modifier: Modifier,
    strokeWidth: Float = 2f,
) {
    val c = Pilunia.colors
    PCard(modifier.fillMaxHeight(), radius = 20.dp, padding = PaddingValues(14.dp)) {
        Box(Modifier.size(32.dp).background(tile, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            LIcon(icon, 18.dp, tint, strokeWidth = strokeWidth)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = outfit(22, 700), color = c.tinte, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(label, style = atkinson(13), color = c.tinteGedaempft)
    }
}

@Composable
private fun WeekCard(week: List<WeekBar>) {
    val c = Pilunia.colors
    val first = week.first().day
    val last = week.last().day
    val range = if (first.month == last.month) "${first.dayOfMonth}. – ${last.dayOfMonth}. ${Fmt.monthShort(last)}"
    else "${Fmt.dayMonthShort(first)} – ${Fmt.dayMonthShort(last)}"
    PCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Diese Woche", style = outfit(18, 600), color = c.tinte)
                Text(range, style = atkinson(14), color = c.tinteGedaempft)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { WeekBarItem(it) }
            }
        }
    }
}

@Composable
private fun WeekBarItem(b: WeekBar) {
    val c = Pilunia.colors
    val trackH = 96.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(28.dp, trackH)
                .clip(RoundedCornerShape(14.dp))
                .background(c.lavendelToenung)
                .then(
                    if (b.isToday) Modifier.drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            c.feldrand,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx() - w / 2),
                            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    } else Modifier
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (!b.future && b.fraction > 0f) {
                val h: Dp = (trackH * b.fraction).coerceAtLeast(28.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(h)
                        .background(if (b.missed) RoseBalken else c.primaer, RoundedCornerShape(14.dp))
                )
            }
        }
        Text(
            Fmt.weekdayShort(b.day),
            style = atkinson(13, if (b.isToday) 700 else 400),
            color = if (b.isToday) c.tinte else c.tinteGedaempft,
        )
    }
}

@Composable
private fun PerMedCard(list: List<MedStat>) {
    val c = Pilunia.colors
    PCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Nach Medikament", style = outfit(18, 600), color = c.tinte)
            list.forEach { m ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.name, style = atkinson(15, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
                        )
                        Text(
                            if (m.missed > 0) "${m.percent} % · ${m.missed} verpasst" else "${m.percent} %",
                            style = atkinson(14, 700), color = if (m.missed > 0) c.warnung else c.tinte, maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (m.missed > 0) c.warnungFlaeche else c.lavendelToenungKraeftig)
                    ) {
                        if (m.percent > 0) Box(
                            Modifier
                                .fillMaxWidth(m.percent / 100f)
                                .height(8.dp)
                                .background(if (m.missed > 0) RoseBalken else c.primaer, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Leerer Zustand
// =====================================================================

@Composable
private fun EmptyHistory(onAdd: () -> Unit) {
    val c = Pilunia.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PiluniaImage(R.drawable.pilunia_kalender, "Pilunia mit Kalender", Modifier.size(180.dp))
        Text("Noch kein Verlauf", style = outfit(22, 600), color = c.tinte, textAlign = TextAlign.Center)
        Text(
            "Lege dein erstes Medikament an – dann siehst du hier jeden Tag, was du genommen hast, und wie treu du dabei bleibst.",
            style = atkinson(15, lineHeight = 1.4f), color = c.tinteGedaempft, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Medikament hinzufügen", onClick = onAdd, icon = Lucide.Plus)
    }
}
