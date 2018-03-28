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
import com.android.tools.adtui.ImageUtils.lowQualityFastScale
import com.android.tools.idea.concurrent.EdtExecutor
import com.android.tools.idea.projectsystem.transform
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.viewmodel.ModuleResourcesBrowserViewModel
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*

private val LOG = Logger.getInstance(DesignAssetCellRenderer::class.java)
private val LINE_BORDER = JBUI.Borders.customLine(Gray.x41, 2)
private val LARGE_MAIN_CELL_BORDER = JBUI.Borders.empty(10, 30, 10, 30)
private val LARGE_CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(10, 0, 10, 0),
  LINE_BORDER
)

private val SMALL_MAIN_CELL_BORDER = JBUI.Borders.empty(5, 15, 5, 15)
private val SMALL_CONTENT_CELL_BORDER = BorderFactory.createCompoundBorder(
  JBUI.Borders.empty(5, 0, 5, 0),
  LINE_BORDER
)
private const val CHESSBOARD_CELL_SIZE = 10
private const val CHESSBOARD_PATTERN_SIZE = 2 * CHESSBOARD_CELL_SIZE
private val CHESSBOARD_COLOR_1 = JBColor(0xFF0000, 0x414243)
private val CHESSBOARD_COLOR_2 = JBColor(0x00FF00, 0x393A3B)
private val EMPTY_ICON = UIUtil.createImage(
  1, 1, BufferedImage.TYPE_INT_ARGB
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
    border = LARGE_MAIN_CELL_BORDER
  }

  private var contentWrapper: JComponent = JPanel(BorderLayout()).apply {
    border = LARGE_CONTENT_CELL_BORDER
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

  var useSmallMargins = false
    set(smallMargin) {
      field = smallMargin
      mainPanel.border = if (smallMargin) SMALL_MAIN_CELL_BORDER else LARGE_MAIN_CELL_BORDER
      contentWrapper.border = if (smallMargin) SMALL_CONTENT_CELL_BORDER else SMALL_CONTENT_CELL_BORDER
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
    val content = getContent(value, contentWrapper.width, contentWrapper.height, isSelected, index)
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
    }
    else {
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
  private val contentRatio = 0.2
  private val assetToImage = CacheBuilder.newBuilder()
    .softValues()
    .maximumSize(200)
    .build<DesignAssetSet, Image>()

  private val updateQueue = MergingUpdateQueue("DrawableResourceCellRenderer", 1000, true, null)

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
    var image = assetToImage.getIfPresent(designAssetSet)
    val targetSize = (height * (1 - contentRatio * 2)).toInt()

    if (image == null) {
      image = queueImageFetch(designAssetSet, index, targetSize)
    }
    else {
      // If an image is cached but does not fit into the content (i.e the list cell size was changed)
      // we do a fast rescaling in place and request a higher quality scaled image in the background
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, imageHeight)
      if (image != EMPTY_ICON && shouldScale(scale)) {
        val bufferedImage = ImageUtil.toBufferedImage(image)
        image = lowQualityFastScale(bufferedImage, scale, scale)
        queueImageFetch(designAssetSet, index, targetSize)
      }
    }
    imageIcon.image = image
    return content
  }

  /**
   * To avoid scaling too many times, we keep an acceptable window for the scale value before actually
   * requiring the scale.
   *
   * Since we have a margin around the image defined by [contentRatio], the image does not need to be resized
   * when it fits into this margin.
   */
  private fun shouldScale(scale: Double) = scale !in (1 - contentRatio)..(1 + contentRatio)

  /**
   * Get the scaling factor from [source] to [target] rounded to 2 digits.
   */
  private fun getScale(target: Int, source: Int) = Math.round(target * 100 / source.toDouble()) / 100.0

  /**
   * Add a call to [fetchImage] into [updateQueue]
   */
  private fun queueImageFetch(
    designAssetSet: DesignAssetSet,
    index: Int,
    targetSize: Int
  ): Image {
    updateQueue.queue(createUpdate(index) {
      fetchImage(targetSize, designAssetSet, index)
    })
    return EMPTY_ICON
  }

  /**
   * Create a new [Update] object overriding [Update.canEat] such that the queue will only keep the last instance of
   * all the [Update] having the same [identity].
   */
  private fun createUpdate(identity: Any, runnable: () -> Unit) = object : Update(identity) {
    override fun canEat(update: Update?) = this == update
    override fun run() = runnable()
  }

  /**
   * Request a new image to the [browserViewModel] that should fit inside [targetSize].
   *
   * Once the image is fetched, we check if it needs to be rescaled in a background Future
   * then we cache the final result or [EMPTY_ICON] if the future was cancelled and refresh the list.
   */
  private fun fetchImage(
    targetSize: Int,
    designAssetSet: DesignAssetSet,
    index: Int
  ) {
    val previewFuture = browserViewModel
      .getDrawablePreview(JBUI.size(targetSize), designAssetSet)
      .transform(PooledThreadExecutor.INSTANCE, { image -> scaleToFitIfNeeded(image, targetSize) })

    previewFuture.addListener(Runnable { useImage(previewFuture, designAssetSet, index) }, EdtExecutor.INSTANCE)
  }

  /**
   * Cache the image from the [previewFuture] and refresh the list
   */
  private fun useImage(
    previewFuture: ListenableFuture<Image>,
    designAssetSet: DesignAssetSet,
    index: Int
  ) {
    val finalImage = if (previewFuture.isCancelled) EMPTY_ICON else previewFuture.get()
    assetToImage.put(designAssetSet, finalImage)
    refreshListCallback(index)
  }

  /**
   * Scale the provided [image] to fit into [targetSize] if needed. It might be converted to a
   * [BufferedImage] before being scaled
   */
  private fun scaleToFitIfNeeded(image: Image?, targetSize: Int): Image {
    return if (image != null) {
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, imageHeight)
      if (shouldScale(scale)) {
        val newWidth = (image.getWidth(null) * scale).toInt()
        val newHeight = (imageHeight * scale).toInt()
        ImageUtil.toBufferedImage(image)
          .getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH)
      }
      else {
        image
      }
    }
    else {
      EMPTY_ICON
    }
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
