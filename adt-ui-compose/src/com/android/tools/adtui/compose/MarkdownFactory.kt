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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
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
 * Provides access to factory functions used to create Jewel Markdown components, such as styling
 * and renderers, depending on the execution context.
 *
 * This allows for different implementations of Markdown rendering depending on the environment. For
 * example, in production, it might delegate to `StudioMarkdownFactory`, while in tests, it could
 * use a test-specific implementation based on Standalone themes.
 *
 * To access the factory, use [JewelTheme.markdownFactory].
 */
@OptIn(ExperimentalJewelApi::class)
interface MarkdownFactory {

  /**
   * Creates a new [MarkdownStyling].
   *
   * Any parameter left as `null` will use the same value as [createDefaultStyling] would.
   *
   * @param baseTextStyle The base text style for the markdown content.
   * @param editorTextStyle The text style for editor-specific markdown elements.
   * @param inlinesStyling The styling for inline markdown elements.
   * @param blockVerticalSpacing The vertical spacing between markdown blocks.
   * @param paragraph The styling for paragraph blocks.
   * @param heading The styling for heading blocks.
   * @param blockQuote The styling for block quote blocks.
   * @param code The styling for code blocks.
   * @param list The styling for list blocks.
   * @param image The styling for image blocks.
   * @param thematicBreak The styling for thematic break blocks.
   * @param htmlBlock The styling for HTML blocks.
   * @return A new [MarkdownStyling] instance.
   */
  fun createStyling(
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

  /**
   * Creates a new [MarkdownStyling] using the default values for the current theme.
   *
   * @param defaultTextStyle The default text style to use as the base.
   * @param editorTextStyle The text style for editor-specific markdown elements.
   * @return A new [MarkdownStyling] instance with default styling.
   */
  fun createDefaultStyling(defaultTextStyle: TextStyle, editorTextStyle: TextStyle): MarkdownStyling

  /**
   * Creates a [MarkdownBlockRenderer] based on the provided styling, extensions, and inline
   * renderer.
   *
   * @param styling The [MarkdownStyling] to use for rendering blocks.
   * @param extensions A list of [MarkdownRendererExtension] to customize rendering.
   * @param inlineRenderer The [InlineMarkdownRenderer] to use for rendering inline elements.
   * @return A new [MarkdownBlockRenderer] instance.
   */
  fun createBlockRenderer(
    styling: MarkdownStyling,
    extensions: kotlin.collections.List<MarkdownRendererExtension> = emptyList(),
    inlineRenderer: InlineMarkdownRenderer = createInlineMarkdownRenderer(extensions),
  ): MarkdownBlockRenderer

  /**
   * Creates an [InlineMarkdownRenderer] based on the provided extensions.
   *
   * @param extensions A list of [MarkdownRendererExtension] to customize rendering.
   * @return A new [InlineMarkdownRenderer] instance.
   */
  fun createInlineMarkdownRenderer(
    extensions: kotlin.collections.List<MarkdownRendererExtension> = emptyList()
  ): InlineMarkdownRenderer = DefaultInlineMarkdownRenderer(extensions)
}

/**
 * CompositionLocal for providing a [MarkdownFactory] to the composition tree.
 *
 * Consumers should use [JewelTheme.markdownFactory] to access the factory.
 */
val LocalMarkdownFactory =
  staticCompositionLocalOf<MarkdownFactory> {
    error("No MarkdownFactory defined — check your theme!")
  }

/** The [MarkdownFactory] for the current [JewelTheme]. */
val JewelTheme.Companion.markdownFactory: MarkdownFactory
  @Composable get() = LocalMarkdownFactory.current