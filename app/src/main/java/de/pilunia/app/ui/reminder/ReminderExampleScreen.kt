package de.pilunia.app.ui.reminder

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.pilunia.app.data.Dose
import de.pilunia.app.data.Intake
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.Medication
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import java.time.LocalDate

enum class ReminderExample(val label: String) {
    SINGLE("Eine Tablette"),
    DOUBLE("Mehrere Tabletten"),
    MULTIPLE("Mehrere Medikamente"),
}

private const val EXAMPLE_MINUTE = 8 * 60

/** Uses the real reminder layout; all actions and sample intakes stay in memory. */
@Composable
fun ReminderExampleScreen(example: ReminderExample, onClose: () -> Unit) {
    val day = remember { LocalDate.now() }
    val initial = remember(example, day) { exampleOccurrences(example, day) }
    var checked by rememberSaveable(example) { mutableStateOf(emptyList<Long>()) }
    var message by rememberSaveable(example) { mutableStateOf<String?>(null) }
    val slot = initial.map { occurrence ->
        occurrence.copy(intake = if (occurrence.med.id in checked) Intake(
            medId = occurrence.med.id,
            doseId = occurrence.dose.id,
            epochDay = day.toEpochDay(),
            minuteOfDay = EXAMPLE_MINUTE,
            status = IntakeStatus.TAKEN,
            amount = occurrence.dose.amount,
        ) else null)
    }
    val data = SlotData(
        day = day,
        meds = slot.map { MedWithDoses(it.med, listOf(it.dose)) },
        intakes = slot.mapNotNull { it.intake },
        dayOcc = slot,
        slot = slot,
    )
    RemindScreen(
        data, EXAMPLE_MINUTE, busy = false,
        actions = RemindActions(
            onClose = onClose,
            onConfirmAll = { message = "In einer echten Erinnerung wird damit die Einnahme bestätigt. In dieser Vorschau wird nichts gespeichert." },
            onTakeOne = { message = "Damit wird nur eine Tablette bestätigt. In dieser Vorschau wird nichts gespeichert." },
            onToggle = { occurrence ->
                checked = if (occurrence.med.id in checked) checked - occurrence.med.id else checked + occurrence.med.id
            },
            onSnooze = { message = "In einer echten Erinnerung wählst du hier, wann Pilunia erneut erinnern soll. Diese Vorschau stellt keinen Wecker." },
            onSkip = { message = "Damit wird die heutige Einnahme ausgelassen. In dieser Vorschau wird nichts gespeichert." },
        ),
        preview = true,
    )
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            containerColor = Pilunia.colors.flaeche,
            title = { Text("Vorschau", style = atkinson(20, 700), color = Pilunia.colors.tinte) },
            text = { Text(text, style = atkinson(16), color = Pilunia.colors.tinte) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("Zurück zum Beispiel", color = Pilunia.colors.akzentText) } },
        )
    }
}

private fun exampleOccurrences(example: ReminderExample, day: LocalDate): List<Occurrence> {
    fun sample(id: Long, name: String, amount: Int, color: MedColor, form: MedForm = MedForm.TABLETTE): Occurrence {
        val med = Medication(
            id = id, name = name, color = color, form = form,
            startDate = day.toEpochDay(), stock = 30, createdAt = 0,
        )
        val dose = Dose(id = id, medId = id, minuteOfDay = EXAMPLE_MINUTE, amount = amount, label = "zum Frühstück")
        return Occurrence(med, dose, day, intake = null, snoozedUntil = null)
    }
    return when (example) {
        ReminderExample.SINGLE -> listOf(sample(1, "Beispielmedikament", 1, MedColor.ROSE))
        ReminderExample.DOUBLE -> listOf(sample(1, "Beispielmedikament", 2, MedColor.LAVENDEL))
        ReminderExample.MULTIPLE -> listOf(
            sample(1, "Beispiel A", 1, MedColor.ROSE),
            sample(2, "Beispiel B", 1, MedColor.MINT, MedForm.KAPSEL),
        )
    }
}
