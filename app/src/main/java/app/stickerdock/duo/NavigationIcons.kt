package app.stickerdock.duo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** High-craft, code-native vector icons with filled and stroke variants. */
@Composable
fun StickerDockNavIcon(label: String, customColor: Color? = null) {
    val color = customColor ?: LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(2.0f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (label) {
            "Favorite", "FavoriteFilled" -> {
                val heart = Path().apply {
                    moveTo(12f * u, 21f * u)
                    cubicTo(8.5f * u, 17.5f * u, 2f * u, 12.5f * u, 2f * u, 7.8f * u)
                    cubicTo(2f * u, 3.5f * u, 5.5f * u, 2f * u, 9f * u, 3.5f * u)
                    cubicTo(10.5f * u, 4.2f * u, 11.5f * u, 5.2f * u, 12f * u, 6.2f * u)
                    cubicTo(12.5f * u, 5.2f * u, 13.5f * u, 4.2f * u, 15f * u, 3.5f * u)
                    cubicTo(18.5f * u, 2f * u, 22f * u, 3.5f * u, 22f * u, 7.8f * u)
                    cubicTo(22f * u, 12.5f * u, 15.5f * u, 17.5f * u, 12f * u, 21f * u)
                    close()
                }
                if (label == "FavoriteFilled") drawPath(heart, color, style = Fill) else drawPath(heart, color, style = stroke)
            }
            "Search" -> {
                drawCircle(color, 6f * u, Offset(10f * u, 10f * u), style = stroke)
                drawLine(color, Offset(14.8f * u, 14.8f * u), Offset(20.5f * u, 20.5f * u), 2.2f * u, StrokeCap.Round)
            }
            "Close" -> {
                drawLine(color, Offset(6f * u, 6f * u), Offset(18f * u, 18f * u), 2.0f * u, StrokeCap.Round)
                drawLine(color, Offset(6f * u, 18f * u), Offset(18f * u, 6f * u), 2.0f * u, StrokeCap.Round)
            }
            "More" -> for (x in listOf(5f, 12f, 19f)) drawCircle(color, 1.8f * u, Offset(x * u, 12f * u))
            "Forward", "Back" -> {
                val path = Path().apply {
                    val sign = if (label == "Back") -1f else 1f
                    moveTo((12f - 3.5f * sign) * u, 5.5f * u)
                    lineTo((12f + 3.5f * sign) * u, 12f * u)
                    lineTo((12f - 3.5f * sign) * u, 18.5f * u)
                }
                drawPath(path, color, style = stroke)
            }
            "Photo" -> {
                drawRoundRect(color, Offset(2.5f * u, 2.5f * u), Size(19f * u, 19f * u), CornerRadius(4f * u), style = stroke)
                drawCircle(color, 2.2f * u, Offset(8f * u, 8f * u))
                val path = Path().apply {
                    moveTo(3.5f * u, 18f * u)
                    lineTo(9.5f * u, 12f * u)
                    lineTo(13.5f * u, 16f * u)
                    lineTo(17.5f * u, 11f * u)
                    lineTo(21f * u, 15f * u)
                }
                drawPath(path, color, style = stroke)
            }
            "Video" -> {
                drawRoundRect(color, Offset(2.5f * u, 2.5f * u), Size(19f * u, 19f * u), CornerRadius(5f * u), style = stroke)
                drawPath(Path().apply {
                    moveTo(10f * u, 7.5f * u)
                    lineTo(16.5f * u, 12f * u)
                    lineTo(10f * u, 16.5f * u)
                    close()
                }, color)
            }
            "Cloud" -> {
                drawPath(Path().apply {
                    moveTo(6f * u, 18f * u)
                    cubicTo(0f * u, 17f * u, 1.5f * u, 8.5f * u, 7.5f * u, 9.5f * u)
                    cubicTo(8f * u, 2.5f * u, 18f * u, 3f * u, 17.5f * u, 10.5f * u)
                    cubicTo(23.5f * u, 10.5f * u, 24f * u, 18f * u, 18f * u, 18f * u)
                    close()
                }, color, style = stroke)
            }
            "Library", "LibraryFilled" -> {
                // Layered sticker cards
                drawRoundRect(color, Offset(6f * u, 2.5f * u), Size(15f * u, 15f * u), CornerRadius(3f * u), style = stroke)
                if (label == "LibraryFilled") {
                    drawRoundRect(color, Offset(3f * u, 6.5f * u), Size(15f * u, 15f * u), CornerRadius(3f * u), style = Fill)
                } else {
                    drawRoundRect(color, Offset(3f * u, 6.5f * u), Size(15f * u, 15f * u), CornerRadius(3f * u), style = stroke)
                }
            }
            "Create", "CreateFilled", "Studio" -> {
                drawRoundRect(color, Offset(2.5f * u, 2.5f * u), Size(19f * u, 19f * u), CornerRadius(5f * u), style = stroke)
                drawLine(color, Offset(12f * u, 6.5f * u), Offset(12f * u, 17.5f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
                drawLine(color, Offset(6.5f * u, 12f * u), Offset(17.5f * u, 12f * u), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            }
            "Discover", "DiscoverFilled", "Sparkles", "AI" -> {
                // 4-pointed radiant sparkle star
                val star = Path().apply {
                    moveTo(12f * u, 2f * u)
                    cubicTo(12.5f * u, 7f * u, 16.5f * u, 11f * u, 21.5f * u, 11.5f * u)
                    cubicTo(16.5f * u, 12f * u, 12.5f * u, 16f * u, 12f * u, 21f * u)
                    cubicTo(11.5f * u, 16f * u, 7.5f * u, 12f * u, 2.5f * u, 11.5f * u)
                    cubicTo(7.5f * u, 11f * u, 11.5f * u, 7f * u, 12f * u, 2f * u)
                    close()
                }
                drawPath(star, color, style = if (label.contains("Filled")) Fill else stroke)
                // Small secondary sparkle in corner
                val miniStar = Path().apply {
                    moveTo(19f * u, 2.5f * u)
                    lineTo(20f * u, 5f * u)
                    lineTo(22.5f * u, 6f * u)
                    lineTo(20f * u, 7f * u)
                    lineTo(19f * u, 9.5f * u)
                    lineTo(18f * u, 7f * u)
                    lineTo(15.5f * u, 6f * u)
                    lineTo(18f * u, 5f * u)
                    close()
                }
                drawPath(miniStar, color, style = Fill)
            }
            "Settings", "SettingsFilled" -> {
                // Modern gear cog
                drawCircle(color, 4f * u, Offset(12f * u, 12f * u), style = stroke)
                for (angle in 0 until 6) {
                    val rad = Math.toRadians((angle * 60).toDouble())
                    val x1 = (12 + 6.5 * Math.cos(rad)).toFloat() * u
                    val y1 = (12 + 6.5 * Math.sin(rad)).toFloat() * u
                    val x2 = (12 + 9.5 * Math.cos(rad)).toFloat() * u
                    val y2 = (12 + 9.5 * Math.sin(rad)).toFloat() * u
                    drawLine(color, Offset(x1, y1), Offset(x2, y2), 2.4f * u, StrokeCap.Round)
                }
            }
            "WhatsApp", "WhatsAppFilled" -> {
                val bubble = Path().apply {
                    moveTo(12f * u, 2.5f * u)
                    cubicTo(6.75f * u, 2.5f * u, 2.5f * u, 6.75f * u, 2.5f * u, 12f * u)
                    cubicTo(2.5f * u, 13.85f * u, 3.05f * u, 15.58f * u, 4f * u, 17.05f * u)
                    lineTo(2.8f * u, 21.2f * u)
                    lineTo(7.1f * u, 20.0f * u)
                    cubicTo(8.6f * u, 20.95f * u, 10.25f * u, 21.5f * u, 12f * u, 21.5f * u)
                    cubicTo(17.25f * u, 21.5f * u, 21.5f * u, 17.25f * u, 21.5f * u, 12f * u)
                    cubicTo(21.5f * u, 6.75f * u, 17.25f * u, 2.5f * u, 12f * u, 2.5f * u)
                    close()
                }
                if (label == "WhatsAppFilled") {
                    drawPath(bubble, color, style = Fill)
                    // Inner handset / check shape in white or inverse
                    val handset = Path().apply {
                        moveTo(8.5f * u, 8.5f * u)
                        cubicTo(9.5f * u, 8.5f * u, 10.2f * u, 10.2f * u, 10.6f * u, 11f * u)
                        cubicTo(11f * u, 11.5f * u, 11f * u, 12f * u, 10.5f * u, 12.5f * u)
                        cubicTo(11.2f * u, 13.8f * u, 12.2f * u, 14.8f * u, 13.5f * u, 15.5f * u)
                        cubicTo(14f * u, 15f * u, 14.5f * u, 15f * u, 15f * u, 15.4f * u)
                        cubicTo(15.8f * u, 15.8f * u, 17.5f * u, 16.5f * u, 17.5f * u, 17.5f * u)
                        cubicTo(17.5f * u, 18.5f * u, 16.5f * u, 19f * u, 15.5f * u, 19f * u)
                        cubicTo(11f * u, 19f * u, 7f * u, 14.5f * u, 7f * u, 10.5f * u)
                        cubicTo(7f * u, 9.5f * u, 7.5f * u, 8.5f * u, 8.5f * u, 8.5f * u)
                        close()
                    }
                    drawPath(handset, Color.White, style = Fill)
                } else {
                    drawPath(bubble, color, style = stroke)
                    drawCircle(color, 2f * u, Offset(12f * u, 12f * u))
                }
            }
            "Check", "Verified" -> {
                drawCircle(color, 9f * u, Offset(12f * u, 12f * u), style = stroke)
                val check = Path().apply {
                    moveTo(7.5f * u, 12.5f * u)
                    lineTo(10.5f * u, 15.5f * u)
                    lineTo(16.5f * u, 9f * u)
                }
                drawPath(check, color, style = stroke)
            }
            "Fire", "Trending" -> {
                val flame = Path().apply {
                    moveTo(12f * u, 2.5f * u)
                    cubicTo(11.5f * u, 6.5f * u, 8f * u, 8.5f * u, 7.5f * u, 12f * u)
                    cubicTo(7f * u, 15f * u, 8.5f * u, 17f * u, 10.5f * u, 17.5f * u)
                    cubicTo(9.5f * u, 16f * u, 9.5f * u, 14f * u, 10.5f * u, 13f * u)
                    cubicTo(11.5f * u, 12f * u, 13f * u, 12f * u, 13.5f * u, 10f * u)
                    cubicTo(15f * u, 12f * u, 16.5f * u, 13.5f * u, 16.5f * u, 16f * u)
                    cubicTo(16.5f * u, 19f * u, 14.5f * u, 21.5f * u, 12f * u, 21.5f * u)
                    cubicTo(6.5f * u, 21.5f * u, 3.5f * u, 17f * u, 4f * u, 12.5f * u)
                    cubicTo(4.5f * u, 8f * u, 8f * u, 4.5f * u, 12f * u, 2.5f * u)
                    close()
                }
                drawPath(flame, color, style = Fill)
            }
            "Import" -> {
                val path = Path().apply {
                    moveTo(12f * u, 3f * u); lineTo(12f * u, 15f * u)
                    moveTo(7f * u, 10f * u); lineTo(12f * u, 15f * u); lineTo(17f * u, 10f * u)
                    moveTo(4f * u, 16f * u); lineTo(4f * u, 21f * u); lineTo(20f * u, 21f * u); lineTo(20f * u, 16f * u)
                }
                drawPath(path, color, style = stroke)
            }
            "Grid" -> {
                for (x in listOf(3.5f, 13f)) for (y in listOf(3.5f, 13f)) {
                    drawRoundRect(color, Offset(x * u, y * u), Size(7.5f * u, 7.5f * u), CornerRadius(2f * u), style = stroke)
                }
            }
            "List" -> {
                for (y in listOf(5f, 12f, 19f)) {
                    drawRoundRect(color, Offset(3.5f * u, (y - 1.2f) * u), Size(17f * u, 2.4f * u), CornerRadius(1.2f * u))
                }
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
