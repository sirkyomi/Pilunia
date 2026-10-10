package de.pilunia.app.ui.more

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldAlert
import com.composables.icons.lucide.ShieldCheck
import de.pilunia.app.AppContainer
import de.pilunia.app.data.AppSettings
import de.pilunia.app.reminder.Permissions
import de.pilunia.app.ui.components.ChoiceChip
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.ListRow
import de.pilunia.app.ui.components.PSwitch
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.nav.SettingsPage
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import kotlinx.coroutines.launch

/** Ändert eine Einstellung und plant die Erinnerungen neu. */
fun AppContainer.changeSettings(transform: (AppSettings) -> AppSettings) {
    scope.launch {
        settings.update(transform)
        reschedule()
    }
}

/** Die fehlende Freigabe mit Text für die Warnkarte. */
private enum class Missing(val title: String, val text: String) {
    NOTIFICATIONS("Mitteilungen sind aus", "Ohne Mitteilungen kann Pilunia nicht erinnern. Tippe hier, um sie zu erlauben."),
    EXACT("Exakte Wecker fehlen", "Damit Erinnerungen pünktlich kommen, braucht Pilunia „Wecker & Erinnerungen“. Tippe hier."),
    BATTERY("Akku-Ausnahme fehlt", "Android könnte Erinnerungen sonst verzögern. Tippe hier, um Pilunia auszunehmen."),
}

private fun missing(context: Context): Missing? = when {
    !Permissions.notifications(context) -> Missing.NOTIFICATIONS
    !Permissions.exactAlarms(context) -> Missing.EXACT
    !Permissions.batteryUnrestricted(context) -> Missing.BATTERY
    else -> null
}

private fun Context.tryStart(intent: Intent?): Boolean =
    intent != null && runCatching { startActivity(intent) }.isSuccess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(nav: NavController) {
    val c = Pilunia.colors
    val context = LocalContext.current
    val container = LocalContainer.current
    val s by rememberSettings()
    var check by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { check++ }
    val miss = remember(check) { missing(context) }
    val fullScreenOk = remember(check) { Permissions.fullScreen(context) }
    var showRepeat by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        check++
        if (!granted) context.tryStart(Permissions.notificationSettingsIntent(context))
    }

    fun fix(m: Missing) {
        when (m) {
            Missing.NOTIFICATIONS -> {
                val perm = Permissions.notificationPermission
                val needsRuntime = perm != null && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(perm) != android.content.pm.PackageManager.PERMISSION_GRANTED
                if (needsRuntime) notifLauncher.launch(perm!!)
                else context.tryStart(Permissions.notificationSettingsIntent(context))
            }
            Missing.EXACT -> context.tryStart(Permissions.exactAlarmIntent(context))
            Missing.BATTERY -> if (!context.tryStart(Permissions.batteryIntent(context))) {
                context.tryStart(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    SettingsScaffold(nav, "Benachrichtigungen") {
        if (miss == null) {
            StatusCard(
                ok = true, title = "Alles bereit",
                text = "Mitteilungen, exakte Wecker und Akku-Ausnahme sind erlaubt.",
            )
        } else {
            StatusCard(ok = false, title = miss.title, text = miss.text, onClick = { fix(miss) })
        }

        SettingsGroup("Einnahme") {
            SwitchRow("Einnahme-Erinnerungen", "Zur geplanten Uhrzeit", s.remindersEnabled, { v ->
                container.changeSettings { it.copy(remindersEnabled = v) }
            })
            val fsMissing = s.fullScreenEnabled && !fullScreenOk
            ListRow(
                title = "Vollbild-Erinnerung",
                subtitle = if (fsMissing) "Android erlaubt kein Vollbild – tippe zum Erlauben" else "Auch auf dem Sperrbildschirm",
                subtitleColor = if (fsMissing) c.warnung else c.tinteGedaempft,
                trailing = {
                    PSwitch(s.fullScreenEnabled, { v ->
                        container.changeSettings { it.copy(fullScreenEnabled = v) }
                        if (v && !fullScreenOk) context.tryStart(Permissions.fullScreenIntent(context))
                    })
                },
                onClick = {
                    if (fsMissing) context.tryStart(Permissions.fullScreenIntent(context))
                    else {
                        val v = !s.fullScreenEnabled
                        container.changeSettings { it.copy(fullScreenEnabled = v) }
                        if (v && !fullScreenOk) context.tryStart(Permissions.fullScreenIntent(context))
                    }
                },
            )
            ListRow(
                title = "Erneut erinnern",
                subtitle = "Alle ${s.repeatIntervalMin} Min., höchstens ${s.repeatMaxCount}-mal",
                onClick = { showRepeat = true },
            )
            ListRow(
                title = "Ton & Vibration",
                subtitle = "${s.sound.label} · Vibration ${if (s.vibration) "an" else "aus"}",
                last = true,
                onClick = { nav.navigate(Routes.settings(SettingsPage.SOUND)) },
            )
        }

        SettingsGroup("Vorrat") {
            SwitchRow("Vorratswarnung", "Wenn es nur noch 7 Tage reicht", s.stockWarningEnabled, { v ->
                container.changeSettings { it.copy(stockWarningEnabled = v) }
            }, last = true)
        }

        SettingsGroup("Zusammenfassungen") {
            SwitchRow("Tagesabschluss", "Täglich nach der letzten Einnahme", s.dailySummary, { v ->
                container.changeSettings { it.copy(dailySummary = v) }
            })
            SwitchRow("Wochenbericht", "Sonntags um 18:00", s.weeklyReport, { v ->
                container.changeSettings { it.copy(weeklyReport = v) }
            }, last = true)
        }
    }

    if (showRepeat) RepeatSheet(s, onDismiss = { showRepeat = false }) { interval, count ->
        container.changeSettings { it.copy(repeatIntervalMin = interval, repeatMaxCount = count) }
    }
}

/** Statuskarte: „Alles bereit“ (Mint) oder Warnung (Rosé) im gleichen Aufbau. */
@Composable
private fun StatusCard(ok: Boolean, title: String, text: String, onClick: (() -> Unit)? = null) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pClickable(shape, onClick = onClick) else Modifier)
            .background(if (ok) c.mintFlaeche else c.warnungFlaeche, shape)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(c.flaeche, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            LIcon(if (ok) Lucide.ShieldCheck else Lucide.ShieldAlert, 22.dp, if (ok) c.erfolg else c.warnung)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            Text(text, style = atkinson(14, lineHeight = 1.35f), color = if (ok) c.erfolg else c.warnung)
        }
    }
}

/** Sheet „Erneut erinnern“: Abstand 5/10/15/30 Min., höchstens 1–5-mal. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RepeatSheet(s: AppSettings, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val close = rememberSheetCloser(state, onDismiss)
    var interval by remember { mutableIntStateOf(s.repeatIntervalMin) }
    var count by remember { mutableIntStateOf(s.repeatMaxCount.coerceIn(1, 5)) }
    PSheet(onDismiss, state) {
        SheetTitle("Erneut erinnern", "Wenn du eine Erinnerung nicht bestätigst, meldet sich Pilunia noch einmal.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Abstand", style = atkinson(14, 700), color = Pilunia.colors.tinte)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30).forEach { m ->
                    ChoiceChip("$m Min.", interval == m, onClick = { interval = m })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Höchstens", style = atkinson(14, 700), color = Pilunia.colors.tinte)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    ChoiceChip("$n×", count == n, onClick = { count = n })
                }
            }
        }
        PrimaryButton("Speichern", onClick = { close { onSave(interval, count) } })
    }
}
