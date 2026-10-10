package de.pilunia.app.ui.more

import android.content.Context
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.navigation.NavController
import com.composables.icons.lucide.ArchiveRestore
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.VolumeX
import de.pilunia.app.R
import de.pilunia.app.data.AppearanceMode
import de.pilunia.app.data.Backup
import de.pilunia.app.data.ReminderSound
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.toLocalDateTime
import de.pilunia.app.export.CsvExport
import de.pilunia.app.export.shareFile
import de.pilunia.app.reminder.Permissions
import de.pilunia.app.ui.components.rememberNativeTimePicker
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.ListRow
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.nav.SettingsPage
import de.pilunia.app.ui.nav.backOrMain
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsPageScreen(nav: NavController, page: SettingsPage) {
    when (page) {
        SettingsPage.QUIET_HOURS -> QuietHoursPage(nav)
        SettingsPage.SOUND -> SoundPage(nav)
        SettingsPage.BACKUP -> BackupPage(nav)
        SettingsPage.EXPORT -> ExportPage(nav)
        SettingsPage.APPEARANCE -> AppearancePage(nav)
        SettingsPage.ABOUT -> AboutPage(nav)
        SettingsPage.PROFILE -> ProfilePage(nav)
    }
}

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/** Uhrzeit rechts in einer Zeile (Outfit 18/600) + Chevron. */
@Composable
private fun TimeValue(minute: Int) {
    val c = Pilunia.colors
    Text(Fmt.time(minute), style = outfit(18, 600), color = c.tinte)
    LIcon(Lucide.ChevronRight, 22.dp, c.tinteGedaempft)
}

// =====================================================================
// Ruhezeiten
// =====================================================================

@Composable
private fun QuietHoursPage(nav: NavController) {
    val container = LocalContainer.current
    val s by rememberSettings()
    val pickTime = rememberNativeTimePicker()

    SettingsScaffold(nav, "Ruhezeiten") {
        SettingsGroup("Nachts") {
            SwitchRow("Ruhezeiten", "Nur leise Hinweise in dieser Zeit", s.quietEnabled, { v ->
                container.changeSettings { it.copy(quietEnabled = v) }
            })
            val mod = if (s.quietEnabled) Modifier else Modifier.alpha(0.45f)
            ListRow(
                title = "Beginn", subtitle = null, modifier = mod,
                trailing = { TimeValue(s.quietStartMin) },
                onClick = if (s.quietEnabled) ({ pickTime(s.quietStartMin) { m -> container.changeSettings { it.copy(quietStartMin = m) } } }) else null,
            )
            ListRow(
                title = "Ende", subtitle = null, modifier = mod, last = true,
                trailing = { TimeValue(s.quietEndMin) },
                onClick = if (s.quietEnabled) ({ pickTime(s.quietEndMin) { m -> container.changeSettings { it.copy(quietEndMin = m) } } }) else null,
            )
        }
        SettingsNote("Erinnerungen kommen dann ohne Ton und Vollbild.")
    }
}

// =====================================================================
// Ton & Vibration
// =====================================================================

