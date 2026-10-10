package de.pilunia.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import de.pilunia.app.R
import de.pilunia.app.data.Backup
import de.pilunia.app.ui.components.LIcon
import de.pilunia.app.ui.components.NavBarSpace
import de.pilunia.app.ui.components.PiluniaImage
import de.pilunia.app.ui.components.PrimaryButton
import de.pilunia.app.ui.components.StatusBarSpace
import de.pilunia.app.ui.components.TextButtonP
import de.pilunia.app.ui.components.screenBackground
import de.pilunia.app.ui.nav.Routes
import de.pilunia.app.ui.theme.Pilunia
import de.pilunia.app.ui.theme.atkinson
import de.pilunia.app.ui.theme.outfit
import kotlinx.coroutines.launch

/** 01 Splash / Willkommen: Wortmarke, Pilunia mit Herz, „Los geht’s“ und „Backup wiederherstellen“. */
@Composable
fun WelcomeScreen(nav: NavController) {
    val c = Pilunia.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoring = true
        error = null
        scope.launch {
            val result = Backup.restore(context, uri)
            restoring = false
            result.onSuccess {
                nav.navigate(Routes.main()) { popUpTo(0) }
            }.onFailure {
                error = "Diese Datei konnte nicht gelesen werden. Bitte wähle eine Pilunia-Sicherung (.json)."
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .screenBackground(c, fadeEnd = 0.55f)
    ) {
        StatusBarSpace()
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Marke
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("pilunia", style = outfit(64, 800, lineHeight = 1.1f, letterSpacing = -1.5f), color = c.pflaume)
                    WordmarkHeart(Modifier.size(22.dp, 20.dp))
                }
                Text(
                    "Deine Medikamente.\nLiebevoll im Blick.",
                    style = atkinson(18, lineHeight = 1.4f),
                    color = c.tinteGedaempft,
                    textAlign = TextAlign.Center,
                )
            }

            // Bild: Pilunia mit Herz (340 × 354, schrumpft auf kleinen Bildschirmen)
            Box(
                Modifier
                    .padding(vertical = 16.dp)
                    .weight(1f, fill = false)
                    .sizeIn(maxWidth = 340.dp, maxHeight = 354.dp)
                    .aspectRatio(340f / 354f),
            ) {
                HeartHalo()
                PiluniaImage(R.drawable.pilunia_herz, "Pilunia umarmt ein Herz", Modifier.fillMaxSize())
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(
                    visible = error != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    ErrorNote(error.orEmpty(), Modifier.padding(bottom = 16.dp))
                }
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PrimaryButton("Los geht’s", onClick = { nav.navigate(Routes.ONBOARDING) }, enabled = !restoring)
                    TextButtonP(
                        if (restoring) "Wird wiederhergestellt …" else "Backup wiederherstellen",
                        onClick = { if (!restoring) picker.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.akzentText,
                    )
                }
            }
        }
        NavBarSpace()
    }
}

/** Weicher Hof hinter der Figur: Kreis 300 bei (20, 44) im 340 × 354-Rahmen, radialer Verlauf. */
@Composable
private fun HeartHalo() {
    val c = Pilunia.colors
    val inner = if (c.isDark) c.lavendelToenungKraeftig else Color(0xFFE4D9FD)
    Canvas(Modifier.fillMaxSize()) {
        val s = size.width / 340f
        val r = 150f * s
        val center = Offset((20f + 150f) * s, (44f + 150f) * s)
        // Wie in der Leinwand gerendert: Verlaufsmitte am linken Rand des Kreises, Radius = Durchmesser.
        val from = Offset(20f * s, (44f + 150f) * s)
        drawCircle(
            Brush.radialGradient(listOf(inner, c.hintergrund.copy(alpha = 0f)), center = from, radius = 2 * r),
            radius = r, center = center,
        )
    }
}

/** Herz der Wortmarke (Pfad aus dem Design, #F48FAA). */
@Composable
private fun WordmarkHeart(modifier: Modifier) {
    Canvas(modifier) {
        // Pfadpunkte liegen im Bereich x 2..22, y 3..21 → auf die Größe abbilden.
        val sx = size.width / 20f
        val sy = size.height / 18f
        fun x(v: Float) = (v - 2f) * sx
        fun y(v: Float) = (v - 3f) * sy
        val p = Path().apply {
            moveTo(x(12f), y(21f))
            cubicTo(x(6.5f), y(16f), x(2f), y(12.5f), x(2f), y(8.5f))
            cubicTo(x(2f), y(5.4f), x(4.4f), y(3f), x(7.5f), y(3f))
            cubicTo(x(9.2f), y(3f), x(11f), y(4f), x(12f), y(5.5f))
            cubicTo(x(13f), y(4f), x(14.8f), y(3f), x(16.5f), y(3f))
            cubicTo(x(19.6f), y(3f), x(22f), y(5.4f), x(22f), y(8.5f))
            cubicTo(x(22f), y(12.5f), x(17.5f), y(16f), x(12f), y(21f))
            close()
        }
        drawPath(p, Color(0xFFF48FAA))
    }
}

/** Fehlermeldung im Stil der Warnkarten („Warnung Fläche“, Symbol und Text „Warnung“). */
@Composable
internal fun ErrorNote(text: String, modifier: Modifier = Modifier) {
    val c = Pilunia.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.warnungFlaeche)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.flaeche), contentAlignment = Alignment.Center) {
            LIcon(Lucide.TriangleAlert, 20.dp, c.warnung)
        }
        Text(text, style = atkinson(15, 700, lineHeight = 1.35f), color = c.warnung, modifier = Modifier.weight(1f))
    }
}
