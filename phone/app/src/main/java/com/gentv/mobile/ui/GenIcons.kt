package com.gentv.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Original 24-unit outline family. Shared optical bounds, 1.5-unit strokes and rounded ends. */
object GenIcons {
    private fun outline(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path).toNodes(),
                    fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Back = outline("Back", "M14.5 5.5 L8 12 L14.5 18.5", "M8 12 H21")
    val Remote = outline("Remote",
        "M9 2.5 H15 Q17.5 2.5 17.5 5 V19 Q17.5 21.5 15 21.5 H9 Q6.5 21.5 6.5 19 V5 Q6.5 2.5 9 2.5 Z",
        "M10.25 6 H13.75", "M12 9 A3 3 0 1 1 12 15 A3 3 0 1 1 12 9 Z",
        "M10 18 H10.05 M14 18 H14.05")
    val Send = outline("Send files",
        "M4 8.5 V18 Q4 20 6 20 H18 Q20 20 20 18 V8.5",
        "M12 15 V3 M7.5 7.5 L12 3 L16.5 7.5")
    val Apps = outline("Apps",
        "M5 3.5 H8 Q9.5 3.5 9.5 5 V8 Q9.5 9.5 8 9.5 H5 Q3.5 9.5 3.5 8 V5 Q3.5 3.5 5 3.5 Z",
        "M16 3.5 H19 Q20.5 3.5 20.5 5 V8 Q20.5 9.5 19 9.5 H16 Q14.5 9.5 14.5 8 V5 Q14.5 3.5 16 3.5 Z",
        "M5 14.5 H8 Q9.5 14.5 9.5 16 V19 Q9.5 20.5 8 20.5 H5 Q3.5 20.5 3.5 19 V16 Q3.5 14.5 5 14.5 Z",
        "M16 14.5 H19 Q20.5 14.5 20.5 16 V19 Q20.5 20.5 19 20.5 H16 Q14.5 20.5 14.5 19 V16 Q14.5 14.5 16 14.5 Z")
    val Clean = outline("Clean up",
        "M12 3 L13.8 8.2 L19 10 L13.8 11.8 L12 17 L10.2 11.8 L5 10 L10.2 8.2 Z",
        "M19 15 V21 M16 18 H22", "M4 3 V6 M2.5 4.5 H5.5")
    val Home = outline("Home screen",
        "M3 10.5 L12 3 L21 10.5", "M5.5 8.5 V19 Q5.5 20.5 7 20.5 H17 Q18.5 20.5 18.5 19 V8.5",
        "M9.5 20.5 V14 H14.5 V20.5")
    val Check = outline("Check my TV",
        "M20 10.5 V17 Q20 19 18 19 H6 Q4 19 4 17 V6 Q4 4 6 4 H14",
        "M10 10.5 L13 13.5 L21 5.5", "M9 22 H15 M12 19 V22")
    val Storage = outline("Free up space",
        "M6 4 H18 Q20 4 20 6 V18 Q20 20 18 20 H6 Q4 20 4 18 V6 Q4 4 6 4 Z",
        "M4 14.5 H20", "M7.5 17.5 H7.55 M11 17.5 H11.05", "M8 8 H16")
    val Shield = outline("Block ads",
        "M12 2.5 Q16 5 20 5 V11 Q20 17.5 12 21.5 Q4 17.5 4 11 V5 Q8 5 12 2.5 Z",
        "M9 12 H15")
    val Restore = outline("Restore",
        "M4 8 A8.5 8.5 0 1 1 3.5 15.5", "M4 3 V8.5 H9.5",
        "M12 7 V12 L15.5 14")
}
