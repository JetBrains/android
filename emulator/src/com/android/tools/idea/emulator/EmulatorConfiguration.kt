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

import com.google.common.base.Splitter
import com.intellij.openapi.util.text.StringUtil.parseInt
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents configuration of a running Emulator.
 */
class EmulatorConfiguration private constructor(
  val avdName: String,
  val avdFolder: Path,
  val displayWidth: Int,
  val displayHeight: Int,
  val density: Int,
  val skinFolder: Path?,
  val hasOrientationSensors: Boolean,
  val hasAudioOutput: Boolean
) {

  companion object {
    /**
     * Creates and returns an [EmulatorConfiguration] using data in the AVD folder.
     * Returns null if any of the essential data is missing.
     */
    fun readAvdDefinition(avdId: String, avdFolder: Path): EmulatorConfiguration? {
      val keysToExtract = setOf("avd.ini.displayname", "hw.lcd.height", "hw.lcd.width", "hw.lcd.density",
                                "showDeviceFrame", "skin.path", "hw.sensors.orientation")
      val configIni = readKeyValueFile(avdFolder.resolve("config.ini"), keysToExtract) ?: return null

      val avdName = configIni["avd.ini.displayname"] ?: avdId.replace('_', ' ')
      val displayWidth = parseInt(configIni["hw.lcd.width"], 0)
      val displayHeight = parseInt(configIni["hw.lcd.height"], 0)
      val density = parseInt(configIni["hw.lcd.density"], 0)
      val skinPath = getSkinPath(configIni, avdFolder)
      val hasOrientationSensors = configIni["hw.sensors.orientation"]?.equals("yes", ignoreCase = true) ?: true
      if (displayWidth <= 0 || displayHeight <= 0) {
        return null
      }

      val hardwareIni = readKeyValueFile(avdFolder.resolve("hardware-qemu.ini"), setOf("hw.audioOutput"))
      val hasAudioOutput = hardwareIni?.get("hw.audioOutput")?.toBoolean() ?: true

      return EmulatorConfiguration(avdName = avdName,
                                   avdFolder = avdFolder,
                                   displayWidth = displayWidth,
                                   displayHeight = displayHeight,
                                   density = density,
                                   skinFolder = skinPath,
                                   hasOrientationSensors = hasOrientationSensors,
                                   hasAudioOutput = hasAudioOutput)
    }

    private fun readKeyValueFile(file: Path, keysToExtract: Set<String>): Map<String, String>? {
      val splitter = Splitter.on('=').trimResults()
      val result = mutableMapOf<String, String>()
      try {
        for (line in Files.readAllLines(file)) {
          val keyValue = splitter.splitToList(line)
          if (keyValue.size == 2 && keysToExtract.contains(keyValue[0])) {
            result[keyValue[0]] = keyValue[1]
          }
        }
        return result
      }
      catch (e: IOException) {
        if (e.message == null) {
          EmulatorConfiguration.logger.error("Error reading ${file}")
        }
        else {
          EmulatorConfiguration.logger.error("Error reading ${file} - ${e.message}")
        }
        return null
      }
    }

    private fun getSkinPath(configIni: Map<String, String>, avdFolder: Path): Path? {
      if (configIni["showDeviceFrame"]?.equals("no", ignoreCase = true) == true) {
        return null
      }
      val skinPath = configIni["skin.path"]
      return if (skinPath == null || skinPath == "_no_skin") null else avdFolder.resolve(skinPath)
    }
  }
}