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
import com.android.tools.adtui.ImageUtils.ellipticalClip
import com.android.tools.adtui.device.DeviceArtPainter
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * A [ScreenshotPostprocessor] using [DeviceArtPainter].
 */
class DeviceArtScreenshotPostprocessor : ScreenshotPostprocessor {
  @Slow
  override fun addFrame(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
    screenshotImage as DeviceScreenshotImage
    if (framingOption == null) {
      return if (screenshotImage.isRoundScreen) ellipticalClip(screenshotImage.image, backgroundColor) else screenshotImage.image
    }
    val frameDescriptor = (framingOption as DeviceArtFramingOption).deviceArtDescriptor
    val framedImage = DeviceArtPainter.createFrame(screenshotImage.image, frameDescriptor)
    return ImageUtils.cropBlank(framedImage, null) ?: throw IllegalArgumentException("The screenshot is completely transparent")
  }
}