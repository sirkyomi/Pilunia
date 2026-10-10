package de.pilunia.app.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.pilunia.app.PiluniaApp
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.adherence
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.domain.cyclePhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } finally { pending.finish() }
    }
}

/** Empfängt alle geplanten Wecker. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val c = PiluniaApp.container(context)
        val repo = c.repository
        val s = repo.settings.current()
        when (intent.action) {
            ReminderScheduler.Kind.DOSE.action -> {
                val slot = c.scheduler.dueSlot(repo)
                if (slot != null) {
                    val (day, minute) = slot
                    val items = repo.slot(day, minute).filter { it.intake == null && it.med.remindAtTime }
                    c.notifier.showDoseReminder(day, minute, items, s)
                }
            }
            ReminderScheduler.Kind.PAUSE_END.action -> {
                val tomorrow = Fmt.today().plusDays(1)
                repo.meds().filter { it.med.isCycle && it.med.pauseEndReminder }.forEach { m ->
                    val phase = m.med.cyclePhase(tomorrow)
                    if (phase != null && !phase.inPause && phase.dayInPhase == 1) {
                        c.notifier.showInfo(
                            "pause", m.med.id.toInt(), "Morgen geht's weiter",
                            "Ab morgen wieder ${m.med.displayName} – ein neuer Blister beginnt.",
                        )
                    }
                }
            }
            ReminderScheduler.Kind.STOCK.action -> checkStock(context)
            ReminderScheduler.Kind.SUMMARY.action -> {
                val today = Fmt.today()
                val occ = occurrencesOn(today, repo.meds(), repo.intakes(today, today))
                if (occ.isNotEmpty()) {
                    val taken = occ.count { it.intake?.status == IntakeStatus.TAKEN }
                    val open = occ.filter { it.intake == null }
                    val (title, text) = if (open.isEmpty() && taken == occ.size) {
                        "Alles erledigt für heute" to "Du hast alle $taken Einnahmen geschafft. Schlaf gut${s.userName.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}!"
                    } else {
                        "Tagesabschluss" to "$taken von ${occ.size} genommen. Offen: ${open.joinToString { it.med.displayName }}"
                    }
                    c.notifier.showInfo("summary", 1, title, text)
                }
            }
            ReminderScheduler.Kind.WEEKLY.action -> {
                val today = Fmt.today()
                val from = today.minusDays(6)
                val a = adherence(from, today, repo.meds(), repo.intakes(from, today))
                c.notifier.showInfo(
                    "weekly", 2, "Dein Wochenbericht",
                    "${a.percent} % Einnahmetreue diese Woche · ${a.taken} von ${a.due} genommen" +
                        if (a.missed > 0) ", ${a.missed} verpasst." else ".",
                )
            }
        }
        c.reschedule()
    }

    companion object {
        /** Prüft alle Vorräte und warnt höchstens einmal pro Tag und Medikament. */
        suspend fun checkStock(context: Context) {
            val c = PiluniaApp.container(context)
            val repo = c.repository
            val s = repo.settings.current()
            if (!s.stockWarningEnabled) return
            val today = Fmt.today()
            val intakes = repo.intakes(today, today)
            for (m in repo.meds()) {
                val med = m.med
                if (!med.stockReminder) continue
                if (med.stockSnoozeUntil != null && med.stockSnoozeUntil > today.toEpochDay()) continue
                if (med.lastStockWarning == today.toEpochDay()) continue
                val f = forecast(m, intakes, today)
                if (!f.isLow(med.warnDays) || f.lastCoveredDay == null) continue
                c.notifier.showStockWarning(
                    med.id, "Dein ${med.name} wird knapp",
                    "Noch ${med.form.amount(med.stock)} – das reicht bis ${Fmt.dayShort(f.lastCoveredDay)}. Zeit für ein Folgerezept.",
                )
                repo.updateMedication(med.copy(lastStockWarning = today.toEpochDay()))
            }
        }
    }
}

/** Aktionen direkt aus der Mitteilung: „Eingenommen“ und „In 30 Min.“. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        val c = PiluniaApp.container(context)
        val repo = c.repository
        val day = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DAY, Fmt.today().toEpochDay()))
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val open = repo.slot(day, minute).filter { it.intake == null }
        when (intent.action) {
            ACTION_TAKE -> repo.takeAll(open)
            ACTION_SNOOZE_30 -> repo.snooze(day, minute, System.currentTimeMillis() + 30 * 60_000L)
        }
        c.notifier.cancelDoseReminder(day, minute)
    }

    companion object {
        const val ACTION_TAKE = "de.pilunia.app.ACTION_TAKE"
        const val ACTION_SNOOZE_30 = "de.pilunia.app.ACTION_SNOOZE_30"
        const val EXTRA_DAY = "day"
        const val EXTRA_MINUTE = "minute"

        fun pending(context: Context, action: String, day: LocalDate, minute: Int): PendingIntent {
            val i = Intent(context, ActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_DAY, day.toEpochDay()).putExtra(EXTRA_MINUTE, minute)
            val code = Notifier.requestCode(day, minute) * 2 + if (action == ACTION_TAKE) 0 else 1
            return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

/** Plant nach Neustart, Zeitzonen- oder Uhrzeitänderung und App-Update neu. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        PiluniaApp.container(context).reschedule()
    }
}
