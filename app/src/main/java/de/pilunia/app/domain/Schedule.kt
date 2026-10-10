package de.pilunia.app.domain

import de.pilunia.app.data.Dose
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.data.Snooze
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Lage eines Tages in einem Einnahme-/Pausenzyklus (z. B. 21 + 7). */
data class CyclePhase(
    /** 0-basiert innerhalb des Zyklus. */
    val dayInCycle: Int,
    val inPause: Boolean,
    /** 1-basiert innerhalb der aktuellen Phase („Tag 3 von 7“). */
    val dayInPhase: Int,
    val phaseLength: Int,
    val phaseStart: LocalDate,
    val phaseEnd: LocalDate,
    /** Erster Einnahmetag des nächsten Zyklus (= neuer Blister). */
    val nextCycleStart: LocalDate,
    /** Beginn der nächsten Pause (bzw. der laufenden, falls in der Pause). */
    val nextPauseStart: LocalDate,
)

fun Medication.isActiveOn(day: LocalDate): Boolean {
    val e = day.toEpochDay()
    return e >= startDate && (endDate == null || e <= endDate)
}

fun Medication.cyclePhase(day: LocalDate): CyclePhase? {
    if (!isCycle) return null
    val len = intakeDays + pauseDays
    val diff = day.toEpochDay() - startDate
    val inCycle = Math.floorMod(diff, len.toLong()).toInt()
    val cycleStart = day.minusDays(inCycle.toLong())
    val inPause = inCycle >= intakeDays
    return if (inPause) {
        CyclePhase(
            dayInCycle = inCycle, inPause = true, dayInPhase = inCycle - intakeDays + 1, phaseLength = pauseDays,
            phaseStart = cycleStart.plusDays(intakeDays.toLong()), phaseEnd = cycleStart.plusDays(len - 1L),
            nextCycleStart = cycleStart.plusDays(len.toLong()),
            nextPauseStart = cycleStart.plusDays(intakeDays.toLong()),
        )
    } else {
        CyclePhase(
            dayInCycle = inCycle, inPause = false, dayInPhase = inCycle + 1, phaseLength = intakeDays,
            phaseStart = cycleStart, phaseEnd = cycleStart.plusDays(intakeDays - 1L),
            nextCycleStart = cycleStart.plusDays(len.toLong()),
            nextPauseStart = cycleStart.plusDays(intakeDays.toLong()),
        )
    }
}

/** Ist an diesem Tag eine geplante Einnahme fällig? („Bei Bedarf“ nie.) */
fun Medication.isIntakeDay(day: LocalDate): Boolean {
    if (!isActiveOn(day)) return false
    val byType = when (scheduleType) {
        ScheduleType.AS_NEEDED -> false
        ScheduleType.DAILY -> true
        ScheduleType.WEEKDAYS -> weekdays and day.dayOfWeek.bit() != 0
    }
    if (!byType) return false
    return cyclePhase(day)?.inPause != true
}

enum class OccStatus { TAKEN, SKIPPED, SNOOZED, OPEN, UPCOMING, MISSED }

/** Eine geplante Einnahme an einem Tag. */
data class Occurrence(
    val med: Medication,
    val dose: Dose,
    val day: LocalDate,
    val intake: Intake?,
    val snoozedUntil: Long?,
) {
    val minute: Int get() = dose.minuteOfDay
    val dateTime: LocalDateTime get() = day.atMinute(minute)
    val isConfirmed: Boolean get() = intake != null

    fun status(now: LocalDateTime = LocalDateTime.now()): OccStatus = when {
        intake?.status == IntakeStatus.TAKEN -> OccStatus.TAKEN
        intake?.status == IntakeStatus.SKIPPED -> OccStatus.SKIPPED
        day < now.toLocalDate() -> OccStatus.MISSED
        day > now.toLocalDate() -> OccStatus.UPCOMING
        snoozedUntil != null && snoozedUntil > now.toMillis() -> OccStatus.SNOOZED
        dateTime <= now -> OccStatus.OPEN
        else -> OccStatus.UPCOMING
    }
}

