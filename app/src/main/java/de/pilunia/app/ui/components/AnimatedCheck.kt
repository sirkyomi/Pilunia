package de.pilunia.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.pilunia.app.ui.theme.Pilunia
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Abhak-Kreis mit Animation beim Bestätigen: Der Kreis füllt sich federnd mit Mint,
 * der Haken zeichnet sich von links nach rechts ein und ein Ring pulsiert nach außen.
 * Beim Zurücknehmen läuft es kurz rückwärts. Bereits beim ersten Anzeigen bestätigte
 * Kreise erscheinen ohne Animation.
 *
 * Aussehen wie im Design: aus = 2er Rand „Feldrand“, an = „Mint“ mit Haken „Erfolg“ (Strich 3).
 */
@Composable
fun AnimatedCheckCircle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    iconSize: Dp = 16.dp,
    fillColor: Color = Pilunia.colors.mint,
    checkColor: Color = Pilunia.colors.erfolg,
    outlineColor: Color = Pilunia.colors.feldrand,
    contentDescription: String? = null,
    haptic: Boolean = true,
) {
    val view = LocalView.current
    val fill = remember { Animatable(if (checked) 1f else 0f) }
    val draw = remember { Animatable(if (checked) 1f else 0f) }
    val pulse = remember { Animatable(0f) }

    LaunchedEffect(checked) {
        if (checked) {
            if (fill.value >= 1f && draw.value >= 1f) return@LaunchedEffect
            if (haptic) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
                )
            }
            coroutineScope {
                launch { fill.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)) }
                launch {
                    delay(110)
                    draw.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                }
                launch {
                    pulse.snapTo(0f)
                    delay(60)
                    pulse.animateTo(1f, tween(560, easing = LinearOutSlowInEasing))
                    pulse.snapTo(0f)
                }
            }
        } else {
            pulse.snapTo(0f)
            coroutineScope {
                launch { draw.animateTo(0f, tween(120)) }
                launch { fill.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
            }
        }
    }

    val semantics = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    Canvas(modifier.size(size).then(semantics)) {
        val r = this.size.minDimension / 2f
        val c = center
        val f = fill.value

        // Ring, der beim Bestätigen nach außen läuft und verblasst.
        val p = pulse.value
        if (p > 0f && p < 1f) {
            drawCircle(
                color = fillColor.copy(alpha = (1f - p) * 0.9f),
                radius = r * (1f + 0.55f * p),
                center = c,
                style = Stroke(width = 2.dp.toPx() * (1f - p) + 0.5f),
            )
        }

        // Offener Kreis (2er Rand), blendet aus, während sich die Fläche füllt.
        val outlineAlpha = (1f - f).coerceIn(0f, 1f)
        if (outlineAlpha > 0f) {
            val w = 2.dp.toPx()
            drawCircle(outlineColor.copy(alpha = outlineColor.alpha * outlineAlpha), radius = r - w / 2f, center = c, style = Stroke(w))
        }

        // Gefüllter Kreis (federt leicht über die Endgröße hinaus).
        if (f > 0f) drawCircle(fillColor, radius = r * f.coerceAtMost(1.18f), center = c)

        // Haken nach Lucide „check“ (M20 6 9 17l-5-5), hier von links nach rechts gezeichnet.
        val d = draw.value
        if (d > 0f) {
            val s = iconSize.toPx() / 24f
            val o = Offset(c.x - 12f * s, c.y - 12f * s)
            val path = Path().apply {
                moveTo(o.x + 4f * s, o.y + 12f * s)
                lineTo(o.x + 9f * s, o.y + 17f * s)
                lineTo(o.x + 20f * s, o.y + 6f * s)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * d.coerceIn(0f, 1f), partial, true)
            drawPath(partial, checkColor, style = Stroke(width = 3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
