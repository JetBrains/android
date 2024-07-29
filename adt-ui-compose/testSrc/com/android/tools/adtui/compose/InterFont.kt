/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.compose

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

private val InterFontFamily =
    FontFamily(
        Font(resource = "test-fonts/inter/Inter-Thin.ttf", weight = FontWeight.Thin),
        Font(resource = "test-fonts/inter/Inter-ThinItalic.ttf", weight = FontWeight.Thin, style = FontStyle.Italic),
        Font(resource = "test-fonts/inter/Inter-ExtraLight.ttf", weight = FontWeight.ExtraLight),
        Font(
            resource = "test-fonts/inter/Inter-ExtraLightItalic.ttf",
            weight = FontWeight.ExtraLight,
            style = FontStyle.Italic,
        ),
        Font(resource = "test-fonts/inter/Inter-Light.ttf", weight = FontWeight.Light),
        Font(resource = "test-fonts/inter/Inter-LightItalic.ttf", weight = FontWeight.Light, style = FontStyle.Italic),
        Font(resource = "test-fonts/inter/Inter-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "test-fonts/inter/Inter-Italic.ttf", weight = FontWeight.Normal, style = FontStyle.Italic),
        Font(resource = "test-fonts/inter/Inter-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "test-fonts/inter/Inter-MediumItalic.ttf", weight = FontWeight.Medium, style = FontStyle.Italic),
        Font(resource = "test-fonts/inter/Inter-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(
            resource = "test-fonts/inter/Inter-SemiBoldItalic.ttf",
            weight = FontWeight.SemiBold,
            style = FontStyle.Italic,
        ),
        Font(resource = "test-fonts/inter/Inter-Bold.ttf", weight = FontWeight.Bold),
        Font(resource = "test-fonts/inter/Inter-BoldItalic.ttf", weight = FontWeight.Bold, style = FontStyle.Italic),
        Font(resource = "test-fonts/inter/Inter-ExtraBold.ttf", weight = FontWeight.ExtraBold),
        Font(
            resource = "test-fonts/inter/Inter-ExtraBoldItalic.ttf",
            weight = FontWeight.ExtraBold,
            style = FontStyle.Italic,
        ),
        Font(resource = "test-fonts/inter/Inter-Black.ttf", weight = FontWeight.Black),
        Font(resource = "test-fonts/inter/Inter-BlackItalic.ttf", weight = FontWeight.Black, style = FontStyle.Italic),
    )

internal val FontFamily.Companion.InterForTests: FontFamily
    get() = InterFontFamily