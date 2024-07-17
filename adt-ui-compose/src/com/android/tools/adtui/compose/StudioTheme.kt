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
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.bridge.theme.retrieveDefaultTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveEditorTextStyle
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.create
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownProcessor
import org.jetbrains.jewel.markdown.extensions.LocalMarkdownStyling
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer

@OptIn(ExperimentalJewelApi::class)
@Composable
fun StudioTheme(content: @Composable () -> Unit) {
  SwingBridgeTheme {
    val provider = StudioMarkdownStylingProvider
    val markdownStyling =
      remember(JewelTheme.name, provider) {
        provider.createDefault(retrieveDefaultTextStyle(), retrieveEditorTextStyle())
      }
    val markdownProcessor = remember { MarkdownProcessor() }
    val blockRenderer = remember(markdownStyling) { MarkdownBlockRenderer.create(markdownStyling) }

    CompositionLocalProvider(
      LocalMarkdownStylingProvider provides provider,
      LocalMarkdownStyling provides markdownStyling,
      LocalMarkdownProcessor provides markdownProcessor,
      LocalMarkdownBlockRenderer provides blockRenderer,
    ) {
      content()
    }
  }
}