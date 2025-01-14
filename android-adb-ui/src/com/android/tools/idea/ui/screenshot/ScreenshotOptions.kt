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

import com.android.prefs.AndroidLocationsSingleton
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRound
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Screen
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.sdk.DeviceManagers
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.EnumSet
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log2

/** The ratio between distances of the worst and the best skin match. */
private const val MAX_MATCH_DISTANCE_RATIO = 2.0
private const val MIN_TABLET_DIAGONAL_SIZE = 7.0 // In inches.

class ScreenshotOptions(
  val serialNumber: String,
  private val deviceModel: String?,
  private val orientationProvider: (() -> ScreenshotAction.ScreenshotRotation)?,
) {

  val screenshotViewerOptions: EnumSet<ScreenshotViewer.Option> =
      orientationProvider?.let { EnumSet.noneOf(ScreenshotViewer.Option::class.java) } ?:
      EnumSet.of(ScreenshotViewer.Option.ALLOW_IMAGE_ROTATION)
  val screenshotDecorator: ScreenshotDecorator = DeviceScreenshotDecorator()

  private var defaultFrameIndex: Int = 0
  private val skinHome: Path? = DeviceArtDescriptor.getBundledDescriptorsFolder()?.toPath()

  fun createScreenshotImage(image: BufferedImage, displayInfo: String, deviceType: DeviceType): ScreenshotImage {
    val rotation = orientationProvider?.invoke()
    val rotatedImage = ImageUtils.rotateByQuadrants(image, rotation?.imageRotationQuadrants ?: 0)
    return ScreenshotImage(rotatedImage, rotation?.orientationQuadrants ?: 0, deviceType, displayInfo)
  }

  /** Returns the list of available framing options for the given image. */
  fun getFramingOptions(screenshotImage: ScreenshotImage): List<FramingOption> {
    val deviceType = screenshotImage.deviceType
    val framingOptions = mutableListOf<DeviceFramingOption>()
    val deviceManager = DeviceManagers.getDeviceManager(AndroidSdkHandler.getInstance(AndroidLocationsSingleton, null))
    val devices = deviceManager.getDevices(
        EnumSet.of(DeviceManager.DeviceCategory.USER, DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR))
    val device = deviceModel?.let { devices.find { it.displayName == deviceModel } }
    if (device != null) {
      val skinFolder = device.skinFolder
      if (skinFolder != null) {
        return listOf(DeviceFramingOption(device.displayName, skinFolder))
      }
    }
    if (device == null) {
      val matchingSkins = findMatchingSkins(screenshotImage, devices)
      if (matchingSkins.isNotEmpty()) {
        val bestMatch = matchingSkins[0]
        for (skin in matchingSkins) {
          framingOptions.add(DeviceFramingOption(skin.displayName, skin.skinFolder))
        }
        framingOptions.sortBy { it.displayName }
        defaultFrameIndex = framingOptions.indexOfFirst { it.skinFolder == bestMatch.skinFolder }
      }
    }
    if (deviceType != DeviceType.WEAR && deviceType != DeviceType.TV) {
      val displaySize = screenshotImage.displaySize
      val displayDensity = screenshotImage.displayDensity
      if (displaySize != null && displayDensity.isFinite()) {
        val descriptors = DeviceArtDescriptor.getDescriptors(null).associateBy { it.id }
        if (deviceType == DeviceType.AUTOMOTIVE) {
          val automotive = descriptors["automotive_1024"]
          val screenSize = automotive?.getScreenSize(ScreenOrientation.LANDSCAPE)
          if (screenSize != null &&
              abs(screenSize.height.toDouble() / screenSize.width - displaySize.height.toDouble() / displaySize.width) < 0.01) {
            framingOptions.add(DeviceFramingOption(automotive))
          }
        }
        val diagonalSize = hypot(displaySize.width.toDouble(), displaySize.height.toDouble()) / displayDensity
        val deviceArtId = if (diagonalSize < MIN_TABLET_DIAGONAL_SIZE) "phone" else "tablet"
        descriptors[deviceArtId]?.let { framingOptions.add(DeviceFramingOption(it)) }
      }
    }
    return framingOptions
  }

  /**
   * Returns the index of the default framing option for the given image.
   * The default framing option is ignored if [getFramingOptions] returned an empty list.
   */
  fun getDefaultFramingOption(): Int = defaultFrameIndex

  private fun findMatchingSkins(screenshotImage: ScreenshotImage, devices: Collection<Device>): List<MatchingSkin> {
    val displaySize = screenshotImage.displaySize ?: return listOf()
    val w = displaySize.width.toDouble()
    val h = displaySize.height.toDouble()
    val diagonalSize = hypot(w, h)
    val isHandheld = screenshotImage.deviceType == DeviceType.HANDHELD
    val isTv = screenshotImage.deviceType == DeviceType.TV
    val isAutomotive = screenshotImage.deviceType == DeviceType.AUTOMOTIVE
    val isWatch = screenshotImage.deviceType == DeviceType.WEAR
    val isTablet = isHandheld && diagonalSize >= MIN_TABLET_DIAGONAL_SIZE
    val aspectRatio = h / w
    val matches = mutableListOf<MatchingSkin>()
    for (device in devices) {
      if (device.isAutomotive() != isAutomotive) {
        continue
      }
      if (device.isTv() != isTv) {
        continue
      }
      if (device.isWatch() != isWatch) {
        continue
      }
      if (device.isTablet() != isTablet) {
        continue
      }
      val screen = device.defaultHardware.screen
      if (screen.isRound() != screenshotImage.isRoundDisplay) {
        continue
      }
      val skinFolder = device.skinFolder ?: continue // Not interested in approximate matches without a skin.
      val width = screen.xDimension
      val height = screen.yDimension
      val deviceDiagonal = hypot(width.toDouble(), height.toDouble())
      val deviceAspectRatio = height.toDouble() / width.toDouble()
      val sizeDifference = abs(log2(deviceDiagonal / diagonalSize))
      val aspectRatioDifference = abs(log2(deviceAspectRatio / aspectRatio))
      val distance = hypot(sizeDifference, aspectRatioDifference)
      matches.addMatch(device.displayName, skinFolder, distance)
    }
    return matches
  }

  /**
   * Adds a new device to a list of matching devices maintaining the [MatchingSkin.matchDistance] ordering
   * and keeping only the matches that don't differ from the best one by more than [MAX_MATCH_DISTANCE_RATIO].
   */
  private fun MutableList<MatchingSkin>.addMatch(displayName: String, skinFolder: Path, matchDistance: Double) {
    if (isNotEmpty()) {
      if (matchDistance > get(0).matchDistance * MAX_MATCH_DISTANCE_RATIO) {
        return
      }
      for (i in indices.reversed()) {
        if (get(i).matchDistance <= matchDistance * MAX_MATCH_DISTANCE_RATIO) {
          break
        }
        removeAt(i)
      }
    }
    var i = 0
    while (i < size) {
      if (matchDistance < get(i).matchDistance) {
        break
      }
      i++
    }
    add(i, MatchingSkin(displayName, skinFolder, matchDistance))
  }

  private val Device.skinFolder: Path?
    get() {
      var skinFolder = defaultHardware.skinFile?.toPath() ?: return null
      if (skinFolder == SkinUtils.noSkin()) {
        return null
      }
      if (!skinFolder.isAbsolute) {
        skinFolder = skinHome?.resolve(skinFolder) ?: return null
      }
      if (!Files.exists(skinFolder.resolve("layout")) && !Files.exists(skinFolder.resolve("default/layout"))) {
        return null
      }
      return skinFolder
    }

  private fun Device.isAutomotive() =
      tagId?.contains("automotive") ?: false

  private fun Device.isTv() =
      tagId?.contains("android-tv") ?: false

  private fun Device.isWatch() =
      tagId?.contains("wear") ?: false

  private fun Device.isTablet(): Boolean {
    if (isAutomotive() || isTv() || !isWatch()) {
      return false
    }
    val screen = defaultHardware.screen
    return hypot(screen.xDimension / screen.xdpi, screen.yDimension / screen.ydpi) >= MIN_TABLET_DIAGONAL_SIZE
  }

  private fun Screen.isRound() =
      screenRound == ScreenRound.ROUND

  private class MatchingSkin(val displayName: String, val skinFolder: Path, val matchDistance: Double)
}