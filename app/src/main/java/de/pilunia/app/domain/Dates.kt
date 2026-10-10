package de.pilunia.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Deutsche Datums- und Zeitformate, wie sie in den Screens vorkommen. */
object Fmt {
    val DE: Locale = Locale.GERMANY
    private val time = DateTimeFormatter.ofPattern("HH:mm", DE)

    fun today(): LocalDate = LocalDate.now()
    fun zone(): ZoneId = ZoneId.systemDefault()

    /** „08:00“ */
    fun time(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)
    fun time(t: LocalTime): String = t.format(time)
    fun time(millis: Long): String = time(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone()).toLocalTime())

    fun minuteOf(millis: Long): Int {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** „Samstag“ */
    fun weekdayLong(d: LocalDate): String = d.dayOfWeek.getDisplayName(TextStyle.FULL, DE)
    /** „Sa“ */
    fun weekdayShort(d: LocalDate): String = weekdayShort(d.dayOfWeek)
    fun weekdayShort(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "Mo"; DayOfWeek.TUESDAY -> "Di"; DayOfWeek.WEDNESDAY -> "Mi"
        DayOfWeek.THURSDAY -> "Do"; DayOfWeek.FRIDAY -> "Fr"; DayOfWeek.SATURDAY -> "Sa"
        DayOfWeek.SUNDAY -> "So"
    }

    /** „Oktober“ */
    fun monthLong(d: LocalDate): String = d.month.getDisplayName(TextStyle.FULL_STANDALONE, DE)
    /** „Okt.“ (Mai/Juni/Juli ohne Punkt) */
    fun monthShort(d: LocalDate): String = when (d.monthValue) {
        1 -> "Jan."; 2 -> "Feb."; 3 -> "März"; 4 -> "Apr."; 5 -> "Mai"; 6 -> "Juni"
        7 -> "Juli"; 8 -> "Aug."; 9 -> "Sep."; 10 -> "Okt."; 11 -> "Nov."; else -> "Dez."
    }

    /** „Samstag, 10. Oktober“ */
    fun dayLong(d: LocalDate): String = "${weekdayLong(d)}, ${d.dayOfMonth}. ${monthLong(d)}"
    /** „Do, 15. Okt.“ */
    fun dayShort(d: LocalDate): String = "${weekdayShort(d)}, ${d.dayOfMonth}. ${monthShort(d)}"
    /** „Do, 15. Okt. 2027“, wenn nicht im aktuellen Jahr, sonst wie [dayShort]. */
    fun dayShortWithYear(d: LocalDate): String =
        if (d.year == today().year) dayShort(d) else "${dayShort(d)} ${d.year}"
    /** „15. Okt.“ */
    fun dayMonthShort(d: LocalDate): String = "${d.dayOfMonth}. ${monthShort(d)}"
    /** „Oktober 2026“ */
    fun monthYear(d: LocalDate): String = "${monthLong(d)} ${d.year}"
    /** „10.10.2026“ */
    fun numeric(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", DE))

    /** „heute“, „morgen“, „gestern“ oder [dayShort]. */
    fun relativeDay(d: LocalDate, today: LocalDate = today()): String = when (d) {
        today -> "heute"
        today.plusDays(1) -> "morgen"
        today.minusDays(1) -> "gestern"
        else -> dayShort(d)
    }

    /** Tageszeit-Wort zu einer Uhrzeit: „Morgens“, „Mittags“, „Abends“, „Nachts“. */
    fun daypart(minuteOfDay: Int): String = when (minuteOfDay / 60) {
        in 5..10 -> "Morgens"
        in 11..13 -> "Mittags"
        in 14..17 -> "Nachmittags"
        in 18..22 -> "Abends"
        else -> "Nachts"
    }

    /** Begrüßung passend zur Uhrzeit. */
    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 4..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        else -> "Guten Abend"
    }

    /** „in 8 Min.“, „in 1 Std. 5 Min.“, „jetzt“ */
    fun inMinutes(minutes: Long): String = when {
        minutes <= 0 -> "jetzt"
        minutes < 60 -> "in $minutes Min."
        minutes % 60 == 0L -> "in ${minutes / 60} Std."
        else -> "in ${minutes / 60} Std. ${minutes % 60} Min."
    }
}

fun LocalDate.atMinute(minuteOfDay: Int): LocalDateTime = atTime(minuteOfDay / 60 % 24, minuteOfDay % 60)

fun LocalDateTime.toMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())

/** Bit für Wochentage in [de.pilunia.app.data.Medication.weekdays] (Montag = Bit 0). */
fun DayOfWeek.bit(): Int = 1 shl (value - 1)
