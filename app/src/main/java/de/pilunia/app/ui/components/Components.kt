package de.pilunia.app.ui.components

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.SprayCan
import com.composables.icons.lucide.Syringe
import com.composables.icons.lucide.Tablets
import com.composables.icons.lucide.X
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.PiluniaColors
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit

// =====================================================================
// Schatten und Flächen
// =====================================================================

/** Ein Schattenlayer wie in der Leinwand (drop-shadow mit y-Versatz, Weichzeichnung, Farbe mit Alpha). */
data class ShadowLayer(val y: Dp, val blur: Dp, val color: Color, val x: Dp = 0.dp)

/** Zweistufiger weicher Schatten in Pflaume (8 % / 12 %) – Standard für Karten. */
val CardShadow = listOf(
    ShadowLayer(1.dp, 2.dp, FixedColors.Schatten.copy(alpha = 0x14 / 255f)),
    ShadowLayer(8.dp, 24.dp, FixedColors.Schatten.copy(alpha = 0x1F / 255f)),
)

/**
 * Zeichnet weiche Schatten hinter ein abgerundetes Rechteck (ab Android 9; darunter ohne Schatten).
 * Im Dunkelmodus werden die Schatten abgeschwächt, wie es das Design vorsieht (Flächen tragen sich selbst).
 */
fun Modifier.softShadow(layers: List<ShadowLayer>, cornerRadius: Dp): Modifier =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) this else drawBehind {
        drawIntoCanvas { canvas ->
            for (l in layers) {
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.TRANSPARENT
                    setShadowLayer(l.blur.toPx() / 2f, l.x.toPx(), l.y.toPx(), l.color.toArgb())
                }
                val r = cornerRadius.toPx()
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            }
        }
    }

/** Weiße Karte (Variable „Fläche“) mit Radius 24 und zweistufigem Schatten. */
@Composable
fun PCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    color: Color = Pilunia.colors.flaeche,
    shadow: List<ShadowLayer> = CardShadow,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .softShadow(shadow, radius)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

/**
 * Bildschirmhintergrund: „Hintergrund“ plus Lavendel-Verlauf von oben
 * (#A78BFA mit 18 % Deckkraft, ausblendend bis [fadeEnd] der Höhe).
 */
fun Modifier.screenBackground(colors: PiluniaColors, fadeEnd: Float = 0.35f, tint: Color = colors.lavendel, alpha: Float = 0x2E / 255f): Modifier =
    background(colors.hintergrund).drawBehind {
        drawRect(
            Brush.verticalGradient(
                0f to tint.copy(alpha = alpha),
                fadeEnd to tint.copy(alpha = 0f),
                startY = 0f, endY = size.height,
            )
        )
    }

/** Klickbar mit Ripple in der Form [shape]. */
fun Modifier.pClickable(shape: Shape = RoundedCornerShape(0.dp), enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier =
    clip(shape).clickable(enabled = enabled, role = role, onClick = onClick)

// =====================================================================
// Buttons
// =====================================================================

/** Primär-Button: Pill 56 hoch, „Primär“, Text Atkinson 17/700 weiß, Symbol 20 (Strich 2,5). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    container: Color = Pilunia.colors.primaer,
    content: Color = FixedColors.Weiss,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .pClickable(RoundedCornerShape(height / 2), enabled, onClick = onClick)
            .background(container.copy(alpha = container.alpha * alpha)),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 20.dp, content.copy(alpha = alpha), strokeWidth = 2.5f)
        Text(text, style = atkinson(17, 700), color = content.copy(alpha = alpha), maxLines = 1)
    }
}

/** Sekundär-Button: Pill 56 hoch, „Lavendel Tönung kräftig“, Text/Symbol „Akzent Text“. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp,
) {
    val c = Pilunia.colors
    PrimaryButton(
        text, onClick, modifier, icon, enabled, height,
        container = c.lavendelToenungKraeftig, content = c.akzentText,
    )
}

/** Text-Button: 48 hoch, Atkinson 16/700 (Standard „Tinte gedämpft“). */
@Composable
fun TextButtonP(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Pilunia.colors.tinteGedaempft,
    style: TextStyle = atkinson(16, 700),
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .height(48.dp)
            .pClickable(RoundedCornerShape(24.dp), onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 18.dp, color, strokeWidth = 2.25f)
        Text(text, style = style, color = color, maxLines = 1)
    }
}

/** Runder Symbol-Button 48 × 48 (Zurück, Schließen, Bearbeiten). */
@Composable
fun IconButtonP(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Pilunia.colors.tinte,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    background: Color = Color.Transparent,
    strokeWidth: Float = 2f,
) {
    Box(
        modifier
            .size(size)
            .pClickable(CircleShape, onClick = onClick)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, iconSize, tint, strokeWidth = strokeWidth, contentDescription = contentDescription)
    }
}

// =====================================================================
// Schalter, Chips, Eingaben
// =====================================================================

/**
 * Schalter wie im Design: 52 × 32, an = „Primär“ mit weißem Knopf 24 rechts,
 * aus = „Lavendel Tönung“ mit 2er Rand „Tinte gedämpft“ und Knopf 16 links.
 */