@Composable
private fun SoundPage(nav: NavController) {
    val context = LocalContext.current
    val container = LocalContainer.current
    val s by rememberSettings()
    val preview = remember { SoundPreview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    SettingsScaffold(nav, "Ton & Vibration") {
        SettingsGroup("Erinnerungston") {
            val options = listOf(
                Triple(ReminderSound.GLOECKCHEN, "Sanfter Glockenklang von Pilunia", Lucide.BellRing),
                Triple(ReminderSound.SYSTEM, "Standardton deines Telefons", Lucide.Smartphone),
                Triple(ReminderSound.LEISE, "Nur Hinweis, ohne Ton", Lucide.VolumeX),
            )
            options.forEachIndexed { i, (sound, sub, icon) ->
                RadioListRow(
                    title = sound.label, subtitle = sub, selected = s.sound == sound, last = i == options.lastIndex,
                    leading = { MoreTile(icon) },
                    onClick = {
                        preview.play(sound)
                        if (s.sound != sound) container.changeSettings { it.copy(sound = sound) }
                    },
                )
            }
        }
        SettingsNote("Tippe auf einen Ton, um ihn anzuhören.")
        SettingsGroup("Vibration") {
            SwitchRow("Vibration", "Bei Erinnerungen vibrieren", s.vibration, { v ->
                container.changeSettings { it.copy(vibration = v) }
            }, last = true)
        }
        SettingsGroup("System") {
            ListRow(
                title = "Systemeinstellungen für Mitteilungen",
                subtitle = "Kanäle, Sperrbildschirm und Töne in Android",
                leading = { MoreTile(Lucide.Settings) },
                last = true,
                onClick = { runCatching { context.startActivity(Permissions.notificationSettingsIntent(context)) } },
            )
        }
    }
}

/** Vorhören der Erinnerungstöne. */
private class SoundPreview(private val context: Context) {
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    fun play(sound: ReminderSound) {
        stop()
        when (sound) {
            ReminderSound.GLOECKCHEN -> player = MediaPlayer.create(context, R.raw.gloeckchen)?.apply {
                setOnCompletionListener { it.release(); if (player === it) player = null }
                start()
            }
            ReminderSound.SYSTEM -> ringtone = RingtoneManager.getRingtone(
                context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )?.apply { play() }
            ReminderSound.LEISE -> Unit
        }
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        ringtone?.runCatching { stop() }
        ringtone = null
    }
}

// =====================================================================
// Backup & Wiederherstellen
// =====================================================================

@Composable
private fun BackupPage(nav: NavController) {
    val context = LocalContext.current
    val s by rememberSettings()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            Backup.write(context, uri)
                .onSuccess { context.toast("Sicherung gespeichert.") }
                .onFailure { context.toast("Die Sicherung konnte nicht gespeichert werden.") }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreUri = uri }

    SettingsScaffold(nav, "Backup & Wiederherstellen") {
        SettingsGroup("Letzte Sicherung") {
            val last = if (s.lastBackupAt <= 0) "Noch keine Sicherung" else {
                val t = s.lastBackupAt.toLocalDateTime()
                "${Fmt.relativeDay(t.toLocalDate()).replaceFirstChar { it.uppercase() }}, ${Fmt.time(t.toLocalTime())}"
            }
            ListRow(
                title = last, subtitle = "Medikamente, Verlauf und Einstellungen",
                leading = { MoreTile(Lucide.Clock) }, trailing = null, last = true,
            )
        }
        SettingsGroup("Sichern") {
            ListRow(
                title = "Sicherung erstellen",
                subtitle = "Als Datei speichern, z. B. in Google Drive",
                leading = { MoreTile(Lucide.CloudUpload) },
                onClick = { if (!busy) create.launch(Backup.suggestedName()) },
            )
            ListRow(
                title = "Wiederherstellen",
                subtitle = "Aus einer Sicherungsdatei laden",
                leading = { MoreTile(Lucide.ArchiveRestore) },
                last = true,
                onClick = { if (!busy) open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            )
        }
        SettingsNote("Pilunia speichert alles nur auf diesem Gerät. Lege regelmäßig eine Sicherung an, damit beim Handywechsel nichts verloren geht.")
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "Sicherung wiederherstellen?",
            text = "Alle Medikamente, der Verlauf und die Einstellungen auf diesem Gerät werden durch die Sicherung ersetzt.",
            confirm = "Wiederherstellen",
            destructive = true,
            onDismiss = { restoreUri = null },
            onConfirm = {
                restoreUri = null
                busy = true
                scope.launch {
                    Backup.restore(context, uri)
                        .onSuccess { n -> context.toast(if (n == 1) "1 Medikament wiederhergestellt." else "$n Medikamente wiederhergestellt.") }
                        .onFailure { context.toast("Die Datei ist keine gültige Pilunia-Sicherung.") }
                    busy = false
                }
            },
        )
    }
}

// =====================================================================
// Daten exportieren
// =====================================================================

