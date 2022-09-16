/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.annotations.concurrency.Slow
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.ui.resourcemanager.model.Asset
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.function.Supplier
import javax.swing.ImageIcon
import kotlin.math.min

private val LOG = Logger.getInstance(SlowResourcePreviewManager::class.java)

/**
 * Interface for a preview provider for resources that take a significant time to render.
 */
interface SlowResourcePreviewProvider {
  /**
   * A placeholder to show while the preview is being rendered asynchronously.
   *
   * This is meant to be a very light weight image and is not expected to be of high quality either.
   */
  val previewPlaceholder: BufferedImage

  /**
   * Returns a [BufferedImage], the preview for the given [asset].
   *
   * May throw an Exception if there's an error while rendering or return a null [BufferedImage] if there's nothing to preview (if it fails
   * to resolve a theme attribute).
   */
  @Slow
  fun getSlowPreview(width: Int, height: Int, asset: Asset): BufferedImage?
}

/**
 * [AssetIconProvider] that helps rendering complex resources that take a significant time to render and updates
 * an [ImageIcon] each time [getIcon] is called. This means that the returned icon should
 * not be cached because it will change next time [getIcon] is called.
 *
 * The generated images are scaled to the provided dimensions and saved in [imageCache].
 *
 * @param resourcePreviewProvider The delegate from which resource previews are actually obtained.
 */
class SlowResourcePreviewManager(
  private val imageCache: ImageCache,
  private val resourcePreviewProvider: SlowResourcePreviewProvider
) : AssetIconProvider {
  private val fetchImageExecutor = service<FetchImageExecutor>()

  private val PLACEHOLDER_IMAGE = resourcePreviewProvider.previewPlaceholder
  private val imageIcon = JBImageIcon(PLACEHOLDER_IMAGE)
  private val contentRatio = 0.1

  override var supportsTransparency: Boolean = true

  override fun getIcon(assetToRender: Asset,
                       width: Int,
                       height: Int,
                       component: Component,
                       refreshCallback: () -> Unit,
                       shouldBeRendered: () -> Boolean): ImageIcon {
    if (height > 0 && width > 0) {
      val targetSize = Dimension(width, height)
      var image = fetchImage(assetToRender, refreshCallback, shouldBeRendered, targetSize, component)
      // If an image is cached but does not fit into the content (i.e the list cell size was changed)
      // we do a fast rescaling in place and request a higher quality scaled image in the background
      val imageWidth = image.getWidth(null)
      val imageHeight = image.getHeight(null)
      val scale = getScale(targetSize, Dimension(imageWidth, imageHeight))
      if (image != PLACEHOLDER_IMAGE && image != ERROR_IMAGE && shouldScale(scale)) {
        if (scale < 1) {
          // Prefer to scale down a high quality image.
          image = ImageUtil.scaleImage(image, scale)
        }
        else {
          // Return a low quality scaled version, then trigger a callback to request high quality version.
          val bufferedImage = ImageUtil.toBufferedImage(image)
          image = ImageUtils.lowQualityFastScale(bufferedImage, scale, scale)
          fetchImage(assetToRender, refreshCallback, shouldBeRendered, targetSize, component, true)
        }
      }
      imageIcon.image = when (image) {
        // Create the actual error icon for the desired size, ERROR_IMAGE it's just used as a placeholder to know there was an error.
        ERROR_IMAGE -> createFailedIcon(targetSize)
        // Scale the placeholder image.
        PLACEHOLDER_IMAGE -> if (shouldScale(scale)) ImageUtils.lowQualityFastScale(PLACEHOLDER_IMAGE, scale, scale) else PLACEHOLDER_IMAGE
        else -> image
      }
      supportsTransparency = image != ERROR_IMAGE
    }
    else {
      imageIcon.image = PLACEHOLDER_IMAGE
    }
    return imageIcon
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
   * Get the scaling factor from [source] to [target].
   */
  private fun getScale(target: Dimension, source: Dimension): Double {
    val xScale = target.width / source.getWidth()
    val yScale = target.height / source.getHeight()
    return min(xScale, yScale)
  }

  /**
   * Returns a rendering of [asset] if its already cached otherwise asynchronously render
   * the [asset] at the given [targetSize] and returns [PLACEHOLDER_IMAGE]
   *
   * @param isStillVisible The isStillVisible of the [asset] in the refreshCallBack used to refresh the correct cell
   * @param forceImageRender if true, render the [asset] even if it's already cached.
   * @return a placeholder image.
   */
  private fun fetchImage(asset: Asset,
                         refreshCallBack: () -> Unit,
                         isStillVisible: () -> Boolean,
                         targetSize: Dimension,
                         component: Component,
                         forceImageRender: Boolean = false): Image {
    return imageCache.computeAndGet(asset, PLACEHOLDER_IMAGE, forceImageRender, refreshCallBack) {
      if (isStillVisible()) {
        CompletableFuture.supplyAsync(Supplier {
          // Check for visibility again right before rendering.
          if (isStillVisible()) {
            try {
              val previewImage = resourcePreviewProvider.getSlowPreview((JBUI.pixScale(component) * targetSize.width).toInt(),
                                                                        (JBUI.pixScale(component) * targetSize.height).toInt(), asset)
                                 ?: throw Exception("Failed to resolve resource")
              return@Supplier scaleToFitIfNeeded(previewImage, targetSize, component)
            }
            catch (throwable: Exception) {
              LOG.warn("Error while rendering $asset", throwable)
              return@Supplier ERROR_IMAGE
            }
          }
          else {
            null
          }
        }, fetchImageExecutor)
      }
      else {
        CompletableFuture.completedFuture(null)
      }
    }
  }

  /**
   * Scale the provided [image] to fit into [targetSize] if needed. It might be converted to a
   * [BufferedImage] before being scaled
   */
  private fun scaleToFitIfNeeded(bufferedImage: BufferedImage, targetSize: Dimension, component: Component): BufferedImage {
    var image = ImageUtil.ensureHiDPI(bufferedImage, ScaleContext.create(component))
    val imageSize = Dimension(image.getWidth(null), image.getHeight(null))
    val scale = getScale(targetSize, imageSize)
    if (shouldScale(scale)) {
      val newWidth = (imageSize.width * scale).toInt()
      val newHeight = (imageSize.height * scale).toInt()
      if (newWidth > 0 && newHeight > 0) {
        image = ImageUtil.scaleImage(image, scale)
      }
    }
    if (image !is BufferedImage) {
      Logger.getInstance(SlowResourcePreviewManager::class.java).error("Not BufferedImage")
      return ImageUtil.toBufferedImage(image)
    }
    return image
  }
}

/**
 * Single-threaded executor, used to render previews for [SlowResourcePreviewManager].
 *
 * Is an Application Service, backed by the AppExecutorService.
 */
private class FetchImageExecutor : ExecutorService by
                                   AppExecutorUtil.createBoundedApplicationPoolExecutor(FetchImageExecutor::class.java.simpleName, 1)