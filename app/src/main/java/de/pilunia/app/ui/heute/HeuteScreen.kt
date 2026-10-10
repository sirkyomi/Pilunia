package de.pilunia.app.ui.heute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.AlarmClock
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import de.pilunia.app.R
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.BlisterSlot
import de.pilunia.app.domain.DayState
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.asNeededIntakesOn
import de.pilunia.app.domain.blisterState
import de.pilunia.app.domain.cyclePhase
import de.pilunia.app.domain.dayState
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.isActiveOn
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.streaks
import de.pilunia.app.ui.components.AnimatedCheckCircle
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.MainTab
import de.pilunia.app.ui.components.MedIcon
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberMeds
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.components.rememberSnoozes
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.reminder.SnoozeSheet
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

/** Text- und Symbolfarbe der weißen Buttons auf der Hero-Karte (im Design fest #5B3FA8). */
private val HeroButtonText = Color(0xFF5B3FA8)

/** 06 Heute: Kopf, Wochenleiste, „Als Nächstes“, Vorrat-Hinweise, „Später heute“, „Bei Bedarf“. */
@Composable
fun HeuteScreen(nav: NavController, contentPadding: PaddingValues, onOpenTab: (MainTab) -> Unit) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    val medsOrNull by rememberMeds()
    val intakes by rememberIntakes()
    val snoozes by rememberSnoozes()
    val settings by rememberSettings()
    val today = now.toLocalDate()
    var selected by rememberSaveable(today) { mutableStateOf(today) }
    var snoozeMinute by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    val meds = medsOrNull
    val streak = remember(meds, intakes, today) {
        if (meds.isNullOrEmpty()) 0 else streaks(meds, intakes, LocalDateTime.now()).current
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground(c, fadeEnd = 0.35f)
    ) {
        StatusBarSpace()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp + contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(
                date = Fmt.dayLong(today),
                greeting = Fmt.greeting(now.toLocalTime()) + settings.userName.trim().let { if (it.isNotEmpty()) ", $it" else "" },
                streak = streak,
                onStreak = { onOpenTab(MainTab.VERLAUF) },
            )
            when {
                meds == null -> Unit
                meds.isEmpty() -> EmptyCard(onAdd = { nav.navigate(Routes.wizard()) })
                else -> {
                    val occToday = remember(meds, intakes, snoozes, today) { occurrencesOn(today, meds, intakes, snoozes) }
                    // Nächster offener Zeitpunkt von heute (überfällig, verschoben oder kommend).
                    val heroOpen = remember(occToday) {
                        val pending = occToday.filter { it.intake == null }
                        val first = pending.minOfOrNull { it.minute }
                        if (first == null) emptyList() else pending.filter { it.minute == first }
                    }

                    WeekBar(meds, intakes, now, today, selected, onSelect = { selected = it })

                    if (heroOpen.isNotEmpty()) {
                        NextIntakeHero(
                            slot = heroOpen,
                            now = now,
                            busy = busy,
                            onTake = {
                                if (!busy) {
                                    busy = true
                                    scope.launch {
                                        try {
                                            container.repository.takeAll(heroOpen)
                                            nav.navigate(Routes.confirmed(today, heroOpen.first().minute))
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            },
                            onSnooze = { snoozeMinute = heroOpen.first().minute },
                        )
                    } else {
                        DoneHero(meds, today, hadIntakesToday = occToday.isNotEmpty(), onHistory = { onOpenTab(MainTab.VERLAUF) })
                    }

                    StockHints(meds, intakes, today, onOpen = { nav.navigate(Routes.stockLow(it)) })

                    val isToday = selected == today
                    val rows = if (isToday) occToday.filter { it !in heroOpen }
                    else remember(meds, intakes, snoozes, selected) { occurrencesOn(selected, meds, intakes, snoozes) }
                    if (!isToday || rows.isNotEmpty()) {
                        val heroMinute = heroOpen.firstOrNull()?.minute
                        val title = when {
                            !isToday -> Fmt.dayLong(selected)
                            heroMinute != null && rows.all { it.minute >= heroMinute } -> "Später heute"
                            else -> "Heute"
                        }
                        DaySection(
                            title = title,
                            rows = rows,
                            now = now,
                            canToggle = !selected.isAfter(today),
                            onToggle = { o ->
                                scope.launch {
                                    if (o.intake != null) container.repository.undo(o) else container.repository.take(o)
                                }
                            },
                            onOpen = { o -> nav.navigate(Routes.medDetail(o.med.id)) },
                        )
                    }

                    AsNeededSection(
                        meds = meds,
                        today = today,
                        intakes = intakes,
                        onTake = { m ->
                            scope.launch {
                                container.repository.takeAsNeeded(m.med.id, m.doses.firstOrNull()?.amount ?: 1)
                            }
                        },
                        onOpen = { m -> nav.navigate(Routes.medDetail(m.med.id)) },
                    )
                }
            }
        }
    }

    snoozeMinute?.let { m ->
        SnoozeSheet(today, m, onDismiss = { snoozeMinute = null }, onSnoozed = { snoozeMinute = null })
    }
}

// =====================================================================
// Kopf
// =====================================================================

@Composable
private fun Header(date: String, greeting: String, streak: Int, onStreak: () -> Unit) {
    val c = Pilunia.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, style = atkinson(14), color = c.tinteGedaempft, maxLines = 1)
            Text(
                greeting, style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (streak > 0) {
            // Serien-Chip: 32 hoch, Radius 16, „Warnung Fläche“, Herz 16 (Strich 2,5)
            Row(
                Modifier
                    .height(32.dp)
                    .pClickable(RoundedCornerShape(16.dp), onClick = onStreak)
                    .background(c.warnungFlaeche)
                    .padding(start = 10.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(Lucide.Heart, 16.dp, c.warnungKraeftig, strokeWidth = 2.5f)
                Text(if (streak == 1) "1 Tag" else "$streak Tage", style = atkinson(14, 700), color = c.warnung, maxLines = 1)
            }
        }
    }
}

// =====================================================================
// Wochenleiste
// =====================================================================

@Composable
private fun WeekBar(
    meds: List<MedWithDoses>,
    intakes: List<de.pilunia.app.data.Intake>,
    now: LocalDateTime,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val c = Pilunia.colors
    val monday = today.with(DayOfWeek.MONDAY)
    val days = (0L..6L).map { monday.plusDays(it) }
    val states = remember(meds, intakes, now) { days.map { dayState(it, meds, intakes, now) } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, day ->
            val isToday = day == today
            val isSelected = day == selected && !isToday
            val bg = when {
                isToday -> c.primaer
                isSelected -> c.lavendelToenungKraeftig
                else -> Color.Transparent
            }
            val dot = when (states[i]) {
                DayState.ALL_TAKEN -> FixedColors.PunktGenommen
                DayState.MISSED, DayState.PARTIAL -> c.warnungKraeftig
                DayState.OPEN -> if (isToday) FixedColors.Weiss.copy(alpha = 0.5f) else c.tinteGedaempft.copy(alpha = 0.5f)
                else -> Color.Transparent
            }
            Column(
                Modifier
                    .size(44.dp, 72.dp)
                    .pClickable(RoundedCornerShape(22.dp), role = Role.Tab) { onSelect(day) }
                    .background(bg),
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Fmt.weekdayShort(day),
                    style = atkinson(13, if (isToday || isSelected) 700 else 400),
                    color = when {
                        isToday -> FixedColors.Weiss
                        isSelected -> c.akzentText
                        else -> c.tinteGedaempft
                    },
                    maxLines = 1,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = outfit(18, 600),
                    color = when {
                        isToday -> FixedColors.Weiss
                        isSelected -> c.akzentText
                        else -> c.tinte
                    },
                    maxLines = 1,
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            }
        }
    }
}

