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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.dark
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.light
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.ui.ComponentStyling

@OptIn(ExperimentalJewelApi::class)
@Suppress("TestFunctionName") // It's a composable
@Composable
fun StudioTestTheme(darkMode: Boolean = false, content: @Composable () -> Unit) {
  val fontFamily =
    FontFamily(
      Font(resource = "fonts/inter/Inter-Thin.ttf", weight = FontWeight.Thin),
      Font(resource = "fonts/inter/Inter-ThinItalic.ttf", weight = FontWeight.Thin, style = FontStyle.Italic),
      Font(resource = "fonts/inter/Inter-ExtraLight.ttf", weight = FontWeight.ExtraLight),
      Font(resource = "fonts/inter/Inter-Light.ttf", weight = FontWeight.Light),
      Font(resource = "fonts/inter/Inter-Regular.ttf", weight = FontWeight.Normal),
      Font(resource = "fonts/inter/Inter-Medium.ttf", weight = FontWeight.Medium),
      Font(resource = "fonts/inter/Inter-SemiBold.ttf", weight = FontWeight.SemiBold),
      Font(resource = "fonts/inter/Inter-Bold.ttf", weight = FontWeight.Bold),
      Font(resource = "fonts/inter/Inter-ExtraBold.ttf", weight = FontWeight.ExtraBold),
      Font(resource = "fonts/inter/Inter-Black.ttf", weight = FontWeight.Black),
    )

  val textStyle = JewelTheme.createDefaultTextStyle().copy(fontFamily = fontFamily)

  // TODO bring in JetBrains Mono and the editor text style, too, when Jewel 0.19.5 is available
  val themeDefinition =
    if (darkMode) {
      JewelTheme.darkThemeDefinition(defaultTextStyle = textStyle)
    } else {
      JewelTheme.lightThemeDefinition(defaultTextStyle = textStyle)
    }

  val componentStyling = if (darkMode) ComponentStyling.dark() else ComponentStyling.light()

  IntUiTheme(themeDefinition, componentStyling, true) {
    val provider = remember(darkMode) { TestMarkdownStylingProvider(darkMode) }
    val markdownStyling = remember(darkMode, provider) { provider.createDefault() }
    val markdownProcessor = remember { MarkdownProcessor() }
    val blockRenderer = remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) }

    CompositionLocalProvider(
      LocalMarkdownStylingProvider provides provider,
    ) {
      ProvideMarkdownStyling(JewelTheme.isDark, markdownStyling, markdownProcessor, blockRenderer, content)
    }
  }
}