package de.pilunia.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import de.pilunia.app.data.AppearanceMode

object Pilunia {
    val colors: PiluniaColors
        @Composable @ReadOnlyComposable get() = LocalPiluniaColors.current
}

@Composable
fun isDarkMode(mode: AppearanceMode): Boolean = when (mode) {
    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
}

/**
 * @param largeText „Schrift groß“ aus dem Erscheinungsbild: vergrößert alle Texte um 12 %.
 */
@Composable
fun PiluniaTheme(
    dark: Boolean = isSystemInDarkTheme(),
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DunkelColors else HellColors
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = colors.primaer, onPrimary = FixedColors.Weiss,
        background = colors.hintergrund, onBackground = colors.tinte,
        surface = colors.flaeche, onSurface = colors.tinte,
        surfaceVariant = colors.lavendelToenung, onSurfaceVariant = colors.tinteGedaempft,
        surfaceContainerHigh = colors.flaeche, surfaceContainer = colors.flaeche,
        outline = colors.feldrand, error = colors.warnung,
    )
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.12f) else density
    CompositionLocalProvider(LocalPiluniaColors provides colors, LocalDensity provides scaled) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