// =====================================================================
// Hero-Karte „Als Nächstes“
// =====================================================================

private val HeroShadow = listOf(ShadowLayer(12.dp, 28.dp, FixedColors.Schatten.copy(alpha = 0x33 / 255f)))

/**
 * Rahmen der Hero-Karte: Verlauf Primär → #6A4FB5 (diagonal), Radius 28, Schatten y 12 / 28.
 * Rechts oben die Figur 139 × 148 vor einem Kreis (Weiß 12 %).
 */
@Composable
private fun HeroFrame(image: Int, imageDescription: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .softShadow(HeroShadow, 28.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7A5CC7), FixedColors.HeroVerlaufEnde), start = Offset.Zero, end = Offset.Infinite))
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 20.dp, end = 7.dp)
                .size(139.dp, 148.dp)
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(139.dp)
                    .clip(CircleShape)
                    .background(FixedColors.Weiss.copy(alpha = 0.12f))
            )
            PiluniaImage(image, imageDescription, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)) {
            content()
        }
    }
}

/** Texte links (bis vor die Figur) mit den Abständen aus dem Design. */
@Composable
private fun HeroTexts(label: String, big: String, bigStyle: TextStyle, name: String?, detail: String?, nameMaxLines: Int = 1) {
    // Label darf bis kurz vor den Kreis reichen, die übrigen Texte enden vor der Figur (x 226).
    Text(
        label, style = atkinson(15, 700), color = FixedColors.Weiss, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(end = 86.dp),
    )
    Text(
        big, style = bigStyle, color = FixedColors.Weiss,
        modifier = Modifier.padding(end = 122.dp),
    )
    if (name != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            name, style = atkinson(18, 700), color = FixedColors.Weiss, maxLines = nameMaxLines, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 122.dp),
        )
    }
    if (detail != null) {
        Spacer(Modifier.height(1.dp))
        Text(
            detail, style = atkinson(15, lineHeight = 1.3f), color = FixedColors.Weiss, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 122.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
}

/** Weißer Pill-Button der Hero-Karte (48 hoch, Radius 24, Symbol 20 + Atkinson 16/700). */
@Composable
private fun RowScope.HeroButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .height(48.dp)
            .pClickable(RoundedCornerShape(24.dp), enabled = enabled, onClick = onClick)
            .background(FixedColors.Weiss)
            .padding(horizontal = 21.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(icon, 20.dp, HeroButtonText, strokeWidth = 2.5f)
        Text(text, style = atkinson(16, 700), color = HeroButtonText, maxLines = 1)
    }
}

