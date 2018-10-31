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
import com.android.tools.idea.res.resolveMultipleColors
import com.android.tools.idea.resourceExplorer.ImageCache
import com.android.tools.idea.resourceExplorer.editor.RESOURCE_DEBUG
import com.android.tools.idea.resourceExplorer.model.DesignAsset
import com.android.tools.idea.resourceExplorer.model.DesignAssetSet
import com.android.tools.idea.resourceExplorer.widget.IssueLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

private val LOG = Logger.getInstance(DesignAssetCellRenderer::class.java)

private val EMPTY_ICON = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
private val ERROR_ICON = if (RESOURCE_DEBUG) createIcon(JBColor.RED) else EMPTY_ICON

private const val VERSION = "version"

private fun String.pluralize(size: Int) = this + (if (size > 1) "s" else "")

fun createIcon(color: Color?): BufferedImage = UIUtil.createImage(
  80, 80, BufferedImage.TYPE_INT_ARGB
).apply {
  with(createGraphics()) {
    this.color = color
    fillRect(0, 0, 80, 80)
    dispose()
  }
}

/**
 * Base renderer for the asset list.
 */
abstract class DesignAssetCellRenderer : ListCellRenderer<DesignAssetSet> {

  override fun getListCellRendererComponent(
    list: JList<out DesignAssetSet>,
    value: DesignAssetSet,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    val assetView = (list as AssetListView).assetView
    assetView.withChessboard = true
    assetView.title = value.name
    assetView.subtitle = value.getHighestDensityAsset().type.displayName
    val size = value.designAssets.size
    assetView.metadata = "$size $VERSION".pluralize(size)
    val thumbnailSize = assetView.thumbnailSize
    assetView.thumbnail = getContent(value, thumbnailSize.width, thumbnailSize.height, isSelected, index)
    assetView.selected = isSelected
    if (RESOURCE_DEBUG) {
      assetView.issueLevel = IssueLevel.ERROR
      assetView.isNew = true
    }
    return assetView
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
  private val imageProvider: (size: Dimension, designAsset: DesignAsset) -> CompletableFuture<out Image?>,
  private val imageCache: ImageCache,
  private val refreshListCallback: (index: Int) -> Unit
) : DesignAssetCellRenderer() {

  private val imageIcon = ImageIcon(EMPTY_ICON)
  private val contentRatio = 0.2
  private val drawablePreview = JLabel(imageIcon).apply {
    border = JBUI.Borders.empty(18)
  }

  override fun getContent(
    designAssetSet: DesignAssetSet,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ): JComponent? {
    val designAsset = designAssetSet.getHighestDensityAsset()
    val targetSize = (height * (1 - contentRatio * 2)).toInt()
    if (targetSize > 0) {
      var image = fetchImage(designAsset, index, targetSize)
      // If an image is cached but does not fit into the content (i.e the list cell size was changed)
      // we do a fast rescaling in place and request a higher quality scaled image in the background
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, imageHeight)
      if (image != EMPTY_ICON && image != ERROR_ICON && shouldScale(scale)) {
        val bufferedImage = ImageUtil.toBufferedImage(image)
        image = lowQualityFastScale(bufferedImage, scale, scale)
        fetchImage(designAsset, index, targetSize, true)
      }
      imageIcon.image = image
    }
    return drawablePreview
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
   * Returns a rendering of [designAsset] if its already cached otherwise asynchronously render
   * the [designAsset] at the given [targetSize] and returns [EMPTY_ICON]
   *
   * @param index The index of the designAsset in the list used to refresh the correct cell
   * @param forceImageRender if true, render the [designAsset] even if it's already cached.
   * @return a placeholder image.
   */
  private fun fetchImage(designAsset: DesignAsset,
                         index: Int,
                         targetSize: Int,
                         forceImageRender: Boolean = false): Image {

    return imageCache.computeAndGet(designAsset, EMPTY_ICON, forceImageRender) {
      imageProvider(JBUI.size(targetSize), designAsset).toCompletableFuture()
        .thenApply { image -> image ?: ERROR_ICON }
        .thenApply { image -> scaleToFitIfNeeded(image, targetSize) }
        .exceptionally { throwable -> LOG.error("Error while rendering $designAsset", throwable); ERROR_ICON }
        .whenCompleteAsync(BiConsumer { _, _ -> refreshListCallback(index) }, EdtExecutor.INSTANCE)
    }
  }

  /**
   * Scale the provided [image] to fit into [targetSize] if needed. It might be converted to a
   * [BufferedImage] before being scaled
   */
  private fun scaleToFitIfNeeded(image: Image, targetSize: Int): Image {
    val imageHeight = image.getHeight(null)
    val scale = getScale(targetSize, imageHeight)
    if (shouldScale(scale)) {
      val newWidth = (image.getWidth(null) * scale).toInt()
      val newHeight = (imageHeight * scale).toInt()
      if (newWidth > 0 && newHeight > 0) {
        return ImageUtil.toBufferedImage(image)
          .getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH)
      }
    }
    return image
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
