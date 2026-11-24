package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val KetikKuDarkScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = CyanOnAccent,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanOnContainer,
    secondary = MintSynced,
    onSecondary = SlateVoid,
    secondaryContainer = SlateCardElevated,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = AmberPending,
    background = SlateVoid,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateOutline,
    error = CoralAlert,
    onError = CoralOnAlert
)

private val KetikKuLightScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = LightSurface,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = Color(0xFF15803D),
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    error = Color(0xFFC62828),
    onError = LightSurface
)

val KetikKuShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KetikKuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> KetikKuDarkScheme
        else -> KetikKuLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KetikKuTypography,
        shapes = KetikKuShapes,
        content = content
    )
}
