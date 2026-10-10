package de.pilunia.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Farbvariablen aus der Leinwand-Sammlung „Pilunia“ (Modi Hell/Dunkel).
 * Die Namen entsprechen den Variablennamen im Design.
 */
@Immutable
data class PiluniaColors(
    val isDark: Boolean,
    val hintergrund: Color,
    val flaeche: Color,
    val lavendelToenung: Color,
    val lavendel: Color,
    val primaer: Color,
    val pflaume: Color,
    val tinte: Color,
    val tinteGedaempft: Color,
    val linie: Color,
    val rose: Color,
    val warnungFlaeche: Color,
    val warnung: Color,
    val mint: Color,
    val erfolg: Color,
    val grau: Color,
    val verlaufLavendel: Color,
    val lavendelToenungKraeftig: Color,
    val lavendelInaktiv: Color,
    val akzentText: Color,
    val feldrand: Color,
    val warnungGedaempft: Color,
    val warnungKraeftig: Color,
    val mintFlaeche: Color,
    val gelbFlaeche: Color,
    val gelb: Color,
    val blauFlaeche: Color,
    val blau: Color,
    val auswahl: Color,
)

val HellColors = PiluniaColors(
    isDark = false,
    hintergrund = Color(0xFFFFF7F4),
    flaeche = Color(0xFFFFFFFF),
    lavendelToenung = Color(0xFFF3EEFE),
    lavendel = Color(0xFFA78BFA),
    primaer = Color(0xFF7A5CC7),
    pflaume = Color(0xFF6B4C93),
    tinte = Color(0xFF2E2440),
    tinteGedaempft = Color(0xFF5F5676),
    linie = Color(0xFFF0EAF6),
    rose = Color(0xFFF9B8C8),
    warnungFlaeche = Color(0xFFFDE6EC),
    warnung = Color(0xFFB4234A),
    mint = Color(0xFFA7E3C1),
    erfolg = Color(0xFF1F7A4D),
    grau = Color(0xFF94A3B8),
    verlaufLavendel = Color(0xFFEFE7FE),
    lavendelToenungKraeftig = Color(0xFFEDE5FE),
    lavendelInaktiv = Color(0xFFDCD0F7),
    akzentText = Color(0xFF5B3FA8),
    feldrand = Color(0xFF8F80B0),
    warnungGedaempft = Color(0xFFF6D3DC),
    warnungKraeftig = Color(0xFFE0567A),
    mintFlaeche = Color(0xFFDDF4E7),
    gelbFlaeche = Color(0xFFFFF1D6),
    gelb = Color(0xFFB7791F),
    blauFlaeche = Color(0xFFE6EEF7),
    blau = Color(0xFF4A6280),
    auswahl = Color(0xFF7A5CC7),
)

val DunkelColors = PiluniaColors(
    isDark = true,
    hintergrund = Color(0xFF17121F),
    flaeche = Color(0xFF241D30),
    lavendelToenung = Color(0xFF2F2642),
    lavendel = Color(0xFFA78BFA),
    primaer = Color(0xFF7A5CC7),
    pflaume = Color(0xFFCDB8FF),
    tinte = Color(0xFFF3EEF9),
    tinteGedaempft = Color(0xFFB3A9C7),
    linie = Color(0xFF362D45),
    rose = Color(0xFFF9B8C8),
    warnungFlaeche = Color(0xFF3D2130),
    warnung = Color(0xFFFF8FAB),
    mint = Color(0xFF2F5E46),
    erfolg = Color(0xFF8EE0B4),
    grau = Color(0xFF94A3B8),
    verlaufLavendel = Color(0xFF251C38),
    lavendelToenungKraeftig = Color(0xFF3A2E55),
    lavendelInaktiv = Color(0xFF3E3358),
    akzentText = Color(0xFFC9B6FF),
    feldrand = Color(0xFF7C6E99),
    warnungGedaempft = Color(0xFF6A3449),
    warnungKraeftig = Color(0xFFF06C8E),
    mintFlaeche = Color(0xFF1E3A2D),
    gelbFlaeche = Color(0xFF3A2E17),
    gelb = Color(0xFFF0C26B),
    blauFlaeche = Color(0xFF1F2A38),
    blau = Color(0xFF9DB8D9),
    auswahl = Color(0xFFB9A2FB),
)

/** Feste Farben, die im Design nicht an Variablen gebunden sind. */
object FixedColors {
    val Weiss = Color(0xFFFFFFFF)
    val PunktGenommen = Color(0xFF3FA672)
    val HeroVerlaufEnde = Color(0xFF6A4FB5)
    /** Schatten-Grundfarbe (Pflaume) für die weichen Kartenschatten. */
    val Schatten = Color(0xFF6B4C93)
}

val LocalPiluniaColors = staticCompositionLocalOf { HellColors }
