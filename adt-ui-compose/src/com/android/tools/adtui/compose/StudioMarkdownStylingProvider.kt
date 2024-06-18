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

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

internal object StudioMarkdownStylingProvider : MarkdownStylingProvider {

  override fun create(
    baseTextStyle: TextStyle?,
    editorTextStyle: TextStyle?,
    inlinesStyling: InlinesStyling?,
    blockVerticalSpacing: Dp?,
    paragraph: MarkdownStyling.Paragraph?,
    heading: MarkdownStyling.Heading?,
    blockQuote: MarkdownStyling.BlockQuote?,
    code: MarkdownStyling.Code?,
    list: MarkdownStyling.List?,
    image: MarkdownStyling.Image?,
    thematicBreak: MarkdownStyling.ThematicBreak?,
    htmlBlock: MarkdownStyling.HtmlBlock?,
  ): MarkdownStyling {
    val defaults = createDefault()
    val defaultInlinesStyling = defaults.paragraph.inlinesStyling

    return MarkdownStyling.create(
      baseTextStyle ?: defaultInlinesStyling.textStyle,
      // TODO restore when Jewel 0.19.5 is merged: editorTextStyle ?: JewelTheme.editorTextStyle,
      inlinesStyling ?: defaultInlinesStyling,
      blockVerticalSpacing ?: defaults.blockVerticalSpacing,
      paragraph ?: defaults.paragraph,
      heading ?: defaults.heading,
      blockQuote ?: defaults.blockQuote,
      code ?: defaults.code,
      list ?: defaults.list,
      image ?: defaults.image,
      thematicBreak ?: defaults.thematicBreak,
      htmlBlock ?: defaults.htmlBlock,
    )
  }

  override fun createDefault(): MarkdownStyling = MarkdownStyling.create()
}