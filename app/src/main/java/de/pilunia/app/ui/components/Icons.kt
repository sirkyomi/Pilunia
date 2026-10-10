package de.pilunia.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lucide-Symbol wie in der Leinwand: Größe in dp, Farbe und Strichstärke (Standard 2).
 * Beispiel: `LIcon(Lucide.Pill, 22.dp, colors.pflaume)`.
 */
@Composable
fun LIcon(
    icon: ImageVector,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    contentDescription: String? = null,
) {
    val vector = remember(icon, strokeWidth) { if (strokeWidth == 2f) icon else icon.withStroke(strokeWidth) }
    Icon(vector, contentDescription, modifier.size(size), tint = color)
}

private val strokeCache = HashMap<Pair<String, Float>, ImageVector>()

/** Kopie eines Lucide-Vektors mit anderer Strichstärke (Lucide nutzt 2 auf 24er Raster). */
fun ImageVector.withStroke(width: Float): ImageVector = strokeCache.getOrPut(name to width) {
    val b = ImageVector.Builder(
        name = "$name-$width", defaultWidth = defaultWidth, defaultHeight = defaultHeight,
        viewportWidth = viewportWidth, viewportHeight = viewportHeight,
    )
    // Pfade und Gruppen rekursiv übernehmen, nur die Strichstärke ändert sich.
    fun ImageVector.Builder.copy(g: VectorGroup) {
        for (n: VectorNode in g) when (n) {
            is VectorPath -> addPath(
                pathData = n.pathData, pathFillType = n.pathFillType, name = n.name,
                fill = n.fill, fillAlpha = n.fillAlpha, stroke = n.stroke, strokeAlpha = n.strokeAlpha,
                strokeLineWidth = if (n.stroke != null) width else n.strokeLineWidth,
                strokeLineCap = n.strokeLineCap, strokeLineJoin = n.strokeLineJoin, strokeLineMiter = n.strokeLineMiter,
            )
            is VectorGroup -> {
                addGroup(
                    name = n.name, rotate = n.rotation, pivotX = n.pivotX, pivotY = n.pivotY,
                    scaleX = n.scaleX, scaleY = n.scaleY, translationX = n.translationX,
                    translationY = n.translationY, clipPathData = n.clipPathData,
                )
                copy(n)
                clearGroup()
            }
        }
    }
    b.copy(root)
    b.build()
}

/** Standardgrößen aus dem Design. */
object IconSize {
    val Tab = 22.dp
    val Row = 22.dp
    val Button = 20.dp
}
