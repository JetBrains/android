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
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.*
import kotlin.properties.Delegates

private val LOG = Logger.getInstance(DesignAssetCellRenderer::class.java)

private val EMPTY_ICON = createIcon(Color.GREEN)

fun createIcon(color: Color?) = UIUtil.createImage(
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

  private val cardView = SingleAssetCard().apply {
    withChessboard = true
  }

  var useSmallMargins by Delegates.observable(false) { _, _, smallMargin -> cardView.useSmallMargins = smallMargin }

  override fun getListCellRendererComponent(
    list: JList<out DesignAssetSet>,
    value: DesignAssetSet,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean
  ): Component {
    cardView.title = value.name
    cardView.preferredSize = JBUI.size(list.fixedCellWidth, list.fixedCellHeight)
    cardView.thumbnail = getContent(value, cardView.thumbnailWidth, cardView.thumbnailHeight, isSelected, index)
    cardView.background = UIUtil.getListBackground(isSelected)

    return cardView
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
  private val imageProvider: (size: Dimension, designAssetSet: DesignAssetSet) -> ListenableFuture<out Image?>,
  private val refreshListCallback: (index: Int) -> Unit
) : DesignAssetCellRenderer() {

  private val imageIcon = ImageIcon(EMPTY_ICON)
  private val contentRatio = 0.2
  private val assetToImage = CacheBuilder.newBuilder()
    .softValues()
    .maximumSize(200)
    .build<DesignAssetSet, Image>()

  private val updateQueue = MergingUpdateQueue("DrawableResourceCellRenderer", 1000, true, null)

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
   * Request a new image to the [imageProvider] that should fit inside [targetSize].
   *
   * Once the image is fetched, we check if it needs to be rescaled in a background Future
   * then we cache the final result or [EMPTY_ICON] if the future was cancelled and refresh the list.
   */
  private fun fetchImage(
    targetSize: Int,
    designAssetSet: DesignAssetSet,
    index: Int
  ) {
    val previewFuture = imageProvider(JBUI.size(targetSize), designAssetSet)
      .transform(PooledThreadExecutor.INSTANCE) { image ->
        if (image == null) return@transform EMPTY_ICON
        scaleToFitIfNeeded(image, targetSize)
      }

    previewFuture.addListener(Runnable { useImage(previewFuture, designAssetSet, index) }, EdtExecutor.INSTANCE)
  }

  /**
   * Cache the image for the designAssetSet and refresh the list
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
  private fun scaleToFitIfNeeded(image: Image, targetSize: Int): Image {
    val imageHeight = image.getHeight(null)
    val scale = getScale(targetSize, imageHeight)
    return if (shouldScale(scale)) {
      val newWidth = (image.getWidth(null) * scale).toInt()
      val newHeight = (imageHeight * scale).toInt()
      ImageUtil.toBufferedImage(image)
        .getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_SMOOTH)
    }
    else {
      image
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
