package de.pilunia.app.ui.more

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.FileDown
import com.composables.icons.lucide.FileSpreadsheet
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Palette
import com.composables.icons.lucide.UserRound
import com.composables.icons.lucide.Volume2
import de.pilunia.app.R
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.toLocalDateTime
import de.pilunia.app.export.PdfReport
import de.pilunia.app.export.shareFile
import de.pilunia.app.reminder.Permissions
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.ListRow
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberSettings
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.nav.SettingsPage
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch

/** Linker Rand der Mehr-Karten 16, rechts 12 (Chevron sitzt näher am Rand). */
private val MoreCardPadding = PaddingValues(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)

/** Akzent-Text auf dem weißen PDF-Button (im Design fest, auch im Dunkelmodus). */
private val PdfButtonText = Color(0xFF5B3FA8)

@Composable
fun MehrScreen(nav: NavController, contentPadding: PaddingValues) {
    val c = Pilunia.colors
    val context = LocalContext.current
    val settings by rememberSettings()
    val scope = rememberCoroutineScope()
    var permissionsOk by remember { mutableStateOf(Permissions.allGranted(context)) }
    var creatingPdf by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionsOk = Permissions.allGranted(context) }

    fun createPdf() {
        if (creatingPdf) return
        creatingPdf = true
        scope.launch {
            PdfReport.create(context)
                .onSuccess { file ->
                    runCatching { shareFile(context, file, PdfReport.MIME, "Pilunia-Bericht teilen") }
                        .onFailure { Toast.makeText(context, "Keine App zum Teilen gefunden.", Toast.LENGTH_LONG).show() }
                }
                .onFailure { Toast.makeText(context, "Das PDF konnte nicht erstellt werden.", Toast.LENGTH_LONG).show() }
            creatingPdf = false
        }
    }

    Box(Modifier.fillMaxSize().screenBackground(c)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarSpace()
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp + contentPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Mehr", style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte)

                DoctorCard(creatingPdf, onCreate = ::createPdf)

                MoreGroup("Erinnerungen") {
                    val notifSubtitle = when {
                        !settings.remindersEnabled -> "Erinnerungen sind aus"
                        !permissionsOk -> "Freigabe fehlt – bitte prüfen"
                        else -> "Alle Erinnerungen aktiv"
                    }
                    MoreRow(
                        Lucide.Bell, "Benachrichtigungen", notifSubtitle,
                        subtitleColor = if (settings.remindersEnabled && !permissionsOk) c.warnung else c.tinteGedaempft,
                    ) { nav.navigate(Routes.NOTIFICATIONS) }
                    MoreRow(
                        Lucide.Moon, "Ruhezeiten",
                        if (settings.quietEnabled) "${Fmt.time(settings.quietStartMin)} – ${Fmt.time(settings.quietEndMin)}, nur leise Hinweise" else "Aus",
                    ) { nav.navigate(Routes.settings(SettingsPage.QUIET_HOURS)) }
                    MoreRow(Lucide.Volume2, "Erinnerungston", settings.sound.label, last = true) {
                        nav.navigate(Routes.settings(SettingsPage.SOUND))
                    }
                }

                MoreGroup("Daten") {
                    val backup = if (settings.lastBackupAt <= 0) "Noch keine Sicherung" else {
                        val t = settings.lastBackupAt.toLocalDateTime()
                        "Zuletzt gesichert ${Fmt.relativeDay(t.toLocalDate())}, ${Fmt.time(t.toLocalTime())}"
                    }
                    MoreRow(Lucide.CloudUpload, "Backup & Wiederherstellen", backup) {
                        nav.navigate(Routes.settings(SettingsPage.BACKUP))
                    }
                    MoreRow(Lucide.FileSpreadsheet, "Daten exportieren", "Verlauf als CSV-Datei", last = true) {
                        nav.navigate(Routes.settings(SettingsPage.EXPORT))
                    }
                }

                MoreGroup("App") {
                    MoreRow(Lucide.UserRound, "Dein Name", settings.userName.trim().ifEmpty { "Nicht festgelegt" }) {
                        nav.navigate(Routes.settings(SettingsPage.PROFILE))
                    }
                    MoreRow(
                        Lucide.Palette, "Erscheinungsbild",
                        settings.appearance.label + if (settings.largeText) " · Schrift groß" else "",
                    ) { nav.navigate(Routes.settings(SettingsPage.APPEARANCE)) }
                    MoreRow(
                        Lucide.Heart, "Über Pilunia", "Hilfe, Datenschutz, Lizenzen",
                        tileColor = c.warnungFlaeche, iconColor = c.warnungKraeftig, last = true,
                    ) { nav.navigate(Routes.settings(SettingsPage.ABOUT)) }
                }
            }
        }
    }
}

/** Karte „Für deinen Arzttermin“: Verlauf Primär → #6A4FB5, Radius 28, Figur rechts. */
@Composable
private fun DoctorCard(busy: Boolean, onCreate: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 156.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Pilunia.colors.primaer, FixedColors.HeroVerlaufEnde)))
    ) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 14.dp).size(120.dp)) {
            Box(
                Modifier
                    .offset(y = 4.dp)
                    .size(116.dp)
                    .background(FixedColors.Weiss.copy(alpha = 0.12f), CircleShape)
            )
            PiluniaImage(R.drawable.pilunia_daumen, "Pilunia zeigt Daumen hoch", Modifier.size(120.dp))
        }
        Column(Modifier.padding(20.dp)) {
            Text("Für deinen Arzttermin", style = outfit(19, 600), color = FixedColors.Weiss, modifier = Modifier.widthIn(max = 220.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "Plan und Verlauf als PDF teilen", style = atkinson(15, lineHeight = 1.35f), color = FixedColors.Weiss,
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .height(44.dp)
                    .pClickable(RoundedCornerShape(22.dp), enabled = !busy, onClick = onCreate)
                    .background(FixedColors.Weiss)
                    .padding(start = 14.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = PdfButtonText, strokeWidth = 2.dp)
                else LIcon(Lucide.FileDown, 18.dp, PdfButtonText)
                Text(if (busy) "Wird erstellt …" else "PDF erstellen", style = atkinson(15, 700), color = PdfButtonText, maxLines = 1)
            }
        }
    }
}

@Composable
private fun MoreGroup(label: String, content: @Composable () -> Unit) {
    SettingsGroup(label, padding = MoreCardPadding) { content() }
}

/** Zeile wie in „20 Mehr“: Kachel 40 (Radius 14), Abstand 14, oben/unten 12, Chevron 22. */
@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    last: Boolean = false,
    tileColor: Color = Pilunia.colors.lavendelToenung,
    iconColor: Color = Pilunia.colors.pflaume,
    subtitleColor: Color = Pilunia.colors.tinteGedaempft,
    onClick: () -> Unit,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { MoreTile(icon, tileColor, iconColor) },
        last = last,
        verticalPadding = 12.dp,
        gap = 14.dp,
        subtitleColor = subtitleColor,
        onClick = onClick,
    )
}
