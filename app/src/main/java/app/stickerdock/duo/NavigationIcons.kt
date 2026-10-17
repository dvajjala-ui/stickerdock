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
            "Search" -> {
                drawCircle(color, 6.5f * u, Offset(10.5f * u, 10.5f * u), style = stroke)
                drawLine(color, Offset(15.5f * u, 15.5f * u), Offset(21f * u, 21f * u), 1.8f * u, StrokeCap.Round)
            }
            "Close" -> {
                drawLine(color, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), 1.8f * u, StrokeCap.Round)
                drawLine(color, Offset(6f * u, 18f * u), Offset(18f * u, 6f * u), 1.8f * u, StrokeCap.Round)
            }
            "More" -> for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f * u, Offset(x * u, 12f * u))
            "Forward", "Back" -> {
                val path = Path().apply {
                    val sign = if (label == "Back") -1f else 1f
                    moveTo((12f - 3f * sign) * u, 6f * u)
                    lineTo((12f + 3f * sign) * u, 12f * u)
                    lineTo((12f - 3f * sign) * u, 18f * u)
                }
                drawPath(path, color, style = stroke)
            }
            "Photo" -> {
                drawRoundRect(color, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), CornerRadius(3f * u), style = stroke)
                drawCircle(color, 2f * u, Offset(8f * u, 8f * u))
                val path = Path().apply { moveTo(4f * u, 18f * u); lineTo(10f * u, 12f * u); lineTo(14f * u, 16f * u); lineTo(18f * u, 11f * u); lineTo(21f * u, 14f * u) }
                drawPath(path, color, style = stroke)
            }
            "Video" -> {
                drawRoundRect(color, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), CornerRadius(5f * u), style = stroke)
                drawPath(Path().apply { moveTo(10f * u, 8f * u); lineTo(16f * u, 12f * u); lineTo(10f * u, 16f * u); close() }, color)
            }
            "Cloud" -> {
                drawPath(Path().apply { moveTo(6f * u, 18f * u); cubicTo(-1f * u, 17f * u, 1f * u, 8f * u, 7f * u, 9f * u); cubicTo(7f * u, 1f * u, 19f * u, 2f * u, 18f * u, 10f * u); cubicTo(25f * u, 10f * u, 25f * u, 18f * u, 18f * u, 18f * u); close() }, color, style = stroke)
            }
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
