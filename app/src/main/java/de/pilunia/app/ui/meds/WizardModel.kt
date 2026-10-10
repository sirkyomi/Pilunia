package de.pilunia.app.ui.meds

import de.pilunia.app.data.Dose
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import androidx.compose.runtime.saveable.Saver
import de.pilunia.app.domain.Fmt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
private data class WizardParcel(val med: Medication, val doses: List<Dose>)

private val WizardJson = Json { ignoreUnknownKeys = true }

/** Speichert den Entwurf über Konfigurationswechsel (Drehen, Dunkelmodus) hinweg. */
val WizardDraftSaver: Saver<WizardDraft?, String> = Saver(
    save = { d ->
        d?.let {
            val med = it.toMedication(null).copy(
                name = it.name, purpose = it.purpose, strength = it.strength, hint = it.hint,
                intakeDays = it.intakeDays, pauseDays = it.pauseDays,
            )
            WizardJson.encodeToString(WizardParcel.serializer(), WizardParcel(med, it.doses))
        }
    },
    restore = { s ->
        runCatching { WizardJson.decodeFromString(WizardParcel.serializer(), s) }.getOrNull()
            ?.let { WizardDraft.from(MedWithDoses(it.med, it.doses)) }
    },
)

/** Speichert das geladene Medikament (Basis beim Bearbeiten). */
val WizardMedSaver: Saver<Medication?, String> = Saver(
    save = { m -> m?.let { WizardJson.encodeToString(Medication.serializer(), it) } },
    restore = { s -> runCatching { WizardJson.decodeFromString(Medication.serializer(), s) }.getOrNull() },
)

/** Einheiten im Auswahlmenü „Einheit“. */
val WizardUnits = listOf("mg", "µg", "g", "I.E.", "ml")

/** Vorgegebene Einnahmerhythmen (Einnahmetage + Pausentage); 0 + 0 = durchgehend. */
val WizardCyclePresets = listOf(21 to 7, 24 to 4)

/**
 * Alle Eingaben des Assistenten. Unveränderlich, damit „Gibt es Änderungen?“
 * einfach per Vergleich mit dem Anfangszustand beantwortet werden kann.
 */