@Composable
private fun NextIntakeHero(slot: List<Occurrence>, now: LocalDateTime, busy: Boolean, onTake: () -> Unit, onSnooze: () -> Unit) {
    val first = slot.first()
    val status = first.status(now)
    val label = when {
        status == OccStatus.SNOOZED && first.snoozedUntil != null -> "Erinnert um ${Fmt.time(first.snoozedUntil)}"
        first.dateTime.isAfter(now) -> {
            val mins = ceil(ChronoUnit.SECONDS.between(now, first.dateTime) / 60.0).toLong()
            "Als Nächstes · ${Fmt.inMinutes(mins)}"
        }
        ChronoUnit.MINUTES.between(first.dateTime, now) < 1 -> "Als Nächstes · jetzt"
        else -> "Fällig seit ${Fmt.time(first.minute)}"
    }
    val name: String
    val detail: String
    if (slot.size == 1) {
        name = first.med.displayName
        detail = listOf(first.med.form.amount(first.dose.amount), first.dose.label.takeIf { it.isNotBlank() })
            .filterNotNull().joinToString(" · ")
    } else {
        name = "${slot.size} Medikamente"
        detail = slot.joinToString(", ") { it.med.name }
    }
    HeroFrame(R.drawable.pilunia_wasser, "Pilunia mit Wasserglas") {
        HeroTexts(
            label = label,
            big = Fmt.time(first.minute),
            bigStyle = outfit(56, 700, lineHeight = 1.1f, letterSpacing = -1.5f),
            name = name,
            detail = detail,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            HeroButton(if (slot.size > 1) "Alle einnehmen" else "Jetzt einnehmen", Lucide.Check, enabled = !busy, onClick = onTake)
            Box(
                Modifier
                    .size(48.dp)
                    .pClickable(CircleShape, onClick = onSnooze)
                    .background(FixedColors.Weiss.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                LIcon(Lucide.AlarmClock, 20.dp, FixedColors.Weiss, contentDescription = "Später erinnern")
            }
        }
    }
}

/** Heute ist nichts mehr offen: gleiche Karte mit schlafender Pilunia und der nächsten Einnahme. */
@Composable
private fun DoneHero(meds: List<MedWithDoses>, today: LocalDate, hadIntakesToday: Boolean, onHistory: () -> Unit) {
    val next = remember(meds, today) { nextSlotAfter(meds, today) }
    val pause = remember(meds, today) {
        meds.firstNotNullOfOrNull { m -> m.med.takeIf { it.isActiveOn(today) }?.cyclePhase(today)?.takeIf { it.inPause } }
    }
    val label = if (next != null) {
        "Als Nächstes · ${Fmt.relativeDay(next.first, today)}, ${Fmt.time(next.second.first().minute)}"
    } else "Heute"
    val title = if (hadIntakesToday) "Alles erledigt für heute" else "Heute ist nichts geplant"
    val name: String?
    val detail: String?
    when {
        !hadIntakesToday && pause != null -> {
            name = "Einnahmepause"
            detail = "Tag ${pause.dayInPhase} von ${pause.phaseLength}"
        }
        next != null && next.second.size == 1 -> {
            val o = next.second.first()
            name = o.med.displayName
            detail = o.med.form.amount(o.dose.amount)
        }
        next != null -> {
            name = "${next.second.size} Medikamente"
            detail = next.second.joinToString(", ") { it.med.name }
        }
        else -> {
            name = null
            detail = null
        }
    }
    HeroFrame(R.drawable.pilunia_schlafen, "Pilunia schläft") {
        HeroTexts(
            label = label,
            big = title,
            bigStyle = outfit(30, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
            name = name,
            detail = detail,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroButton("Verlauf ansehen", Lucide.CalendarDays, onClick = onHistory)
        }
    }
}

/** Erster Einnahmezeitpunkt an einem der nächsten Tage (Tag + alle Einnahmen dieses Zeitpunkts). */
private fun nextSlotAfter(meds: List<MedWithDoses>, today: LocalDate): Pair<LocalDate, List<Occurrence>>? {
    for (i in 1L..60L) {
        val d = today.plusDays(i)
        val occ = occurrencesOn(d, meds, emptyList())
        if (occ.isNotEmpty()) {
            val m = occ.first().minute
            return d to occ.filter { it.minute == m }
        }
    }
    return null
}

/** Leerer Zustand: Karte im Stil der Hero-Karte mit winkender Pilunia. */
@Composable
private fun EmptyCard(onAdd: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(HeroShadow, 28.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7A5CC7), FixedColors.HeroVerlaufEnde), start = Offset.Zero, end = Offset.Infinite))
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(160.dp, 170.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(FixedColors.Weiss.copy(alpha = 0.12f))
            )
            PiluniaImage(R.drawable.pilunia_winken, "Pilunia winkt", Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Noch keine Medikamente", style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
            color = FixedColors.Weiss, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Leg dein erstes Medikament an – Pilunia erinnert dich dann pünktlich und behält den Vorrat im Blick.",
            style = atkinson(15, lineHeight = 1.4f), color = FixedColors.Weiss, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            HeroButton("Erstes Medikament anlegen", Lucide.Plus, onClick = onAdd)
        }
    }
}

