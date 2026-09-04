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
@file:Suppress("UnstableApiUsage")
@file:OptIn(ExperimentalJewelApi::class)

package com.android.tools.idea.whatsnew.assistant.v2.ui

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.tools.adtui.compose.getDefaultMarkdownRenderExtensions
import com.android.tools.adtui.compose.markdownFactory
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewAssets
import com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewMarkdownDocument
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ActiveItemTracker
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.AdaptiveVerticalScrollbarAdapter
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoader
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoaderMarkdownRendererExtension
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.StudioBotMarkdownStylingCopy
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.lazyListStateActiveItemTracker
import com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.rememberWhatsNewScrollbarAdapter
import com.intellij.ide.BrowserUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.MarkdownBlock
import org.jetbrains.jewel.markdown.extensions.markdownProcessor
import org.jetbrains.jewel.markdown.rawMarkdown
import org.jetbrains.jewel.markdown.rendering.MarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.scrollbarStyle

/**
 * The main panel for the "What's New" editor.
 *
 * This component displays multiple "What's New" documents. The documents are rendered as a sequence of Markdown sections in a vertically
 * scrollable area.
 *
 * ### Layout
 *
 * The panel consists of an optional navigation panel on the left and the main content area on the right.
 *
 * ```
 * +---------------------------------------------------------+
 * |                                                         |
 * |  +------------+  +-----------------------------------+  |
 * |  |            |  |                                   |  |
 * |  | Navigation |  |        What's New Documents       |  |
 * |  |   Panel    |  |          (LazyColumn)             |  |
 * |  |            |  |                                   |  |
 * |  | (Expand-   |  |  +-----------------------------+  |  |
 * |  |  able)     |  |  |                             |  |  |
 * |  |            |  |  |  Document 1 (Markdown)      |  |  |
 * |  |            |  |  |                             |  |  |
 * |  |            |  |  +-----------------------------+  |  |
 * |  |            |  |                                   |  |
 * |  |            |  |  +-----------------------------+  |  |
 * |  |            |  |  |                             |  |  |
 * |  |            |  |  |  Document 2 (Markdown)      |  |  |
 * |  |            |  |  |                             |  |  |
 * |  |            |  |  +-----------------------------+  |  |
 * |  |            |  |                                   |  |
 * |  +------------+  +-----------------------------------+  |
 * |                                                         |
 * +---------------------------------------------------------+
 * ```
 *
 * ### Features
 * * **Rendering:** Each [com.android.tools.idea.whatsnew.assistant.v2.model.WhatsNewMarkdownDocument] is rendered as a separate item within
 *   a `LazyColumn`.
 * * **Smooth Scrolling:** Because document heights can vary significantly and are difficult to predict, a custom
 *   [AdaptiveVerticalScrollbarAdapter] is used. This adapter employs a segment tree to maintain accurate scrollbar positioning and size.
 * * **Navigation:** An expandable navigation panel allows users to jump quickly between documents. Synchronization between the scroll
 *   position and the navigation selection is managed via an [ActiveItemTracker].
 * * **Image Handling:** Images within the Markdown are loaded asynchronously through a
 *   [com.android.tools.idea.whatsnew.assistant.v2.ui.composeutils.ImagePainterLoader] to ensure the UI remains responsive.
 *
 * @param markdownDocuments The parsed "What's New" Markdown documents to display.
 * @param whatsNewAssets The background resource assets to display.
 * @param imageLoader The loader used for Markdown images.
 * @param onUrlClick Callback invoked when a link in the Markdown is clicked.
 */
@Composable
internal fun WhatsNewEditorPanel(
  markdownDocuments: List<WhatsNewMarkdownDocument>,
  whatsNewAssets: WhatsNewAssets,
  imageLoader: ImagePainterLoader,
  onUrlClick: (String) -> Unit = BrowserUtil::browse,
) {
  if (markdownDocuments.isEmpty()) {
    return
  }

  val lazyListState = rememberLazyListState()
  val scrollbarAdapter = rememberWhatsNewScrollbarAdapter(lazyListState)

  Box(Modifier.fillMaxSize()) {
    WhatsNewAllDocuments(
      markdownDocuments = markdownDocuments,
      whatsNewAssets = whatsNewAssets,
      imageLoader = imageLoader,
      onUrlClick = onUrlClick,
      lazyListState = lazyListState,
      scrollbarAdapter = scrollbarAdapter,
    )
  }
}