data class WizardDraft(
    val name: String = "",
    val purpose: String = "",
    val strength: String = "",
    val unit: String = "mg",
    val form: MedForm = MedForm.TABLETTE,
    val color: MedColor = MedColor.ROSE,
    val hint: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val weekdays: Int = 0b1111111,
    val intakeDays: Int = 0,
    val pauseDays: Int = 0,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val remindAtTime: Boolean = true,
    val repeatReminder: Boolean = true,
    /** Vorhandene Dosen behalten ihre id, neue haben id 0. */
    val doses: List<Dose> = listOf(Dose(medId = 0, minuteOfDay = 8 * 60, amount = 1)),
    val unitsPerBlister: Int = 14,
    val blistersPerPack: Int = 2,
    val stock: Int = 28,
    val warnDays: Int = 7,
) {
    val isCycle: Boolean get() = intakeDays > 0 && pauseDays > 0
    val isAsNeeded: Boolean get() = scheduleType == ScheduleType.AS_NEEDED
    val packSize: Int get() = unitsPerBlister * blistersPerPack

    /** Ist der eingestellte Rhythmus keiner der Vorgaben („Eigener“)? */
    val isCustomCycle: Boolean get() = isCycle && (intakeDays to pauseDays) !in WizardCyclePresets

    fun toMedication(base: Medication?): Medication {
        val m = base ?: Medication(name = name.trim(), startDate = startDate.toEpochDay())
        return m.copy(
            name = name.trim(),
            purpose = purpose.trim(),
            strength = strength.trim().trimEnd(',', '.'),
            unit = unit,
            form = form,
            color = color,
            hint = hint.trim(),
            scheduleType = scheduleType,
            weekdays = weekdays,
            intakeDays = if (isCycle) intakeDays else 0,
            pauseDays = if (isCycle) pauseDays else 0,
            startDate = startDate.toEpochDay(),
            endDate = endDate?.toEpochDay(),
            remindAtTime = remindAtTime,
            repeatReminder = repeatReminder,
            unitsPerBlister = unitsPerBlister,
            blistersPerPack = blistersPerPack,
            stock = stock,
            warnDays = warnDays,
        )
    }

    /** Dosen zum Speichern („Bei Bedarf“ hat keine festen Uhrzeiten). */
    fun dosesForSave(medId: Long): List<Dose> =
        if (isAsNeeded) emptyList() else doses.sortedBy { it.minuteOfDay }.map { it.copy(medId = medId, label = it.label.trim()) }

    companion object {
        fun from(m: MedWithDoses): WizardDraft {
            val med = m.med
            return WizardDraft(
                name = med.name,
                purpose = med.purpose,
                strength = med.strength,
                unit = med.unit,
                form = med.form,
                color = med.color,
                hint = med.hint,
                scheduleType = med.scheduleType,
                weekdays = med.weekdays,
                intakeDays = if (med.isCycle) med.intakeDays else 0,
                pauseDays = if (med.isCycle) med.pauseDays else 0,
                startDate = LocalDate.ofEpochDay(med.startDate),
                endDate = med.endDate?.let { LocalDate.ofEpochDay(it) },
                remindAtTime = med.remindAtTime,
                repeatReminder = med.repeatReminder,
                doses = m.sortedDoses.ifEmpty { listOf(Dose(medId = med.id, minuteOfDay = 8 * 60, amount = 1)) },
                unitsPerBlister = med.unitsPerBlister,
                blistersPerPack = med.blistersPerPack,
                stock = med.stock,
                warnDays = med.warnDays,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Texte passend zur Darreichungsform
// ---------------------------------------------------------------------

/** Behältnis einer Einheit: Blister bzw. Flasche bei Tropfen und Spray. */
private val MedForm.wizardInBottle: Boolean get() = this == MedForm.TROPFEN || this == MedForm.SPRAY

/** „Tabletten pro Blister“, „Tropfen pro Flasche“ … */
fun MedForm.wizardPerContainer(): String = "$plural pro ${if (wizardInBottle) "Flasche" else "Blister"}"

/** „Blister pro Packung“, „Flaschen pro Packung“. */
fun MedForm.wizardContainersPerPack(): String = "${if (wizardInBottle) "Flaschen" else "Blister"} pro Packung"

/** „Packung mit 100 Stück“ bzw. „Packung mit 600 Tropfen“. */
fun MedForm.wizardPackLabel(n: Int): String = if (wizardInBottle) "Packung mit ${amount(n)}" else "Packung mit $n Stück"

/** „die Tablette“, „das Spray“ … (für Fließtext). */
fun MedForm.wizardWithArticle(): String = when (this) {
    MedForm.TABLETTE -> "die Tablette"
    MedForm.KAPSEL -> "die Kapsel"
    MedForm.TROPFEN -> "die Tropfen"
    MedForm.SPRAY -> "das Spray"
    MedForm.SPRITZE -> "die Spritze"
}

/** „Täglich eine Tablette“ bzw. „Täglich 2 Tabletten“. */
fun MedForm.wizardDailyLabel(n: Int): String = if (n == 1) {
    val article = if (this == MedForm.TROPFEN || this == MedForm.SPRAY) "einen" else "eine"
    "Täglich $article $singular"
} else "Täglich ${amount(n)}"

/** „Keine Tablette“, „Kein Tropfen“ … */
fun MedForm.wizardNoneLabel(): String =
    "${if (this == MedForm.TROPFEN || this == MedForm.SPRAY) "Kein" else "Keine"} $singular"

// ---------------------------------------------------------------------
// Datumstexte
// ---------------------------------------------------------------------

/** „So 11. Okt.“ (wie im Design ohne Komma), mit Jahr außerhalb des aktuellen Jahres. */
fun wizardDay(d: LocalDate, today: LocalDate = Fmt.today()): String =
    "${Fmt.weekdayShort(d)} ${d.dayOfMonth}. ${Fmt.monthShort(d)}" + if (d.year != today.year) " ${d.year}" else ""

/** „Ab morgen, So 11. Okt. · ohne Ende“ */
fun wizardBeginLabel(start: LocalDate, end: LocalDate?, today: LocalDate = Fmt.today()): String {
    val s = when {
        start == today -> "Ab heute, ${wizardDay(start, today)}"
        start == today.plusDays(1) -> "Ab morgen, ${wizardDay(start, today)}"
        start < today -> "Seit ${wizardDay(start, today)}"
        else -> "Ab ${wizardDay(start, today)}"
    }
    val e = end?.let { "bis ${wizardDay(it, today)}" } ?: "ohne Ende"
    return "$s · $e"
}

/** „So 13. – Sa 19. Dez.“ bzw. über Monatsgrenzen „So 27. Dez. – Sa 2. Jan. 2027“. */
fun wizardRange(a: LocalDate, b: LocalDate, today: LocalDate = Fmt.today()): String {
    if (a == b) return wizardDay(a, today)
    val sameMonth = a.year == b.year && a.month == b.month
    val left = when {
        sameMonth -> "${Fmt.weekdayShort(a)} ${a.dayOfMonth}."
        a.year != b.year -> wizardDay(a, today)
        else -> "${Fmt.weekdayShort(a)} ${a.dayOfMonth}. ${Fmt.monthShort(a)}"
    }
    return "$left – ${wizardDay(b, today)}"
}

/** „1 Tag“ / „7 Tage“ */
fun wizardDays(n: Int): String = if (n == 1) "1 Tag" else "$n Tage"

/** Untertitel einer Uhrzeit-Karte: Tageszeit, ergänzt um die Beschreibung („Morgens, nüchtern“). */
fun wizardDoseCaption(d: Dose): String {
    val part = Fmt.daypart(d.minuteOfDay)
    val label = d.label.trim()
    return when {
        label.isEmpty() -> part
        label.startsWith(part, ignoreCase = true) -> label
        else -> "$part, $label"
    }
}

/** Vorschlag für eine weitere Uhrzeit: erste freie Standardzeit, sonst eine Stunde nach der letzten. */
fun wizardNextDoseMinute(doses: List<Dose>): Int {
    val used = doses.map { it.minuteOfDay }.toSet()
    listOf(8 * 60, 12 * 60, 18 * 60, 22 * 60).firstOrNull { it !in used }?.let { return it }
    return ((doses.maxOfOrNull { it.minuteOfDay } ?: (8 * 60)) + 60) % (24 * 60)
}
