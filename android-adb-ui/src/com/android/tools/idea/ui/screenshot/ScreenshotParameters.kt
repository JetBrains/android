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
import com.android.sdklib.devices.DeviceManager.DeviceCategory.DEFAULT
import com.android.sdklib.devices.DeviceManager.DeviceCategory.USER
import com.android.sdklib.devices.DeviceManager.DeviceCategory.VENDOR
import com.android.sdklib.devices.Screen
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.device.DeviceArtDescriptor
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.SkinUtils
import com.android.tools.idea.ui.AndroidAdbUiBundle
import com.android.tools.sdk.DeviceManagers
import com.intellij.openapi.actionSystem.DataKey
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.log2

/** The ratio between distances of the worst and the best skin match. */
private const val MAX_MATCH_DISTANCE_RATIO = 2.0
private const val MIN_TABLET_DIAGONAL_SIZE = 7.0 // In inches.

/** Information required by [ScreenshotAction] */
class ScreenshotParameters
private constructor(
  val serialNumber: String,
  val deviceType: DeviceType,
  val deviceName: String,
  val framingOption: DeviceFramingOption?,
) {
  private var defaultFrameIndex: Int = 0
  val screenshotDecorator: ScreenshotDecorator = DeviceScreenshotDecorator()

  /**
   * Create a [ScreenshotParameters] with a skin defined in an AVD file
   *
   * @param serialNumber The serial number of the device
   * @param deviceType The type of the device
   * @param avdFolder The path of an AVD directory
   */
  constructor(
    serialNumber: String,
    deviceType: DeviceType,
    avdFolder: Path,
    avdManagerConnection: AvdManagerConnection = AvdManagerConnection.getDefaultAvdManagerConnection(),
  ) : this(serialNumber, deviceType, getAvdProperties(avdFolder, avdManagerConnection))

  /**
   * Create a [ScreenshotParameters] with a skin derived from a device model
   *
   * @param serialNumber The serial number of the device
   * @param deviceType The type of the device
   * @param deviceModel The model of the device
   */
  constructor(
    serialNumber: String,
    deviceType: DeviceType,
    deviceModel: String?,
  ) : this(serialNumber, deviceType, deviceModel ?: "Unknown", framingOptionFromModel(deviceModel))

  @Suppress("unused") // Incorrectly flagged as unused
  private constructor(
    serialNumber: String,
    deviceType: DeviceType,
    properties: Map<String, String>,
  ) : this(serialNumber, deviceType, properties.getDeviceName(), properties.getFramingOption())

  /** Returns the list of available framing options for the given image. */
  fun getFramingOptions(screenshotImage: ScreenshotImage): List<FramingOption> {
    if (framingOption != null) {
      return listOf(framingOption)
    }
    val framingOptions = mutableListOf<DeviceFramingOption>()
    val matchingSkins = findMatchingSkins(screenshotImage, getDevices())
    if (matchingSkins.isNotEmpty()) {
      val bestMatch = matchingSkins[0]
      for (skin in matchingSkins) {
        framingOptions.add(DeviceFramingOption(skin.displayName, skin.skinFolder))
      }
      framingOptions.sortBy { it.displayName }
      defaultFrameIndex = framingOptions.indexOfFirst { it.skinFolder == bestMatch.skinFolder }
    }
    if (deviceType == DeviceType.HANDHELD || deviceType == DeviceType.AUTOMOTIVE) {
      val displaySize = screenshotImage.displaySize
      val descriptors = DeviceArtDescriptor.getDescriptors(null).associateBy { it.id }
      if (deviceType == DeviceType.AUTOMOTIVE) {
        val automotive = descriptors["automotive_1024"]
        val screenSize = automotive?.getScreenSize(ScreenOrientation.LANDSCAPE)
        if (screenSize != null &&
            abs(screenSize.height.toDouble() / screenSize.width - displaySize.height.toDouble() / displaySize.width) < 0.01) {
          framingOptions.add(DeviceFramingOption(automotive))
        }
      }
      val displayDensity = screenshotImage.displayDensity
      val diagonalSize =
          if (displayDensity == 0) Double.NaN else hypot(displaySize.width.toDouble(), displaySize.height.toDouble()) / displayDensity
      val deviceArtId =
        when {
          deviceType == DeviceType.HANDHELD && (diagonalSize.isNaN() || diagonalSize < MIN_TABLET_DIAGONAL_SIZE) -> "phone"
          else -> "tablet"
        }
      descriptors[deviceArtId]?.let { framingOptions.add(DeviceFramingOption(it)) }
    }
    return framingOptions
  }

  /**
   * Returns the index of the default framing option for the given image. The default framing option
   * is ignored if [getFramingOptions] returned an empty list.
   */
  fun getDefaultFramingOption(): Int = defaultFrameIndex

  private fun findMatchingSkins(
    screenshotImage: ScreenshotImage,
    devices: Collection<Device>,
  ): List<MatchingSkin> {
    val displaySize = screenshotImage.displaySize
    val w = displaySize.width.toDouble()
    val h = displaySize.height.toDouble()
    val diagonalSize = hypot(w, h)
    val aspectRatio = h / w
    val matches = mutableListOf<MatchingSkin>()
    for (device in devices) {
      if (device.isDeprecated) {
        continue
      }
      if (device.isAutomotive() != (screenshotImage.deviceType == DeviceType.AUTOMOTIVE)) {
        continue
      }
      if (device.isTv() != (screenshotImage.deviceType == DeviceType.TV)) {
        continue
      }
      if (device.isWatch() != (screenshotImage.deviceType == DeviceType.WEAR)) {
        continue
      }
      if (device.isXrHeadset() != (screenshotImage.deviceType == DeviceType.XR_HEADSET)) {
        continue
      }
      if (device.isTablet() != (screenshotImage.deviceType == DeviceType.HANDHELD && diagonalSize >= MIN_TABLET_DIAGONAL_SIZE)) {
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
   * Adds a new device to a list of matching devices maintaining the [MatchingSkin.matchDistance]
   * ordering and keeping only the matches that don't differ from the best one by more than
   * [MAX_MATCH_DISTANCE_RATIO].
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

  private fun Device.isAutomotive() = tagId?.contains("automotive") ?: false

  private fun Device.isTv() = tagId?.contains("android-tv") ?: false

  private fun Device.isWatch() = tagId?.contains("wear") ?: false

  private fun Device.isXrHeadset() = tagId?.contains("android-xr") ?: false

  private fun Device.isTablet(): Boolean {
    if (isAutomotive() || isTv() || !isWatch() || isXrHeadset()) {
      return false
    }
    val screen = defaultHardware.screen
    return hypot(screen.xDimension / screen.xdpi, screen.yDimension / screen.ydpi) >= MIN_TABLET_DIAGONAL_SIZE
  }

  private fun Screen.isRound() = screenRound == ScreenRound.ROUND

  private class MatchingSkin(
    val displayName: String,
    val skinFolder: Path,
    val matchDistance: Double,
  )

  companion object {

    val DATA_KEY = DataKey.create<ScreenshotParameters>("ScreenshotParameters")

    private fun getAvdProperties(
      avdFolder: Path,
      avdManagerConnection: AvdManagerConnection,
      ): Map<String, String> {
      return avdManagerConnection.findAvdWithFolder(avdFolder)?.properties ?: emptyMap()
    }

    private fun Map<String, String>.getDeviceName() = getOrDefault("avd.ini.displayname", "Unknown")

    private fun Map<String, String>.getFramingOption(): DeviceFramingOption? {
      val skinPathValue = get("skin.path")
      if (skinPathValue == null || skinPathValue == "_no_skin") {
        return null
      }
      val skinPath = Path.of(skinPathValue)
      return DeviceFramingOption(AndroidAdbUiBundle.message("screenshot.framing.option.name"), skinPath)
    }

    private fun framingOptionFromModel(deviceModel: String?): DeviceFramingOption? {
      val device = getDevices().find { it.displayName == deviceModel } ?: return null
      val skinFolder = device.skinFolder ?: return null
      return DeviceFramingOption(device.displayName, skinFolder)
    }

    private fun getDevices(): Collection<Device> {
      val deviceManager =
        DeviceManagers.getDeviceManager(
          AndroidSdkHandler.getInstance(AndroidLocationsSingleton, null)
        )
      return deviceManager.getDevices(setOf(USER, DEFAULT, VENDOR))
    }

    private val skinHome: Path? = DeviceArtDescriptor.getBundledDescriptorsFolder()?.toPath()

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
  }
}
