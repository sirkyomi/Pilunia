package de.pilunia.app.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.occurrencesOn
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Status eines Verlaufseintrags in Export und Bericht. */
enum class EntryStatus(val label: String) {
    TAKEN("Genommen"),
    SKIPPED("Ausgelassen"),
    MISSED("Verpasst"),
    OPEN("Offen"),
    AS_NEEDED("Bei Bedarf genommen"),
}

/** Ein Eintrag des Verlaufs: geplante (oder bei Bedarf protokollierte) Einnahme an einem Tag. */
data class HistoryEntry(
    val day: LocalDate,
    /** Geplante Uhrzeit; bei „Bei Bedarf“ die Uhrzeit der Einnahme. */
    val minute: Int,
    val med: Medication,
    /** Geplante bzw. genommene Menge. */
    val amount: Int,
    val status: EntryStatus,
    val takenAt: Long?,
)

/**
 * Baut den gesamten Verlauf von [from] (Standard: frühester Beginn/Eintrag) bis [to].
 * Künftige Einnahmen von heute werden ausgelassen.
 */
fun buildHistory(
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    now: LocalDateTime = LocalDateTime.now(),
    from: LocalDate? = null,
    to: LocalDate = now.toLocalDate(),
): List<HistoryEntry> {
    if (meds.isEmpty() && intakes.isEmpty()) return emptyList()
    val byDay = intakes.groupBy { it.epochDay }
    val medById = meds.associateBy { it.med.id }
    val earliest = listOfNotNull(
        meds.minOfOrNull { it.med.startDate },
        intakes.minOfOrNull { it.epochDay },
    ).minOrNull()?.let { LocalDate.ofEpochDay(it) } ?: to
    var d = maxOf(from ?: earliest, earliest)
    val out = ArrayList<HistoryEntry>()
    while (d <= to) {
        val dayIntakes = byDay[d.toEpochDay()].orEmpty()
        val occ = occurrencesOn(d, meds, dayIntakes)
        val used = HashSet<Long>()
        for (o in occ) {
            o.intake?.let { used += it.id }
            val st = when (o.status(now)) {
                OccStatus.TAKEN -> EntryStatus.TAKEN
                OccStatus.SKIPPED -> EntryStatus.SKIPPED
                OccStatus.MISSED -> EntryStatus.MISSED
                OccStatus.OPEN, OccStatus.SNOOZED -> EntryStatus.OPEN
                OccStatus.UPCOMING -> null
            } ?: continue
            val amount = if (st == EntryStatus.TAKEN) o.intake?.amount ?: o.dose.amount else o.dose.amount
            out += HistoryEntry(d, o.minute, o.med, amount, st, o.intake?.takenAt)
        }
        // Bei-Bedarf-Einnahmen und Einträge, deren Dosis inzwischen geändert wurde.
        for (i in dayIntakes) {
            if (i.id in used) continue
            val med = medById[i.medId]?.med ?: continue
            val st = when {
                i.doseId < 0 -> EntryStatus.AS_NEEDED
                i.status == IntakeStatus.TAKEN -> EntryStatus.TAKEN
                else -> EntryStatus.SKIPPED
            }
            out += HistoryEntry(d, i.minuteOfDay, med, i.amount, st, i.takenAt)
        }
        d = d.plusDays(1)
    }
    return out.sortedWith(compareBy({ it.day }, { it.minute }, { it.med.name.lowercase() }))
}

/** „5 mg“ oder leer. */
val Medication.strengthText: String get() = if (strength.isBlank()) "" else "$strength $unit"

/** „Täglich“, „Mo, Mi, Fr“, „Bei Bedarf“ – mit Zyklus und Zeitraum. */
fun Medication.scheduleText(): String {
    val base = when (scheduleType) {
        ScheduleType.DAILY -> "Täglich"
        ScheduleType.AS_NEEDED -> "Bei Bedarf"
        ScheduleType.WEEKDAYS -> DayOfWeek.entries
            .filter { weekdays and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { Fmt.weekdayShort(it) }
            .ifEmpty { "Keine Wochentage" }
    }
    val parts = mutableListOf(base)
    if (isCycle) parts += "$intakeDays Tage Einnahme, $pauseDays Tage Pause"
    val today = Fmt.today().toEpochDay()
    if (startDate > today) parts += "ab ${Fmt.numeric(LocalDate.ofEpochDay(startDate))}"
    endDate?.let { parts += "bis ${Fmt.numeric(LocalDate.ofEpochDay(it))}" }
    return parts.joinToString(" · ")
}

/** Datei im Cache-Ordner „exports“ (über den FileProvider teilbar). */
fun exportFile(context: Context, name: String): File {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    return File(dir, name)
}

/** Teilt eine Datei aus dem Export-Ordner über ACTION_SEND. */
fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
