package de.pilunia.app.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavController
import com.composables.icons.lucide.AlarmClock
import com.composables.icons.lucide.BatteryCharging
import com.composables.icons.lucide.BellRing
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pill
import de.pilunia.app.R
import de.pilunia.app.domain.Fmt
import de.pilunia.app.reminder.Permissions
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.MainTab
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.StepProgress
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEP_REMIND = 0
private const val STEP_STOCK = 1
private const val STEP_NAME = 2
private const val STEP_PERMISSIONS = 3
private const val STEP_COUNT = 4

/**
 * 02 Erinnern → 03 Vorrat → Vorname (neu) → 04 Freigaben.
 * Ein Screen mit gemeinsamer Kopfzeile (Fortschritt + „Überspringen“); die Schritte gleiten seitlich.
 */
@Composable
fun OnboardingScreen(nav: NavController) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(STEP_REMIND) }
    var name by rememberSaveable { mutableStateOf("") }
    var nameLoaded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!nameLoaded) {
            val saved = container.settings.current().userName
            if (name.isEmpty()) name = saved
            nameLoaded = true
        }
    }

    fun saveName() {
        val n = name.trim()
        container.scope.launch { container.settings.update { it.copy(userName = n) } }
    }

    fun goTo(target: Int) {
        if (step == STEP_NAME && target > step) saveName()
        step = target.coerceIn(0, STEP_COUNT - 1)
    }

    fun finish() {
        scope.launch {
            container.settings.update { it.copy(onboardingDone = true) }
            nav.navigate(Routes.main(MainTab.MEDIKAMENTE)) { popUpTo(0) }
        }
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.hintergrund)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    ) {
        StatusBarSpace()
        // Kopfzeile: 56 hoch, links 20 / rechts 8
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepProgress(STEP_COUNT, step, segmentWidth = 28.dp)
            if (step < STEP_PERMISSIONS) {
                TextButtonP("Überspringen", onClick = { goTo(STEP_PERMISSIONS) }, color = c.akzentText)
            }
        }

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(280)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(280)))
                    .togetherWith(slideOutHorizontally(tween(220)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(200)))
            },
            label = "onboarding",
        ) { s ->
            when (s) {
                STEP_REMIND -> StepPage(
                    title = "Nie wieder vergessen.",
                    text = "Pilunia erinnert dich genau zur Einnahmezeit – auch wenn dein Handy gesperrt ist.",
                    illustration = {
                        IllustrationCard(R.drawable.pilunia_wecker, "Pilunia mit Wecker") { ReminderPreviewCard() }
                    },
                    bottom = { PrimaryButton("Weiter", onClick = { goTo(STEP_STOCK) }) },
                )
                STEP_STOCK -> StepPage(
                    title = "Dein Vorrat im Blick.",
                    text = "Pilunia zählt jede Tablette im Blister mit und sagt dir rechtzeitig, wann du eine neue Packung brauchst.",
                    illustration = {
                        IllustrationCard(R.drawable.pilunia_blister, "Pilunia hält einen Blister") { StockPreviewCard() }
                    },
                    bottom = { PrimaryButton("Weiter", onClick = { goTo(STEP_NAME) }) },
                )
                STEP_NAME -> NameStep(
                    name = name,
                    onName = { name = it.take(40) },
                    onNext = { goTo(STEP_PERMISSIONS) },
                )
                else -> PermissionsStep(onFinish = ::finish)
            }
        }
    }
}

// =====================================================================
// Aufbau eines Schritts
// =====================================================================

/**
 * Inhalt wie im Design: oben Bild + Text (Abstand 32), unten der Button.
 * Oben scrollt, falls der Bildschirm kleiner ist als 915.
 */
@Composable
private fun StepPage(
    title: String,
    text: String,
    illustration: @Composable () -> Unit,
    gap: Dp = 32.dp,
    extra: (@Composable () -> Unit)? = null,
    bottom: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            illustration()
            StepText(title, text)
            extra?.invoke()
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

/** Überschrift Outfit 30/700 und Beschreibung Atkinson 17 (Zeilenhöhe 1,45), zentriert, innen 8 Rand. */
@Composable
private fun StepText(title: String, text: String) {
    val c = Pilunia.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title, style = outfit(30, 700, lineHeight = 1.15f, letterSpacing = -0.5f), color = c.tinte,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text, style = atkinson(17, lineHeight = 1.45f), color = c.tinteGedaempft,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Große Bildkarte 372 × 440, Radius 32, Lavendel-Verlauf; Figur 300 × 282 oben, Karte unten (Abstand 24 / 28). */
@Composable
private fun IllustrationCard(
    image: Int,
    description: String,
    height: Dp = 440.dp,
    imageHeight: Dp = 282.dp,
    card: @Composable BoxScope.() -> Unit,
) {
    val c = Pilunia.colors
    val top = if (c.isDark) Color(0xFF2C2242) else Color(0xFFEFE7FE)
    val bottom = if (c.isDark) Color(0xFF211A2E) else Color(0xFFF8F3FF)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 36.dp, end = 36.dp)
                .fillMaxWidth()
                .height(imageHeight)
        ) {
            PiluniaImage(image, description, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            content = card,
        )
    }
}