// =====================================================================
// Vorrat-Hinweise
// =====================================================================

@Composable
private fun StockHints(meds: List<MedWithDoses>, intakes: List<de.pilunia.app.data.Intake>, today: LocalDate, onOpen: (Long) -> Unit) {
    val low = remember(meds, intakes, today) {
        meds.mapNotNull { m ->
            val med = m.med
            if (!med.stockReminder || !med.isActiveOn(today)) return@mapNotNull null
            if (med.stockSnoozeUntil != null && med.stockSnoozeUntil > today.toEpochDay()) return@mapNotNull null
            val f = forecast(m, intakes, today)
            if (!f.isLow(med.warnDays)) return@mapNotNull null
            val takenToday = intakes.any { it.medId == med.id && it.epochDay == today.toEpochDay() && it.status == IntakeStatus.TAKEN }
            val reach = when {
                med.stock <= 0 -> "Vorrat aufgebraucht"
                f.lastCoveredDay == null -> null
                f.lastCoveredDay.isBefore(today) -> "reicht nicht mehr für heute"
                f.lastCoveredDay == today -> "reicht bis heute"
                else -> "reicht bis ${Fmt.dayShort(f.lastCoveredDay)}"
            }
            val info = if (med.stock <= 0) reach!! else listOfNotNull("Noch ${med.form.amount(med.stock)}", reach).joinToString(" · ")
            Triple(med, info, miniBlisterDots(med, takenToday))
        }
    }
    low.forEach { (med, info, dots) ->
        StockHintCard(med, info, dots, onClick = { onOpen(med.id) })
    }
}

