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
package com.android.tools.idea.streaming.emulator

import com.android.SdkConstants.ANDROID_HOME_ENV
import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.Posture.PostureValue
import com.android.emulator.control.Rotation.SkinRotation
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.FOLDING_STATE_ICONS
import com.google.common.base.Splitter
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil.parseInt
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.Icon

/**
 * Represents configuration of a running Emulator.
 */
class EmulatorConfiguration private constructor(
  val avdName: String,
  val avdFolder: Path,
  val displaySize: Dimension,
  val density: Int,
  val skinFolder: Path?,
  val isWearOs: Boolean,
  val hasOrientationSensors: Boolean,
  val hasAudioOutput: Boolean,
  val initialOrientation: SkinRotation,
  val displayModes: List<DisplayMode>,
  val postures: List<PostureDescriptor>,
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
                                 "hw.sensor.hinge.resizable.config")
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

      val configIniFile = avdFolder.resolve("config.ini")
      val configIni = readKeyValueFile(configIniFile) ?: return null

      val avdName = configIni["avd.ini.displayname"] ?: avdId.replace('_', ' ')
      val initialOrientation = when {
        "landscape".equals(configIni["hw.initialOrientation"], ignoreCase = true) -> SkinRotation.LANDSCAPE
        else -> SkinRotation.PORTRAIT
      }
      val skinPath = getSkinPath(configIni, androidSdkRoot)
      val isWearOs = configIni["tag.id"]?.equals("android-wear", ignoreCase = true) ?: false
      val hasOrientationSensors = configIni["hw.sensors.orientation"]?.equals("yes", ignoreCase = true) ?: true
      val postureMode = if (StudioFlags.EMBEDDED_EMULATOR_RESIZABLE_FOLDING.get())
          parseInt(hardwareIni["hw.sensor.hinge.resizable.config"], -1) else -1
      val displayModes = try {
        configIni["hw.resizable.configs"]?.let { parseDisplayModes(it, postureMode) } ?: emptyList()
      }
      catch (e: Exception) {
        thisLogger().warn("Unrecognized value of the hw.resizable.configs property, \"${configIni["hw.resizable.configs"]}\"," +
                          " in $configIniFile")
        emptyList()
      }

      val postureValues = try {
        configIni["hw.sensor.posture_list"]?.let(::parsePostures) ?: emptyList()
      }
      catch (e: Exception) {
        thisLogger().warn("Unrecognized value of the hw.sensor.posture_list property, \"${configIni["hw.sensor.posture_list"]}\"," +
                          " in $configIniFile")
        emptyList()
      }
      var postures = emptyList<PostureDescriptor>()
      for (type in PostureDescriptor.ValueType.values()) {
        val key = when (type) {
          PostureDescriptor.ValueType.HINGE_ANGLE -> "hw.sensor.hinge_angles_posture_definitions"
          else -> "hw.sensor.roll_percentages_posture_definitions"
        }
        val ranges = configIni[key]
        if (ranges != null) {
          try {
            val postureRanges = parseRanges(ranges)
            if (postureValues.size == postureRanges.size) {
              postures = List(postureValues.size) { i ->
                PostureDescriptor(postureValues[i], type, postureRanges[i].first, postureRanges[i].second)
              }
            }
          }
          catch (e: Exception) {
            thisLogger().warn("Unrecognized value of the $key property, \"$ranges\", in $configIniFile")
          }
          break
        }
      }

      val api = configIni["image.sysdir.1"]?.let { systemImage ->
        val sourcePropertiesFile = androidSdkRoot.resolve(systemImage).resolve("source.properties")
        val sourceProperties = readKeyValueFile(sourcePropertiesFile, setOf("AndroidVersion.ApiLevel")) ?: return null
        parseInt(sourceProperties["AndroidVersion.ApiLevel"], 0)
      } ?: 0

      return EmulatorConfiguration(avdName = avdName,
                                   avdFolder = avdFolder,
                                   displaySize = Dimension(displayWidth, displayHeight),
                                   density = density,
                                   skinFolder = skinPath,
                                   isWearOs = isWearOs,
                                   hasOrientationSensors = hasOrientationSensors,
                                   hasAudioOutput = hasAudioOutput,
                                   initialOrientation = initialOrientation,
                                   displayModes = displayModes,
                                   postures = postures,
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
    private fun parseDisplayModes(modes: String, postureMode: Int): List<DisplayMode> =
        Splitter.on(',').trimResults().splitToList(modes).map { parseDisplayMode(it, postureMode) }

    private fun parseDisplayMode(mode: String, postureMode: Int): DisplayMode {
      val segments = Splitter.on('-').splitToList(mode)
      val displayModeId = DisplayModeValue.values()[segments[1].toInt()]
      val width = segments[2].toInt()
      val height = segments[3].toInt()
      if (width <= 0 || height <= 0) {
        throw IllegalArgumentException()
      }
      return DisplayMode(displayModeId, width, height, displayModeId.number == postureMode)
    }

    private fun parsePostures(postures: String): List<PostureValue> =
        Splitter.on(',').trimResults().splitToList(postures).map(::postureForNumber)

    private fun postureForNumber(postureIndex: String): PostureValue {
      return when (val posture = PostureValue.forNumber(postureIndex.toInt())) {
        PostureValue.POSTURE_UNKNOWN, PostureValue.UNRECOGNIZED, PostureValue.POSTURE_MAX -> throw IllegalArgumentException()
        else -> posture
      }
    }

    private fun parseRanges(ranges: String): List<Pair<Double, Double>> =
        Splitter.on(',').trimResults().splitToList(ranges).map(::parseRange)

    private fun parseRange(range: String): Pair<Double, Double> {
      val values = Splitter.on('-').trimResults().split(range).map(String::toDouble)
      if (values.size != 2) {
        throw IllegalArgumentException()
      }
      return Pair(values[0], values[1])
    }
  }

  data class DisplayMode(val displayModeId: DisplayModeValue, val displaySize: Dimension, val hasPostures: Boolean) {

    constructor(displayModeId: DisplayModeValue, width: Int, height: Int, hasPostures: Boolean) :
        this(displayModeId, Dimension(width, height), hasPostures)

    val width
      get() = displaySize.width

    val height
      get() = displaySize.height
  }

  /**
   * [minValue] and [maxValue] are hinge angles for foldables and roll percentages for rollables.
   */
  data class PostureDescriptor(val posture: PostureValue, val valueType: ValueType, val minValue: Double, val maxValue: Double) {

    val displayName: String = when (posture) {
      PostureValue.POSTURE_CLOSED -> "Closed"
      PostureValue.POSTURE_HALF_OPENED -> "Half-Open"
      PostureValue.POSTURE_OPENED -> "Open"
      PostureValue.POSTURE_FLIPPED -> "Flipped"
      PostureValue.POSTURE_TENT -> "Tent"
      else -> "Unknown"
    }

    val icon: Icon? = FOLDING_STATE_ICONS[displayName]

    enum class ValueType { HINGE_ANGLE, ROLL_PERCENTAGE }
  }
}
