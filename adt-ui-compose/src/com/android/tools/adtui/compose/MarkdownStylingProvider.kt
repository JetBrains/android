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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.BlockQuote
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Heading
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.HtmlBlock
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Image
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Paragraph
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.ThematicBreak

/**
 * Provides access to [MarkdownStyling] factory functions, depending on the execution context.
 *
 * If running production code, it will delegate to [StudioMarkdownStylingProvider]; otherwise,
 * it will use the test implementation, that is based on the Standalone themes.
 */
interface MarkdownStylingProvider {

  /**
   * Create a new [MarkdownStyling].
   *
   * Any parameter left as `null` will use the same value as [createDefault] would.
   */
  fun create(
    baseTextStyle: TextStyle,
    editorTextStyle: TextStyle,
    inlinesStyling: InlinesStyling? = null,
    blockVerticalSpacing: Dp? = null,
    paragraph: Paragraph? = null,
    heading: Heading? = null,
    blockQuote: BlockQuote? = null,
    code: Code? = null,
    list: List? = null,
    image: Image? = null,
    thematicBreak: ThematicBreak? = null,
    htmlBlock: HtmlBlock? = null,
  ): MarkdownStyling

  /** Create a new [MarkdownStyling], using the default values for the current theme. */
  fun createDefault(defaultTextStyle: TextStyle, editorTextStyle: TextStyle): MarkdownStyling
}

val LocalMarkdownStylingProvider = staticCompositionLocalOf<MarkdownStylingProvider> {
  error("No MarkdownStylingProvider defined — check your theme!")
}

val JewelTheme.Companion.markdownStylingProvider: MarkdownStylingProvider
  @Composable get() = LocalMarkdownStylingProvider.current