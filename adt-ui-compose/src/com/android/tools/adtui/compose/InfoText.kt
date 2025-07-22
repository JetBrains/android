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
package com.android.tools.adtui.compose

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.jewel.bridge.medium
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Typography

/**
 * Displays info text, based on
 * [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info] and
 * [`Typography.medium`][org.jetbrains.jewel.bridge.medium].
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param fontStyle The typeface variant to use, e.g. [FontStyle.Italic].
 * @param fontWeight The typeface thickness to use, e.g. [FontWeight.Bold].
 * @param fontFamily The font family to be used when rendering the text.
 * @param letterSpacing The amount of space to add between each letter.
 * @param textDecoration The decorations to apply to the text (e.g., underline).
 * @param textAlign The alignment of the text within its container.
 * @param lineHeight The height of each line of text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 *   text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 *   [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *   If the text exceeds the given number of lines, it will be truncated according to [overflow] and
 *   [softWrap].
 * @param inlineContent A map store composables that replaces certain ranges of the text. It's used
 *   to insert composables into text layout. Check [InlineTextContent] for more information.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 *   [TextLayoutResult] object that callback provides contains paragraph information, size of the
 *   text, baselines and other details. The callback can be used to add additional decoration or
 *   functionality to the text. For example, to draw selection around the text.
 */
@Composable
fun InfoText(
  text: AnnotatedString,
  modifier: Modifier = Modifier,
  style: TextStyle = Typography.medium(),
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign = TextAlign.Unspecified,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  inlineContent: Map<String, InlineTextContent> = emptyMap(),
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val color = JewelTheme.globalColors.text.info
  val combinedTextStyle = if (color.isSpecified) style.copy(color = color) else style

  Text(
    text,
    modifier,
    color,
    fontSize = TextUnit.Unspecified,
    fontStyle,
    fontWeight,
    fontFamily,
    letterSpacing,
    textDecoration,
    textAlign,
    lineHeight,
    overflow,
    softWrap,
    maxLines,
    inlineContent,
    onTextLayout,
    combinedTextStyle,
  )
}

/**
 * Displays info text, based on
 * [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info] and
 * [`Typography.medium`][org.jetbrains.jewel.bridge.medium].
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param color The color of the text.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param fontStyle The typeface variant to use, e.g. [FontStyle.Italic].
 * @param fontWeight The typeface thickness to use, e.g. [FontWeight.Bold].
 * @param fontFamily The font family to be used when rendering the text.
 * @param letterSpacing The amount of space to add between each letter.
 * @param textDecoration The decorations to apply to the text (e.g., underline).
 * @param textAlign The alignment of the text within its container.
 * @param lineHeight The height of each line of text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 *   text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 *   [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *   If the text exceeds the given number of lines, it will be truncated according to [overflow] and
 *   [softWrap].
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 *   [TextLayoutResult] object that callback provides contains paragraph information, size of the
 *   text, baselines and other details. The callback can be used to add additional decoration or
 *   functionality to the text. For example, to draw selection around the text.
 */
@Composable
fun InfoText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = JewelTheme.globalColors.text.info,
  style: TextStyle = Typography.medium(),
  fontStyle: FontStyle? = null,
  fontWeight: FontWeight? = null,
  fontFamily: FontFamily? = null,
  letterSpacing: TextUnit = TextUnit.Unspecified,
  textDecoration: TextDecoration? = null,
  textAlign: TextAlign = TextAlign.Unspecified,
  lineHeight: TextUnit = TextUnit.Unspecified,
  overflow: TextOverflow = TextOverflow.Clip,
  softWrap: Boolean = true,
  maxLines: Int = Int.MAX_VALUE,
  onTextLayout: (TextLayoutResult) -> Unit = {},
) {
  val combinedTextStyle = if (color.isSpecified) style.copy(color = color) else style

  Text(
    text,
    modifier,
    color,
    fontSize = TextUnit.Unspecified,
    fontStyle,
    fontWeight,
    fontFamily,
    letterSpacing,
    textDecoration,
    textAlign,
    lineHeight,
    overflow,
    softWrap,
    maxLines,
    onTextLayout,
    combinedTextStyle,
  )
}