/** Mitteilungs-Ausschnitt aus 02: App-Symbol 40 (Radius 14, Primär) + Absender/Nachricht. */
@Composable
private fun ReminderPreviewCard() {
    val c = Pilunia.colors
    PCard(Modifier.fillMaxWidth(), radius = 20.dp) {
        Row(
            Modifier.padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.primaer), contentAlignment = Alignment.Center) {
                LIcon(Lucide.Pill, 20.dp, FixedColors.Weiss)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Pilunia · 08:00", style = atkinson(13), color = c.tinteGedaempft, maxLines = 1)
                Text("Zeit für Ramipril 5 mg", style = atkinson(16, 700), color = c.tinte, maxLines = 1)
            }
        }
    }
}

/** Vorrats-Ausschnitt aus 03: Name + „6 von 28“, Balken 8 hoch, Reichweite in „Warnung“. */
@Composable
private fun StockPreviewCard() {
    val c = Pilunia.colors
    PCard(Modifier.fillMaxWidth(), radius = 20.dp) {
        Column(
            Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ramipril 5 mg", style = atkinson(16, 700), color = c.tinte, maxLines = 1)
                Text("6 von 28", style = outfit(16, 600), color = c.pflaume, maxLines = 1)
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.lavendelToenungKraeftig)
            ) {
                Box(
                    Modifier.fillMaxWidth(63f / 292f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.warnungKraeftig)
                )
            }
            Text("Reicht bis Do, 15. Okt. – Rezept besorgen", style = atkinson(14), color = c.warnung, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =====================================================================
// Neuer Schritt: Vorname
// =====================================================================

@Composable
private fun NameStep(name: String, onName: (String) -> Unit, onNext: () -> Unit) {
    val focus = LocalFocusManager.current
    StepPage(
        title = "Wie heißt du?",
        text = "Pilunia begrüßt dich gern mit deinem Vornamen. Das ist freiwillig und bleibt auf deinem Handy.",
        gap = 24.dp,
        illustration = {
            IllustrationCard(R.drawable.pilunia_winken, "Pilunia winkt", height = 316.dp, imageHeight = 176.dp) {
                GreetingPreviewCard(name.trim())
            }
        },
        extra = {
            NameField(
                value = name,
                onValueChange = onName,
                onDone = { focus.clearFocus(); onNext() },
            )
        },
        bottom = { PrimaryButton("Weiter", onClick = { focus.clearFocus(); onNext() }) },
    )
}

/** Vorschau der Begrüßung auf „Heute“ – aktualisiert sich beim Tippen. */
@Composable
private fun GreetingPreviewCard(name: String) {
    val c = Pilunia.colors
    val greeting = Fmt.greeting() + if (name.isNotEmpty()) ", $name" else ""
    PCard(Modifier.fillMaxWidth(), radius = 20.dp) {
        Row(
            Modifier.padding(start = 14.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.primaer), contentAlignment = Alignment.Center) {
                LIcon(Lucide.House, 20.dp, FixedColors.Weiss)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Heute · ${Fmt.dayLong(Fmt.today())}", style = atkinson(13), color = c.tinteGedaempft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(greeting, style = atkinson(16, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Eingabefeld wie in 15: Beschriftung Atkinson 14/700, Feld 56 hoch, Radius 16, „Fläche“,
 * Rand 1 „Feldrand“ bzw. 2 „Primär“ im Fokus, Text Atkinson 17.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, onDone: () -> Unit) {
    val c = Pilunia.colors
    var focused by remember { mutableStateOf(false) }
    val bring = remember { BringIntoViewRequester() }
    LaunchedEffect(focused) {
        if (focused) {
            // Warten, bis die Tastatur den Inhalt verkleinert hat, dann Feld sichtbar machen.
            delay(350)
            bring.bringIntoView()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Vorname (optional)", style = atkinson(14, 700), color = c.tinte)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = atkinson(17).copy(color = c.tinte),
            cursorBrush = SolidColor(c.primaer),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bring)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(shape)
                        .background(c.flaeche)
                        .border(if (focused) BorderStroke(2.dp, c.primaer) else BorderStroke(1.dp, c.feldrand), shape)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text("Dein Vorname", style = atkinson(17), color = c.tinteGedaempft, maxLines = 1)
                    inner()
                }
            },
        )
    }
}

// =====================================================================
// 04 Freigaben
// =====================================================================

private enum class Grant { NOTIFICATIONS, EXACT_ALARMS, BATTERY }
private enum class Waiting { PERMISSION, SETTINGS }

@Composable
private fun PermissionsStep(onFinish: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val notifOk = remember(tick) { Permissions.notifications(context) }
    val alarmsOk = remember(tick) { Permissions.exactAlarms(context) }
    val batteryOk = remember(tick) { Permissions.batteryUnrestricted(context) }
    val allOk = notifOk && alarmsOk && batteryOk

    // Ablauf „Freigaben erteilen“: nacheinander jede fehlende Freigabe einmal anfragen.
    val attempted = remember { mutableStateListOf<Grant>() }
    var waiting by remember { mutableStateOf<Waiting?>(null) }
    var askedRuntime by rememberSaveable { mutableStateOf(false) }
    var chain by remember { mutableStateOf(false) }

    fun open(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent)
            waiting = Waiting.SETTINGS
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    lateinit var next: () -> Unit
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (waiting == Waiting.PERMISSION) {
            waiting = null
            next()
        }
    }

    next = next@{
        tick++
        if (Grant.NOTIFICATIONS !in attempted && !Permissions.notifications(context)) {
            attempted += Grant.NOTIFICATIONS
            val perm = Permissions.notificationPermission
            val runtimeMissing = perm != null &&
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            if (runtimeMissing && !askedRuntime) {
                askedRuntime = true
                waiting = Waiting.PERMISSION
                permissionLauncher.launch(perm!!)
                return@next
            }
            // Schon einmal abgelehnt oder in den Systemeinstellungen abgeschaltet → Systemseite.
            if (open(Permissions.notificationSettingsIntent(context))) return@next
        }
        if (Grant.EXACT_ALARMS !in attempted && !Permissions.exactAlarms(context)) {
            attempted += Grant.EXACT_ALARMS
            if (open(Permissions.exactAlarmIntent(context))) return@next
        }
        if (Grant.BATTERY !in attempted && !Permissions.batteryUnrestricted(context)) {
            attempted += Grant.BATTERY
            if (open(Permissions.batteryIntent(context))) return@next
        }
        val finishedChain = chain
        chain = false
        if (finishedChain && Permissions.allGranted(context)) onFinish()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
        if (waiting == Waiting.SETTINGS) {
            waiting = null
            next()
        }
    }

    fun requestAll() {
        attempted.clear()
        chain = true
        next()
    }

    fun requestOnly(g: Grant) {
        attempted.clear()
        attempted += Grant.entries.filter { it != g }
        chain = true
        next()
    }

    StepPage(
        title = "Darf Pilunia dich erinnern?",
        text = "Für pünktliche Erinnerungen braucht Android drei Freigaben. Du kannst sie jederzeit ändern.",
        gap = 24.dp,
        illustration = {
            Box(Modifier.size(180.dp)) {
                PiluniaImage(R.drawable.pilunia_kalender, "Pilunia mit Kalender und Glocke", Modifier.fillMaxSize())
            }
        },
        extra = {
            PCard(Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                PermissionRow(Lucide.BellRing, "Mitteilungen", "Erinnerung genau zur Einnahmezeit", notifOk, divider = true) {
                    requestOnly(Grant.NOTIFICATIONS)
                }
                PermissionRow(Lucide.AlarmClock, "Wecker & Erinnerungen", "Auf die Minute genau, auch im Ruhemodus", alarmsOk, divider = true) {
                    requestOnly(Grant.EXACT_ALARMS)
                }
                PermissionRow(Lucide.BatteryCharging, "Akku-Optimierung aus", "Damit Android keine Erinnerung zurückhält", batteryOk, divider = false) {
                    requestOnly(Grant.BATTERY)
                }
            }
        },
        bottom = {
            PrimaryButton(
                if (allOk) "Weiter" else "Freigaben erteilen",
                onClick = { if (allOk) onFinish() else requestAll() },
            )
            TextButtonP("Später", onClick = onFinish, modifier = Modifier.fillMaxWidth(), color = Pilunia.colors.akzentText)
        },
    )
}

/**
 * Zeile wie im Design (Symbol 44, Radius 14, „Lavendel Tönung“; Titel 16/700, Info 14).
 * Erteilte Freigaben bekommen rechts den Mint-Haken aus dem Verlauf (28, Haken 16 „Erfolg“).
 */
@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    info: String,
    granted: Boolean,
    divider: Boolean,
    onRequest: () -> Unit,
) {
    val c = Pilunia.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (!granted) Modifier.clickable(onClick = onRequest) else Modifier)
            .then(
                if (divider) Modifier.drawBehind {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(c.linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                } else Modifier
            )
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(if (granted) c.mintFlaeche else c.lavendelToenung),
            contentAlignment = Alignment.Center,
        ) {
            LIcon(icon, 22.dp, if (granted) c.erfolg else c.pflaume)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = atkinson(16, 700), color = c.tinte)
            Text(info, style = atkinson(14, lineHeight = 1.35f), color = c.tinteGedaempft)
        }
        if (granted) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.mint), contentAlignment = Alignment.Center) {
                LIcon(Lucide.Check, 16.dp, c.erfolg, strokeWidth = 3f, contentDescription = "Erteilt")
            }
        }
    }
}