@Composable
private fun WhatsNewAllDocuments(
  markdownDocuments: List<WhatsNewMarkdownDocument>,
  whatsNewAssets: WhatsNewAssets,
  imageLoader: ImagePainterLoader,
  onUrlClick: (String) -> Unit,
  lazyListState: LazyListState,
  scrollbarAdapter: AdaptiveVerticalScrollbarAdapter,
) {
  // First, we parse all Markdown files, so that we don't try to display "empty" documents in the "LazyColumn".
  // If we were to parse documents asynchronously while displaying the LazyColumn of documents, the scrollbar
  // would "jump" around and being jittery overall.
  val processor = JewelTheme.markdownProcessor
  var parsedMarkdownDocuments by
    rememberSaveable(markdownDocuments) { mutableStateOf<Map<WhatsNewMarkdownDocument, Result<List<MarkdownBlock>>>>(emptyMap()) }
  LaunchedEffect(markdownDocuments) {
    parsedMarkdownDocuments =
      withContext(Dispatchers.Default) {
        markdownDocuments.associateWith { runCatching { processor.processMarkdownDocument(it.fullMarkdownContents) } }
      }
  }
  if (parsedMarkdownDocuments.isEmpty()) {
    // Don't compose anything until we parsed all documents
    return
  }

  val tracker = lazyListStateActiveItemTracker(markdownDocuments, lazyListState)

  Row(Modifier.fillMaxWidth()) {
    TableOfContents(markdownDocuments, tracker)

    Divider(Orientation.Vertical, Modifier.fillMaxHeight())

    // A box with the Markdown document and a vertical scrollbar
    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp)) {
      val blockRenderer = createWhatsNewMarkdownBlockRenderer(onUrlClick, imageLoader)
      LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), state = lazyListState) {
        itemsIndexed(markdownDocuments, key = { _, document -> document }) { index, document ->
          if (index > 0) {
            Divider(
              orientation = Orientation.Horizontal,
              modifier = Modifier.padding(PaddingValues(top = 16.dp, bottom = 8.dp)).fillMaxWidth(),
              color = blockRenderer.rootStyling.thematicBreak.lineColor,
              thickness = 1.dp,
            )
          }
          WhatsNewMarkdown(
            markdownBlocks = parsedMarkdownDocuments[document]?.getOrNull() ?: emptyList(),
            rawMarkdown = document.fullMarkdownContents,
            markdownStyling = blockRenderer.rootStyling,
            blockRenderer = blockRenderer,
            selectable = true,
            onUrlClick = onUrlClick,
          )
        }
      }

      // The scrollbar, fed by our custom adapter
      VerticalScrollBarWithAdapter(modifier = Modifier.align(Alignment.CenterEnd), adapter = scrollbarAdapter)
    }
  }
}

@Composable
private fun TableOfContents(
  markdownDocuments: List<WhatsNewMarkdownDocument>,
  activeItemTracker: ActiveItemTracker<WhatsNewMarkdownDocument>,
) {
  Column(Modifier.fillMaxHeight().padding(horizontal = 4.dp, vertical = 16.dp)) {
    val activeDocument by activeItemTracker.currentItem
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.padding(horizontal = 8.dp)) {
      Icon(if (isExpanded) AllIconsKeys.General.ArrowLeft else AllIconsKeys.General.ArrowRight, contentDescription = "Menu")
    }
    if (isExpanded) {
      VerticallyScrollableContainer(Modifier.weight(1f)) {
        Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 8.dp, vertical = 8.dp)) {
          markdownDocuments.forEach { doc ->
            val isSelected = (doc == activeDocument)
            SimpleListItem(
              selected = isSelected,
              modifier =
                Modifier.fillMaxWidth().heightIn(min = JewelTheme.globalMetrics.rowHeight).clickable {
                  coroutineScope.launch { activeItemTracker.scrollToItem(doc) }
                },
              height = Dp.Unspecified,
            ) {
              Text("${doc.shortName} | ${doc.productVersion}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
          }
        }
      }
    }
  }
}