/** Mulden des Mini-Blisters (true = voll), volle zuerst; mehr als 14 Mulden werden auf 14 verkleinert. */
private fun miniBlisterDots(med: Medication, takenToday: Boolean): List<Boolean> {
    val st = blisterState(med, takenToday)
    val total = st.slots.size
    val full = st.slots.count { it == BlisterSlot.FULL }
    return if (total <= 14) List(total) { it < full }
    else {
        val scaled = ceil(full * 14.0 / total).toInt().coerceIn(if (full > 0) 1 else 0, 14)
        List(14) { it < scaled }
    }
}

/** Karte „Ramipril wird knapp“: „Warnung Fläche“, Radius 24, Mini-Blister 7 × 2, Chevron „Warnung“. */
@Composable
private fun StockHintCard(med: Medication, info: String, dots: List<Boolean>, onClick: () -> Unit) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pClickable(RoundedCornerShape(24.dp), onClick = onClick)
            .background(c.warnungFlaeche)
            .padding(start = 16.dp, top = 16.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.flaeche)
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            dots.chunked(7).forEach { row ->
                Row(Modifier.width(72.dp), horizontalArrangement = Arrangement.spacedBy(2.67.dp)) {
                    row.forEach { full ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (full) c.warnungKraeftig else c.warnungGedaempft))
                    }
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${med.name} wird knapp", style = atkinson(16, 700), color = c.tinte, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(info, style = atkinson(14, lineHeight = 1.35f), color = c.warnung)
        }
        LIcon(Lucide.ChevronRight, 22.dp, c.warnung)
    }
}

// =====================================================================
// „Später heute“ bzw. gewählter Tag
// =====================================================================

@Composable
private fun SectionTitle(title: String, counter: String?) {
    val c = Pilunia.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = outfit(20, 600), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (counter != null) Text(counter, style = atkinson(14), color = c.tinteGedaempft, maxLines = 1)
    }
}

@Composable
private fun DaySection(
    title: String,
    rows: List<Occurrence>,
    now: LocalDateTime,
    canToggle: Boolean,
    onToggle: (Occurrence) -> Unit,
    onOpen: (Occurrence) -> Unit,
) {
    val c = Pilunia.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title, if (rows.isEmpty()) null else "${rows.count { it.intake != null }} von ${rows.size} erledigt")
        PCard(Modifier.fillMaxWidth(), radius = 24.dp, shadow = CardShadow, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            if (rows.isEmpty()) {
                Text(
                    "An diesem Tag ist nichts geplant.", style = atkinson(15), color = c.tinteGedaempft,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
            rows.forEachIndexed { i, o ->
                IntakeRow(o, o.status(now), canToggle, last = i == rows.lastIndex, onToggle = { onToggle(o) }, onOpen = { onOpen(o) })
            }
        }
    }
}

/** Dosis-Zeile wie im Design: „1 Kapsel · zum Abendessen“ bzw. „1.000 I.E. · 1 Tablette“. */
private fun doseText(o: Occurrence): String {
    val amount = o.med.form.amount(o.dose.amount)
    return if (o.dose.label.isNotBlank()) "$amount · ${o.dose.label}"
    else listOfNotNull(o.med.strength.takeIf { it.isNotBlank() }?.let { "$it ${o.med.unit}" }, amount).joinToString(" · ")
}

