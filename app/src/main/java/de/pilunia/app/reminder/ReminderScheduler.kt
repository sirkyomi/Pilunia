package de.pilunia.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.pilunia.app.data.AppSettings
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.PiluniaRepository
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.atMinute
import de.pilunia.app.domain.cyclePhase
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.toMillis
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Plant die nächsten Wecker. Es gibt pro Art genau einen anstehenden Alarm:
 * Einnahme-Erinnerung (inkl. „Später“ und „Erneut erinnern“), Start-Erinnerung nach
 * der Pause, täglicher Vorrats-Check, Tagesabschluss und Wochenbericht.
 */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    suspend fun rescheduleAll(repo: PiluniaRepository) {
        val settings = repo.settings.current()
        val meds = repo.meds()
        val today = Fmt.today()
        val intakes = repo.intakes(today.minusDays(1), today.plusDays(8))
        val snoozes = repo.snoozes()
        val now = LocalDateTime.now()

        // 1) Einnahme-Erinnerung
        val next = if (settings.remindersEnabled && settings.onboardingDone) {
            nextDoseTrigger(meds, intakes, snoozes, settings, now)
        } else null
        set(Kind.DOSE, next, exact = true)

        // 2) Start-Erinnerung am Vorabend eines neuen Blisters (20:00)
        val pauseEnd = meds.mapNotNull { m ->
            val med = m.med
            if (!med.isCycle || !med.pauseEndReminder) return@mapNotNull null
            val phase = med.cyclePhase(today) ?: return@mapNotNull null
            val eve = phase.nextCycleStart.minusDays(1).atMinute(20 * 60)
            if (eve.isAfter(now)) eve.toMillis() else phase.nextCycleStart.plusDays((med.intakeDays + med.pauseDays).toLong() - 1)
                .atMinute(20 * 60).toMillis()
        }.minOrNull()
        set(Kind.PAUSE_END, if (settings.remindersEnabled) pauseEnd else null, exact = false)

        // 3) Vorrats-Check täglich um 09:00
        val stockCheck = nextAt(now, 9 * 60)
        set(Kind.STOCK, if (settings.stockWarningEnabled) stockCheck else null, exact = false)

        // 4) Tagesabschluss: eine Stunde nach der letzten geplanten Einnahme des Tages
        val summary = if (settings.dailySummary) {
            (0..1).firstNotNullOfOrNull { offset ->
                val d = today.plusDays(offset.toLong())
                val last = occurrencesOn(d, meds, intakes).maxOfOrNull { it.minute } ?: return@firstNotNullOfOrNull null
                val t = d.atMinute(minOf(last + 60, 23 * 60 + 30))
                if (t.isAfter(now)) t.toMillis() else null
            }
        } else null
        set(Kind.SUMMARY, summary, exact = false)

        // 5) Wochenbericht sonntags 18:00
        val weekly = if (settings.weeklyReport) {
            var t = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atMinute(18 * 60)
            if (!t.isAfter(now)) t = t.plusWeeks(1)
            t.toMillis()
        } else null
        set(Kind.WEEKLY, weekly, exact = false)
    }

    private fun nextAt(now: LocalDateTime, minute: Int): Long {
        var t = now.toLocalDate().atMinute(minute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toMillis()
    }

    /**
     * Nächster Zeitpunkt, zu dem eine offene Einnahme erinnert werden muss:
     * geplante Zeit (oder „Später“-Zeit) und – bei „Erneut erinnern“ – alle [AppSettings.repeatIntervalMin]
     * Minuten bis zu [AppSettings.repeatMaxCount]-mal.
     */
    private fun nextDoseTrigger(
        meds: List<MedWithDoses>,
        intakes: List<de.pilunia.app.data.Intake>,
        snoozes: List<de.pilunia.app.data.Snooze>,
        settings: AppSettings,
        now: LocalDateTime,
    ): Long? {
        val nowMs = now.toMillis()
        var best: Long? = null
        for (offset in 0..8) {
            val day = now.toLocalDate().plusDays(offset.toLong())
            for (o in occurrencesOn(day, meds, intakes, snoozes)) {
                if (!o.med.remindAtTime || o.isConfirmed) continue
                val base = o.snoozedUntil ?: o.dateTime.toMillis()
                val repeats = if (o.med.repeatReminder) settings.repeatMaxCount else 0
                for (k in 0..repeats) {
                    val t = base + k * settings.repeatIntervalMin * 60_000L
                    if (t > nowMs + 500) {
                        if (best == null || t < best) best = t
                        break
                    }
                }
            }
            if (best != null && offset >= 1) break
        }
        return best
    }

    /** Welche Erinnerung ist zum Zeitpunkt [now] fällig? Liefert Tag + geplante Uhrzeit des Slots. */
    suspend fun dueSlot(repo: PiluniaRepository, now: LocalDateTime = LocalDateTime.now()): Pair<LocalDate, Int>? {
        val today = now.toLocalDate()
        val meds = repo.meds()
        val intakes = repo.intakes(today.minusDays(1), today)
        val snoozes = repo.snoozes()
        val nowMs = now.toMillis() + 60_000
        val candidates = listOf(today.minusDays(1), today).flatMap { d ->
            occurrencesOn(d, meds, intakes, snoozes).filter { o ->
                o.med.remindAtTime && !o.isConfirmed && (o.snoozedUntil ?: o.dateTime.toMillis()) <= nowMs &&
                    o.status(now) != OccStatus.MISSED
            }
        }
        val o = candidates.maxByOrNull { it.snoozedUntil ?: it.dateTime.toMillis() } ?: return null
        return o.day to o.minute
    }

    private fun set(kind: Kind, at: Long?, exact: Boolean) {
        val pi = pending(kind)
        alarms.cancel(pi)
        if (at == null) return
        if (exact && canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(kind: Kind): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(kind.action)
        return PendingIntent.getBroadcast(
            context, kind.ordinal + 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    enum class Kind(val action: String) {
        DOSE("de.pilunia.app.DOSE"),
        PAUSE_END("de.pilunia.app.PAUSE_END"),
        STOCK("de.pilunia.app.STOCK"),
        SUMMARY("de.pilunia.app.SUMMARY"),
        WEEKLY("de.pilunia.app.WEEKLY"),
    }
}
