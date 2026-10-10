package de.pilunia.app.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import de.pilunia.app.ui.components.MainTab
import de.pilunia.app.ui.components.TabBar
import de.pilunia.app.ui.heute.HeuteScreen
import de.pilunia.app.ui.history.VerlaufScreen
import de.pilunia.app.ui.meds.MedsScreen
import de.pilunia.app.ui.more.MehrScreen

/** Höhe der Tab-Leiste ohne Systemleiste: 12 + 32 + 4 + 16 + 4 + 12. */
val TabBarHeight = 80.dp

/**
 * Rahmen mit den vier Tabs. Die Inhalte laufen hinter der Tab-Leiste weiter
 * (wie im Design) und erhalten dafür [PaddingValues] für den unteren Rand.
 */
@Composable
fun MainScreen(nav: NavController, initialTab: MainTab) {
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = TabBarHeight + navInset)

    BackHandler(enabled = tab != MainTab.HEUTE) { tab = MainTab.HEUTE }

    Box(Modifier.fillMaxSize()) {
        Crossfade(tab, animationSpec = tween(180), label = "tabs") { t ->
            when (t) {
                MainTab.HEUTE -> HeuteScreen(nav, contentPadding, onOpenTab = { tab = it })
                MainTab.MEDIKAMENTE -> MedsScreen(nav, contentPadding)
                MainTab.VERLAUF -> VerlaufScreen(nav, contentPadding)
                MainTab.MEHR -> MehrScreen(nav, contentPadding)
            }
        }
        TabBar(current = tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
