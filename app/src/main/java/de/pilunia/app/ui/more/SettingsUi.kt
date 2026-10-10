package de.pilunia.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import de.pilunia.app.domain.Fmt
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.ListRow
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PSwitch
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.TopBar
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.nav.backOrMain
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch

// Bausteine der Einstellungsseiten im Stil von „21 Benachrichtigungen“.

/** Seitenrahmen: Hintergrund, Kopfzeile mit Zurück + Titel (Outfit 20/600), scrollbarer Inhalt (oben 8, seitlich 20, Abstand 16). */
@Composable
fun SettingsScaffold(
    nav: NavController,
    title: String,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxSize().background(c.hintergrund).imePadding()) {
        StatusBarSpace()
        TopBar(title = title, onNav = { nav.backOrMain() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (bottomBar != null) {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), content = bottomBar)
        }
        NavBarSpace()
    }
}

/** Gruppe: Beschriftung (Atkinson 14/700) + weiße Karte, Abstand 8. */
@Composable
fun SettingsGroup(
    label: String?,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(start = 18.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label != null) Text(label, style = atkinson(14, 700), color = Pilunia.colors.tinteGedaempft)
        PCard(Modifier.fillMaxWidth(), padding = padding, content = content)
    }
}

/** Zeile mit Schalter rechts; Tippen auf die Zeile schaltet ebenfalls. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    last: Boolean = false,
    subtitleColor: Color = Pilunia.colors.tinteGedaempft,
    leading: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title, subtitle = subtitle, last = last, subtitleColor = subtitleColor, leading = leading,
        trailing = { PSwitch(checked, onChange) },
        onClick = { onChange(!checked) },
    )
}

/** Radio-Kreis 24: aus = 2er Rand „Feldrand“, an = Rand + Punkt 12 „Auswahl“. */
@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    Box(
        modifier.size(24.dp).border(2.dp, if (selected) c.auswahl else c.feldrand, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(12.dp).background(c.auswahl, CircleShape))
    }
}

/** Zeile mit Radio rechts (für Auswahl in Karten). */
@Composable
fun RadioListRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    ListRow(
        title = title, subtitle = subtitle, last = last, leading = leading,
        trailing = { RadioDot(selected) },
        onClick = onClick,
    )
}

/** Option im Sheet wie „Später erinnern“: 56 hoch, Radius 16, gewählt „Lavendel Tönung“. */
@Composable
fun SheetOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    value: String? = null,
) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pClickable(RoundedCornerShape(16.dp), onClick = onClick)
            .background(if (selected) c.lavendelToenung else Color.Transparent)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 22.dp, c.pflaume)
        Text(label, style = atkinson(16, 700), color = c.tinte, modifier = Modifier.weight(1f), maxLines = 1)
        if (value != null) Text(value, style = outfit(15, 500), color = c.tinteGedaempft)
        RadioDot(selected)
    }
}

/** Unteres Sheet im App-Stil: „Fläche“, oben Radius 28, Griff 36 × 4 „Feldrand“. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PSheet(
    onDismiss: () -> Unit,
    state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Pilunia.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = c.flaeche,
        contentColor = c.tinte,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp).size(36.dp, 4.dp).background(c.feldrand, RoundedCornerShape(2.dp)))
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** Titel + Info eines Sheets (Outfit 22/600, Atkinson 14). */
@Composable
fun SheetTitle(title: String, info: String? = null) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = outfit(22, 600), color = c.tinte)
        if (info != null) Text(info, style = atkinson(14, lineHeight = 1.35f), color = c.tinteGedaempft)
    }
}

/** Schließt ein Sheet mit Animation und ruft danach [then] auf. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetCloser(state: SheetState, then: () -> Unit): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { action ->
        scope.launch { state.hide() }.invokeOnCompletion { action(); then() }
    }
}

/** Bestätigungsdialog im App-Stil: weiße Karte Radius 28, Titel Outfit 22/600, Primär- + Text-Button. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String = "Abbrechen",
    destructive: Boolean = false,
) {
    val c = Pilunia.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), radius = 28.dp, padding = PaddingValues(24.dp)) {
            Text(title, style = outfit(22, 600), color = c.tinte)
            Spacer(Modifier.height(8.dp))
            Text(text, style = atkinson(16, lineHeight = 1.4f), color = c.tinteGedaempft)
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                confirm, onConfirm,
                container = if (destructive) c.warnungKraeftig else c.primaer,
            )
            Spacer(Modifier.height(4.dp))
            TextButtonP(dismiss, onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** Kleine Info-Zeile unter Karten (Atkinson 14, „Tinte gedämpft“). */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier, color: Color = Pilunia.colors.tinteGedaempft) {
    Text(text, style = atkinson(14, lineHeight = 1.4f), color = color, modifier = modifier.padding(horizontal = 4.dp))
}

/** Symbolkachel wie in „20 Mehr“: 40 × 40, Radius 14, Symbol 20. */
@Composable
fun MoreTile(icon: ImageVector, background: Color = Pilunia.colors.lavendelToenung, tint: Color = Pilunia.colors.pflaume, size: Dp = 40.dp) {
    Box(Modifier.size(size).background(background, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        LIcon(icon, 20.dp, tint)
    }
}