@Composable
fun PSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Pilunia.colors
    val track by animateColorAsState(if (checked) c.primaer else c.lavendelToenung, label = "track")
    val knobSize by animateDpAsState(if (checked) 24.dp else 16.dp, label = "knob")
    val knobX by animateDpAsState(if (checked) 24.dp else 8.dp, label = "knobX")
    val knob by animateColorAsState(if (checked) FixedColors.Weiss else c.tinteGedaempft, label = "knobColor")
    Box(
        modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 28.dp),
                enabled = enabled, role = Role.Switch,
            ) { onCheckedChange(!checked) }
            .background(track)
            .then(if (!checked) Modifier.border(2.dp, c.tinteGedaempft, RoundedCornerShape(16.dp)) else Modifier),
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = (32.dp - knobSize) / 2)
                .size(knobSize)
                .background(knob, CircleShape)
        )
    }
}


/**
 * Auswahl-Chip, 40 hoch, Radius 20. Gewählt: „Primär“ mit Häkchen, Text weiß;
 * sonst „Fläche“ mit 1er Rand „Feldrand“, Text „Tinte“. Text Atkinson 15/700.
 */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    showCheck: Boolean = true,
    textStyle: TextStyle = atkinson(15, 700),
) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(height / 2)
    Row(
        modifier
            .height(height)
            .pClickable(shape, onClick = onClick)
            .background(if (selected) c.primaer else c.flaeche)
            .then(if (selected) Modifier else Modifier.border(1.dp, c.feldrand, shape))
            .padding(start = if (selected && showCheck) 12.dp else 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected && showCheck) LIcon(Lucide.Check, 18.dp, FixedColors.Weiss, strokeWidth = 2.5f)
        Text(text, style = textStyle, color = if (selected) FixedColors.Weiss else c.tinte, maxLines = 1)
    }
}

/** Kleiner Status-Chip (z. B. „6 übrig“): 28 hoch, Radius 14, Atkinson 13/700. */
@Composable
fun SmallChip(
    text: String,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 28.dp,
    textStyle: TextStyle = atkinson(13, 700),
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(background)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) LIcon(icon, 14.dp, content, strokeWidth = 2.25f)
        Text(text, style = textStyle, color = content, maxLines = 1)
    }
}

/** Abschnittsbeschriftung über Karten (Atkinson 14/700, „Tinte gedämpft“). */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = Pilunia.colors.tinteGedaempft) {
    Text(text, style = atkinson(14, 700), color = color, modifier = modifier)
}

// =====================================================================
// Listenzeilen
// =====================================================================

/**
 * Zeile in einer Karte: optional Symbolkachel, Titel (16/700) + Untertitel (14),
 * rechts Schalter, Chevron oder eigener Inhalt. Trennlinie „Linie“ unten (außer [last]).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = { LIcon(Lucide.ChevronRight, 22.dp, Pilunia.colors.tinteGedaempft) },
    last: Boolean = false,
    verticalPadding: Dp = 14.dp,
    gap: Dp = 12.dp,
    subtitleColor: Color = Pilunia.colors.tinteGedaempft,
    onClick: (() -> Unit)? = null,
) {
    val c = Pilunia.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (!last) Modifier.drawBehind {
                drawLine(c.linie, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
            } else Modifier)
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            if (subtitle != null) Text(subtitle, style = atkinson(14, lineHeight = 1.3f), color = subtitleColor)
        }
        trailing?.invoke(this)
    }
}

/** Symbolkachel für Einstellungszeilen: 40 × 40, Radius 12, „Lavendel Tönung“, Symbol 20 „Pflaume“. */
@Composable
fun RowIconTile(icon: ImageVector, size: Dp = 40.dp, radius: Dp = 12.dp, iconSize: Dp = 20.dp, background: Color = Pilunia.colors.lavendelToenung, tint: Color = Pilunia.colors.pflaume) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(background), contentAlignment = Alignment.Center) {
        LIcon(icon, iconSize, tint)
    }
}

// =====================================================================
// Medikamentfarben
// =====================================================================

/** Kachel-Tönung + kräftiges Symbol je Medikamentfarbe. */
data class MedPalette(val tile: Color, val icon: Color, val swatch: Color, val bar: Color)

fun PiluniaColors.palette(color: MedColor): MedPalette = when (color) {
    MedColor.ROSE -> MedPalette(warnungFlaeche, warnungKraeftig, Color(0xFFF48FAA), warnungKraeftig)
    MedColor.GELB -> MedPalette(gelbFlaeche, gelb, Color(0xFFFFC94D), Color(0xFFFFC94D))
    MedColor.MINT -> MedPalette(mintFlaeche, erfolg, Color(0xFF7FD3A6), Color(0xFF7FD3A6))
    MedColor.LAVENDEL -> MedPalette(lavendelToenungKraeftig, pflaume, Color(0xFFA78BFA), lavendel)
    MedColor.BLAU -> MedPalette(blauFlaeche, blau, Color(0xFF8EB8F0), Color(0xFF8EB8F0))
    MedColor.GRAU -> MedPalette(if (isDark) Color(0xFF2A2E36) else Color(0xFFEEF1F5), if (isDark) Color(0xFFB8C2D1) else Color(0xFF64748B), Color(0xFF94A3B8), grau)
}

