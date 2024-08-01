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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.intui.markdown.standalone.ProvideMarkdownStyling
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
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
  val defaultTextStyle = JewelTheme.createDefaultTextStyle(fontFamily = FontFamily.InterForTests)
  val editorTextStyle =
    JewelTheme.createEditorTextStyle(fontFamily = FontFamily.JetBrainsMonoForTests)

  val themeDefinition =
    if (darkMode) {
      JewelTheme.darkThemeDefinition(
        defaultTextStyle = defaultTextStyle,
        editorTextStyle = editorTextStyle,
      )
    } else {
      JewelTheme.lightThemeDefinition(
        defaultTextStyle = defaultTextStyle,
        editorTextStyle = editorTextStyle,
      )
    }

  val componentStyling = createComponentStyling(darkMode)

  IntUiTheme(themeDefinition, componentStyling, true) {
    val provider = remember(darkMode) { TestMarkdownStylingProvider(darkMode) }
    val markdownStyling =
      remember(darkMode, provider) { provider.createDefault(defaultTextStyle, editorTextStyle) }
    val markdownProcessor = remember { MarkdownProcessor() }
    val blockRenderer = remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) }

    CompositionLocalProvider(LocalMarkdownStylingProvider provides provider) {
      ProvideMarkdownStyling(
        JewelTheme.isDark,
        markdownStyling,
        markdownProcessor,
        blockRenderer,
        content,
      )
    }
  }
}

@Composable
private fun createComponentStyling(darkMode: Boolean) =
  if (darkMode) ComponentStyling.dark() else ComponentStyling.light()