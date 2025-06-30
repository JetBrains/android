/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.adtui.ImageUtils.ellipticalClip
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.adtui.device.DeviceArtPainter
import com.android.tools.adtui.device.SkinDefinition
import com.android.tools.adtui.device.SkinDefinitionCache
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/** Screenshot framer accepting a [DeviceFramingOption]. */
class DeviceScreenshotDecorator : ScreenshotDecorator {

  /** Keys are skin folders, values are skin subfolders keyed by the corresponding display sizes. */
  private val skinSubfolderInfo = mutableMapOf<Path, Map<Dimension, Path>>()

  @Slow
  override fun decorate(screenshotImage: ScreenshotImage, framingOption: FramingOption?, backgroundColor: Color?): BufferedImage {
    if (framingOption == null) {
      return if (screenshotImage.isRoundDisplay) ellipticalClip(screenshotImage.image, backgroundColor) else screenshotImage.image
    }
    framingOption as DeviceFramingOption
    val skinFolder = framingOption.skinFolder
    return when {
      skinFolder != null -> addSkinBasedFrame(screenshotImage, skinFolder)
      framingOption.deviceArtDescriptor != null -> addDeviceArtBasedFrame(screenshotImage, framingOption.deviceArtDescriptor)
      else -> screenshotImage.image
    }
  }

  override val canClipToDisplayShape: Boolean
    get() = false

  private fun addSkinBasedFrame(screenshotImage: ScreenshotImage, skinFolder: Path): BufferedImage {
    val skinDefinition = getSkinDefinition(skinFolder, screenshotImage.displaySize) ?: return screenshotImage.image
    return screenshotImage.decorate(true, skinDefinition, null)
  }

  private fun getSkinDefinition(skinFolder: Path, displaySize: Dimension?): SkinDefinition? {
    if (Files.exists(skinFolder.resolve("layout"))) {
      val skinDefinition = SkinDefinitionCache.getInstance().getSkinDefinition(skinFolder)
      if (skinDefinition != null || displaySize == null) {
        return skinDefinition
      }
    }
    val subfolderInfo = skinSubfolderInfo.computeIfAbsent(skinFolder) {
      getSubfolderSkinInfo(skinFolder)
    }
    return subfolderInfo[displaySize]?.let { SkinDefinitionCache.getInstance().getSkinDefinition(it) }
  }

  private fun getSubfolderSkinInfo(skinFolder: Path): Map<Dimension, Path> {
    val map = mutableMapOf<Dimension, Path>()
    Files.list(skinFolder).use { stream ->
      stream
        .filter { Files.isDirectory(it) }
        .forEach { dir ->
          try {
            val displaySize = SkinDefinition.getSkinDisplaySize(dir)
            map[displaySize] = dir
          }
          catch (_: Exception) {
          }
        }
    }
    return map
  }

  private fun addDeviceArtBasedFrame(screenshotImage: ScreenshotImage, frameDescriptor: DeviceArtDescriptor): BufferedImage {
    val framedImage = DeviceArtPainter.createFrame(screenshotImage.image, frameDescriptor)
    return ImageUtils.cropBlank(framedImage, null) ?: throw IllegalArgumentException("The screenshot is completely transparent")
  }
}