package de.pilunia.app.ui.meds

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.X
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Schatten des gewählten Segments und der gewählten Rhythmus-Kachel (y 1, Weichzeichnung 3, Pflaume 15 %). */
val WizardSelectShadow = listOf(ShadowLayer(1.dp, 3.dp, FixedColors.Schatten.copy(alpha = 0x26 / 255f)))

/** Feldbeschriftung über Eingaben und Gruppen (Atkinson 14/700, „Tinte“). */
@Composable
fun WizardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = atkinson(14, 700), color = Pilunia.colors.tinte, modifier = modifier)
}

/** Überschrift eines Schritts (Outfit 26/700, Zeilenhöhe 1,15, Zeichenabstand −0,4). */
@Composable
fun WizardHeadline(text: String, modifier: Modifier = Modifier) {
    Text(text, style = outfit(26, 700, lineHeight = 1.15f, letterSpacing = -0.4f), color = Pilunia.colors.tinte, modifier = modifier)
}

/** Gestrichelter Rand (z. B. „Eigener“ und „Weitere Uhrzeit“). */
fun Modifier.wizardDashedBorder(width: Dp, color: Color, radius: Dp, on: Dp = 6.dp, off: Dp = 4.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius((radius.toPx() - w / 2).coerceAtLeast(0f)),
        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on.toPx(), off.toPx()))),
    )
}

/**
 * Eingabefeld wie im Design: 56 hoch, Radius 16, „Fläche“, Rand 1 „Feldrand“,
 * im Fokus Rand 2 „Primär“ und Cursor 2 „Primär“. Text Atkinson 17.
 */
@Composable
fun WizardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
) {
    val c = Pilunia.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused },
        textStyle = atkinson(17).copy(color = c.tinte),
        cursorBrush = SolidColor(c.primaer),
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() ?: focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
            onDone = { onImeAction?.invoke() ?: focusManager.clearFocus() },
        ),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(c.flaeche)
                    .border(if (focused) 2.dp else 1.dp, if (focused) c.primaer else c.feldrand, shape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = atkinson(17), color = c.tinteGedaempft, maxLines = 1)
                }
                inner()
            }
        },
    )
}

/** Feld mit Beschriftung darüber (Abstand 6). */
@Composable
fun WizardFieldGroup(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        WizardLabel(label)
        content()
    }
}

