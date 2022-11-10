/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.android.SdkConstants.ANDROID_HOME_ENV
import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.Rotation.SkinRotation
import com.google.common.base.Splitter
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil.parseInt
import java.awt.Dimension
import java.nio.file.Path

/**
 * Represents configuration of a running Emulator.
 */
class EmulatorConfiguration private constructor(
  val avdName: String,
  val avdFolder: Path,
  val displaySize: Dimension,
  val density: Int,
  val skinFolder: Path?,
  val isFoldable: Boolean,
  val isRollable: Boolean,
  val isWearOs: Boolean,
  val hasOrientationSensors: Boolean,
  val hasAudioOutput: Boolean,
  val initialOrientation: SkinRotation,
  val displayModes: List<DisplayMode>,
  val api: Int,
) {

  val displayWidth
    get() = displaySize.width

  val displayHeight
    get() = displaySize.height

  companion object {
    /**
     * Creates and returns an [EmulatorConfiguration] using data in the AVD folder.
     * Returns null if any of the essential data is missing.
     */
    fun readAvdDefinition(avdId: String, avdFolder: Path): EmulatorConfiguration? {
      val hardwareIniFile = avdFolder.resolve("hardware-qemu.ini")
      val keysToExtract1 = setOf("android.sdk.root", "hw.audioOutput", "hw.lcd.height", "hw.lcd.width", "hw.lcd.density",
                                 "hw.sensor.hinge.count", "hw.sensor.roll.count")
      val hardwareIni = readKeyValueFile(hardwareIniFile, keysToExtract1) ?: return null

      val sdkPath = hardwareIni["android.sdk.root"] ?: System.getenv(ANDROID_HOME_ENV) ?: ""
      val androidSdkRoot = avdFolder.resolve(sdkPath)
      val displayWidth = parseInt(hardwareIni["hw.lcd.width"], 0)
      val displayHeight = parseInt(hardwareIni["hw.lcd.height"], 0)
      if (displayWidth <= 0 || displayHeight <= 0) {
        return null
      }
      val density = parseInt(hardwareIni["hw.lcd.density"], 0)
      val hasAudioOutput = hardwareIni["hw.audioOutput"]?.toBoolean() ?: true
      val isFoldable = parseInt(hardwareIni["hw.sensor.hinge.count"], 0) > 0
      val isRollable = parseInt(hardwareIni["hw.sensor.roll.count"], 0) > 0

      val configIniFile = avdFolder.resolve("config.ini")
      val keysToExtract2 = setOf("avd.ini.displayname", "hw.resizable.configs", "hw.sensors.orientation", "hw.initialOrientation",
                                 "image.sysdir.1", "showDeviceFrame", "skin.path", "tag.id")
      val configIni = readKeyValueFile(configIniFile, keysToExtract2) ?: return null

      val avdName = configIni["avd.ini.displayname"] ?: avdId.replace('_', ' ')
      val initialOrientation = if ("landscape".equals(configIni["hw.initialOrientation"], ignoreCase = true))
          SkinRotation.LANDSCAPE else SkinRotation.PORTRAIT
      val skinPath = getSkinPath(configIni, androidSdkRoot)
      val isWearOs = configIni["tag.id"]?.equals("android-wear", ignoreCase = true) ?: false
      val hasOrientationSensors = configIni["hw.sensors.orientation"]?.equals("yes", ignoreCase = true) ?: true
      val displayModes = try {
        configIni["hw.resizable.configs"]?.let(::parseDisplayModes) ?: emptyList()
      }
      catch (e: Exception) {
        thisLogger().warn("Unrecognized value of the hw.resizable.configs property, \"${configIni["hw.resizable.configs"]}\"," +
                          " in $configIniFile")
        emptyList()
      }

      val api: Int
      val systemImage = configIni["image.sysdir.1"]
      if (systemImage != null) {
        val sourcePropertiesFile = androidSdkRoot.resolve(systemImage).resolve("source.properties")
        val sourceProperties = readKeyValueFile(sourcePropertiesFile, setOf("AndroidVersion.ApiLevel")) ?: return null
        api = parseInt(sourceProperties["AndroidVersion.ApiLevel"], 0)
      }
      else {
        api = 0
      }

      return EmulatorConfiguration(avdName = avdName,
                                   avdFolder = avdFolder,
                                   displaySize = Dimension(displayWidth, displayHeight),
                                   density = density,
                                   skinFolder = skinPath,
                                   isFoldable = isFoldable,
                                   isRollable = isRollable,
                                   isWearOs = isWearOs,
                                   hasOrientationSensors = hasOrientationSensors,
                                   hasAudioOutput = hasAudioOutput,
                                   initialOrientation = initialOrientation,
                                   displayModes = displayModes,
                                   api = api)
    }

    private fun getSkinPath(configIni: Map<String, String>, androidSdkRoot: Path): Path? {
      if ("no".equals(configIni["showDeviceFrame"], ignoreCase = true)) {
        return null
      }
      val skinPath = configIni["skin.path"]
      return if (skinPath == null || skinPath == "_no_skin") null else androidSdkRoot.resolve(skinPath)
    }

    /**
     * Parses a value of the "hw.resizable.configs" parameter that has the following format:
     * "phone-0-1080-2340-420, unfolded-1-1768-2208-420, tablet-2-1920-1200-240, desktop-3-1920-1080-160".
     */
    private fun parseDisplayModes(modes: String): List<DisplayMode> {
      return Splitter.on(',').trimResults().splitToList(modes).map(::parseDisplayMode)
    }

    private fun parseDisplayMode(mode: String): DisplayMode {
      val segments = Splitter.on('-').splitToList(mode)
      val displayModeId = DisplayModeValue.values()[segments[1].toInt()]
      val width = segments[2].toInt()
      val height = segments[3].toInt()
      if (width <= 0 || height <= 0) {
        throw IllegalArgumentException()
      }
      return DisplayMode(displayModeId, width, height)
    }
  }

  data class DisplayMode(val displayModeId: DisplayModeValue, val displaySize: Dimension) {

    constructor(displayModeId: DisplayModeValue, width: Int, height: Int) : this(displayModeId, Dimension(width, height))

    val width
      get() = displaySize.width

    val height
      get() = displaySize.height
  }
}