/** Alle geplanten Einnahmen eines Tages, nach Uhrzeit sortiert. */
fun occurrencesOn(
    day: LocalDate,
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    snoozes: List<Snooze> = emptyList(),
): List<Occurrence> {
    val e = day.toEpochDay()
    val byKey = intakes.filter { it.epochDay == e }.associateBy { it.medId to it.doseId }
    val snoozeByMinute = snoozes.filter { it.epochDay == e }.associateBy { it.minuteOfDay }
    return meds.flatMap { m ->
        if (!m.med.isIntakeDay(day)) emptyList()
        else m.sortedDoses.map { d ->
            Occurrence(m.med, d, day, byKey[m.med.id to d.id], snoozeByMinute[d.minuteOfDay]?.untilMillis)
        }
    }.sortedWith(compareBy({ it.minute }, { it.med.createdAt }))
}

/** Bestätigte „Bei Bedarf“-Einnahmen eines Tages (ohne geplante Dosis). */
fun asNeededIntakesOn(day: LocalDate, intakes: List<Intake>): List<Intake> =
    intakes.filter { it.epochDay == day.toEpochDay() && it.doseId < 0 }

/** Vorrats-Vorschau. */
data class StockForecast(
    /** Letzter Tag, an dem der Vorrat noch reicht („reicht bis Do, 15. Okt.“). */
    val lastCoveredDay: LocalDate?,
    /** Erster Tag ohne Vorrat („Ab Freitag, 16. Okt. ist die Packung leer“). */
    val firstEmptyDay: LocalDate?,
    /** Anzahl Tage nach heute, die der Vorrat reicht. */
    val daysLeft: Int?,
) {
    fun isLow(warnDays: Int): Boolean = daysLeft != null && daysLeft <= warnDays
}

/**
 * Simuliert den Verbrauch ab heute. Heute noch offene Einnahmen werden zuerst abgezogen.
 * Für „Bei Bedarf“ oder ohne Dosen ist keine Vorschau möglich (alles null).
 */
fun forecast(m: MedWithDoses, intakes: List<Intake>, today: LocalDate = Fmt.today()): StockForecast {
    val med = m.med
    if (med.scheduleType == ScheduleType.AS_NEEDED || m.doses.isEmpty()) return StockForecast(null, null, null)
    var remaining = med.stock
    if (med.isIntakeDay(today)) {
        val done = intakes.filter { it.medId == med.id && it.epochDay == today.toEpochDay() }.map { it.doseId }.toSet()
        remaining -= m.doses.filter { it.id !in done }.sumOf { it.amount }
    }
    if (remaining < 0) return StockForecast(today.minusDays(1), today, 0)
    var day = today
    var days = 0
    val limit = 3 * 366
    for (i in 1..limit) {
        val d = today.plusDays(i.toLong())
        if (med.endDate != null && d.toEpochDay() > med.endDate) return StockForecast(null, null, null)
        val need = if (med.isIntakeDay(d)) m.doses.sumOf { it.amount } else 0
        if (need > remaining) return StockForecast(day, d, days)
        remaining -= need
        day = d
        days = i
    }
    return StockForecast(null, null, null)
}

/** Darstellung des aktuellen Blisters (Signatur-Element der App). */
data class BlisterState(
    /** „Blister 2 von 2“ (1-basiert). */
    val index: Int,
    val count: Int,
    val slots: List<BlisterSlot>,
    /** Einheiten im aktuellen Blister. */
    val remainingInBlister: Int,
)

enum class BlisterSlot { EMPTY, JUST_TAKEN, FULL }

/**
 * @param takenToday wurde heute schon eine Einheit entnommen? Dann wird die zuletzt
 * geleerte Mulde mint statt gestrichelt gezeigt.
 */
