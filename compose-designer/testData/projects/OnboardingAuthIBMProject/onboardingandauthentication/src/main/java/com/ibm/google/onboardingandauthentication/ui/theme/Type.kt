/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.google.onboardingandauthentication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ibm.google.onboardingandauthentication.R

const val providerAuthority = "com.google.android.gms.fonts"
const val providerPackage = "com.google.android.gms"
const val quicksand = "Quicksand"
const val roboto = "Roboto"

private val fontProvider =
  GoogleFont.Provider(
    providerAuthority = providerAuthority,
    providerPackage = providerPackage,
    certificates = R.array.com_google_android_gms_fonts_certs,
  )

private val fontName = GoogleFont(quicksand)
private val robotoFontName = GoogleFont(roboto)

private val quicksandFontFamily =
  FontFamily(Font(googleFont = fontName, fontProvider = fontProvider))

private val robotoFontFamily =
  FontFamily(Font(googleFont = robotoFontName, fontProvider = fontProvider))

// Set of Material typography styles to start with
val MediumTypography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 64.sp,
        lineHeight = 72.sp,
        letterSpacing = (-0.2).sp,
      ),
    displayMedium =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = 0.sp,
      ),
    displaySmall =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.15.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.25.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
      ),
  )

// Set of Material typography styles to start with
val CompactTypography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.2).sp,
      ),
    displayMedium =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = (0).sp,
      ),
    displaySmall =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (0).sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (0).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (0).sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (0).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = quicksandFontFamily,
        fontWeight = FontWeight.W400,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (0).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (0.15).sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.1).sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.1).sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.5).sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.5).sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (0.5).sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (0.25).sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = robotoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (0.4).sp,
      ),
  )
