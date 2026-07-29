/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.extensions.markdownStyling
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.theme.textAreaStyle

/**
 * This is a copy of `com.google.studiobot.ui.components.markdown.StudioBotMarkdownStyling.create`, but we can't take a dependency on it
 * here. The goal is to make the H1, H2, etc. text style smaller than the default.
 */
@Suppress("OPT_IN_USAGE", "UnstableApiUsage")
object StudioBotMarkdownStylingCopy {
  /**
   * Returns the [org.jetbrains.jewel.markdown.rendering.MarkdownStyling] to use for StudioBot, using the current
   * [org.jetbrains.jewel.foundation.theme.JewelTheme] as a base.
   */
  @Composable
  fun create(
    baseStyle: MarkdownStyling = JewelTheme.markdownStyling,
    editorTextStyle: TextStyle = JewelTheme.editorTextStyle,
    defaultTextStyle: TextStyle = JewelTheme.defaultTextStyle,
    codeBlockPadding: PaddingValues = PaddingValues(8.dp),
    onUrlClick: ((String) -> Unit)? = null,
  ): MarkdownStyling {
    val textAreaBorderColor = JewelTheme.textAreaStyle.colors.border
    return remember(baseStyle, editorTextStyle, defaultTextStyle, codeBlockPadding, onUrlClick, textAreaBorderColor) {
      val defaultHeadingPadding = PaddingValues(top = 8.dp, bottom = 4.dp)
      val noLinkStyle = defaultTextStyle.toSpanStyle()

      MarkdownStyling(
        blockVerticalSpacing = 12.dp,
        paragraph =
          if (onUrlClick == null) {
            with(baseStyle.paragraph) {
              MarkdownStyling.Paragraph(
                inlinesStyling =
                  with(inlinesStyling) {
                    InlinesStyling(
                      textStyle = textStyle,
                      inlineCode = inlineCode,
                      link = noLinkStyle,
                      linkDisabled = noLinkStyle,
                      linkFocused = noLinkStyle,
                      linkHovered = noLinkStyle,
                      linkPressed = noLinkStyle,
                      linkVisited = noLinkStyle,
                      emphasis = emphasis,
                      strongEmphasis = strongEmphasis,
                      inlineHtml = inlineHtml,
                    )
                  }
              )
            }
          } else baseStyle.paragraph,
        heading =
          with(baseStyle.heading) {
            MarkdownStyling.Heading(
              h1 =
                with(h1) {
                  MarkdownStyling.Heading.H1(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize * 2,
                              lineHeight = defaultTextStyle.fontSize * 2 * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = 0.dp,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding =
                      PaddingValues(
                        top = 36.dp,
                        bottom = 2.dp,
                      ),
                  )
                },
              h2 =
                with(h2) {
                  MarkdownStyling.Heading.H2(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize * 1.5,
                              lineHeight = defaultTextStyle.fontSize * 1.5 * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = underlineWidth,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding = defaultHeadingPadding,
                  )
                },
              h3 =
                with(h3) {
                  MarkdownStyling.Heading.H3(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize * 1.25,
                              lineHeight = defaultTextStyle.fontSize * 1.25 * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = underlineWidth,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding = defaultHeadingPadding,
                  )
                },
              h4 =
                with(h4) {
                  MarkdownStyling.Heading.H4(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize,
                              lineHeight = defaultTextStyle.fontSize * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = underlineWidth,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding = defaultHeadingPadding,
                  )
                },
              h5 =
                with(h5) {
                  MarkdownStyling.Heading.H5(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize * .875,
                              lineHeight = defaultTextStyle.fontSize * .875 * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = underlineWidth,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding = defaultHeadingPadding,
                  )
                },
              h6 =
                with(h6) {
                  MarkdownStyling.Heading.H6(
                    inlinesStyling =
                      with(inlinesStyling) {
                        InlinesStyling(
                          textStyle =
                            defaultTextStyle.copy(
                              fontSize = defaultTextStyle.fontSize * .85,
                              lineHeight = defaultTextStyle.fontSize * .85 * 1.25,
                              fontWeight = FontWeight.SemiBold,
                            ),
                          inlineCode = inlineCode,
                          link = link,
                          linkDisabled = linkDisabled,
                          linkFocused = linkFocused,
                          linkHovered = linkHovered,
                          linkPressed = linkPressed,
                          linkVisited = linkVisited,
                          emphasis = emphasis,
                          strongEmphasis = strongEmphasis,
                          inlineHtml = inlineHtml,
                        )
                      },
                    underlineWidth = underlineWidth,
                    underlineColor = underlineColor,
                    underlineGap = underlineGap,
                    padding = defaultHeadingPadding,
                  )
                },
            )
          },
        blockQuote = baseStyle.blockQuote,
        code =
          MarkdownStyling.Code(
            // Tweak code blocks to have smaller margins
            indented =
              with(baseStyle.code.indented) {
                MarkdownStyling.Code.Indented(
                  editorTextStyle,
                  padding = codeBlockPadding,
                  shape,
                  background,
                  borderWidth,
                  borderColor,
                  fillWidth,
                  scrollsHorizontally,
                )
              },
            fenced =
              with(baseStyle.code.fenced) {
                MarkdownStyling.Code.Fenced(
                  editorTextStyle,
                  padding = codeBlockPadding,
                  RoundedCornerShape(4.dp),
                  background,
                  1.dp,
                  textAreaBorderColor,
                  fillWidth,
                  scrollsHorizontally,
                  infoTextStyle,
                  infoPadding,
                  infoPosition,
                )
              },
          ),
        list = baseStyle.list,
        image = baseStyle.image,
        thematicBreak =
          with(baseStyle.thematicBreak) {
            MarkdownStyling.ThematicBreak(
              padding = PaddingValues(top = 16.dp, bottom = 0.dp),
              lineWidth = 6.dp,
              lineColor = Color.DarkGray, // this.lineColor.
            )
          },
        htmlBlock = baseStyle.htmlBlock,
      )
    }
  }
}