fun blisterState(med: Medication, takenToday: Boolean): BlisterState {
    val upb = med.unitsPerBlister.coerceAtLeast(1)
    val stock = med.stock.coerceAtLeast(0)
    val inBlister = when {
        stock == 0 -> 0
        stock % upb == 0 -> upb
        else -> stock % upb
    }
    val blistersLeft = if (stock == 0) 0 else (stock + upb - 1) / upb
    val index = (med.blistersPerPack - blistersLeft + 1).coerceIn(1, med.blistersPerPack.coerceAtLeast(1))
    val emptied = upb - inBlister
    val slots = List(upb) { i ->
        when {
            i < emptied - 1 -> BlisterSlot.EMPTY
            i == emptied - 1 -> if (takenToday) BlisterSlot.JUST_TAKEN else BlisterSlot.EMPTY
            else -> BlisterSlot.FULL
        }
    }
    return BlisterState(index, med.blistersPerPack.coerceAtLeast(1), slots, inBlister)
}

enum class DayState { ALL_TAKEN, MISSED, PARTIAL, OPEN, NOTHING_PLANNED, FUTURE }

/** Zustand eines Tages für Kalender und Wochenleiste. */
fun dayState(
    day: LocalDate,
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    now: LocalDateTime = LocalDateTime.now(),
): DayState {
    val today = now.toLocalDate()
    if (day > today) return DayState.FUTURE
    val occ = occurrencesOn(day, meds, intakes)
    if (occ.isEmpty()) return DayState.NOTHING_PLANNED
    val statuses = occ.map { it.status(now) }
    return when {
        statuses.all { it == OccStatus.TAKEN } -> DayState.ALL_TAKEN
        statuses.any { it == OccStatus.MISSED || it == OccStatus.SKIPPED } -> DayState.MISSED
        day == today -> DayState.OPEN
        else -> DayState.PARTIAL
    }
}

/** Serien in Tagen: aktuelle und längste Folge von Tagen, an denen alles genommen wurde. */
data class Streaks(val current: Int, val longest: Int)

fun streaks(meds: List<MedWithDoses>, intakes: List<Intake>, now: LocalDateTime = LocalDateTime.now()): Streaks {
    if (meds.isEmpty()) return Streaks(0, 0)
    val today = now.toLocalDate()
    val first = meds.minOf { it.med.startDate }.let { LocalDate.ofEpochDay(it) }
    var longest = 0
    var run = 0
    var d = first
    while (d < today) {
        when (dayState(d, meds, intakes, now)) {
            DayState.ALL_TAKEN -> { run++; longest = maxOf(longest, run) }
            DayState.NOTHING_PLANNED -> Unit
            else -> run = 0
        }
        d = d.plusDays(1)
    }
    // Heute zählt mit, sobald alles erledigt ist.
    if (dayState(today, meds, intakes, now) == DayState.ALL_TAKEN) { run++; longest = maxOf(longest, run) }
    return Streaks(run, longest)
}

/** Einnahmetreue im Zeitraum: genommen / fällig (offene Einnahmen von heute zählen nicht mit). */
data class Adherence(val taken: Int, val due: Int, val missed: Int) {
    val percent: Int get() = if (due == 0) 100 else Math.round(taken * 100f / due)
}

fun adherence(
    from: LocalDate,
    to: LocalDate,
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    now: LocalDateTime = LocalDateTime.now(),
): Adherence {
    var taken = 0
    var due = 0
    var missed = 0
    var d = from
    while (d <= to) {
        for (o in occurrencesOn(d, meds, intakes)) {
            when (o.status(now)) {
                OccStatus.TAKEN -> { taken++; due++ }
                OccStatus.MISSED, OccStatus.SKIPPED -> { missed++; due++ }
                else -> Unit
            }
        }
        d = d.plusDays(1)
    }
    return Adherence(taken, due, missed)
}

fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)
