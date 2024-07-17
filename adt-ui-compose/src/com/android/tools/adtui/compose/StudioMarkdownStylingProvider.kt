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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import org.jetbrains.jewel.intui.markdown.bridge.styling.create
import org.jetbrains.jewel.intui.markdown.bridge.styling.default
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

internal object StudioMarkdownStylingProvider : MarkdownStylingProvider {

  override fun create(
    baseTextStyle: TextStyle,
    editorTextStyle: TextStyle,
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
    val actualInlinesStyling =
      inlinesStyling
        ?: InlinesStyling.create(
          baseTextStyle,
          editorTextStyle
            .copy(
              fontSize = baseTextStyle.fontSize * .85,
              background = inlineCodeBackgroundColor,
            )
            .toSpanStyle(),
        )

    return MarkdownStyling.create(
      baseTextStyle = baseTextStyle,
      editorTextStyle = editorTextStyle,
      inlinesStyling = actualInlinesStyling,
      blockVerticalSpacing = blockVerticalSpacing ?: 16.dp,
      paragraph = paragraph ?: MarkdownStyling.Paragraph.create(actualInlinesStyling),
      heading = heading ?: MarkdownStyling.Heading.create(baseTextStyle),
      blockQuote =
      blockQuote ?: MarkdownStyling.BlockQuote.create(textColor = baseTextStyle.color),
      code = code ?: MarkdownStyling.Code.create(editorTextStyle),
      list = list ?: MarkdownStyling.List.create(baseTextStyle),
      image = image ?: MarkdownStyling.Image.default(),
      thematicBreak = thematicBreak ?: MarkdownStyling.ThematicBreak.create(),
      htmlBlock = htmlBlock ?: MarkdownStyling.HtmlBlock.create(editorTextStyle),
    )
  }

  override fun createDefault(defaultTextStyle: TextStyle, editorTextStyle: TextStyle): MarkdownStyling =
    MarkdownStyling.create(defaultTextStyle, editorTextStyle)
}

// Copied from org.intellij.plugins.markdown.ui.preview.PreviewLAFThemeStyles#createStylesheet
private val inlineCodeBackgroundColor
  get() =
    if (JBColor.isBright()) {
      Color(red = 212, green = 222, blue = 231, alpha = 255 / 4)
    } else {
      Color(red = 212, green = 222, blue = 231, alpha = 25)
    }