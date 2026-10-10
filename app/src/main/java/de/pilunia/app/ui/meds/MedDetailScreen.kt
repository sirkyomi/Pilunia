package de.pilunia.app.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavController
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Package
import com.composables.icons.lucide.PackagePlus
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Repeat
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.BlisterSlot
import de.pilunia.app.domain.CyclePhase
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.StockForecast
import de.pilunia.app.domain.blisterState
import de.pilunia.app.domain.cyclePhase
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.isActiveOn
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.toLocalDateTime
import de.pilunia.app.ui.components.icon
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.PSwitch
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.palette
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.nav.backOrMain
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 13 Medikament-Detail (Blister) bzw. 13a während einer Einnahmepause. */
@Composable
fun MedDetailScreen(nav: NavController, id: Long) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val repo = container.repository
    val flow = remember(id) { repo.observeMed(id) }
    val data by flow.collectAsState(initial = null)
    val intakes by rememberIntakes()
    val now by rememberNow()
    val today = now.toLocalDate()

    var toast by remember { mutableStateOf<ToastData?>(null) }
    var stockSheet by remember { mutableStateOf(false) }
    var noteSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val m = data
    if (m == null) {
        Box(Modifier.fillMaxSize().background(c.hintergrund))
        return
    }
    val med = m.med
    val medIntakes = remember(intakes, med.id) { intakes.filter { it.medId == med.id } }
    val asNeeded = med.scheduleType == ScheduleType.AS_NEEDED
    val phase = if (asNeeded) null else med.cyclePhase(today)
    val inPause = phase?.inPause == true && med.isActiveOn(today)
    val fc = remember(m, intakes, today) { forecast(m, intakes, today) }

    fun launch(block: suspend () -> Unit) {
        container.scope.launch { block() }
    }

    val addPack = {
        val before = med.stock
        launch { repo.addPack(med.id) }
        toast = ToastData(
            "Neue Packung eingetragen · ${med.form.amount(before.coerceAtLeast(0) + med.packSize)}",
            "Rückgängig",
        ) { launch { repo.setStock(med.id, before) } }
    }

    Box(Modifier.fillMaxSize().medBackground(c, med.color)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarSpace()
            // Kopfzeile 56: Zurück links, Bearbeiten rechts.
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButtonP(Lucide.ArrowLeft, "Zurück", { nav.backOrMain() })
                IconButtonP(Lucide.Pencil, "Medikament bearbeiten", { nav.navigate(Routes.wizard(med.id, 0)) }, iconSize = 22.dp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MedHead(m)
                if (inPause && phase != null) {
                    CycleCard(med, phase, medIntakes, today)
                    CardGroup("Während der Pause") {
                        RowsCard(rows = 2) {
                            DetailRow(
                                Lucide.BellOff, "Einnahme-Erinnerung pausiert",
                                "Bis ${Fmt.dayShort(phase.phaseEnd)} keine Mitteilung",
                                divider = true, trailing = null,
                            )
                            DetailRow(
                                Lucide.BellRing, "Start-Erinnerung",
                                "Am Vorabend, ${Fmt.weekdayShort(phase.phaseEnd)} um 20:00",
                                onClick = { launch { repo.updateMedication(med.copy(pauseEndReminder = !med.pauseEndReminder)) } },
                                trailing = {
                                    PSwitch(med.pauseEndReminder, { v -> launch { repo.updateMedication(med.copy(pauseEndReminder = v)) } })
                                },
                            )
                        }
                    }
                    CardGroup("Einnahmeplan") {
                        RowsCard(rows = 2) {
                            val times = m.sortedDoses.joinToString(", ") { Fmt.time(it.minuteOfDay) }
                            val start = Fmt.weekdayLong(phase.nextCycleStart).lowercase() + "s"
                            DetailRow(
                                Lucide.Repeat, "${med.intakeDays} Tage nehmen, ${med.pauseDays} Tage Pause",
                                listOf(if (times.isEmpty()) "" else "${med.dayPrefix()} um $times", "Start $start")
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                divider = true,
                                onClick = { nav.navigate(Routes.wizard(med.id, 1)) },
                            )
                            DetailRow(
                                Lucide.Package, stockRowTitle(med), reachText(fc, med, today),
                                onClick = { stockSheet = true },
                            )
                        }
                    }
                } else {
                    StockCard(med, fc, medIntakes, today) { stockSheet = true }
                    RowsCard(rows = 2) {
                        DetailRow(
                            Lucide.BellRing, "Vorrats-Erinnerung",
                            "${med.warnDays} ${if (med.warnDays == 1) "Tag" else "Tage"} bevor der Vorrat endet",
                            divider = true,
                            onClick = { launch { repo.updateMedication(med.copy(stockReminder = !med.stockReminder)) } },
                            trailing = {
                                PSwitch(med.stockReminder, { v -> launch { repo.updateMedication(med.copy(stockReminder = v)) } })
                            },
                        )
                        DetailRow(
                            Lucide.FileText, "Rezept-Notiz",
                            med.prescriptionNote.ifBlank { "Notiz hinzufügen" },
                            onClick = { noteSheet = true },
                        )
                    }
                    if (asNeeded) {
                        AsNeededCard(m, medIntakes) { amount ->
                            val at = System.currentTimeMillis()
                            launch { repo.takeAsNeeded(med.id, amount, at) }
                            toast = ToastData("Einnahme eingetragen · ${Fmt.time(at)}", "Rückgängig") {
                                launch {
                                    repo.intakes(today, today).firstOrNull { it.medId == med.id && it.doseId == -at }
                                        ?.let { repo.undoIntake(it) }
                                }
                            }
                        }
                    } else {
                        PlanCard(m, phase) { nav.navigate(Routes.wizard(med.id, 1)) }
                    }
                    WeekCard(m, intakes, medIntakes, now)
                }
                TextButtonP(
                    "Medikament löschen", onClick = { confirmDelete = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = c.warnung, icon = Lucide.Trash2,
                )
            }
            // Aktionsleiste: fester Button unten.
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)) {
                ActionButton("Neue Packung eintragen", Lucide.PackagePlus, addPack)
            }
            NavBarSpace()
        }
        PToastHost(
            toast, { toast = null },
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
        )
    }

    if (stockSheet) {
        StockSheet(med, onDismiss = { stockSheet = false }) { v ->
            stockSheet = false
            val before = med.stock
            if (v != before) {
                launch { repo.setStock(med.id, v) }
                toast = ToastData("Vorrat auf ${med.form.amount(v)} gesetzt", "Rückgängig") { launch { repo.setStock(med.id, before) } }
            }
        }
    }
    if (noteSheet) {
        NoteSheet(med, onDismiss = { noteSheet = false }) { note ->
            noteSheet = false
            launch { repo.updateMedication(med.copy(prescriptionNote = note)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            icon = Lucide.Trash2,
            title = "${med.name} löschen?",
            text = "Alle Erinnerungen und der Verlauf dieses Medikaments werden entfernt. Das lässt sich nicht rückgängig machen.",
            confirmLabel = "Löschen",
            onConfirm = {
                confirmDelete = false
                launch { repo.deleteMedication(med) }
                nav.backOrMain()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

// =====================================================================
// Kopf
// =====================================================================

@Composable
private fun MedHead(m: MedWithDoses) {
    val c = Pilunia.colors
    val med = m.med
    val iconColor = if (med.color == MedColor.LAVENDEL) c.primaer else c.palette(med.color).icon
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.flaeche), contentAlignment = Alignment.Center) {
            LIcon(med.form.icon, 32.dp, iconColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                med.displayName, style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
                color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(m.detailSubtitle(), style = atkinson(15), color = c.tinteGedaempft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =====================================================================
// Vorrat
// =====================================================================

private fun isLow(fc: StockForecast, med: Medication) = med.stock <= 0 || fc.isLow(med.warnDays)

/** „reicht bis Do, 15. Okt.“ bzw. Ersatztexte. */
private fun reachText(fc: StockForecast, med: Medication, today: LocalDate): String {
    val last = fc.lastCoveredDay
    return when {
        med.stock <= 0 -> "Vorrat aufgebraucht"
        last != null && last < today -> "Vorrat aufgebraucht"
        last != null -> "reicht bis ${Fmt.dayShortWithYear(last)}"
        med.scheduleType == ScheduleType.AS_NEEDED -> "bei Bedarf"
        med.endDate != null -> "reicht bis zum Ende"
        else -> "reicht über ein Jahr"
    }
}

private fun stockRowTitle(med: Medication): String {
    val stock = med.stock.coerceAtLeast(0)
    return if (med.usesBlister) {
        val n = (stock + med.unitsPerBlister - 1) / med.unitsPerBlister.coerceAtLeast(1)
        "$n Blister übrig"
    } else "$stock ${med.form.leftLabel(stock)}"
}

/** Vorratskarte: große Zahl, Reichweite, Blister, Packungsinfo und letzte Einnahme. */
@Composable
private fun StockCard(med: Medication, fc: StockForecast, medIntakes: List<Intake>, today: LocalDate, onEditStock: () -> Unit) {
    val c = Pilunia.colors
    val stock = med.stock.coerceAtLeast(0)
    val low = isLow(fc, med)
    val takenToday = medIntakes.any { it.epochDay == today.toEpochDay() && it.status == IntakeStatus.TAKEN && it.amount > 0 }
    val lastTaken = medIntakes.filter { it.status == IntakeStatus.TAKEN && it.takenAt != null }.maxByOrNull { it.takenAt!! }
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.flaeche)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Kennzahl – Tippen öffnet „Vorrat korrigieren“.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Vorrat korrigieren", role = Role.Button, onClick = onEditStock),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$stock", style = outfit(72, 800, lineHeight = 1f, letterSpacing = -2f), color = c.tinte, maxLines = 1)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(med.form.leftLabel(stock), style = atkinson(17, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (med.scheduleType != ScheduleType.AS_NEEDED || stock <= 0) {
                    ReachChip(reachText(fc, med, today), low)
                }
            }
        }
        if (med.usesBlister) {
            val b = blisterState(med, takenToday)
            BlisterView(b.slots)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                packInfo(med), style = atkinson(14), color = c.tinteGedaempft,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
            )
            if (lastTaken != null) {
                val at = lastTaken.takenAt!!.toLocalDateTime()
                Text(
                    "${Fmt.relativeDay(at.toLocalDate(), today).capitalizeFirst()} ${Fmt.time(at.toLocalTime())}",
                    style = atkinson(14), color = c.tinteGedaempft, maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

private fun packInfo(med: Medication): String {
    val stock = med.stock.coerceAtLeast(0)
    if (!med.usesBlister) return "Packung mit ${med.form.amount(med.packSize)}"
    val upb = med.unitsPerBlister.coerceAtLeast(1)
    val blistersLeft = (stock + upb - 1) / upb
    return if (stock > med.packSize) "$blistersLeft Blister übrig · Packung mit ${med.packSize}"
    else {
        val b = blisterState(med, false)
        "Blister ${b.index} von ${b.count} · Packung mit ${med.packSize}"
    }
}

/** Chip „reicht bis …“: 28 hoch, Radius 14, Symbol calendar-clock 16, Atkinson 13/700. */
@Composable
private fun ReachChip(text: String, low: Boolean) {
    val c = Pilunia.colors
    val bg = if (low) c.warnungFlaeche else c.mintFlaeche
    val fg = if (low) c.warnung else c.erfolg
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(start = 8.dp, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.CalendarClock, 16.dp, fg)
        Text(text, style = atkinson(13, 700), color = fg, maxLines = 1)
    }
}

/** Spaltenzahl für eine Blistergröße (14 → 7 × 2, 10 → 5 × 2, 20 → 5 × 4 …). */
private fun blisterColumns(n: Int): Int = when {
    n <= 7 -> n
    n % 7 == 0 && n <= 28 -> 7
    n <= 12 && n % 2 == 0 -> n / 2
    n % 5 == 0 && n <= 20 -> 5
    n % 6 == 0 && n <= 24 -> 6
    n % 10 == 0 -> 10
    n % 8 == 0 -> 8
    else -> 7
}

private val BlisterEmptyFill = Color(0xFFE4DBF0)
private val BlisterEmptyStroke = Color(0xFFB9A9D6)
private val BlisterMint = Color(0xFFA7E3C1)
private val BlisterCheck = Color(0xFF1F7A4D)
private val PillTop = Color(0xFFFFFFFF)
private val PillBottom = Color(0xFFF0EAF8)

/** Blister: Verlauf #F1ECF8 → #D8CEE8, Radius 20, Mulden 34 im Raster (Abstand 8 / Zeilen 14). */
@Composable
private fun BlisterView(slots: List<BlisterSlot>) {
    val cols = blisterColumns(slots.size).coerceAtLeast(1)
    val shape = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFF1ECF8), Color(0xFFD8CEE8)), start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        val cell = (maxWidth - 8.dp * (cols - 1)) / cols
        val dot = min(34.dp, cell)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            slots.chunked(cols).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until cols) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            row.getOrNull(i)?.let { BlisterDot(it, dot) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlisterDot(slot: BlisterSlot, size: Dp) {
    when (slot) {
        BlisterSlot.EMPTY -> Box(
            Modifier.size(size).background(BlisterEmptyFill, CircleShape).dashedCircle(BlisterEmptyStroke, 1.5.dp, 3f, 3f)
        )
        BlisterSlot.JUST_TAKEN -> Box(
            Modifier.size(size).background(BlisterMint, CircleShape),
            contentAlignment = Alignment.Center,
        ) { LIcon(Lucide.Check, size * 0.47f, BlisterCheck, strokeWidth = 3f, contentDescription = "Gerade genommen") }
        BlisterSlot.FULL -> Box(
            Modifier
                .size(size)
                .softShadow(listOf(ShadowLayer(2.dp, 4.dp, Color(0x456B4C93))), size / 2)
                .drawBehind {
                    drawCircle(
                        Brush.linearGradient(
                            listOf(PillTop, PillBottom),
                            start = Offset(size.toPx() * 0.3f, 0f),
                            end = Offset(size.toPx() * 0.7f, size.toPx()),
                        )
                    )
                }
        )
    }
}

// =====================================================================
// Plan
// =====================================================================

@Composable
private fun PlanCard(m: MedWithDoses, phase: CyclePhase?, onEdit: () -> Unit) {
    val med = m.med
    val doses = m.sortedDoses
    val cycleRow = med.isCycle && phase != null
    val rows = (if (cycleRow) 1 else 0) + doses.size.coerceAtLeast(1)
    RowsCard(rows) {
        if (cycleRow && phase != null) {
            DetailRow(
                Lucide.Repeat, "${med.intakeDays} Tage nehmen, ${med.pauseDays} Tage Pause",
                "Einnahme · Tag ${phase.dayInPhase} von ${phase.phaseLength} · Pause ab ${Fmt.dayShort(phase.nextPauseStart)}",
                divider = true, onClick = onEdit,
            )
        }
        if (doses.isEmpty()) {
            DetailRow(Lucide.Clock, "Noch keine Uhrzeit", "Einnahmeplan festlegen", onClick = onEdit)
        }
        doses.forEachIndexed { i, d ->
            DetailRow(
                Lucide.Clock, "${med.dayPrefix()} um ${Fmt.time(d.minuteOfDay)}",
                doseSubtitle(med.form, d),
                divider = i < doses.lastIndex,
                onClick = onEdit,
            )
        }
    }
}

/** „Bei Bedarf“: Einnahme direkt eintragen. */
@Composable
private fun AsNeededCard(m: MedWithDoses, medIntakes: List<Intake>, onTake: (Int) -> Unit) {
    val c = Pilunia.colors
    val med = m.med
    val amount = m.sortedDoses.firstOrNull()?.amount ?: 1
    val sub = listOf(med.form.amount(amount), med.hint.trim()).filter { it.isNotEmpty() }.joinToString(" · ")
    RowsCard(rows = 1) {
        DetailRow(
            med.form.icon, "Einnahme eintragen", sub,
            onClick = { onTake(amount) },
            trailing = {
                Box(Modifier.size(40.dp).background(c.primaer, CircleShape), contentAlignment = Alignment.Center) {
                    LIcon(Lucide.Plus, 20.dp, FixedColors.Weiss, strokeWidth = 2.5f, contentDescription = "Einnahme eintragen")
                }
            },
        )
    }
}

// =====================================================================
// Diese Woche
// =====================================================================

private enum class WeekDot { TAKEN, MISSED, OPEN_TODAY, FUTURE, NONE, PAUSE }

@Composable
private fun WeekCard(m: MedWithDoses, intakes: List<Intake>, medIntakes: List<Intake>, now: LocalDateTime) {
    val c = Pilunia.colors
    val med = m.med
    val today = now.toLocalDate()
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val asNeeded = med.scheduleType == ScheduleType.AS_NEEDED

    var taken = 0
    var due = 0
    val states = days.map { d ->
        if (asNeeded) {
            val n = medIntakes.count { it.epochDay == d.toEpochDay() && it.status == IntakeStatus.TAKEN }
            taken += n
            when {
                n > 0 -> WeekDot.TAKEN
                d > today -> WeekDot.FUTURE
                else -> WeekDot.NONE
            }
        } else {
            val occ = occurrencesOn(d, listOf(m), intakes)
            if (occ.isEmpty()) {
                if (med.isActiveOn(d) && med.cyclePhase(d)?.inPause == true) WeekDot.PAUSE else WeekDot.NONE
            } else if (d > today) WeekDot.FUTURE
            else {
                val st = occ.map { it.status(now) }
                taken += st.count { it == OccStatus.TAKEN }
                due += if (d < today) st.size else st.count { it != OccStatus.UPCOMING }
                when {
                    st.all { it == OccStatus.TAKEN } -> WeekDot.TAKEN
                    d < today || st.any { it == OccStatus.SKIPPED } -> WeekDot.MISSED
                    else -> WeekDot.OPEN_TODAY
                }
            }
        }
    }
    val countText = when {
        asNeeded -> when (taken) { 0 -> "Keine Einnahme"; 1 -> "1 Einnahme"; else -> "$taken Einnahmen" }
        due == 0 -> ""
        else -> "$taken von $due genommen"
    }
    val countColor = if (!asNeeded && due > 0 && taken < due) c.tinteGedaempft else c.erfolg

    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.flaeche)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Diese Woche", style = atkinson(16, 700), color = c.tinte)
            if (countText.isNotEmpty()) Text(countText, style = atkinson(14, 700), color = countColor)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                val isToday = d == today
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeekCircle(states[i], isToday)
                    Text(
                        Fmt.weekdayShort(d),
                        style = atkinson(13, if (isToday) 700 else 400),
                        color = if (isToday) c.tinte else c.tinteGedaempft,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCircle(state: WeekDot, today: Boolean) {
    val c = Pilunia.colors
    val base = Modifier.size(32.dp)
    when (state) {
        WeekDot.TAKEN -> Box(
            base.background(c.mint, CircleShape).then(if (today) Modifier.border(2.dp, c.erfolg, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { LIcon(Lucide.Check, 16.dp, c.erfolg, strokeWidth = 3f, contentDescription = "Eingenommen") }
        WeekDot.MISSED -> Box(
            base.background(c.warnungFlaeche, CircleShape).then(if (today) Modifier.border(2.dp, c.warnung, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { LIcon(Lucide.X, 14.dp, c.warnung, strokeWidth = 3f, contentDescription = "Nicht genommen") }
        WeekDot.OPEN_TODAY -> Box(base.border(2.dp, c.primaer, CircleShape))
        WeekDot.FUTURE -> Box(base.dashedCircle(c.feldrand, 2.dp, 4f, 3f))
        WeekDot.NONE -> Box(base.background(c.lavendelToenung, CircleShape).then(if (today) Modifier.border(2.dp, c.feldrand, CircleShape) else Modifier))
        WeekDot.PAUSE -> Box(base.background(c.lavendelInaktiv, CircleShape).then(if (today) Modifier.border(2.dp, c.primaer, CircleShape) else Modifier))
    }
}

// =====================================================================
// 13a Zyklus-Ring (Einnahmepause)
// =====================================================================

private enum class RingDot { TAKEN, PAUSE_PAST, TODAY, OPEN }

@Composable
private fun CycleCard(med: Medication, phase: CyclePhase, medIntakes: List<Intake>, today: LocalDate) {
    val c = Pilunia.colors
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(c.flaeche)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CycleRing(med, phase, medIntakes, today)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                noneToday(med.form), style = outfit(22, 700, letterSpacing = -0.3f),
                color = c.tinte, textAlign = TextAlign.Center,
            )
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(c.lavendelToenungKraeftig)
                    .padding(start = 10.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(Lucide.CalendarCheck, 16.dp, c.akzentText)
                Text(
                    "${if (med.usesBlister) "Neuer Blister" else "Weiter"} ab ${Fmt.dayShort(phase.nextCycleStart)}",
                    style = atkinson(14, 700), color = c.akzentText, maxLines = 1,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem("Genommen") { Box(Modifier.size(10.dp).background(c.mint, CircleShape)) }
            LegendItem("Pause") { Box(Modifier.size(10.dp).background(c.lavendelInaktiv, CircleShape)) }
            LegendItem("Heute") { Box(Modifier.size(10.dp).background(c.primaer, CircleShape)) }
            LegendItem("Offen") {
                Box(Modifier.size(10.dp).background(c.lavendelToenung, CircleShape).dashedCircle(c.feldrand, 1.5.dp, 2f, 2f))
            }
        }
    }
}

private fun noneToday(form: MedForm): String = when (form) {
    MedForm.SPRAY -> "Heute kein Sprühstoß"
    MedForm.TROPFEN -> "Heute keine Tropfen"
    else -> "Heute keine ${form.singular}"
}

@Composable
private fun LegendItem(text: String, dot: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        dot()
        Text(text, style = atkinson(14), color = Pilunia.colors.tinteGedaempft, maxLines = 1)
    }
}

/** Ring aus Punkten (Radius 114 in 264 × 264), beginnend oben, im Uhrzeigersinn. */
@Composable
private fun CycleRing(med: Medication, phase: CyclePhase, medIntakes: List<Intake>, today: LocalDate) {
    val c = Pilunia.colors
    val len = med.intakeDays + med.pauseDays
    val cycleStart = today.minusDays(phase.dayInCycle.toLong())
    val takenDays = remember(medIntakes) {
        medIntakes.filter { it.status == IntakeStatus.TAKEN }.map { it.epochDay }.toSet()
    }
    val ringSize = 264.dp
    val radius = 114f
    // Bei sehr langen Zyklen werden die Punkte kleiner, damit sie sich nicht berühren.
    val dot = minOf(18f, (2 * PI * radius / len * 0.72).toFloat())
    val todayDot = minOf(26f, dot + 8f)
    Box(Modifier.size(ringSize)) {
        for (i in 0 until len) {
            val d = cycleStart.plusDays(i.toLong())
            val state = when {
                d == today -> RingDot.TODAY
                i < med.intakeDays -> if (d.toEpochDay() in takenDays) RingDot.TAKEN else RingDot.OPEN
                d < today -> RingDot.PAUSE_PAST
                else -> RingDot.OPEN
            }
            val angle = 2 * PI * i / len
            val cx = 132f + radius * sin(angle).toFloat()
            val cy = 132f - radius * cos(angle).toFloat()
            val s = if (state == RingDot.TODAY) todayDot else dot
            val mod = Modifier.offset((cx - s / 2).dp, (cy - s / 2).dp).size(s.dp)
            when (state) {
                RingDot.TAKEN -> Box(mod.background(c.mint, CircleShape))
                RingDot.PAUSE_PAST -> Box(mod.background(c.lavendelInaktiv, CircleShape))
                RingDot.OPEN -> Box(mod.background(c.lavendelToenung, CircleShape).dashedCircle(c.feldrand, 1.5.dp, 3f, 3f))
                RingDot.TODAY -> Box(mod.background(c.primaer, CircleShape), contentAlignment = Alignment.Center) {
                    if (phase.inPause) LIcon(Lucide.Pause, (s * 0.54f).dp, FixedColors.Weiss, strokeWidth = 3f)
                }
            }
        }
        Column(
            Modifier.align(Alignment.Center).width(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (phase.inPause) "Einnahmepause" else "Einnahme",
                style = atkinson(14, 700), color = c.pflaume, maxLines = 1,
            )
            Text(
                "Tag ${phase.dayInPhase}", style = outfit(56, 800, lineHeight = 1f, letterSpacing = -1.5f),
                color = c.tinte, maxLines = 1,
            )
            Text(
                "von ${phase.phaseLength} ${if (phase.phaseLength == 1) "Tag" else "Tagen"}",
                style = atkinson(16, 700), color = c.tinteGedaempft, maxLines = 1,
            )
        }
    }
}
