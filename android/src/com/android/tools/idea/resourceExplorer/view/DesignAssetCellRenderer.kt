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
import com.android.tools.idea.resourceExplorer.widget.AssetView
import com.android.tools.idea.resourceExplorer.widget.IssueLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollingUtil
import com.intellij.util.ui.ImageUtil
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
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer
import kotlin.math.min
import kotlin.math.roundToInt

private val LOG = Logger.getInstance(DesignAssetCellRenderer::class.java)

val EMPTY_ICON = createIcon(if (RESOURCE_DEBUG) JBColor.GREEN else Color(0, 0, 0, 0))
val ERROR_ICON = if (RESOURCE_DEBUG) createIcon(JBColor.RED) else EMPTY_ICON

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
    val thumbnailSize = assetView.thumbnailSize
    assetView.withChessboard = true
    assetView.selected = isSelected
    customizeComponent(assetView, value, list, thumbnailSize.width, thumbnailSize.height, isSelected, index)
    if (RESOURCE_DEBUG) {
      assetView.issueLevel = IssueLevel.ERROR
      assetView.isNew = true
    }
    return assetView
  }

  abstract fun customizeComponent(
    assetView: AssetView,
    designAssetSet: DesignAssetSet,
    list: JList<out DesignAssetSet>,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  )
}

class ColorResourceCellRenderer(
  private val project: Project,
  private val resourceResolver: ResourceResolver
) : DesignAssetCellRenderer() {
  private val backgroundPanel = ColorPreviewPanel()

  override fun customizeComponent(
    assetView: AssetView,
    designAssetSet: DesignAssetSet,
    list: JList<out DesignAssetSet>,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ) {

    // TODO compute in background
    val colors = resourceResolver.resolveMultipleColors(designAssetSet.resolveValue(resourceResolver), project).toSet().toList()
    assetView.title = designAssetSet.name
    assetView.subtitle = if (colors.size == 1) "#${ColorUtil.toHex(colors.first())}" else "Multiple colors"
    assetView.metadata = designAssetSet.versionCountString()
    backgroundPanel.colorList = colors
    assetView.thumbnail = backgroundPanel
  }

  inner class ColorPreviewPanel : JPanel(BorderLayout()) {
    internal var colorList = emptyList<Color>()

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
  private val contentRatio = 0.1
  private val drawablePreview = JLabel(imageIcon)

  override fun customizeComponent(
    assetView: AssetView,
    designAssetSet: DesignAssetSet,
    list: JList<out DesignAssetSet>,
    width: Int,
    height: Int,
    isSelected: Boolean,
    index: Int
  ) {
    customizeTextData(assetView, designAssetSet)
    val designAsset = designAssetSet.getHighestDensityAsset()
    val targetWidth = (width * (1 - contentRatio)).roundToInt()
    val targetHeight = (height * (1 - contentRatio)).roundToInt()
    if (targetHeight > 0 && targetWidth > 0) {
      val targetSize = Dimension(targetWidth, targetHeight)
      var image = fetchImage(designAsset, list, index, targetSize)
      // If an image is cached but does not fit into the content (i.e the list cell size was changed)
      // we do a fast rescaling in place and request a higher quality scaled image in the background
      val imageWidth = image.getWidth(null)
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, Dimension(imageWidth, imageHeight))
      if (image != EMPTY_ICON && image != ERROR_ICON && shouldScale(scale)) {
        val bufferedImage = ImageUtil.toBufferedImage(image)
        image = lowQualityFastScale(bufferedImage, scale, scale)
        fetchImage(designAsset, list, index, targetSize, true)
      }
      imageIcon.image = image
    }
    else {
      imageIcon.image = EMPTY_ICON
    }
    assetView.thumbnail = drawablePreview
  }


  private fun customizeTextData(assetView: AssetView, designAssetSet: DesignAssetSet) {
    assetView.title = designAssetSet.name
    assetView.subtitle = designAssetSet.getHighestDensityAsset().type.displayName
    assetView.metadata = designAssetSet.versionCountString()
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
  private fun getScale(target: Dimension, source: Dimension): Double {
    val xScale = (target.width * 100 / source.getWidth()).roundToInt() / 100.0
    val yScale = (target.height * 100 / source.getHeight()).roundToInt() / 100.0
    return min(xScale, yScale)
  }

  /**
   * Returns a rendering of [designAsset] if its already cached otherwise asynchronously render
   * the [designAsset] at the given [targetSize] and returns [EMPTY_ICON]
   *
   * @param index The index of the designAsset in the list used to refresh the correct cell
   * @param forceImageRender if true, render the [designAsset] even if it's already cached.
   * @return a placeholder image.
   */
  private fun fetchImage(designAsset: DesignAsset,
                         list: JList<out DesignAssetSet>,
                         index: Int,
                         targetSize: Dimension,
                         forceImageRender: Boolean = false): Image {
    return imageCache.computeAndGet(designAsset, EMPTY_ICON, forceImageRender) {
      if (ScrollingUtil.isIndexFullyVisible(list, index)) {
        imageProvider(targetSize, designAsset)
          .thenApplyAsync { image -> image ?: ERROR_ICON }
          .thenApply { image -> scaleToFitIfNeeded(image, targetSize) }
          .exceptionally { throwable -> LOG.error("Error while rendering $designAsset", throwable); ERROR_ICON }
          .whenCompleteAsync(BiConsumer { _, _ -> refreshListCallback(index) }, EdtExecutor.INSTANCE)
      }
      else CompletableFuture.completedFuture(null)
    }
  }

  /**
   * Scale the provided [image] to fit into [targetSize] if needed. It might be converted to a
   * [BufferedImage] before being scaled
   */
  private fun scaleToFitIfNeeded(image: Image, targetSize: Dimension): Image {
    val imageSize = Dimension(image.getWidth(null), image.getHeight(null))
    val scale = getScale(targetSize, imageSize)
    if (shouldScale(scale)) {
      val newWidth = (imageSize.width * scale).toInt()
      val newHeight = (imageSize.height * scale).toInt()
      if (newWidth > 0 && newHeight > 0) {
        return ImageUtil.toBufferedImage(image)
          .getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH)
      }
    }
    return image
  }
}

private fun DesignAssetSet.versionCountString(): String {
  val size = designAssets.size
  return "$size $VERSION".pluralize(size)
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