/**
 * We need a custom [org.jetbrains.jewel.markdown.Markdown] composable, so that we can pass the top level list of [MarkdownBlock] to our
 * [WhatsNewMarkdownBlockRenderer]. The default [org.jetbrains.jewel.markdown.Markdown] composable does a `topLevelBlocks.forEach {
 * renderer.renderBlock(it) }`, and we need instead a `renderer.RenderBlocks(topLevelBlocks)`.
 */
@Composable
@OptIn(ExperimentalJewelApi::class)
internal fun WhatsNewMarkdown(
  markdownBlocks: List<MarkdownBlock>,
  rawMarkdown: String,
  modifier: Modifier = Modifier,
  markdownStyling: MarkdownStyling,
  blockRenderer: MarkdownBlockRenderer,
  selectable: Boolean = false,
  enabled: Boolean = true,
  onUrlClick: (String) -> Unit = {},
) {
  // We keep the existing behavior in terms of where the rawMarkdown semantic is applied to
  MaybeSelectable(selectable, Modifier.thenIf(selectable) { semantics { this.rawMarkdown = rawMarkdown } }) {
    @Suppress("ModifierNotUsedAtRoot") // Intentional
    Column(
      modifier = modifier.thenIf(!selectable) { semantics { this.rawMarkdown = rawMarkdown } },
      verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
    ) {
      blockRenderer.RenderBlocks(markdownBlocks, enabled, onUrlClick, Modifier)
    }
  }
}

@Composable
private fun createWhatsNewMarkdownBlockRenderer(
  onUrlClick: (String) -> Unit,
  imageLoader: ImagePainterLoader,
): WhatsNewMarkdownBlockRenderer {
  val markdownFactory = JewelTheme.markdownFactory
  val markdownStyling = StudioBotMarkdownStylingCopy.create(onUrlClick = onUrlClick)
  val whatsNewImageExtension =
    remember(imageLoader) { ImagePainterLoaderMarkdownRendererExtension(imageLoader = imageLoader, popupEffect = true) }
  val renderExtensions =
    remember(markdownStyling, whatsNewImageExtension) { getDefaultMarkdownRenderExtensions(markdownStyling) + whatsNewImageExtension }
  val inlineRenderer = remember(markdownFactory, renderExtensions) { markdownFactory.createInlineMarkdownRenderer(renderExtensions) }
  val customRenderer =
    remember(markdownStyling, renderExtensions, inlineRenderer) {
      WhatsNewMarkdownBlockRenderer(rootStyling = markdownStyling, rendererExtensions = renderExtensions, inlineRenderer = inlineRenderer)
    }
  return customRenderer
}

@Composable
private fun VerticalScrollBarWithAdapter(modifier: Modifier, adapter: ScrollbarAdapter) {
  val jewelScrollbarStyle = JewelTheme.scrollbarStyle
  val composeScrollbarStyle = LocalScrollbarStyle.current
  VerticalScrollbar(
    modifier = modifier.padding(all = 2.dp),
    adapter = adapter,
    style =
      composeScrollbarStyle.copy(
        minimalHeight = 50.dp,
        thickness = 8.dp,
        shape = RoundedCornerShape(8.dp),
        unhoverColor = jewelScrollbarStyle.colors.thumbOpaqueBackground,
        hoverColor = jewelScrollbarStyle.colors.thumbOpaqueBackgroundHovered,
      ),
  )
}

/** This is a copy of the private function [org.jetbrains.jewel.markdown.MaybeSelectable] */
@Composable
private fun MaybeSelectable(selectable: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val movableContent = remember { movableContentOf(content) }
  if (selectable) {
    SelectionContainer(modifier) { movableContent() }
  } else {
    movableContent()
  }
}
