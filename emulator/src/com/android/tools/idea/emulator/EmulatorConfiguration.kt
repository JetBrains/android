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

import com.android.emulator.EntryList
import java.lang.Boolean.parseBoolean
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Represents configuration of a running Emulator.
 */
class EmulatorConfiguration private constructor(
  val avdId: String,
  val avdName: String,
  val avdPath: Path,
  val displayWidth: Int,
  val displayHeight: Int,
  val hasAudioOutput: Boolean,
  val hasOrientationSensors: Boolean
) {

  companion object {
    /**
     * Creates and returns an [EmulatorConfiguration] using data contained in a proto message.
     * Returns null if any of the essential data is missing.
     */
    fun fromHardwareConfig(hardwareConfig: EntryList): EmulatorConfiguration? {
      var avdHome: String? = null
      var avdId: String? = null
      var avdName: String? = null
      var displayWidth: Int? = null
      var displayHeight: Int? = null
      var hasOrientationSensors = true
      var hasAudioOutput = true
      for (entry in hardwareConfig.entryList) {
        when (entry.key) {
          "android.avd.home" -> {
            avdHome = entry.value
          }
          "avd.id" -> {
            avdId = entry.value
          }
          "avd.name" -> {
            avdName = entry.value.replace('_', ' ')
          }
          "hw.lcd.width" -> {
            displayWidth = parseInt(entry.value)
          }
          "hw.lcd.height" -> {
            displayHeight = parseInt(entry.value)
          }
          "hw.sensors.orientation" -> {
            hasOrientationSensors = parseBoolean(entry.value)
          }
          "hw.audioOutput" -> {
            hasAudioOutput = parseBoolean(entry.value)
          }
        }
      }
      return if (avdHome != null && avdId != null && avdName != null &&
                 displayWidth != null && displayWidth > 0 && displayHeight != null && displayHeight > 0) {
        EmulatorConfiguration(avdId = avdId, avdName = avdName, avdPath = Paths.get(avdHome, avdId), displayWidth = displayWidth,
                              displayHeight = displayHeight, hasOrientationSensors = hasOrientationSensors, hasAudioOutput = hasAudioOutput)
      }
      else {
        null
      }
    }

    private fun parseInt(value: String): Int? {
      return try {
        Integer.parseInt(value)
      }
      catch (e: NumberFormatException) {
        null
      }
    }
  }
}