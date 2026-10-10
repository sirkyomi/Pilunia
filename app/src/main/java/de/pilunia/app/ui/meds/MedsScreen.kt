package de.pilunia.app.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import de.pilunia.app.R
import de.pilunia.app.data.Intake
import de.pilunia.app.data.MedWithDoses
import de.pilunia.app.data.ScheduleType
import de.pilunia.app.domain.Fmt
import de.pilunia.app.domain.forecast
import de.pilunia.app.ui.components.CardShadow
import de.pilunia.app.ui.components.ChoiceChip
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.MedIcon
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.ShadowLayer
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.components.rememberIntakes
import de.pilunia.app.ui.components.rememberMeds
import de.pilunia.app.ui.components.rememberNow
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.components.softShadow
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.theme.FixedColors
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import java.time.LocalDate

private enum class MedFilter { ALLE, TAEGLICH, BEDARF }

private fun MedWithDoses.matches(f: MedFilter) = when (f) {
    MedFilter.ALLE -> true
    MedFilter.TAEGLICH -> med.scheduleType != ScheduleType.AS_NEEDED
    MedFilter.BEDARF -> med.scheduleType == ScheduleType.AS_NEEDED
}

/** 05 Medikamente · leer / 12 Medikamente (Tab). */
@Composable
fun MedsScreen(nav: NavController, contentPadding: PaddingValues) {
    val c = Pilunia.colors
    val meds by rememberMeds()
    val intakes by rememberIntakes()
    val today = rememberNow().value.toLocalDate()
    val bottom = contentPadding.calculateBottomPadding()

    Box(Modifier.fillMaxSize().screenBackground(c, fadeEnd = 0.35f)) {
        val list = meds
        when {
            list == null -> Column(Modifier.fillMaxSize()) {
                StatusBarSpace()
                Title(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp))
            }
            list.isEmpty() -> EmptyState(bottom) { nav.navigate(Routes.wizard()) }
            else -> {
                MedList(list, intakes, today, bottom) { nav.navigate(Routes.medDetail(it)) }
                Fab(
                    Modifier.align(Alignment.BottomEnd).padding(end = 33.dp, bottom = bottom + 32.dp),
                ) { nav.navigate(Routes.wizard()) }
            }
        }
    }
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    Text(
        "Medikamente",
        style = outfit(28, 700, lineHeight = 1.15f, letterSpacing = -0.5f),
        color = Pilunia.colors.tinte,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------
// 05 Leer
// ---------------------------------------------------------------------

@Composable
private fun EmptyState(bottom: androidx.compose.ui.unit.Dp, onAdd: () -> Unit) {
    val c = Pilunia.colors
    Column(Modifier.fillMaxSize()) {
        StatusBarSpace()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val minH = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minH)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Title()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                ) {
                    PiluniaImage(R.drawable.pilunia_nachdenken, "Pilunia überlegt", Modifier.size(200.dp, 242.dp))
                    Text(
                        "Noch keine Medikamente",
                        style = outfit(24, 700, lineHeight = 1.2f, letterSpacing = -0.3f),
                        color = c.tinte, textAlign = TextAlign.Center,
                    )
                    Text(
                        "Lege dein erstes Medikament an. Pilunia erinnert dich dann pünktlich und behält deinen Vorrat im Blick.",
                        style = atkinson(16, lineHeight = 1.45f),
                        color = c.tinteGedaempft, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .height(56.dp)
                            .pClickable(RoundedCornerShape(28.dp), onClick = onAdd)
                            .background(c.primaer)
                            .padding(start = 24.dp, end = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LIcon(Lucide.Plus, 22.dp, FixedColors.Weiss, strokeWidth = 2.5f)
                        Text("Erstes Medikament anlegen", style = atkinson(17, 700), color = FixedColors.Weiss, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// 12 Liste
// ---------------------------------------------------------------------

@Composable
private fun MedList(
    meds: List<MedWithDoses>,
    intakes: List<Intake>,
    today: LocalDate,
    bottom: androidx.compose.ui.unit.Dp,
    onOpen: (Long) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(MedFilter.ALLE) }
    val counts = MedFilter.entries.associateWith { f -> meds.count { it.matches(f) } }
    // Filter ohne Einträge zurück auf „Alle“ (z. B. nach dem Löschen).
    val active = if (counts.getValue(filter) == 0) MedFilter.ALLE else filter
    val shown = meds.filter { it.matches(active) }

    Column(Modifier.fillMaxSize()) {
        StatusBarSpace()
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottom + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") { Title() }
            item(key = "filter") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip("Alle · ${counts[MedFilter.ALLE]}", active == MedFilter.ALLE) { filter = MedFilter.ALLE }
                    if (counts.getValue(MedFilter.TAEGLICH) > 0) {
                        FilterChip("Täglich · ${counts[MedFilter.TAEGLICH]}", active == MedFilter.TAEGLICH) { filter = MedFilter.TAEGLICH }
                    }
                    if (counts.getValue(MedFilter.BEDARF) > 0) {
                        FilterChip("Bei Bedarf · ${counts[MedFilter.BEDARF]}", active == MedFilter.BEDARF) { filter = MedFilter.BEDARF }
                    }
                }
            }
            items(shown, key = { it.med.id }) { m ->
                MedCard(m, intakes, today) { onOpen(m.med.id) }
            }
        }
    }
}

/** Filter-Chip: 36 hoch, Radius 18, Atkinson 14/700, ohne Häkchen. */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    ChoiceChip(text, selected, onClick, height = 36.dp, showCheck = false, textStyle = atkinson(14, 700))
}

