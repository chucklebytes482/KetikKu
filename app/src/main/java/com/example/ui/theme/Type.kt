package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// Roboto (Apache 2.0) untuk seluruh UI, Roboto Mono untuk kode & label teknis.
val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal)
)

// Alias agar pemakaian font di keyboard tetap satu nama.
val InterFamily = RobotoFamily
val PlusJakartaSansFamily = RobotoFamily
val JetBrainsMonoFamily = RobotoMonoFamily
val ArialFamily = RobotoFamily

val KetikKuTypography = Typography(
    headlineLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = RobotoMonoFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)
