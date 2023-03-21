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

import com.android.tools.adtui.ImageUtils
import java.awt.image.BufferedImage

@Deprecated(message = "Use com.android.tools.idea.ui.screenshot.ScreenshotImage")
class DeviceScreenshotImage(image: BufferedImage, screenRotationQuadrants: Int, val isRoundScreen: Boolean) :
    ScreenshotImage(image, screenRotationQuadrants) {

  /**
   * Returns the rotated screenshot.
   */
  override fun rotated(rotationQuadrants: Int): DeviceScreenshotImage {
    if (rotationQuadrants == 0) {
      return this
    }
    require(rotationQuadrants in -3..3)
    return DeviceScreenshotImage(ImageUtils.rotateByQuadrants(image, rotationQuadrants),
                                 (screenRotationQuadrants + rotationQuadrants + 4) % 4,
                                 isRoundScreen)
  }
}