/** Symbol je Darreichungsform: Tablette, Kapsel, Tropfen, Spray, Spritze. */
val MedForm.icon: ImageVector
    get() = when (this) {
        MedForm.TABLETTE -> Lucide.Tablets
        MedForm.KAPSEL -> Lucide.Pill
        MedForm.TROPFEN -> Lucide.Droplet
        MedForm.SPRAY -> Lucide.SprayCan
        MedForm.SPRITZE -> Lucide.Syringe
    }

/** Medikament-Symbolkachel (Standard 44 × 44, Radius 14, Symbol 22 passend zur Form). */
@Composable
fun MedIcon(
    color: MedColor,
    form: MedForm,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 14.dp,
    iconSize: Dp = 22.dp,
    icon: ImageVector = form.icon,
) {
    val p = Pilunia.colors.palette(color)
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(p.tile), contentAlignment = Alignment.Center) {
        LIcon(icon, iconSize, p.icon)
    }
}

// =====================================================================
// Kopfzeilen
// =====================================================================

/**
 * Kopfzeile 56 hoch, links 8 / rechts [endPadding]: Zurück- oder Schließen-Symbol,
 * Titel Outfit [titleSize]/600, rechts optional Aktionen.
 */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleSize: Int = 20,
    navIcon: ImageVector? = Lucide.ArrowLeft,
    navDescription: String = "Zurück",
    onNav: (() -> Unit)? = null,
    endPadding: Dp = 20.dp,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navIcon != null && onNav != null) IconButtonP(navIcon, navDescription, onNav)
        if (title != null) {
            Text(
                title, style = outfit(titleSize, 600), color = Pilunia.colors.tinte,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Schließen-Kopfzeile (X rechts), wie auf den Erinnerungs-Screens. */
@Composable
fun CloseBar(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        IconButtonP(Lucide.X, "Schließen", onClose)
    }
}

/** Fortschrittsbalken aus Segmenten (6 hoch, Radius 3, Abstand 6). */
@Composable
fun StepProgress(steps: Int, current: Int, modifier: Modifier = Modifier, height: Dp = 6.dp, gap: Dp = 6.dp, segmentWidth: Dp? = null) {
    val c = Pilunia.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(steps) { i ->
            Box(
                (if (segmentWidth != null) Modifier.width(segmentWidth) else Modifier.weight(1f))
                    .height(height)
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (i <= current) c.primaer else c.lavendelInaktiv)
            )
        }
    }
}

// =====================================================================
// Systemleisten
// =====================================================================

/** Platz für die Statusleiste (entspricht der 40 px hohen Statusleiste im Design). */
@Composable
fun StatusBarSpace() = Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars))

/** Platz für die Gestenleiste unten (im Design 24 px). */
@Composable
fun NavBarSpace(min: Dp = 0.dp) {
    Box(Modifier.fillMaxWidth()) {
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(min))
    }
}

enum class MainTab(val label: String, val icon: ImageVector) {
    HEUTE("Heute", Lucide.House),
    MEDIKAMENTE("Medikamente", Lucide.Pill),
    VERLAUF("Verlauf", Lucide.CalendarDays),
    MEHR("Mehr", Lucide.Menu),
}

/**
 * Tab-Leiste (Komponente „Tab-Leiste / …“): „Fläche“, oben Radius 24, Schatten nach oben,
 * Tabs mit Indikator 64 × 32 („Lavendel Tönung kräftig“) und Beschriftung 12.
 */
@Composable
fun TabBar(current: MainTab, onSelect: (MainTab) -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(listOf(ShadowLayer((-2).dp, 16.dp, FixedColors.Schatten.copy(alpha = 0x1A / 255f))), 24.dp)
            .clip(shape)
            .background(c.flaeche)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            MainTab.entries.forEach { tab ->
                val selected = tab == current
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, role = Role.Tab,
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(64.dp, 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) c.lavendelToenungKraeftig else Color.Transparent)
                            .clickable { onSelect(tab) },
                        contentAlignment = Alignment.Center,
                    ) {
                        LIcon(tab.icon, 22.dp, if (selected) c.pflaume else c.tinteGedaempft)
                    }
                    Text(
                        tab.label,
                        style = atkinson(12, if (selected) 700 else 400),
                        color = if (selected) c.tinte else c.tinteGedaempft,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// =====================================================================
// Bilder
// =====================================================================

/** Figurenbild (Seitenverhältnis bleibt, wie „fit“ im Design). */
@Composable
fun PiluniaImage(@DrawableRes res: Int, contentDescription: String?, modifier: Modifier = Modifier) {
    Image(painterResource(res), contentDescription, modifier, contentScale = ContentScale.Fit)
}

/** Leerer Zustand / zentrierter Text. */
@Composable
fun CenterText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, style = style, color = color, textAlign = TextAlign.Center, modifier = modifier)
}
