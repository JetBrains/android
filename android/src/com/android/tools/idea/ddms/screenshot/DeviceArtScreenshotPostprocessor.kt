/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot

import com.android.annotations.concurrency.Slow
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtPainter
import com.intellij.util.ui.ImageUtil.applyQualityRenderingHints
import java.awt.AlphaComposite
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import kotlin.math.max

/**
 * A [ScreenshotPostprocessor] using [DeviceArtPainter].
 */
class DeviceArtScreenshotPostprocessor : ScreenshotPostprocessor {
  @Slow
  override fun addFrame(screenshotImage: ScreenshotImage, framingOption: FramingOption?): BufferedImage {
    screenshotImage as DeviceScreenshotImage
    if (framingOption == null) {
      return if (screenshotImage.isRoundScreen) circularClip(screenshotImage.image) else screenshotImage.image
    }
    val frameDescriptor = (framingOption as DeviceArtFramingOption).deviceArtDescriptor
    val framedImage = DeviceArtPainter.createFrame(screenshotImage.image, frameDescriptor, false, false)
    return ImageUtils.cropBlank(framedImage, null) ?: throw IllegalArgumentException("The screenshot is completely transparent")
  }

  private fun circularClip(image: BufferedImage): BufferedImage {
    val mask = ImageUtils.createDipImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    mask.createGraphics().apply {
      applyQualityRenderingHints(this)
      val diameter = max(image.width, image.height).toDouble()
      fill(Area(Ellipse2D.Double(0.0, 0.0, diameter, diameter)))
      dispose()
    }
    val shapedImage = ImageUtils.createDipImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
    shapedImage.createGraphics().apply {
      applyQualityRenderingHints(this)
      drawImage(image, 0, 0, null)
      composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
      drawImage(mask, 0, 0, null)
      dispose()
    }
    return shapedImage
  }
}