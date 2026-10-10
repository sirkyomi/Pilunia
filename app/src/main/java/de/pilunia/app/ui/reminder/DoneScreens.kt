package de.pilunia.app.ui.reminder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CalendarClock
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import de.pilunia.app.R
import de.pilunia.app.data.IntakeStatus
import de.pilunia.app.data.MedForm
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.domain.BlisterSlot
import de.pilunia.app.domain.BlisterState
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.Occurrence
import de.pilunia.app.domain.blisterState
import de.pilunia.app.domain.forecast
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.PCard
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import java.time.LocalDateTime

// =====================================================================
// 10 Einnahme bestätigt
// =====================================================================

/** Mint-Tönung des Hintergrunds von 10 (#7FD3A6, 25 % → 0 bei 60 %), in beiden Modi gleich. */
private val MintVerlauf = Color(0xFF7FD3A6)

/**
 * 10 „Super gemacht!“ für die gerade bestätigten Einnahmen [shown]:
 * Jubel-Figur mit Konfetti, Info-Zeile und je Medikament eine Vorrats-Karte mit Blister.
 */
@Composable
internal fun ConfirmedScreen(
    data: SlotData,
    shown: List<Occurrence>,
    busy: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
) {
    val c = Pilunia.colors
    val meds = shown.distinctBy { it.med.id }.mapNotNull { data.medWithDoses(it.med.id) }
    val multi = meds.size > 1
    ReminderScaffold(
        Modifier.screenBackground(c, fadeEnd = 0.6f, tint = MintVerlauf, alpha = 0x40 / 255f),
        onClose,
    ) { scale ->
        // Oben: Figur, Überschrift, Info (Abstand 8)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CheerFigure(if (multi) scale * 0.7f else scale)
            Text(
                "Super gemacht!", style = outfit(36, 800, lineHeight = 1.15f, letterSpacing = -0.8f), color = c.tinte,
                textAlign = TextAlign.Center,
            )
            Text(confirmedInfo(shown), style = atkinson(17), color = c.tinteGedaempft, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            meds.forEach { StockCard(it, data, multi) }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton("Fertig", { if (!busy) onDone() }, c.primaer, OnPrimary)
            TextButtonP("Rückgängig machen", { if (!busy) onUndo() }, Modifier.fillMaxWidth(), color = c.akzentText)
        }
    }
}

/** „Ramipril 5 mg um 08:01 eingenommen.“ bzw. für mehrere Medikamente. */
private fun confirmedInfo(shown: List<Occurrence>): String {
    val at = shown.mapNotNull { it.intake?.takenAt }.maxOrNull() ?: System.currentTimeMillis()
    val time = Fmt.time(at)
    if (shown.size == 1) {
        val o = shown[0]
        val taken = o.intake?.amount ?: o.dose.amount
        return if (taken in 1 until o.dose.amount) {
            "${o.med.displayName}: $taken von ${o.med.form.amount(o.dose.amount)} um $time eingenommen."
        } else "${o.med.displayName} um $time eingenommen."
    }
    return if (shown.size == 2) "${shown[0].med.displayName} und ${shown[1].med.displayName} um $time eingenommen."
    else "${shown.size} Medikamente um $time eingenommen."
}

/** Jubelnde Pilunia (260 × 250) mit Konfetti im 320 × 260-Rahmen. */
@Composable
private fun CheerFigure(scale: Float) {
    @Composable
    fun confetti(x: Int, y: Int, w: Int, h: Int, color: Long, rotation: Float = 0f, round: Boolean = false) {
        Box(
            Modifier
                .offset(x.dp * scale, y.dp * scale)
                .size(w.dp * scale, h.dp * scale)
                .rotate(rotation)
                .background(Color(color), if (round) CircleShape else RoundedCornerShape(3.dp * scale))
        )
    }
    Box(Modifier.size(320.dp * scale, 260.dp * scale)) {
        confetti(18, 40, 8, 16, 0xFFA78BFA, rotation = 30f)
        confetti(60, 8, 10, 10, 0xFF7FD3A6, round = true)
        confetti(262, 20, 8, 8, 0xFFFFC94D, round = true)
        confetti(34, 170, 8, 14, 0xFFFFC94D, rotation = -40f)
        confetti(8, 110, 8, 8, 0xFFF48FAA, round = true)
        confetti(304, 130, 10, 10, 0xFF7FD3A6, round = true)
        Box(Modifier.offset(30.dp * scale, 6.dp * scale).size(260.dp * scale, 250.dp * scale)) {
            PiluniaImage(R.drawable.pilunia_jubeln, "Pilunia jubelt", Modifier.fillMaxSize())
        }
    }
}