@Composable
private fun ExportPage(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExport.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            CsvExport.write(context, uri)
                .onSuccess { n -> context.toast(if (n == 1) "1 Eintrag exportiert." else "$n Einträge exportiert.") }
                .onFailure { context.toast("Die Datei konnte nicht gespeichert werden.") }
            busy = false
        }
    }

    SettingsScaffold(nav, "Daten exportieren") {
        SettingsGroup("Verlauf als CSV-Datei") {
            ListRow(
                title = "Als Datei speichern",
                subtitle = "z. B. in Downloads oder Google Drive",
                leading = { MoreTile(Lucide.Download) },
                onClick = { if (!busy) save.launch(CsvExport.suggestedName()) },
            )
            ListRow(
                title = "Teilen",
                subtitle = "Per E-Mail, Messenger oder an eine andere App",
                leading = { MoreTile(Lucide.Share2) },
                last = true,
                onClick = {
                    if (busy) return@ListRow
                    busy = true
                    scope.launch {
                        CsvExport.cacheFile(context)
                            .onSuccess { f ->
                                runCatching { shareFile(context, f, CsvExport.MIME, "Pilunia-Verlauf teilen") }
                                    .onFailure { context.toast("Keine App zum Teilen gefunden.") }
                            }
                            .onFailure { context.toast("Der Export ist fehlgeschlagen.") }
                        busy = false
                    }
                },
            )
        }
        SettingsNote(
            "Die Datei enthält den gesamten Verlauf: Datum, geplante Zeit, Medikament, Stärke, Menge, Status und " +
                "Uhrzeit der Einnahme. Sie lässt sich mit Excel, Numbers oder LibreOffice öffnen."
        )
    }
}

// =====================================================================
// Erscheinungsbild
// =====================================================================

@Composable
private fun AppearancePage(nav: NavController) {
    val container = LocalContainer.current
    val s by rememberSettings()
    SettingsScaffold(nav, "Erscheinungsbild") {
        SettingsGroup("Farben") {
            val modes = listOf(
                AppearanceMode.SYSTEM to Lucide.Smartphone,
                AppearanceMode.LIGHT to Lucide.Sun,
                AppearanceMode.DARK to Lucide.Moon,
            )
            modes.forEachIndexed { i, (mode, icon) ->
                RadioListRow(
                    title = mode.label,
                    subtitle = when (mode) {
                        AppearanceMode.SYSTEM -> "Folgt der Einstellung deines Telefons"
                        AppearanceMode.LIGHT -> "Immer helle Farben"
                        AppearanceMode.DARK -> "Immer dunkle Farben"
                    },
                    selected = s.appearance == mode,
                    last = i == modes.lastIndex,
                    leading = { MoreTile(icon) },
                    onClick = { container.changeSettings { it.copy(appearance = mode) } },
                )
            }
        }
        SettingsGroup("Schrift") {
            SwitchRow("Schrift groß", "Alle Texte in der App etwas größer", s.largeText, { v ->
                container.changeSettings { it.copy(largeText = v) }
            }, last = true)
        }
    }
}

// =====================================================================
// Über Pilunia
// =====================================================================

private const val LUCIDE_LICENSE = """ISC License

Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT). All other copyright (c) for Lucide are held by Lucide Contributors 2022.

Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE."""

private data class License(val name: String, val license: String, val asset: String?)

