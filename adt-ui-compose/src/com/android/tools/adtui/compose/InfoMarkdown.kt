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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.intellij.lang.annotations.Language
import org.jetbrains.jewel.bridge.medium
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.Markdown
import org.jetbrains.jewel.markdown.extensions.markdownProcessor
import org.jetbrains.jewel.markdown.processing.MarkdownProcessor
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.copyWithSize

/**
 * Renders a Markdown string with a default styling for information text. This means that text will
 * be based on [Typography.medium] with color
 * [`JewelTheme.globalColors.text.info`][org.jetbrains.jewel.foundation.TextColors.info].
 *
 * This composable is a wrapper around [Markdown] that provides a default styling that is suitable
 * for displaying informational messages to the user.
 *
 * @param markdown The Markdown string to render.
 * @param modifier The modifier to apply to this layout node.
 * @param selectable Whether the text can be selected.
 * @param enabled Whether the text is enabled.
 * @param textStyle The base text style to use for the Markdown text.
 * @param textStyle The base editor text style to use for the Markdown text.
 * @param textColor The color to use for the Markdown text.
 * @param renderingDispatcher The coroutine dispatcher to use for rendering the Markdown.
 * @param onUrlClick A callback that is invoked when the user clicks on a URL.
 * @param onTextClick A callback that is invoked when the user clicks on the text.
 * @param markdownFactory The [MarkdownFactory] to use for styling the Markdown.
 * @param processor The [MarkdownProcessor] to use for processing the Markdown.
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun InfoMarkdown(
  @Language("Markdown") markdown: String,
  modifier: Modifier = Modifier,
  selectable: Boolean = false,
  enabled: Boolean = true,
  textStyle: TextStyle = Typography.medium(),
  editorTextStyle: TextStyle = JewelTheme.editorTextStyle.copyWithSize(textStyle.fontSize),
  textColor: Color = JewelTheme.globalColors.text.info,
  renderingDispatcher: CoroutineDispatcher = Dispatchers.Default,
  onUrlClick: (String) -> Unit = {},
  onTextClick: () -> Unit = {},
  markdownFactory: MarkdownFactory = JewelTheme.markdownFactory,
  processor: MarkdownProcessor = JewelTheme.markdownProcessor,
) {
  val markdownStyling =
    remember(
      JewelTheme.name,
      JewelTheme.isDark,
      textStyle,
      markdownFactory,
      editorTextStyle,
      textColor,
    ) {
      markdownFactory.createStyling(
        baseTextStyle = if (textColor.isSpecified) textStyle.copy(color = textColor) else textStyle,
        editorTextStyle =
          if (textColor.isSpecified) editorTextStyle.copy(color = textColor) else editorTextStyle,
      )
    }

  Markdown(
    markdown = markdown,
    modifier = modifier,
    selectable = selectable,
    enabled = enabled,
    renderingDispatcher = renderingDispatcher,
    onUrlClick = onUrlClick,
    onTextClick = onTextClick,
    markdownStyling = markdownStyling,
    processor = processor,
    blockRenderer =
      remember(markdownStyling, markdownFactory) {
        markdownFactory.createBlockRenderer(markdownStyling)
      },
  )
}