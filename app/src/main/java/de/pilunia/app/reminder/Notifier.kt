package de.pilunia.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.pilunia.app.MainActivity
import de.pilunia.app.R
import de.pilunia.app.data.AppSettings
import de.pilunia.app.data.ReminderSound
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.Occurrence
import java.time.LocalDate
import java.time.LocalTime

/** Baut und zeigt alle Mitteilungen der App. */
class Notifier(private val context: Context) {

    private val nm = context.getSystemService(NotificationManager::class.java)

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canUseFullScreen(): Boolean = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()

    fun notificationsEnabled(): Boolean = canPost() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    // ---------- Kanäle ----------

    private fun reminderChannel(s: AppSettings, quiet: Boolean): String {
        if (quiet || s.sound == ReminderSound.LEISE) return ensureChannel(
            CH_QUIET, "Leise Erinnerungen", NotificationManager.IMPORTANCE_LOW, null, false,
            "Erinnerungen während der Ruhezeiten oder ohne Ton",
        )
        val id = "rem_${s.sound.name.lowercase()}_${if (s.vibration) "vib" else "novib"}"
        val sound = when (s.sound) {
            ReminderSound.GLOECKCHEN -> Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.gloeckchen}")
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        // Alte Varianten mit anderem Ton entfernen, damit in den Systemeinstellungen nur einer erscheint.
        nm.notificationChannels.filter { it.id.startsWith("rem_") && it.id != id }.forEach { nm.deleteNotificationChannel(it.id) }
        return ensureChannel(
            id, "Einnahme-Erinnerungen", NotificationManager.IMPORTANCE_HIGH, sound, s.vibration,
            "Erinnert dich genau zur Einnahmezeit",
        )
    }

    private fun ensureChannel(
        id: String, name: String, importance: Int, sound: Uri?, vibrate: Boolean, description: String,
    ): String {
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, name, importance).apply {
                this.description = description
                if (sound != null) {
                    setSound(sound, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                } else setSound(null, null)
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }
        return id
    }

    private fun infoChannel(id: String, name: String) =
        ensureChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT, null, false, name)

    fun isQuietNow(s: AppSettings, now: LocalTime = LocalTime.now()): Boolean {
        if (!s.quietEnabled) return false
        val m = now.hour * 60 + now.minute
        return if (s.quietStartMin <= s.quietEndMin) m in s.quietStartMin until s.quietEndMin
        else m >= s.quietStartMin || m < s.quietEndMin
    }

    // ---------- Einnahme-Erinnerung ----------

    /**
     * Mitteilung „Zeit für deine Tablette“ mit den Aktionen „Eingenommen“ und „In 30 Min.“
     * und – wenn erlaubt – Vollbild-Erinnerung über dem Sperrbildschirm.
     */
    fun showDoseReminder(day: LocalDate, minute: Int, items: List<Occurrence>, s: AppSettings) {
        if (!canPost() || items.isEmpty()) return
        val quiet = isQuietNow(s)
        val channel = reminderChannel(s, quiet)
        val count = items.size
        val units = items.sumOf { it.dose.amount }
        val title = when {
            count > 1 -> "Zeit für deine $count Medikamente"
            units > 1 -> "Zeit für $units ${items.first().med.form.plural}"
            else -> "Zeit für deine ${items.first().med.form.singular}"
        }
        val text = if (count == 1) {
            val o = items.first()
            val amount = listOf(o.med.form.amount(o.dose.amount), o.dose.label.takeIf { it.isNotBlank() })
                .filterNotNull().joinToString(" ")
            "${o.med.displayName} · $amount"
        } else items.joinToString(" · ") { it.med.displayName }

        val open = ReminderActivity.intent(context, day, minute)
        val contentPi = PendingIntent.getActivity(
            context, requestCode(day, minute), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val takePi = ActionReceiver.pending(context, ActionReceiver.ACTION_TAKE, day, minute)
        val snoozePi = ActionReceiver.pending(context, ActionReceiver.ACTION_SNOOZE_30, day, minute)

        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pilunia)
            .setColor(0xFF7A5CC7.toInt())
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.pilunia_winken_small))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentPi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .addAction(R.drawable.ic_check, "Eingenommen", takePi)
            .addAction(R.drawable.ic_alarm_clock, "In 30 Min.", snoozePi)
        if (s.fullScreenEnabled && !quiet && canUseFullScreen()) {
            b.setFullScreenIntent(contentPi, true)
        }
        nm.notify(TAG_DOSE, requestCode(day, minute), b.build())
    }

    fun cancelDoseReminder(day: LocalDate, minute: Int) = nm.cancel(TAG_DOSE, requestCode(day, minute))

    // ---------- Weitere Mitteilungen ----------

    fun showStockWarning(medId: Long, title: String, text: String) {
        if (!canPost()) return
        val ch = infoChannel(CH_STOCK, "Vorratswarnungen")
        val pi = PendingIntent.getActivity(
            context, (medId % 100000).toInt() + 5000, MainActivity.stockIntent(context, medId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(TAG_STOCK, medId.toInt(), base(ch, title, text).setContentIntent(pi).build())
    }

    fun showInfo(kind: String, id: Int, title: String, text: String) {
        if (!canPost()) return
        val ch = infoChannel(CH_INFO, "Zusammenfassungen und Hinweise")
        val pi = PendingIntent.getActivity(
            context, 9000 + id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(kind, id, base(ch, title, text).setContentIntent(pi).build())
    }

    private fun base(channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_pilunia)
            .setColor(0xFF7A5CC7.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    /** Ein Hinweis, wie die Mitteilung auf dem Sperrbildschirm aussieht (für „Jetzt testen“). */
    fun previewTime(minute: Int) = Fmt.time(minute)

    companion object {
        const val TAG_DOSE = "dose"
        const val TAG_STOCK = "stock"
        const val CH_QUIET = "erinnerungen_leise"
        const val CH_STOCK = "vorrat"
        const val CH_INFO = "info"

        fun requestCode(day: LocalDate, minute: Int): Int = ((day.toEpochDay() % 10000) * 1440 + minute).toInt()
    }
}