@Composable
private fun IntakeRow(
    o: Occurrence,
    status: OccStatus,
    canToggle: Boolean,
    last: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = Pilunia.colors
    val detail = when (status) {
        OccStatus.TAKEN -> o.intake?.takenAt?.let { "Genommen um ${Fmt.time(it)}" } ?: "Genommen"
        OccStatus.SKIPPED -> "Ausgelassen"
        OccStatus.SNOOZED -> "Erinnert um ${o.snoozedUntil?.let { Fmt.time(it) } ?: ""} · ${doseText(o)}"
        OccStatus.MISSED -> "Verpasst · ${doseText(o)}"
        else -> doseText(o)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .then(if (!last) Modifier.drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(c.linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            } else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIcon(o.med.color, o.med.form)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.med.name, style = atkinson(16, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail, style = atkinson(14),
                color = if (status == OccStatus.MISSED) c.warnung else c.tinteGedaempft,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Fmt.time(o.minute), style = outfit(16, 600),
            color = if (status == OccStatus.OPEN || status == OccStatus.MISSED) c.warnung else c.tinte,
            maxLines = 1,
        )
        Box(
            Modifier
                .size(44.dp)
                .pClickable(CircleShape, enabled = canToggle, role = Role.Checkbox, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                OccStatus.SKIPPED -> Box(Modifier.size(28.dp).clip(CircleShape).background(c.warnungFlaeche), contentAlignment = Alignment.Center) {
                    LIcon(Lucide.X, 16.dp, c.warnung, strokeWidth = 3f, contentDescription = "Ausgelassen – tippen zum Zurücknehmen")
                }
                else -> AnimatedCheckCircle(
                    checked = status == OccStatus.TAKEN,
                    outlineColor = if (canToggle) c.feldrand else c.feldrand.copy(alpha = 0.4f),
                    contentDescription = if (status == OccStatus.TAKEN) "Eingenommen – tippen zum Zurücknehmen" else null,
                )
            }
        }
    }
}

// =====================================================================
// „Bei Bedarf“
// =====================================================================

@Composable
private fun AsNeededSection(
    meds: List<MedWithDoses>,
    today: LocalDate,
    intakes: List<de.pilunia.app.data.Intake>,
    onTake: (MedWithDoses) -> Unit,
    onOpen: (MedWithDoses) -> Unit,
) {
    val c = Pilunia.colors
    val list = meds.filter { it.med.scheduleType == ScheduleType.AS_NEEDED && it.med.isActiveOn(today) }
    if (list.isEmpty()) return
    val todays = remember(intakes, today) { asNeededIntakesOn(today, intakes) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Bei Bedarf", null)
        PCard(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            list.forEachIndexed { i, m ->
                val mine = todays.filter { it.medId == m.med.id && it.status == IntakeStatus.TAKEN }
                val last = mine.maxByOrNull { it.takenAt ?: 0L }
                val detail = if (last == null) "Heute noch nicht genommen"
                else "Heute ${mine.size}× · zuletzt ${last.takenAt?.let { Fmt.time(it) } ?: Fmt.time(last.minuteOfDay)}"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(m) }
                        .then(if (i != list.lastIndex) Modifier.drawBehind {
                            val y = size.height - 0.5.dp.toPx()
                            drawLine(c.linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        } else Modifier)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MedIcon(m.med.color, m.med.form)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.med.displayName, style = atkinson(16, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail, style = atkinson(14), color = c.tinteGedaempft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(44.dp).pClickable(CircleShape) { onTake(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c.lavendelToenungKraeftig),
                            contentAlignment = Alignment.Center,
                        ) {
                            LIcon(Lucide.Plus, 18.dp, c.akzentText, strokeWidth = 2.5f, contentDescription = "Einnahme eintragen")
                        }
                    }
                }
            }
        }
    }
}
