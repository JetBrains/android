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
import org.jetbrains.jewel.intui.markdown.standalone.styling.dark
import org.jetbrains.jewel.intui.markdown.standalone.styling.light
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

class TestMarkdownStylingProvider(private val isDark: Boolean) : MarkdownStylingProvider {
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
    val defaults =
      createDefault(defaultTextStyle = baseTextStyle, editorTextStyle = editorTextStyle)

    val defaultInlinesStyling = defaults.paragraph.inlinesStyling
    val defaultTextSize = defaultInlinesStyling.textStyle.fontSize
    val defaultEditorTextStyle =
      JewelTheme.createEditorTextStyle(
        fontSize = defaultTextSize,
        lineHeight = defaultTextSize * 1.2,
      )

    return if (isDark) {
      MarkdownStyling.dark(
        baseTextStyle = defaultInlinesStyling.textStyle.merge(baseTextStyle),
        editorTextStyle = defaultEditorTextStyle.merge(editorTextStyle),
        inlinesStyling = defaultInlinesStyling.merge(inlinesStyling),
        blockVerticalSpacing = blockVerticalSpacing ?: defaults.blockVerticalSpacing,
        paragraph = paragraph ?: defaults.paragraph,
        heading = heading ?: defaults.heading,
        blockQuote = blockQuote ?: defaults.blockQuote,
        code = code ?: defaults.code,
        list = list ?: defaults.list,
        image = image ?: defaults.image,
        thematicBreak = thematicBreak ?: defaults.thematicBreak,
        htmlBlock = htmlBlock ?: defaults.htmlBlock,
      )
    } else {
      MarkdownStyling.light(
        baseTextStyle = defaultInlinesStyling.textStyle.merge(baseTextStyle),
        editorTextStyle = defaultEditorTextStyle.merge(editorTextStyle),
        inlinesStyling = defaultInlinesStyling.merge(inlinesStyling),
        blockVerticalSpacing = blockVerticalSpacing ?: defaults.blockVerticalSpacing,
        paragraph = paragraph ?: defaults.paragraph,
        heading = heading ?: defaults.heading,
        blockQuote = blockQuote ?: defaults.blockQuote,
        code = code ?: defaults.code,
        list = list ?: defaults.list,
        image = image ?: defaults.image,
        thematicBreak = thematicBreak ?: defaults.thematicBreak,
        htmlBlock = htmlBlock ?: defaults.htmlBlock,
      )
    }
  }

  private fun InlinesStyling.merge(other: InlinesStyling?): InlinesStyling {
    if (other == null) return this

    return InlinesStyling(
      textStyle = textStyle.merge(other.textStyle),
      inlineCode = inlineCode.merge(other.inlineCode),
      link = link.merge(other.link),
      linkDisabled = link.merge(other.linkDisabled),
      linkFocused = link.merge(other.linkFocused),
      linkHovered = link.merge(other.linkHovered),
      linkPressed = link.merge(other.linkPressed),
      linkVisited = link.merge(other.linkVisited),
      emphasis = emphasis.merge(other.emphasis),
      strongEmphasis = strongEmphasis.merge(other.strongEmphasis),
      inlineHtml = inlineHtml.merge(other.inlineHtml),
      renderInlineHtml = renderInlineHtml,
    )
  }

  override fun createDefault(
    defaultTextStyle: TextStyle,
    editorTextStyle: TextStyle,
  ): MarkdownStyling =
    if (isDark) {
      MarkdownStyling.dark(baseTextStyle = defaultTextStyle, editorTextStyle = editorTextStyle)
    } else {
      MarkdownStyling.light(baseTextStyle = defaultTextStyle, editorTextStyle = editorTextStyle)
    }
}