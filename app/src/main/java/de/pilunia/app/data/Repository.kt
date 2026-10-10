package de.pilunia.app.data

import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.occurrencesOn
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Zentrale Datenschicht. Nach jeder Änderung wird [onChanged] aufgerufen,
 * damit die Erinnerungen neu geplant werden.
 */
class PiluniaRepository(
    private val db: PiluniaDatabase,
    val settings: SettingsStore,
    private val onChanged: suspend () -> Unit,
) {
    private val meds = db.medications()
    private val intakes = db.intakes()
    private val snoozes = db.snoozes()

    // ---------- Lesen ----------

    fun observeMeds(): Flow<List<MedWithDoses>> = meds.observeAll()
    fun observeMed(id: Long): Flow<MedWithDoses?> = meds.observe(id)
    fun observeIntakes(): Flow<List<Intake>> = intakes.observeAll()
    fun observeIntakes(from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        intakes.observeBetween(from.toEpochDay(), to.toEpochDay())
    fun observeSnoozes(): Flow<List<Snooze>> = snoozes.observeAll()

    suspend fun meds(): List<MedWithDoses> = meds.getAll()
    suspend fun med(id: Long): MedWithDoses? = meds.get(id)
    suspend fun intakes(): List<Intake> = intakes.getAll()
    suspend fun intakes(from: LocalDate, to: LocalDate): List<Intake> = intakes.getBetween(from.toEpochDay(), to.toEpochDay())
    suspend fun snoozes(): List<Snooze> = snoozes.getAll()

    /** Alle geplanten Einnahmen eines Erinnerungszeitpunkts (Tag + Uhrzeit), z. B. 3 Medikamente um 08:00. */
    suspend fun slot(day: LocalDate, minute: Int): List<Occurrence> =
        occurrencesOn(day, meds(), intakes(day, day), snoozes()).filter { it.minute == minute }

    // ---------- Medikamente ----------

    /**
     * Legt ein Medikament an oder aktualisiert es. Dosen mit id 0 werden neu angelegt,
     * vorhandene behalten ihre id (damit der Verlauf erhalten bleibt), fehlende werden gelöscht.
     * @return id des Medikaments
     */
    suspend fun saveMedication(med: Medication, doses: List<Dose>): Long {
        val id = if (med.id == 0L) meds.insert(med) else { meds.update(med); med.id }
        val existing = meds.get(id)?.doses.orEmpty()
        val keepIds = doses.filter { it.id != 0L }.map { it.id }.toSet()
        val toDelete = existing.filter { it.id !in keepIds }
        val toUpdate = doses.filter { it.id != 0L }
        val toInsert = doses.filter { it.id == 0L }.map { it.copy(medId = id) }
        db.runInTransactionSuspend {
            if (toDelete.isNotEmpty() || toUpdate.isNotEmpty()) {
                meds.deleteDoses(id)
                meds.insertDoses(toUpdate.map { it.copy(medId = id) })
            }
            if (toInsert.isNotEmpty()) meds.insertDoses(toInsert)
        }
        onChanged()
        return id
    }

    suspend fun updateMedication(med: Medication) {
        meds.update(med)
        onChanged()
    }

    suspend fun deleteMedication(med: Medication) {
        meds.delete(med)
        onChanged()
    }

    /** „Neue Packung eintragen“: Vorrat um eine Packung erhöhen. */
    suspend fun addPack(medId: Long, packs: Int = 1) {
        val m = meds.get(medId)?.med ?: return
        meds.update(m.copy(stock = m.stock + m.packSize * packs, stockSnoozeUntil = null, lastStockWarning = null))
        onChanged()
    }

    /** Vorrat direkt setzen (z. B. nach Zählen). */
    suspend fun setStock(medId: Long, stock: Int) {
        val m = meds.get(medId)?.med ?: return
        meds.update(m.copy(stock = stock.coerceAtLeast(0)))
        onChanged()
    }

    /** „In 2 Tagen erinnern“ bei knappem Vorrat. */
    suspend fun snoozeStockWarning(medId: Long, days: Int = 2) {
        val m = meds.get(medId)?.med ?: return
        meds.update(m.copy(stockSnoozeUntil = Fmt.today().plusDays(days.toLong()).toEpochDay()))
        onChanged()
    }

    // ---------- Einnahmen ----------

    /** Einnahme bestätigen. [amount] < geplante Menge bei „Nur 1 genommen“. */
    suspend fun take(occ: Occurrence, amount: Int = occ.dose.amount, at: Long = System.currentTimeMillis()) {
        val old = intakes.find(occ.med.id, occ.dose.id, occ.day.toEpochDay())
        val oldTaken = if (old?.status == IntakeStatus.TAKEN) old.amount else 0
        intakes.upsert(
            Intake(
                id = old?.id ?: 0, medId = occ.med.id, doseId = occ.dose.id, epochDay = occ.day.toEpochDay(),
                minuteOfDay = occ.minute, status = IntakeStatus.TAKEN, takenAt = at, amount = amount,
            )
        )
        meds.changeStock(occ.med.id, -(amount - oldTaken))
        clearSnoozeIfDone(occ.day, occ.minute)
        onChanged()
    }

    /** Mehrere Einnahmen eines Zeitpunkts auf einmal bestätigen („Alle 3 bestätigen“). */
    suspend fun takeAll(list: List<Occurrence>) {
        val now = System.currentTimeMillis()
        for (o in list) {
            if (o.intake?.status == IntakeStatus.TAKEN) continue
            val old = intakes.find(o.med.id, o.dose.id, o.day.toEpochDay())
            intakes.upsert(
                Intake(
                    id = old?.id ?: 0, medId = o.med.id, doseId = o.dose.id, epochDay = o.day.toEpochDay(),
                    minuteOfDay = o.minute, status = IntakeStatus.TAKEN, takenAt = now, amount = o.dose.amount,
                )
            )
            meds.changeStock(o.med.id, -o.dose.amount)
        }
        list.firstOrNull()?.let { clearSnoozeIfDone(it.day, it.minute) }
        onChanged()
    }

    /** „Heute auslassen“. */
    suspend fun skip(list: List<Occurrence>) {
        for (o in list) {
            val old = intakes.find(o.med.id, o.dose.id, o.day.toEpochDay())
            if (old?.status == IntakeStatus.TAKEN) meds.changeStock(o.med.id, old.amount)
            intakes.upsert(
                Intake(
                    id = old?.id ?: 0, medId = o.med.id, doseId = o.dose.id, epochDay = o.day.toEpochDay(),
                    minuteOfDay = o.minute, status = IntakeStatus.SKIPPED, takenAt = null, amount = 0,
                )
            )
        }
        list.firstOrNull()?.let { clearSnoozeIfDone(it.day, it.minute) }
        onChanged()
    }

    /** „Rückgängig machen“: Bestätigung/Auslassen zurücknehmen, Vorrat wiederherstellen. */
    suspend fun undo(occ: Occurrence) {
        val old = intakes.find(occ.med.id, occ.dose.id, occ.day.toEpochDay()) ?: return
        undoIntake(old)
    }

    suspend fun undoIntake(intake: Intake) {
        intakes.delete(intake)
        if (intake.status == IntakeStatus.TAKEN) meds.changeStock(intake.medId, intake.amount)
        onChanged()
    }

    /** Einnahme „Bei Bedarf“ protokollieren. */
    suspend fun takeAsNeeded(medId: Long, amount: Int = 1, at: Long = System.currentTimeMillis()) {
        val day = Fmt.today().toEpochDay()
        intakes.upsert(
            Intake(
                medId = medId, doseId = -at, epochDay = day, minuteOfDay = Fmt.minuteOf(at),
                status = IntakeStatus.TAKEN, takenAt = at, amount = amount,
            )
        )
        meds.changeStock(medId, -amount)
        onChanged()
    }

    /** „Später erinnern“ für einen Zeitpunkt. */
    suspend fun snooze(day: LocalDate, minute: Int, untilMillis: Long) {
        snoozes.put(Snooze(day.toEpochDay(), minute, untilMillis))
        onChanged()
    }

    private suspend fun clearSnoozeIfDone(day: LocalDate, minute: Int) {
        val open = slot(day, minute).any { it.intake == null }
        if (!open) snoozes.clear(day.toEpochDay(), minute)
    }

    suspend fun cleanupOldSnoozes() = snoozes.clearBefore(Fmt.today().toEpochDay())

    // ---------- Sicherung ----------

    suspend fun replaceAll(newMeds: List<MedWithDoses>, newIntakes: List<Intake>) {
        db.runInTransactionSuspend {
            snoozes.deleteAll()
            intakes.deleteAll()
            meds.deleteAll()
            for (m in newMeds) {
                meds.insert(m.med)
                meds.insertDoses(m.doses)
            }
            intakes.insertAll(newIntakes)
        }
        onChanged()
    }
}

private suspend fun PiluniaDatabase.runInTransactionSuspend(block: suspend () -> Unit) {
    withTransaction { block() }
}
