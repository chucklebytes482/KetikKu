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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val KetikKuDarkScheme = darkColorScheme(
    primary = GoogleBlue200,
    onPrimary = GoogleBlue950,
    primaryContainer = GoogleBlue800,
    onPrimaryContainer = GoogleBlue100,
    secondary = GoogleGreen300,
    onSecondary = GoogleGrey900,
    secondaryContainer = GoogleGrey700,
    onSecondaryContainer = GoogleGrey100,
    tertiary = GoogleYellow300,
    background = GoogleGrey900,
    onBackground = GoogleGrey100,
    surface = GoogleGrey800,
    onSurface = GoogleGrey100,
    surfaceVariant = GoogleGrey700,
    onSurfaceVariant = GoogleGrey400,
    outline = GoogleGrey600,
    error = GoogleRed300,
    onError = GoogleGrey900
)

private val KetikKuLightScheme = lightColorScheme(
    primary = GoogleBlue600,
    onPrimary = GoogleWhite,
    primaryContainer = GoogleBlue100,
    onPrimaryContainer = GoogleBlue900,
    secondary = GoogleGreen700,
    onSecondary = GoogleWhite,
    tertiary = GoogleYellow700,
    background = GoogleGrey50,
    onBackground = GoogleGrey900,
    surface = GoogleWhite,
    onSurface = GoogleGrey900,
    surfaceVariant = GoogleGrey50,
    onSurfaceVariant = GoogleGrey600,
    outline = GoogleGrey300,
    error = GoogleRed600,
    onError = GoogleWhite
)

// Standar Google: kartu dan dialog 16dp.
val KetikKuShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
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
