package de.pilunia.app.ui.theme

import android.content.res.AssetManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.graphics.Typeface as PlatformTypeface
import androidx.compose.ui.text.font.Typeface as ComposeTypeface

/**
 * Schriften aus den variablen Dateien in assets/fonts. Für jedes Gewicht wird eine eigene Typeface
 * mit fester „wght“-Achse gebaut und zwischengespeichert. (Mehrere `Font(resId, variationSettings)`
 * auf dieselbe Ressource teilen sich in Android einen Cache-Eintrag; das Gewicht sprang dadurch je
 * nach Start zwischen dünn und fett.)
 */
object Fonts {
    private lateinit var assets: AssetManager
    private val cache = HashMap<String, PlatformTypeface>()

    fun init(assetManager: AssetManager) {
        assets = assetManager
    }

    /** Plattform-Typeface, z. B. für die PDF-Ausgabe. */
    @Synchronized
    fun typeface(file: String, weight: Int): PlatformTypeface = cache.getOrPut("$file@$weight") {
        runCatching {
            PlatformTypeface.Builder(assets, "fonts/$file")
                .setFontVariationSettings("'wght' $weight")
                .setWeight(weight)
                .build()
        }.getOrElse { PlatformTypeface.create(PlatformTypeface.DEFAULT, weight, false) }
    }

    const val OUTFIT = "outfit.ttf"
    const val ATKINSON = "atkinson_next.ttf"
}

private val families = HashMap<String, FontFamily>()

private fun family(file: String, weight: Int): FontFamily = synchronized(families) {
    families.getOrPut("$file@$weight") { FontFamily(ComposeTypeface(Fonts.typeface(file, weight))) }
}

/** Outfit – Titel und Zahlen (Gewicht 700). */
val Outfit: FontFamily get() = family(Fonts.OUTFIT, 700)

/** Atkinson Hyperlegible Next – Fließtext und Beschriftungen (Gewicht 400). */
val Atkinson: FontFamily get() = family(Fonts.ATKINSON, 400)

private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/**
 * Textstile wie in der Leinwand: Größe in px = sp, Zeilenhöhe als Faktor (z. B. 1.15),
 * Zeichenabstand in px (z. B. -0.5).
 */
fun outfit(size: Number, weight: Int = 700, lineHeight: Float? = null, letterSpacing: Float = 0f) =
    style(family(Fonts.OUTFIT, weight), size.toFloat(), weight, lineHeight, letterSpacing)

fun atkinson(size: Number = 16, weight: Int = 400, lineHeight: Float? = null, letterSpacing: Float = 0f) =
    style(family(Fonts.ATKINSON, weight), size.toFloat(), weight, lineHeight, letterSpacing)

private fun style(family: FontFamily, size: Float, weight: Int, lineHeight: Float?, letterSpacing: Float): TextStyle =
    TextStyle(
        fontFamily = family,
        fontWeight = FontWeight(weight),
        fontSynthesis = FontSynthesis.None,
        fontSize = size.sp,
        lineHeight = if (lineHeight != null) lineHeight.em else TextUnit.Unspecified,
        letterSpacing = if (letterSpacing != 0f) (letterSpacing / size).em else TextUnit.Unspecified,
        lineHeightStyle = CenteredLineHeight,
    )