/** „Einheit“: Auswahlfeld mit Menü im App-Stil (Karte mit Häkchen bei der gewählten Einheit). */
@Composable
fun WizardUnitSelect(value: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    val density = LocalDensity.current
    var open by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(128.dp) }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val shape = RoundedCornerShape(16.dp)
    val units = if (value in WizardUnits) WizardUnits else WizardUnits + value
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }
                .clip(shape)
                .background(c.flaeche)
                .border(if (open) 2.dp else 1.dp, if (open) c.primaer else c.feldrand, shape)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(start = 16.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = atkinson(17), color = c.tinte, maxLines = 1)
            LIcon(Lucide.ChevronDown, 20.dp, c.tinteGedaempft, Modifier.rotate(rotation))
        }
        if (open) {
            val margin = 12.dp
            Popup(
                alignment = Alignment.TopStart,
                offset = with(density) { IntOffset(-margin.roundToPx(), (56.dp + 6.dp - margin).roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(Modifier.padding(margin)) {
                    Column(
                        Modifier
                            .width(anchorWidth)
                            .softShadow(CardShadow, 16.dp)
                            .clip(shape)
                            .background(c.flaeche)
                            .padding(vertical = 6.dp)
                    ) {
                        units.forEach { u ->
                            val selected = u == value
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { onSelect(u); open = false }
                                    .background(if (selected) c.lavendelToenung else Color.Transparent)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(u, style = atkinson(17, if (selected) 700 else 400), color = if (selected) c.akzentText else c.tinte)
                                if (selected) LIcon(Lucide.Check, 18.dp, c.akzentText, strokeWidth = 2.5f)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Segment-Schalter (48 hoch, „Lavendel Tönung kräftig“, Radius 24, Innenabstand 4):
 * das gewählte Segment ist eine weiße Pille mit leichtem Schatten, die zur Auswahl gleitet.
 */
@Composable
fun WizardSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.lavendelToenungKraeftig)
            .padding(4.dp)
    ) {
        val gap = 4.dp
        val segment = (maxWidth - gap * (options.size - 1)) / options.size
        val x by animateDpAsState((segment + gap) * selected, spring(dampingRatio = 0.85f, stiffness = 500f), label = "segment")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .softShadow(WizardSelectShadow, 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(c.flaeche)
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(if (i == selected) c.tinte else c.akzentText, label = "segmentText")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pClickable(RoundedCornerShape(20.dp), role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = atkinson(15, 700), color = color, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * Runder −/+-Knopf (44 × 44), wiederholt beim Gedrückthalten.
 * Standard: „Lavendel Tönung kräftig“ wie in Schritt 3; im Sheet „Lavendel Tönung“.
 */
@Composable
fun WizardStepButton(
    icon: ImageVector,
    contentDescription: String,
    onStep: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    background: Color = Pilunia.colors.lavendelToenungKraeftig,
    size: Dp = 44.dp,
) {
    val c = Pilunia.colors
    val step by rememberUpdatedState(onStep)
    val canStep by rememberUpdatedState(enabled)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(background)
            .indication(interaction, ripple())
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                onClick { if (canStep) step(); canStep }
            }
            .pointerInput(Unit) {
                coroutineScope {
                    val scope = this
                    this@pointerInput.awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!canStep) return@awaitEachGesture
                        val press = PressInteraction.Press(down.position)
                        scope.launch { interaction.emit(press) }
                        step()
                        val repeat = scope.launch {
                            delay(420)
                            while (canStep) {
                                step()
                                delay(70)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                        scope.launch { interaction.emit(PressInteraction.Release(press)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        LIcon(icon, 20.dp, c.akzentText, strokeWidth = 2.5f)
    }
}

/**
 * Direkt editierbare Zahl. Ohne Rahmen (Schritt 3) oder als Kasten (Sheet „Eigener Rhythmus“).
 * Ungültige Zwischenstände werden beim Verlassen des Felds verworfen.
 */
@Composable
fun WizardNumberField(
    value: Int,
    onValue: (Int) -> Unit,
    range: IntRange,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    maxDigits: Int = 3,
    format: (Int) -> String = { it.toString() },
    boxShape: RoundedCornerShape? = null,
    contentDescription: String? = null,
) {
    val c = Pilunia.colors
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(format(value))) }
    LaunchedEffect(value) {
        if (field.text.toIntOrNull() != value) {
            val t = if (focused) value.toString() else format(value)
            field = TextFieldValue(t, TextRange(t.length))
        }
    }
    BasicTextField(
        value = field,
        onValueChange = { new ->
            val digits = new.text.filter { it.isDigit() }.take(maxDigits)
            field = new.copy(text = digits, selection = if (digits == new.text) new.selection else TextRange(digits.length))
            digits.toIntOrNull()?.let { if (it in range) onValue(it) }
        },
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    val t = value.toString()
                    field = TextFieldValue(t, TextRange(0, t.length))
                } else if (!it.isFocused && focused) {
                    field = TextFieldValue(format(value))
                }
                focused = it.isFocused
            }
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        textStyle = textStyle.copy(color = c.tinte, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(c.primaer),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        decorationBox = { inner ->
            Box(
                if (boxShape != null) Modifier
                    .fillMaxSize()
                    .clip(boxShape)
                    .background(c.flaeche)
                    .border(if (focused) 2.dp else 1.5.dp, if (focused) c.primaer else c.feldrand, boxShape)
                else Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { inner() }
        },
    )
}

/** Stepper wie in Schritt 3: − (44) · Zahl (48 breit, Outfit 20/600) · + (44). */
@Composable
fun WizardStepper(
    value: Int,
    onValue: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        WizardStepButton(Lucide.Minus, "$label: weniger", { onValue((value - step).coerceIn(range)) }, enabled = value > range.first)
        WizardNumberField(
            value = value, onValue = onValue, range = range,
            textStyle = outfit(20, 600),
            modifier = Modifier.width(48.dp).height(44.dp),
            maxDigits = range.last.toString().length,
            contentDescription = label,
        )
        WizardStepButton(Lucide.Plus, "$label: mehr", { onValue((value + step).coerceIn(range)) }, enabled = value < range.last)
    }
}

/** Zeile in einer Karte mit Trennlinie „Linie“ unten (außer [last]). */
fun Modifier.wizardDivider(last: Boolean, color: Color): Modifier =
    if (last) this else drawBehind {
        val y = size.height - 0.5.dp.toPx()
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }

/** Karte „Fläche“, Radius 20, Standard-Kartenschatten. */
@Composable
fun WizardCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Pilunia.colors.flaeche)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Runder Schließen-Knopf der Sheets (44, „Lavendel Tönung“, X 20 „Akzent Text“, Strich 2,5). */
@Composable
fun WizardCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    Box(
        modifier.size(44.dp).pClickable(CircleShape, onClick = onClick).background(c.lavendelToenung),
        contentAlignment = Alignment.Center,
    ) {
        LIcon(Lucide.X, 20.dp, c.akzentText, strokeWidth = 2.5f, contentDescription = "Schließen")
    }
}

/** Kopf eines Sheets: Titel (Outfit 26/700) + Beschreibung (15, Zeilenhöhe 1,4) und X rechts. */
@Composable
fun WizardSheetHeader(title: String, description: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WizardHeadline(title)
            if (description != null) {
                Text(description, style = atkinson(15, lineHeight = 1.4f), color = Pilunia.colors.tinteGedaempft)
            }
        }
        WizardCloseButton(onClose)
    }
}

/**
 * Unteres Sheet im App-Stil: Scrim, „Hintergrund“ mit Radius 28 oben, Schatten nach oben,
 * Griff 40 × 4 („Lavendel inaktiv“). Der Inhalt bleibt während der Ausblend-Animation erhalten.
 * Inhalt: Abstand 20, Innenabstand 8 / 20 / 16; scrollt, wenn der Platz nicht reicht.
 */
@Composable
fun <T : Any> WizardSheetHost(
    item: T?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    val c = Pilunia.colors
    var last by remember { mutableStateOf<T?>(null) }
    if (item != null) last = item
    BackHandler(enabled = item != null) { onDismiss() }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(item != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(WizardScrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            item != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .softShadow(listOf(ShadowLayer((-8).dp, 32.dp, Color(0x292E2440))), 28.dp)
                        .clip(shape)
                        .background(c.hintergrund)
                        // Klicks auf das Sheet nicht an den Scrim durchreichen.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))
                        .padding(top = 10.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(16.dp), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.size(40.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.lavendelInaktiv))
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        last?.let { content(it) }
                    }
                }
            }
        }
    }
}

/** Scrim hinter Sheets (Tinte, 45 %; in beiden Modi gleich). */
val WizardScrim = Color(0xFF2E2440).copy(alpha = 0.45f)

/** Kurze Rückfrage im App-Stil (z. B. „Änderungen verwerfen?“). */
@Composable
fun WizardConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    dismiss: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Pilunia.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .softShadow(CardShadow, 28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.hintergrund)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = outfit(22, 700, lineHeight = 1.15f, letterSpacing = -0.3f), color = c.tinte)
            Text(text, style = atkinson(15, lineHeight = 1.4f), color = c.tinteGedaempft)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(dismiss, onDismiss)
            TextButtonP(confirm, onConfirm, Modifier.fillMaxWidth(), color = c.warnung)
        }
    }
}
