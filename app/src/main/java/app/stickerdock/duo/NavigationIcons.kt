package app.stickerdock.duo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Consistent code-native icons; labels are supplied by the navigation destination. */
@Composable
fun StickerDockNavIcon(label: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (label) {
            "Library" -> for (x in listOf(3f, 13f)) for (y in listOf(3f, 13f)) {
                drawRoundRect(color, Offset(x * u, y * u), Size(8f * u, 8f * u), CornerRadius(1.5f * u), style = stroke)
            }
            "Create" -> {
                drawRoundRect(color, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), CornerRadius(4f * u), style = stroke)
                drawLine(color, Offset(12f * u, 7f * u), Offset(12f * u, 17f * u), strokeWidth = 1.8f * u)
                drawLine(color, Offset(7f * u, 12f * u), Offset(17f * u, 12f * u), strokeWidth = 1.8f * u)
            }
            "Import" -> {
                val path = Path().apply {
                    moveTo(12f * u, 3f * u); lineTo(12f * u, 15f * u)
                    moveTo(7f * u, 10f * u); lineTo(12f * u, 15f * u); lineTo(17f * u, 10f * u)
                    moveTo(4f * u, 16f * u); lineTo(4f * u, 21f * u); lineTo(20f * u, 21f * u); lineTo(20f * u, 16f * u)
                }
                drawPath(path, color, style = stroke)
            }
            else -> {
                drawCircle(color, 9f * u, style = stroke)
                val needle = Path().apply {
                    moveTo(16f * u, 8f * u); lineTo(14f * u, 14f * u)
                    lineTo(8f * u, 16f * u); lineTo(10f * u, 10f * u); close()
                }
                drawPath(needle, color, style = stroke)
            }
        }
    }
}
