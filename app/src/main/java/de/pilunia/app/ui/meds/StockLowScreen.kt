package de.pilunia.app.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PackagePlus
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.X
import de.pilunia.app.R
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.OccStatus
import de.pilunia.app.domain.StockForecast
import de.pilunia.app.domain.forecast
import de.pilunia.app.domain.isIntakeDay
import de.pilunia.app.domain.occurrencesOn
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.IconButtonP
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.SecondaryButton
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.nav.LocalContainer
import de.pilunia.app.ui.nav.backOrMain
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.PiluniaColors
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Rose-Verlauf des Screens (#F48FAA 22 % bis 60 % der Höhe). */
private fun Modifier.roseBackground(c: PiluniaColors): Modifier = background(c.hintergrund).drawBehind {
    val rose = Color(0xFFF48FAA)
    drawRect(Brush.verticalGradient(0f to rose.copy(alpha = 0x38 / 255f), 0.6f to rose.copy(alpha = 0f), startY = 0f, endY = size.height))
}

/** 14 Vorrat niedrig. */
@Composable
fun StockLowScreen(nav: NavController, id: Long) {
    val c = Pilunia.colors
    val container = LocalContainer.current
    val repo = container.repository
    val flow = remember(id) { repo.observeMed(id) }
    val data by flow.collectAsState(initial = null)
    val intakes by rememberIntakes()
    val now by rememberNow()
    val today = now.toLocalDate()

    Box(Modifier.fillMaxSize().roseBackground(c)) {
        Column(Modifier.fillMaxSize()) {
            StatusBarSpace()
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButtonP(Lucide.X, "Schließen", { nav.backOrMain() })
            }
            val m = data
            if (m != null) {
                val fc = remember(m, intakes, today) { forecast(m, intakes, today) }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val minH = maxHeight
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = minH)
                            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Top(m, fc, today)
                        RangeCard(m, fc, intakes, now)
                        Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionButton("Neue Packung eintragen", Lucide.PackagePlus, {
                                container.scope.launch { repo.addPack(id) }
                                nav.backOrMain()
                            })
                            SecondaryButton("In 2 Tagen erinnern", {
                                container.scope.launch { repo.snoozeStockWarning(id, 2) }
                                nav.backOrMain()
                            })
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f))
            }
            NavBarSpace()
        }
    }
}

@Composable
private fun Top(m: MedWithDoses, fc: StockForecast, today: LocalDate) {
    val c = Pilunia.colors
    val med = m.med
    val stock = med.stock.coerceAtLeast(0)
    val empty = stock == 0 || (fc.firstEmptyDay != null && fc.firstEmptyDay <= today)
    val title = if (stock == 0) "Dein ${med.name} ist aufgebraucht" else "Dein ${med.name} wird knapp"
    val units = "${stock} ${if (stock == 1) med.form.singular else med.form.plural}"
    val last = fc.lastCoveredDay
    val reach = when {
        empty -> null
        last == null -> null
        last == today -> "das reicht nur noch für heute"
        last == today.plusDays(1) -> "das reicht bis morgen"
        last < today.plusDays(7) -> "das reicht bis ${Fmt.weekdayLong(last)}"
        else -> "das reicht bis ${Fmt.dayShortWithYear(last)}"
    }
    val info = when {
        stock == 0 -> "Dein Vorrat ist leer. Zeit, ein Folgerezept zu besorgen."
        reach != null -> "Noch $units – $reach. Zeit, ein Folgerezept zu besorgen."
        else -> "Noch $units. Zeit, ein Folgerezept zu besorgen."
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PiluniaImage(R.drawable.pilunia_blister_warnung, "Pilunia mit fast leerem Blister", Modifier.size(240.dp))
        Text(
            title, style = outfit(30, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
            color = c.tinte, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            info, style = atkinson(17, lineHeight = 1.45f),
            color = c.tinteGedaempft, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class RangeDot { TAKEN, COVERED, LAST, EMPTY, NONE }

private val CoveredStroke = Color(0xFFD9CFE9)
private val PillWhite = Color(0xFFFFFFFF)

/** Wochenkarte ab heute: genommen, Vorrat reicht, letzte Tablette, danach leer. */
@Composable
private fun RangeCard(m: MedWithDoses, fc: StockForecast, intakes: List<de.pilunia.app.data.Intake>, now: LocalDateTime) {
    val c = Pilunia.colors
    val med = m.med
    val today = now.toLocalDate()
    val days = (0..6).map { today.plusDays(it.toLong()) }
    val lastCovered = fc.lastCoveredDay
    // Letzter Einnahmetag, der noch abgedeckt ist („Letzte Tablette“).
    val lastPillDay = lastCovered?.let { lc ->
        days.filter { it <= lc && med.isIntakeDay(it) }.maxOrNull()
    }
    val empty = fc.firstEmptyDay
    val states = days.map { d ->
        when {
            !med.isIntakeDay(d) -> RangeDot.NONE
            d == today && occurrencesOn(d, listOf(m), intakes).let { o -> o.isNotEmpty() && o.all { it.status(now) == OccStatus.TAKEN } } -> RangeDot.TAKEN
            empty != null && d >= empty -> RangeDot.EMPTY
            d == lastPillDay -> RangeDot.LAST
            else -> RangeDot.COVERED
        }
    }
    val hint = when {
        med.stock <= 0 -> "Die Packung ist leer."
        empty == null -> null
        empty <= today -> "Ab heute ist die Packung leer."
        else -> "Ab ${Fmt.weekdayLong(empty)}, ${empty.dayOfMonth}. ${Fmt.monthShort(empty)} ist die Packung leer."
    }

    Column(
        Modifier
            .padding(top = 24.dp)
            .fillMaxWidth()
            .softShadow(CardShadow, 24.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.flaeche)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { i, d ->
                val st = states[i]
                Column(
                    Modifier.width(44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        Fmt.weekdayShort(d),
                        style = atkinson(13, if (st == RangeDot.LAST) 700 else 400),
                        color = if (st == RangeDot.LAST) c.warnung else c.tinteGedaempft,
                    )
                    RangeCircle(st)
                }
            }
        }
        if (hint != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LIcon(Lucide.TriangleAlert, 18.dp, c.warnung)
                Text(hint, style = atkinson(14, 700), color = c.warnung, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RangeCircle(state: RangeDot) {
    val c = Pilunia.colors
    val base = Modifier.size(30.dp)
    when (state) {
        RangeDot.TAKEN -> Box(base.background(c.mint, CircleShape), contentAlignment = Alignment.Center) {
            LIcon(Lucide.Check, 16.dp, c.erfolg, strokeWidth = 3f, contentDescription = "Eingenommen")
        }
        RangeDot.COVERED -> Box(base.background(PillWhite, CircleShape).border(1.5.dp, CoveredStroke, CircleShape))
        RangeDot.LAST -> Box(base.background(PillWhite, CircleShape).border(2.dp, c.warnungKraeftig, CircleShape))
        RangeDot.EMPTY -> Box(base.dashedCircle(c.warnungKraeftig, 1.5.dp, 3f, 3f))
        RangeDot.NONE -> Box(base.background(c.lavendelToenung, CircleShape))
    }
}