@Composable
private fun MedCard(m: MedWithDoses, intakes: List<Intake>, today: LocalDate, onClick: () -> Unit) {
    val c = Pilunia.colors
    val med = m.med
    val shape = RoundedCornerShape(24.dp)
    val asNeeded = med.scheduleType == ScheduleType.AS_NEEDED
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(CardShadow, 24.dp)
            .clip(shape)
            .background(c.flaeche)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedIcon(med.color, med.form, size = 48.dp, radius = 16.dp, iconSize = 24.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(med.displayName, style = atkinson(17, 700), color = c.tinte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.planLine(), style = atkinson(14), color = c.tinteGedaempft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LIcon(Lucide.ChevronRight, 22.dp, c.tinteGedaempft)
        }
        if (!asNeeded) StockBar(m, intakes, today)
    }
}

/** Vorratsbalken mit „5 von 28 übrig“ und „reicht bis Do, 15. Okt.“. */
@Composable
private fun StockBar(m: MedWithDoses, intakes: List<Intake>, today: LocalDate) {
    val c = Pilunia.colors
    val med = m.med
    val fc = forecast(m, intakes, today)
    val stock = med.stock.coerceAtLeast(0)
    val low = stock == 0 || fc.isLow(med.warnDays)
    val pack = med.packSize.coerceAtLeast(1)
    // Mehr als eine Packung im Vorrat: Bezug auf volle Packungen („40 von 56“).
    val total = if (stock <= pack) pack else ((stock + pack - 1) / pack) * pack
    val fraction = (stock.toFloat() / total).coerceIn(0f, 1f)
    val track = if (low) c.warnungFlaeche else c.lavendelToenungKraeftig
    val fill = if (low) c.warnungKraeftig else c.lavendel
    val reach = when {
        fc.lastCoveredDay != null ->
            if (fc.lastCoveredDay < today) "Vorrat aufgebraucht" else "reicht bis ${Fmt.dayShortWithYear(fc.lastCoveredDay)}"
        med.endDate != null -> "reicht bis zum Ende"
        m.doses.isEmpty() -> ""
        else -> "reicht über ein Jahr"
    }
    Column(
        Modifier.fillMaxWidth().padding(end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
            if (stock > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .size(width = (maxWidth * fraction).coerceAtLeast(8.dp), height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(fill)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$stock von $total übrig", style = atkinson(14, 700),
                color = if (low) c.warnung else c.tinte, maxLines = 1,
            )
            Text(
                reach, style = atkinson(14), color = if (low) c.warnung else c.tinteGedaempft,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
            )
        }
    }
}

/** Schwebender Button „Hinzufügen“: 56 hoch, Radius 20, zweistufiger Schatten. */
@Composable
private fun Fab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Pilunia.colors
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(56.dp)
            .softShadow(
                listOf(
                    ShadowLayer(2.dp, 4.dp, FixedColors.Schatten.copy(alpha = 0x26 / 255f)),
                    ShadowLayer(10.dp, 24.dp, FixedColors.Schatten.copy(alpha = 0x40 / 255f)),
                ),
                20.dp,
            )
            .pClickable(shape, onClick = onClick)
            .background(c.primaer)
            .padding(start = 16.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LIcon(Lucide.Plus, 22.dp, FixedColors.Weiss, strokeWidth = 2.5f)
        Text("Hinzufügen", style = atkinson(16, 700), color = FixedColors.Weiss, maxLines = 1)
    }
}
