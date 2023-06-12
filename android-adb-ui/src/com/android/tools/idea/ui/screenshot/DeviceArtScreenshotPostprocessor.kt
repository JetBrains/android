/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.idea.ui.screenshot

import com.android.annotations.concurrency.Slow
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtPainter
import com.intellij.util.ui.ImageUtil.applyQualityRenderingHints
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * A [ScreenshotPostprocessor] using [DeviceArtPainter].
 */
internal class DeviceArtScreenshotPostprocessor : ScreenshotPostprocessor {
  @Slow
  override fun addFrame(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
    if (framingOption == null) {
      return if (screenshotImage.isRoundDisplay) circularClip(screenshotImage.image, backgroundColor) else screenshotImage.image
    }
    val frameDescriptor = (framingOption as DeviceArtFramingOption).deviceArtDescriptor
    val framedImage = DeviceArtPainter.createFrame(screenshotImage.image, frameDescriptor)
    return ImageUtils.cropBlank(framedImage, null) ?: throw IllegalArgumentException("The screenshot is completely transparent")
  }

  override val canClipToDisplayShape: Boolean
    get() = false

  @Suppress("UndesirableClassUsage")
  private fun circularClip(image: BufferedImage, backgroundColor: Color?): BufferedImage {
    val mask = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    mask.createGraphics().apply {
      applyQualityRenderingHints(this)
      val diameter = max(image.width, image.height).toDouble()
      fill(Area(Ellipse2D.Double(0.0, 0.0, diameter, diameter)))
      dispose()
    }
    val shapedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    shapedImage.createGraphics().apply {
      applyQualityRenderingHints(this)
      drawImage(image, 0, 0, null)
      composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
      drawImage(mask, 0, 0, null)
      if (backgroundColor != null) {
        color = backgroundColor
        composite = AlphaComposite.getInstance(AlphaComposite.DST_OVER)
        fillRect(0, 0, image.width, image.height)
      }
      dispose()
    }
    return shapedImage
  }
}