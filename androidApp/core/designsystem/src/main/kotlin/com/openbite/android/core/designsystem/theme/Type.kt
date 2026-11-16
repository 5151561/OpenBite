package com.openbite.android.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 字号取自设计稿 --text-* 变量（与 Material 3 基线一致），字体用系统默认（中文走系统 CJK 字体）。
private fun style(weight: FontWeight, size: Int, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

internal val OpenBiteTypography = Typography(
    displayLarge = style(FontWeight.Normal, 57, 64, -0.25),
    displayMedium = style(FontWeight.Normal, 45, 52),
    displaySmall = style(FontWeight.Normal, 36, 44),
    headlineLarge = style(FontWeight.Normal, 32, 40),
    headlineMedium = style(FontWeight.Normal, 28, 36),
    headlineSmall = style(FontWeight.Normal, 24, 32),
    titleLarge = style(FontWeight.Normal, 22, 28),
    titleMedium = style(FontWeight.Medium, 16, 24, 0.15),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.5),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.25),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.4),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.5),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.5),
)

/** 设计稿的 *-emphasized 字号：同一档字号加粗一级（400→500，500→600）。 */
fun TextStyle.emphasized(): TextStyle = copy(
    fontWeight = if ((fontWeight ?: FontWeight.Normal) >= FontWeight.Medium) FontWeight.SemiBold else FontWeight.Medium,
)
