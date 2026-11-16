package com.openbite.android.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// 浅色：逐个取自设计稿 :root 变量（种子色 #006a64）。
internal val LightColors = lightColorScheme(
    primary = Color(0xFF006A64),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9DF2E9),
    onPrimaryContainer = Color(0xFF00504B),
    inversePrimary = Color(0xFF81D5CD),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF324B49),
    tertiary = Color(0xFF48617A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFE5FF),
    onTertiaryContainer = Color(0xFF304962),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF4FBF9),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    surfaceTint = Color(0xFF006A64),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F0),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF4FBF9),
    surfaceDim = Color(0xFFD5DBD9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F3),
    surfaceContainer = Color(0xFFE9EFED),
    surfaceContainerHigh = Color(0xFFE3E9E8),
    surfaceContainerHighest = Color(0xFFDDE4E2),
)

// 深色：设计稿没有给，按同一种子色的 Material 3 色调表推出（primary 80 / container 30 等）。
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF81D5CD),
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504B),
    onPrimaryContainer = Color(0xFF9DF2E9),
    inversePrimary = Color(0xFF006A64),
    secondary = Color(0xFFB0CCC8),
    onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF324B49),
    onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFAFC9E7),
    onTertiary = Color(0xFF18324A),
    tertiaryContainer = Color(0xFF304962),
    onTertiaryContainer = Color(0xFFCFE5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E2),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E2),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    surfaceTint = Color(0xFF81D5CD),
    inverseSurface = Color(0xFFDDE4E2),
    inverseOnSurface = Color(0xFF2B3231),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3F4947),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF343B3A),
    surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1C),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    surfaceContainerHighest = Color(0xFF2F3635),
)

/** Material 3 的 fixed 色，浅色和深色主题下相同。设计稿的“茶”头像用它。 */
object FixedColors {
    val primaryFixedDim = Color(0xFF81D5CD)
    val onPrimaryFixed = Color(0xFF00201E)
}