/** Karte „Vorrat“: Blister-Stand, Restmenge und ggf. Hinweis auf ein neues Rezept. */
@Composable
private fun StockCard(m: MedWithDoses, data: SlotData, multi: Boolean) {
    val c = Pilunia.colors
    val med = m.med
    val f = forecast(m, data.intakes)
    val low = f.isLow(med.warnDays)
    val hasBlister = med.form == MedForm.TABLETTE || med.form == MedForm.KAPSEL
    val state = blisterState(med, takenToday = true)
    val blisterTitle = "Blister ${state.index} von ${state.count}"
    val stockText = if (med.stock <= 0) "Kein Vorrat mehr" else "Noch ${med.form.amount(med.stock)}"
    PCard(Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (multi) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(med.displayName, style = atkinson(16, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (hasBlister) Text(blisterTitle, style = atkinson(14), color = c.tinteGedaempft)
                    }
                } else {
                    Text(
                        if (hasBlister) blisterTitle else "Vorrat", style = atkinson(16, 700), color = c.tinte,
                        modifier = Modifier.weight(1f), maxLines = 1,
                    )
                }
                Text(stockText, style = outfit(16, 600), color = if (low) c.warnung else c.tinteGedaempft, maxLines = 1)
            }
            if (hasBlister) BlisterGrid(state)
            if (low) {
                val today = Fmt.today()
                val d = f.lastCoveredDay
                val text = when {
                    d == null || d < today -> "Vorrat reicht nicht mehr – Zeit für ein neues Rezept"
                    d == today -> "Reicht nur noch heute – Zeit für ein neues Rezept"
                    else -> "Reicht bis ${Fmt.dayShort(d)} – Zeit für ein neues Rezept"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LIcon(Lucide.CalendarClock, 18.dp, c.warnung)
                    Text(text, style = atkinson(14, lineHeight = 1.35f), color = c.warnung, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Blister
// ---------------------------------------------------------------------

private val BlisterStart = Color(0xFFEEE8F6)
private val BlisterEnd = Color(0xFFDDD4EB)
private val MuldeLeer = Color(0xFFB9A9D6)
private val MuldeGenommen = Color(0xFFA7E3C1)
private val TabletteEnde = Color(0xFFF1ECF8)
private val TabletteSchatten = Color(0x406B4C93)

/** Spaltenzahl je Blistergröße: 14/21/28 → 7, 10/20 → 5, 12/30 → 6, 16/24 → 8, bis 8 eine Reihe. */
private fun blisterColumns(n: Int): Int = when {
    n <= 8 -> n.coerceAtLeast(1)
    n % 7 == 0 -> 7
    n > 72 -> 9
    else -> (8 downTo 5).firstOrNull { n % it == 0 } ?: 7
}

/** Blister-Fläche (Verlauf, Radius 16, Rand 14) mit Mulden in gleich breiten Spalten (Abstand 8 / Zeilen 10). */
@Composable
private fun BlisterGrid(state: BlisterState) {
    val cols = blisterColumns(state.slots.size)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(BlisterStart, BlisterEnd)))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.slots.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until cols) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        row.getOrNull(i)?.let { BlisterDot(it, Modifier.fillMaxWidth().widthIn(max = 26.dp).aspectRatio(1f)) }
                    }
                }
            }
        }
    }
}

/** Mulde: leer = gestrichelt, gerade genommen = Mint, voll = weiße Tablette mit Schatten. */
@Composable
private fun BlisterDot(slot: BlisterSlot, modifier: Modifier) {
    when (slot) {
        BlisterSlot.EMPTY -> Canvas(modifier) {
            val w = 1.5.dp.toPx()
            val dash = 3.dp.toPx()
            drawCircle(
                MuldeLeer, radius = size.minDimension / 2 - w / 2,
                style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
            )
        }
        BlisterSlot.JUST_TAKEN -> {
            // Die gerade geleerte Mulde ploppt federnd auf, ein Ring läuft nach außen.
            val pop = remember { Animatable(0f) }
            val ring = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(380)
                launch { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
                ring.animateTo(1f, tween(650))
            }
            Canvas(modifier) {
                val w = 1.5.dp.toPx()
                val r = size.minDimension / 2
                val p = ring.value
                if (p > 0f && p < 1f) {
                    drawCircle(FixedColors.PunktGenommen.copy(alpha = 1f - p), radius = r * (1f + 0.6f * p), style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f))
                }
                val s = pop.value
                drawCircle(MuldeGenommen, radius = r * s)
                if (s > 0f) drawCircle(FixedColors.PunktGenommen, radius = (r - w / 2) * s, style = Stroke(width = w))
            }
        }
        BlisterSlot.FULL -> Canvas(modifier.softShadow(listOf(ShadowLayer(1.dp, 3.dp, TabletteSchatten)), 13.dp)) {
            drawCircle(
                Brush.linearGradient(
                    listOf(FixedColors.Weiss, TabletteEnde),
                    start = Offset(size.width * 0.3f, 0f),
                    end = Offset(size.width * 0.7f, size.height),
                )
            )
        }
    }
}

