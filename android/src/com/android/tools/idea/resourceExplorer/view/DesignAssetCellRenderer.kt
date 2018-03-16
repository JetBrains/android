/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ModuleResourcesBrowserViewModel
import com.google.common.cache.CacheBuilder
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

private val LOG = Logger.getInstance(DesignAssetCellRenderer::class.java)
private val MAIN_CELL_BORDER = JBUI.Borders.empty(10, 30, 10, 30)
private val CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10, 0, 10, 0),
  JBUI.Borders.customLine(Gray.x41, 2)
)
private const val CHESSBOARD_CELL_SIZE = 10
private const val CHESSBOARD_PATTERN_SIZE = 2 * CHESSBOARD_CELL_SIZE
private val CHESSBOARD_COLOR_1 = JBColor(0xFF0000, 0x414243)
private val CHESSBOARD_COLOR_2 = JBColor(0x00FF00, 0x393A3B)
private val ICON_SIZE = JBUI.size(128)
private val EMPTY_ICON = UIUtil.createImage(
  ICON_SIZE.width, ICON_SIZE.height, BufferedImage.TYPE_INT_ARGB
)

/**
 * Base renderer for the asset list.
 */
abstract class DesignAssetCellRenderer : ListCellRenderer<DesignAssetSet> {

  var title: String
    get() = titleLabel.text
    set(value) {
      titleLabel.text = value
    }

  var subtitle: String
    get() = subtitleLabel.text
    set(value) {
      subtitleLabel.text = value
    }

  private val mainPanel = JPanel(BorderLayout()).apply {
    border = MAIN_CELL_BORDER
  }

  private var contentWrapper: JComponent = JPanel(BorderLayout()).apply {
    border = CONTENT_CELL_BORDER
  }

  private val bottomPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
  }
  private val titleLabel = JLabel()
  private val subtitleLabel = JLabel()

  init {
    mainPanel.add(contentWrapper)
    mainPanel.add(bottomPanel, BorderLayout.SOUTH)
    with(bottomPanel) {
      add(titleLabel)
      add(subtitleLabel, BorderLayout.SOUTH)
      add(JLabel(StudioIcons.Common.WARNING), BorderLayout.EAST)
    }
  }

  override fun getListCellRendererComponent(
    list: JList<out DesignAssetSet>,
    value: DesignAssetSet,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    mainPanel.preferredSize = JBUI.size(list.fixedCellWidth, list.fixedCellHeight)
    contentWrapper.removeAll()
    val content = getContent(value, list.fixedCellWidth, list.fixedCellHeight, isSelected, index)
    if (content != null) {
      contentWrapper.add(content)
    }
    mainPanel.background = UIUtil.getListBackground(isSelected)
    contentWrapper.background = mainPanel.background

    return mainPanel
  }

  abstract fun getContent(
    designAssetSet: DesignAssetSet,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ): JComponent?
}

class ColorResourceCellRenderer(
  private val project: Project,
  private val resourceResolver: ResourceResolver
) : DesignAssetCellRenderer() {
  private val backgroundPanel = ColorPreviewPanel()

  override fun getContent(
    designAssetSet: DesignAssetSet,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ): JComponent? {
    title = designAssetSet.name

    // TODO compute in background
    val colors = resourceResolver.resolveMultipleColors(designAssetSet.resolveValue(resourceResolver), project).toSet().toList()
    backgroundPanel.colorList = colors
    backgroundPanel.colorCodeLabel.text = if (colors.size == 1) {
      "#${ColorUtil.toHex(colors.first())}"
    } else {
      ""
    }
    return backgroundPanel
  }

  inner class ColorPreviewPanel : JPanel(BorderLayout()) {
    internal var colorList = emptyList<Color>()
    internal val colorCodeLabel = JLabel()

    init {
      add(colorCodeLabel, BorderLayout.SOUTH)
    }

    override fun paintComponent(g: Graphics) {
      if (colorList.isEmpty()) return

      val splitSize = width / colorList.size
      for (i in 0 until colorList.size) {
        g.color = colorList[i]
        g.fillRect(i * splitSize, 0, splitSize, height)
      }
    }
  }
}

/**
 * Renderer for drawable resource type.
 * Rendering of the drawable is done in the background and [refreshListCallback] is
 * called once it's finished.
 */
class DrawableResourceCellRenderer(
  private val browserViewModel: ModuleResourcesBrowserViewModel,
  private val refreshListCallback: (index: Int) -> Unit
) : DesignAssetCellRenderer() {

  private val imageIcon = ImageIcon(EMPTY_ICON)
  private val content = ChessBoardPanel()
  private val assetToImage = CacheBuilder.newBuilder()
    .softValues()
    .maximumSize(200)
    .build<DesignAssetSet, Image>()

  init {
    content.add(JLabel(imageIcon).apply {
      border = JBUI.Borders.empty(18)
    })
  }

  override fun getContent(
    designAssetSet: DesignAssetSet,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ): JComponent? {
    title = designAssetSet.name
    val image = assetToImage.getIfPresent(designAssetSet) ?: fetchImage(designAssetSet, index)
    imageIcon.image = image
    return content
  }

  private fun fetchImage(designAssetSet: DesignAssetSet, index: Int): Image {
    val previewFuture = browserViewModel.getDrawablePreview(ICON_SIZE, designAssetSet)
    val listener = Runnable {
      val image = if (!previewFuture.isCancelled) previewFuture.get() ?: EMPTY_ICON else EMPTY_ICON
      assetToImage.put(designAssetSet, image)
      refreshListCallback(index)
    }
    previewFuture.addListener(listener, EdtExecutor.INSTANCE)
    return EMPTY_ICON
  }
}

private class ChessBoardPanel : JPanel(BorderLayout()) {
  private var pattern: BufferedImage? = null

  private fun createChessBoardPattern(g: Graphics, patternSize: Int, cellSize: Int): BufferedImage {
    return UIUtil.createImage(g, patternSize, patternSize, BufferedImage.TYPE_INT_ARGB).apply {
      val imageGraphics = this.graphics
      imageGraphics.color = CHESSBOARD_COLOR_1
      imageGraphics.fillRect(0, 0, patternSize, patternSize)
      imageGraphics.color = CHESSBOARD_COLOR_2
      imageGraphics.fillRect(0, cellSize, cellSize, cellSize)
      imageGraphics.fillRect(cellSize, 0, cellSize, cellSize)
    }
  }

  override fun paintComponent(g: Graphics) {
    if (pattern == null) {
      pattern = createChessBoardPattern(g, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_CELL_SIZE)
    }
    pattern?.let {
      (g as Graphics2D).paint = TexturePaint(it, Rectangle(0, 0, CHESSBOARD_PATTERN_SIZE, CHESSBOARD_PATTERN_SIZE))
      g.fillRect(0, 0, size.width, size.height)
    }
  }
}

private fun DesignAssetSet.resolveValue(
  resourceResolver: ResourceResolver
): ResourceValue? {
  val resourceItem = this.getHighestDensityAsset().resourceItem
  val resolvedValue = resourceResolver.resolveResValue(resourceItem.resourceValue)
  if (resolvedValue == null) {
    LOG.warn("${resourceItem.name} couldn't be resolved")
  }
  return resolvedValue
}
