package com.openbite.android.core.designsystem.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 设计稿里的 Material 3 Expressive 装饰形状（soft-burst、cookie、clover、heart）。
 * Compose Material3 1.4 稳定版还没有 MaterialShapes，这里用极坐标波浪近似。
 */
class ScallopShape(private val lobes: Int, private val depth: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = min(cx, cy)
        val path = Path()
        val steps = lobes * 24
        for (i in 0..steps) {
            val theta = 2 * PI * i / steps - PI / 2
            val r = radius * (1 - depth * (1 - cos(lobes * (theta + PI / 2))) / 2)
            val x = cx + (r * cos(theta)).toFloat()
            val y = cy + (r * sin(theta)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

object HeartShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0.5f * w, 0.92f * h)
            cubicTo(0.18f * w, 0.70f * h, 0.02f * w, 0.50f * h, 0.02f * w, 0.32f * h)
            cubicTo(0.02f * w, 0.14f * h, 0.15f * w, 0.04f * h, 0.29f * w, 0.04f * h)
            cubicTo(0.39f * w, 0.04f * h, 0.46f * w, 0.10f * h, 0.5f * w, 0.18f * h)
            cubicTo(0.54f * w, 0.10f * h, 0.61f * w, 0.04f * h, 0.71f * w, 0.04f * h)
            cubicTo(0.85f * w, 0.04f * h, 0.98f * w, 0.14f * h, 0.98f * w, 0.32f * h)
            cubicTo(0.98f * w, 0.50f * h, 0.82f * w, 0.70f * h, 0.5f * w, 0.92f * h)
            close()
        }
        return Outline.Generic(path)
    }
}

object DecorativeShapes {
    val SoftBurst: Shape = ScallopShape(lobes = 10, depth = 0.16f)
    val Cookie7: Shape = ScallopShape(lobes = 7, depth = 0.14f)
    val Clover4: Shape = ScallopShape(lobes = 4, depth = 0.32f)
    val Heart: Shape = HeartShape
}