// =====================================================================
// 11 Alles erledigt
// =====================================================================

/** Abends (ab 18 Uhr bzw. nachts) „Schlaf gut“ und „Gute Nacht“, tagsüber passende Texte. */
private fun isEvening(now: LocalDateTime): Boolean = now.hour >= 18 || now.hour < 4

@Composable
internal fun AllDoneScreen(
    data: SlotData,
    userName: String,
    streak: Int,
    now: LocalDateTime,
    onClose: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Pilunia.colors
    val evening = isEvening(now)
    val occ = data.dayOcc
    val taken = occ.count { it.isTaken }
    val name = userName.trim()
    val first = when {
        taken == occ.size && occ.size == 1 -> "Du hast deine Einnahme geschafft."
        taken == occ.size -> "Du hast alle ${occ.size} Einnahmen geschafft."
        else -> "Du hast $taken von ${occ.size} Einnahmen geschafft."
    }
    val wish = when {
        evening -> "Schlaf gut"
        now.hour < 12 -> "Hab einen schönen Tag"
        else -> "Genieß den restlichen Tag"
    }
    val second = if (name.isNotEmpty()) "$wish, $name!" else "$wish!"

    ReminderScaffold(
        Modifier.screenBackground(c, fadeEnd = 0.65f, alpha = 0x4D / 255f),
        onClose,
    ) { scale ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(305.dp * scale, 220.dp * scale)) {
                if (evening) PiluniaImage(R.drawable.pilunia_schlafen, "Pilunia schläft", Modifier.fillMaxSize())
                else PiluniaImage(R.drawable.pilunia_daumen, "Pilunia zeigt den Daumen", Modifier.fillMaxSize())
            }
            Text(
                "Alles erledigt für heute", style = outfit(30, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
                color = c.tinte, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "$first\n$second", style = atkinson(17, lineHeight = 1.45f), color = c.tinteGedaempft,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(Modifier.padding(vertical = 12.dp)) {
            PCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                occ.forEach { DoneRow(it) }
            }
        }
        if (streak > 1) {
            Row(
                Modifier
                    .padding(bottom = 12.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.warnungFlaeche)
                    .padding(start = 14.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LIcon(Lucide.Heart, 18.dp, c.warnungKraeftig, strokeWidth = 2.5f)
                Text("$streak Tage in Folge – weiter so!", style = atkinson(15, 700), color = c.warnung, maxLines = 1)
            }
        }
        PillButton(if (evening) "Gute Nacht" else "Fertig", onDone, c.primaer, OnPrimary)
    }
}

/** Zeile der Tageszusammenfassung: Haken 24, Name, Uhrzeit der Einnahme (bzw. „ausgelassen“). */
@Composable
private fun DoneRow(o: Occurrence) {
    val c = Pilunia.colors
    val skipped = o.intake?.status == IntakeStatus.SKIPPED
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (skipped) {
            Box(Modifier.size(24.dp).background(c.lavendelToenung, CircleShape), contentAlignment = Alignment.Center) {
                LIcon(Lucide.X, 14.dp, c.tinteGedaempft, strokeWidth = 3f)
            }
        } else CheckCircle(checked = o.isTaken, size = 24.dp, iconSize = 14.dp)
        Text(
            o.med.displayName, style = atkinson(16, 700), color = c.tinte, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val takenAt = o.intake?.takenAt
        val right = when {
            skipped -> "ausgelassen"
            takenAt != null -> Fmt.time(takenAt)
            else -> Fmt.time(o.minute)
        }
        Text(right, style = if (skipped) atkinson(14) else outfit(15, 500), color = c.tinteGedaempft, maxLines = 1)
    }
}
