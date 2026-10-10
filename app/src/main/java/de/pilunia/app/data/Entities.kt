package de.pilunia.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

/** Darreichungsform, wie im Assistenten „Form“ wählbar. */
@Serializable
enum class MedForm(val label: String, val singular: String, val plural: String) {
    TABLETTE("Tablette", "Tablette", "Tabletten"),
    KAPSEL("Kapsel", "Kapsel", "Kapseln"),
    TROPFEN("Tropfen", "Tropfen", "Tropfen"),
    SPRAY("Spray", "Sprühstoß", "Sprühstöße"),
    SPRITZE("Spritze", "Spritze", "Spritzen");

    fun amount(n: Int): String = "$n ${if (n == 1) singular else plural}"
}

/** „Farbe in der App“: Kachel-Tönung + kräftiges Symbol. */
@Serializable
enum class MedColor { ROSE, GELB, MINT, LAVENDEL, BLAU, GRAU }

@Serializable
enum class ScheduleType { DAILY, WEEKDAYS, AS_NEEDED }

@Serializable
enum class IntakeStatus { TAKEN, SKIPPED }

@Serializable
@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Wofür (optional), z. B. „Blutdruck“. */
    val purpose: String = "",
    /** Stärke als Text, z. B. „5“ oder „1.000“. */
    val strength: String = "",
    /** Einheit, z. B. „mg“, „µg“, „I.E.“. */
    val unit: String = "mg",
    val form: MedForm = MedForm.TABLETTE,
    val color: MedColor = MedColor.ROSE,
    /** Hinweis (optional), z. B. „30 Min. vor dem Frühstück“. */
    val hint: String = "",
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    /** Wochentage für [ScheduleType.WEEKDAYS]: Bit 0 = Montag … Bit 6 = Sonntag. */
    val weekdays: Int = 0b1111111,
    /** Einnahmerhythmus: 0 = durchgehend, sonst Einnahmetage eines Zyklus (z. B. 21). */
    val intakeDays: Int = 0,
    /** Pausentage eines Zyklus (z. B. 7). */
    val pauseDays: Int = 0,
    /** Beginn (epochDay); bei Zyklen zugleich erster Einnahmetag des ersten Zyklus. */
    val startDate: Long,
    /** Ende (epochDay, einschließlich) oder null = ohne Ende. */
    val endDate: Long? = null,
    /** „Zur Einnahmezeit erinnern“. */
    val remindAtTime: Boolean = true,
    /** „Erneut erinnern“ nach 15 Min., wenn nicht bestätigt. */
    val repeatReminder: Boolean = true,
    /** „Start-Erinnerung“ am Vorabend des neuen Blisters nach einer Pause (20:00). */
    val pauseEndReminder: Boolean = true,
    /** Tabletten pro Blister. */
    val unitsPerBlister: Int = 14,
    /** Blister pro Packung. */
    val blistersPerPack: Int = 2,
    /** Aktueller Vorrat in Einheiten. */
    val stock: Int = 0,
    /** Warnen, wenn der Vorrat nur noch so viele Tage reicht (3, 7 oder 14). */
    val warnDays: Int = 7,
    /** „Vorrats-Erinnerung“ in der Detailansicht. */
    val stockReminder: Boolean = true,
    /** „In 2 Tagen erinnern“: keine Vorratswarnung vor diesem Tag (epochDay). */
    val stockSnoozeUntil: Long? = null,
    /** Zuletzt gezeigte Vorratswarnung (epochDay), damit sie nur einmal täglich kommt. */
    val lastStockWarning: Long? = null,
    /** Rezept-Notiz, z. B. „Folgerezept telefonisch in der Praxis“. */
    val prescriptionNote: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val packSize: Int get() = unitsPerBlister * blistersPerPack
    val isCycle: Boolean get() = intakeDays > 0 && pauseDays > 0
    /** „Ramipril 5 mg“ */
    val displayName: String get() = listOf(name, strength.takeIf { it.isNotBlank() }?.let { "$it $unit" })
        .filterNotNull().joinToString(" ")
}

/** Eine Uhrzeit mit Menge, z. B. 06:30 · 1 Tablette · „Morgens, nüchtern“. */
@Serializable
@Entity(
    tableName = "dose",
    foreignKeys = [ForeignKey(Medication::class, ["id"], ["medId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medId")],
)
data class Dose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val minuteOfDay: Int,
    val amount: Int = 1,
    /** z. B. „zum Frühstück“ oder „Morgens, nüchtern“. */
    val label: String = "",
)

/** Eine bestätigte oder ausgelassene Einnahme. Fehlt der Eintrag, ist die Einnahme offen/verpasst. */
@Serializable
@Entity(
    tableName = "intake",
    foreignKeys = [ForeignKey(Medication::class, ["id"], ["medId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("medId"), Index("epochDay"), Index(value = ["medId", "doseId", "epochDay"], unique = true)],
)
data class Intake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    /** Geplante Dosis; bei „Bei Bedarf“-Einnahmen eine negative, eindeutige Zahl. */
    val doseId: Long,
    val epochDay: Long,
    /** Geplante Uhrzeit (Minuten seit Mitternacht). */
    val minuteOfDay: Int,
    val status: IntakeStatus,
    /** Zeitpunkt der Bestätigung (epoch millis). */
    val takenAt: Long? = null,
    /** Tatsächlich genommene Menge (bei „Nur 1 genommen“ weniger als geplant). */
    val amount: Int = 0,
)

/** „Später erinnern“ für einen Erinnerungszeitpunkt (Tag + geplante Uhrzeit). */
@Serializable
@Entity(tableName = "snooze", primaryKeys = ["epochDay", "minuteOfDay"])
data class Snooze(
    val epochDay: Long,
    val minuteOfDay: Int,
    val untilMillis: Long,
)

data class MedWithDoses(
    @Embedded val med: Medication,
    @Relation(parentColumn = "id", entityColumn = "medId")
    val doses: List<Dose>,
) {
    val sortedDoses: List<Dose> get() = doses.sortedBy { it.minuteOfDay }
}
