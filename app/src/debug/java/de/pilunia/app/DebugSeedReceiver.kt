package de.pilunia.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.pilunia.app.data.Dose
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.atMinute
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.toMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Nur im Debug-Build: spielt Testdaten wie in den Leinwand-Screens ein, um die App mit dem Design zu vergleichen.
 * adb shell am broadcast -a de.pilunia.app.DEBUG_SEED -n de.pilunia.app/.DebugSeedReceiver [--es name Elias] [--ez empty true]
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val soon = intent.getIntExtra("soon", 0)
                if (soon > 0) addSoon(context, soon)
                else seed(context, intent.getStringExtra("name") ?: "Elias", intent.getBooleanExtra("empty", false))
            } finally {
                pending.finish()
            }
        }
    }

    /** Legt „Testmittel“ an, das in [minutes] Minuten fällig ist (zum Prüfen von Wecker und Mitteilung). */
    private suspend fun addSoon(context: Context, minutes: Int) {
        val c = PiluniaApp.container(context)
        val t = java.time.LocalTime.now().plusMinutes(minutes.toLong())
        c.repository.saveMedication(
            Medication(name = "Testmittel", strength = "10", unit = "mg", color = MedColor.MINT,
                startDate = LocalDate.now().toEpochDay(), stock = 20),
            listOf(Dose(medId = 0, minuteOfDay = t.hour * 60 + t.minute, amount = 1, label = "zum Testen")),
        )
    }

    private suspend fun seed(context: Context, name: String, empty: Boolean) {
        val c = PiluniaApp.container(context)
        val today = LocalDate.now()
        val start = today.minusDays(45).toEpochDay()
        c.settings.update { it.copy(onboardingDone = true, userName = name) }
        if (empty) {
            c.repository.replaceAll(emptyList(), emptyList())
            return
        }
        var id = 1L
        fun med(m: Medication, vararg doses: Dose): MedWithDoses {
            val mid = id++
            return MedWithDoses(m.copy(id = mid, createdAt = mid), doses.mapIndexed { i, d -> d.copy(id = mid * 10 + i, medId = mid) })
        }
        val meds = listOf(
            med(
                Medication(name = "Ramipril", purpose = "Blutdruck", strength = "5", unit = "mg", color = MedColor.ROSE,
                    startDate = start, unitsPerBlister = 14, blistersPerPack = 2, stock = 6, warnDays = 7,
                    prescriptionNote = "Folgerezept telefonisch in der Praxis"),
                Dose(medId = 0, minuteOfDay = 8 * 60, amount = 1, label = "zum Frühstück"),
            ),
            med(
                Medication(name = "Vitamin D3", strength = "1.000", unit = "I.E.", color = MedColor.GELB,
                    startDate = start, unitsPerBlister = 30, blistersPerPack = 3, stock = 54, warnDays = 7),
                Dose(medId = 0, minuteOfDay = 12 * 60, amount = 1, label = ""),
            ),
            med(
                Medication(name = "Omega-3", strength = "1.000", unit = "mg", form = MedForm.KAPSEL, color = MedColor.MINT,
                    startDate = start, unitsPerBlister = 15, blistersPerPack = 4, stock = 38, warnDays = 7),
                Dose(medId = 0, minuteOfDay = 19 * 60, amount = 1, label = "zum Abendessen"),
            ),
            med(
                Medication(name = "Magnesium", strength = "300", unit = "mg", color = MedColor.LAVENDEL,
                    startDate = start, unitsPerBlister = 10, blistersPerPack = 5, stock = 18, warnDays = 7),
                Dose(medId = 0, minuteOfDay = 20 * 60, amount = 1, label = ""),
            ),
            med(
                Medication(name = "Ibuprofen", strength = "400", unit = "mg", color = MedColor.BLAU,
                    scheduleType = ScheduleType.AS_NEEDED, startDate = start, unitsPerBlister = 10, blistersPerPack = 2,
                    stock = 14, stockReminder = false),
            ),
            med(
                Medication(name = "Verhütungspille", purpose = "Verhütung", color = MedColor.LAVENDEL,
                    intakeDays = 21, pauseDays = 7, startDate = today.minusDays(23).toEpochDay(),
                    unitsPerBlister = 21, blistersPerPack = 3, stock = 42, warnDays = 7),
                Dose(medId = 0, minuteOfDay = 7 * 60 + 30, amount = 1, label = "Morgens"),
            ),
        )
        // Verlauf: alles genommen bis gestern, ein paar verpasste Einnahmen; heute noch offen.
        val intakes = mutableListOf<Intake>()
        var d = LocalDate.ofEpochDay(start)
        while (d < today) {
            for (o in occurrencesOn(d, meds, emptyList())) {
                val missed = (d == today.minusDays(3) && o.med.name == "Omega-3") ||
                    (d.dayOfMonth % 13 == 0 && o.med.name == "Magnesium")
                if (missed) continue
                val delay = (d.dayOfMonth * 7 + o.minute) % 17
                intakes += Intake(
                    medId = o.med.id, doseId = o.dose.id, epochDay = d.toEpochDay(), minuteOfDay = o.minute,
                    status = IntakeStatus.TAKEN, takenAt = d.atMinute(o.minute + delay).toMillis(), amount = o.dose.amount,
                )
            }
            d = d.plusDays(1)
        }
        c.repository.replaceAll(meds, intakes)
    }
}