@Composable
private fun AboutPage(nav: NavController) {
    val c = Pilunia.colors
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
        }.getOrDefault("")
    }
    var showLicense by remember { mutableStateOf<License?>(null) }
    val licenses = listOf(
        License("Outfit", "SIL Open Font License 1.1", "licenses/OFL_Outfit.txt"),
        License("Atkinson Hyperlegible Next", "SIL Open Font License 1.1", "licenses/OFL_AtkinsonHyperlegibleNext.txt"),
        License("Lucide Icons", "ISC-Lizenz", null),
    )

    SettingsScaffold(nav, "Über Pilunia") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // App-Symbol (docs/appicon.png) mit Radius wie im Designsystem (26 auf 88).
            PiluniaImage(
                R.drawable.pilunia_appicon, "App-Symbol Pilunia",
                Modifier.size(112.dp).softShadow(CardShadow, 33.dp).clip(RoundedCornerShape(33.dp)),
            )
            Spacer(Modifier.height(12.dp))
            Text("Pilunia", style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte)
            Spacer(Modifier.height(4.dp))
            Text("Deine freundliche Tabletten-Erinnerung", style = atkinson(15), color = c.tinteGedaempft, textAlign = TextAlign.Center)
            if (version.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(version, style = atkinson(14), color = c.tinteGedaempft)
            }
        }

        Row(
            Modifier.fillMaxWidth().background(c.lavendelToenung, RoundedCornerShape(24.dp)).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(c.flaeche, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                LIcon(Lucide.Info, 22.dp, c.pflaume)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pilunia ersetzt keine ärztliche Beratung.", style = atkinson(16, 700), color = c.tinte)
                Text(
                    "Sprich Änderungen an deiner Einnahme immer mit deiner Ärztin, deinem Arzt oder deiner Apotheke ab.",
                    style = atkinson(14, lineHeight = 1.35f), color = c.tinteGedaempft,
                )
            }
        }

        SettingsGroup("Hilfe") {
            ListRow(
                title = "Erinnerungen kommen nicht?", subtitle = "Freigaben und Benachrichtigungen prüfen",
                onClick = { nav.navigate(Routes.NOTIFICATIONS) },
            )
            ListRow(
                title = "Handy wechseln", subtitle = "Sicherung erstellen und wiederherstellen", last = true,
                onClick = { nav.navigate(Routes.settings(SettingsPage.BACKUP)) },
            )
        }

        SettingsGroup("Datenschutz", padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            Text(
                "Alle Daten bleiben auf deinem Gerät. Pilunia braucht kein Konto, zeigt keine Werbung und sendet nichts " +
                    "ins Internet. Sicherungen und Exporte entstehen nur, wenn du sie selbst anlegst.",
                style = atkinson(15, lineHeight = 1.4f), color = c.tinte,
            )
        }

        SettingsGroup("Lizenzen") {
            licenses.forEachIndexed { i, l ->
                ListRow(title = l.name, subtitle = l.license, last = i == licenses.lastIndex, onClick = { showLicense = l })
            }
        }
    }

    showLicense?.let { LicenseSheet(it) { showLicense = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseSheet(l: License, onDismiss: () -> Unit) {
    val c = Pilunia.colors
    val context = LocalContext.current
    var text by remember(l) { mutableStateOf(if (l.asset == null) LUCIDE_LICENSE else "") }
    LaunchedEffect(l) {
        if (l.asset != null) text = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(l.asset).bufferedReader(Charsets.UTF_8).use { it.readText() } }
                .getOrDefault("Lizenztext nicht gefunden.")
        }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    PSheet(onDismiss, state) {
        SheetTitle(l.name, l.license)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .background(c.lavendelToenung, RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text.trim(), style = atkinson(13, lineHeight = 1.45f), color = c.tinte)
        }
    }
}

// =====================================================================
// Dein Name
// =====================================================================

@Composable
private fun ProfilePage(nav: NavController) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    var loaded by rememberSaveable { mutableStateOf(false) }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    // Den gespeicherten Namen direkt laden (rememberSettings startet mit leeren Standardwerten).
    LaunchedEffect(Unit) {
        if (!loaded) {
            val n = container.settings.current().userName
            field = TextFieldValue(n, TextRange(n.length))
            loaded = true
        }
    }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun save() {
        val v = field.text.trim()
        container.changeSettings { it.copy(userName = v) }
        nav.backOrMain()
    }

    SettingsScaffold(nav, "Dein Name", bottomBar = {
        PrimaryButton("Speichern", onClick = ::save)
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vorname", style = atkinson(14, 700), color = c.tinte)
            val shape = RoundedCornerShape(16.dp)
            BasicTextField(
                value = field,
                onValueChange = { if (it.text.length <= 30) field = it },
                singleLine = true,
                textStyle = atkinson(17).copy(color = c.tinte),
                cursorBrush = SolidColor(c.primaer),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { inner ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(c.flaeche, shape)
                            .border(if (focused) 2.dp else 1.dp, if (focused) c.primaer else c.feldrand, shape)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (field.text.isEmpty()) Text("z. B. Anna", style = atkinson(17), color = c.tinteGedaempft)
                            inner()
                        }
                    }
                },
            )
        }
        SettingsNote("So begrüßt dich Pilunia auf dem Startbildschirm. Du kannst das Feld auch leer lassen.")
    }
}
