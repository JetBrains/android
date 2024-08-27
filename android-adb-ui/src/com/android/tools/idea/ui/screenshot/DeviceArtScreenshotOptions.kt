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

import com.android.resources.ScreenOrientation
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.ui.screenshot.ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION
import java.awt.image.BufferedImage
import java.util.EnumSet
import kotlin.math.min

/**
 * Parameters for obtaining device screenshots using device art framing.
 */
class DeviceArtScreenshotOptions(
  override val serialNumber: String,
  val deviceModel: String?
) : ScreenshotAction.ScreenshotOptions {

  override val screenshotViewerOptions: EnumSet<ScreenshotViewer.Option> = EnumSet.of(ALLOW_IMAGE_ROTATION)

  override val screenshotDecorator: ScreenshotDecorator = DeviceArtScreenshotDecorator()

  override fun createScreenshotImage(image: BufferedImage, displayInfo: String, deviceType: DeviceType): ScreenshotImage {
    return ScreenshotImage(image, 0, deviceType, displayInfo)
  }

  override fun getFramingOptions(screenshotImage: ScreenshotImage): List<FramingOption> {
    val imgAspectRatio = screenshotImage.width.toDouble() / screenshotImage.height
    val orientation = if (imgAspectRatio >= 1 - DeviceArtDescriptor.EPSILON) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT
    val allDescriptors = DeviceArtDescriptor.getDescriptors(null)
    return allDescriptors
      .filter { it.canFrameImage(screenshotImage.image, orientation) }
      .filter { it.isCompatible(screenshotImage) }
      .map { DeviceArtFramingOption(it) }
  }

  override fun getDefaultFramingOption(framingOptions: List<FramingOption>, screenshotImage: ScreenshotImage): Int {
    if (deviceModel != null) {
      val index = findFrameIndexForDeviceModel(framingOptions, deviceModel)
      if (index >= 0) {
        return index
      }
    }
    // Assume that if the min resolution is > 1280, then we are on a tablet.
    val defaultDevice = if (min(screenshotImage.width, screenshotImage.height) > 1280) "Generic Tablet" else "Generic Phone"
    // If we can't find anything (which shouldn't happen since we should get the Generic Phone/Tablet),
    // default to the first one.
    return findFrameIndexForDeviceModel(framingOptions, defaultDevice).coerceAtLeast(0)
  }

  private fun findFrameIndexForDeviceModel(frames: List<FramingOption>, deviceModel: String): Int {
    return frames.indexOfFirst { it.displayName.equals(deviceModel, ignoreCase = true) }
  }

  private fun DeviceArtDescriptor.isCompatible(screenshotImage: ScreenshotImage): Boolean {
    if (!screenshotImage.isWear) {
      return true
    }
    // In the case of wear devices, we want to filter out phone/tablet descriptors and only end up with the wear descriptor
    // that matches the shape of the wear device.
    val compatibleDeviceArtId = if (screenshotImage.isRoundDisplay) "watch_round" else "watch_square"
    return this.id == compatibleDeviceArtId
  }
}
