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
@file:Suppress("OPT_IN_USAGE", "UnstableApiUsage")

package com.android.tools.idea.whatsnew.assistant.v2.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.tools.adtui.compose.IntUiPaletteDefaults
import com.android.tools.adtui.compose.rememberColor
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoaderMarkdownRendererExtension
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.WithInlineMarkdown
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling

/**
 * A custom [DefaultMarkdownBlockRenderer] for the What's New panel.
 *
 * This renderer creates a column/grid-based layout from a Markdown document. It groups blocks by H1 and H2 headings.
 * - Content under an H1 but not under an H2 is considered a "row" and spans the full width.
 * - Content under an H2 is considered a "cell" and is placed in a grid layout.
 *
 * It also adds a hover effect to the H2 blocks, making the blocks appear elevated and zooming in on the image, if applicable.
 */
@OptIn(ExperimentalJewelApi::class)
internal class WhatsNewMarkdownBlockRenderer(
  rootStyling: MarkdownStyling,
  rendererExtensions: List<MarkdownRendererExtension>,
  inlineRenderer: InlineMarkdownRenderer,
) : DefaultMarkdownBlockRenderer(rootStyling, rendererExtensions, inlineRenderer) {

  @Composable
  override fun RenderBlocks(blocks: List<MarkdownBlock>, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
    if (LocalIsTopLevel.current) {
      CompositionLocalProvider(LocalIsTopLevel provides false) { RenderTopLevelBlocks(blocks, enabled, onUrlClick, modifier) }
    } else {
      RenderBlocksImpl(blocks, enabled, onUrlClick, modifier)
    }
  }

  @Composable
  private fun RenderTopLevelBlocks(blocks: List<MarkdownBlock>, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
    RenderTopLevelBlocks_ColumnAndRows(blocks, enabled, onUrlClick, modifier)
  }

  @Composable
  private fun RenderBlocksImpl(blocks: List<MarkdownBlock>, enabled: Boolean, onUrlClick: (String) -> Unit, modifier: Modifier) {
    val firstBlock = blocks.firstOrNull() ?: return
    val isHoveredState = remember { mutableStateOf(false) }
    val isHovered = isHoveredState.value

    val columnModifier =
      if (firstBlock is MarkdownBlock.Heading && firstBlock.level == 2) {
        val elevation by animateDpAsState(if (isHovered) 8.dp else 0.dp)
        val popupModifier =
          modifier
            .onHover { hovering -> isHoveredState.value = hovering }
            .graphicsLayer {
              this.shadowElevation = elevation.toPx()
              this.shape = RoundedCornerShape(20.dp)
              this.clip = false
            }
            .thenIf(isHovered) { zIndex(1f) }

        val cardColor = JewelTheme.globalColors.borders.normal
        popupModifier
          .clip(RoundedCornerShape(20.dp))
          .background(cardColor)
          .border(1.dp, cardBorderColor(), RoundedCornerShape(20.dp))
          .padding(16.dp)
      } else {
        modifier
      }

    CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalCardHovered provides isHovered) {
      super.RenderBlocks(blocks, enabled, onUrlClick, columnModifier)
    }
  }

  @Composable
  private fun RenderTopLevelBlocks_ColumnAndRows(
    blocks: List<MarkdownBlock>,
    enabled: Boolean,
    onUrlClick: (String) -> Unit,
    modifier: Modifier,
  ) {
    val markdownGroups = remember(blocks) { groupBlocksByH1ThenH2Blocks(blocks) }

    BoxWithConstraints(modifier = modifier) {
      val columnHorizontalPadding = 24.dp
      val columnMinWidth = 400.dp
      val containerWidth = maxWidth
      val columnCount = maxOf(1, (maxWidth / columnMinWidth).toInt())
      val columnWidth = (maxWidth - columnHorizontalPadding * (columnCount - 1)) / columnCount

      // We simulate a grid by displaying a bunch of rows that span the entire
      // width. For "grid", we display Rows that contain `columns` elements of `columnWidth` size
      Column {
        var isFirstCellRow = true
        val groupIterator = markdownGroups.listIterator()
        while (groupIterator.hasNext()) {
          when (val markdownGroup = groupIterator.next()) {
            is MarkdownGroup.Rows -> {
              // Check if it's the top level, i.e. beginning of the markdown content, to place the release animal icon on the left
              val blocks = markdownGroup.blocks
              val hasH1 = blocks.any { it is MarkdownBlock.Heading && it.level == 1 }
              val imageBlocks = blocks.filter { block ->
                block is WithInlineMarkdown && block.inlineContent.any { it is InlineMarkdown.Image }
              }
              if (hasH1 && imageBlocks.size == 1) {
                RenderHeaderWithReleaseIcon(blocks, imageBlocks.single(), enabled, onUrlClick, containerWidth)
              } else {
                CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalContainerWidth provides containerWidth) {
                  RenderBlocks(blocks, enabled, onUrlClick, Modifier)
                }
              }
            }
            is MarkdownGroup.Cell -> {
              // When getting a cell, collect them all until next row or end of collection, then
              // create "chunks" of "columnCount" elements (one chunk == one row)
              val rows = collectNextCells(markdownGroup, groupIterator).chunked(columnCount)
              rows.forEach { cells ->
                val topPadding = if (isFirstCellRow) 20.dp else 0.dp
                isFirstCellRow = false
                // Make all Columns within the Row the same height
                Row(
                  Modifier.fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = rootStyling.blockVerticalSpacing + topPadding, bottom = rootStyling.blockVerticalSpacing),
                  horizontalArrangement = Arrangement.spacedBy(columnHorizontalPadding),
                ) {
                  cells.forEach { cell ->
                    CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalContainerWidth provides columnWidth) {
                      RenderBlocks(cell.blocks, enabled, onUrlClick, Modifier.width(columnWidth).fillMaxHeight())
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Composable
  private fun RenderHeaderWithReleaseIcon(
    blocks: List<MarkdownBlock>,
    imageBlock: MarkdownBlock,
    enabled: Boolean,
    onUrlClick: (String) -> Unit,
    containerWidth: Dp,
  ) {
    // Split the content such that anything before the image is placed on the same row as the image, while everything after is rendered
    // normally
    val imageIndex = blocks.indexOf(imageBlock)
    val blocksBefore = blocks.subList(0, imageIndex)
    val blocksAfter = blocks.subList(imageIndex + 1, blocks.size)

    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = rootStyling.blockVerticalSpacing),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Box(modifier = Modifier.width(100.dp)) {
        CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalContainerWidth provides 120.dp) {
          RenderBlocks(listOf(imageBlock), enabled, onUrlClick, Modifier)
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        val rightContainerWidth =
          if (containerWidth != Dp.Unspecified && containerWidth > 136.dp) containerWidth - 136.dp else containerWidth
        CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalContainerWidth provides rightContainerWidth) {
          RenderBlocks(blocksBefore, enabled, onUrlClick, Modifier)
        }
      }
    }
    if (blocksAfter.isNotEmpty()) {
      CompositionLocalProvider(ImagePainterLoaderMarkdownRendererExtension.LocalContainerWidth provides containerWidth) {
        RenderBlocks(blocksAfter, enabled, onUrlClick, Modifier)
      }
    }
  }

  @Composable
  private fun cardBorderColor(): Color {
    return rememberColor(
      key = "WNACard.borderColor",
      darkFallbackKey = "ColorPalette.Gray3",
      darkDefault = Color(IntUiPaletteDefaults.Dark.Gray3),
      lightFallbackKey = "ColorPalette.Gray11",
      lightDefault = Color(IntUiPaletteDefaults.Light.Gray11),
    )
  }

  private fun collectNextCells(markdownGroup: MarkdownGroup.Cell, groupIterator: ListIterator<MarkdownGroup>): List<MarkdownGroup.Cell> {
    val list = mutableListOf(markdownGroup)
    while (groupIterator.hasNext()) {
      val next = groupIterator.next()
      if (next is MarkdownGroup.Cell) {
        list.add(next)
      } else {
        groupIterator.previous()
        break
      }
    }
    return list
  }

  companion object {
    /** Whether the [WhatsNewMarkdownBlockRenderer] is currently rendering the top levels blocks of the Markdown document. */
    private val LocalIsTopLevel = staticCompositionLocalOf { true }

    internal sealed class MarkdownGroup {
      /** A row of the grid, to render in a single row spanning the entire grid */
      class Rows(val blocks: List<MarkdownBlock>) : MarkdownGroup()

      /** A single cell of the grid, to render in a columns */
      class Cell(val blocks: List<MarkdownBlock>) : MarkdownGroup()
    }

    internal fun groupBlocksByH1ThenH2Blocks(blocks: List<MarkdownBlock>): List<MarkdownGroup> {
      val result = mutableListOf<MarkdownGroup>()
      var currentBlocks = mutableListOf<MarkdownBlock>()
      var inH2Section = false

      for (block in blocks) {
        if (block is MarkdownBlock.Heading && block.level == 1) {
          if (currentBlocks.isNotEmpty()) {
            if (inH2Section) {
              result.add(MarkdownGroup.Cell(currentBlocks))
            } else {
              result.add(MarkdownGroup.Rows(currentBlocks))
            }
          }
          currentBlocks = mutableListOf(block)
          inH2Section = false
        } else if (block is MarkdownBlock.Heading && block.level == 2) {
          if (currentBlocks.isNotEmpty()) {
            if (inH2Section) {
              result.add(MarkdownGroup.Cell(currentBlocks))
            } else {
              result.add(MarkdownGroup.Rows(currentBlocks))
            }
          }
          currentBlocks = mutableListOf(block)
          inH2Section = true
        } else {
          currentBlocks.add(block)
        }
      }

      if (currentBlocks.isNotEmpty()) {
        if (inH2Section) {
          result.add(MarkdownGroup.Cell(currentBlocks))
        } else {
          result.add(MarkdownGroup.Rows(currentBlocks))
        }
      }

      return result
    }
  }
}
