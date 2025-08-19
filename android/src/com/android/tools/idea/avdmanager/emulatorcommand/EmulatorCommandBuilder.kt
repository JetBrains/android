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
package com.android.tools.idea.avdmanager.emulatorcommand

import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.BootMode
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.QuickBoot
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.file.Path

/**
 * Builds emulator GeneralCommandLines such as `/home/user/Android/Sdk/emulator/emulator -netdelay
 * none -netspeed full -avd Pixel_4_API_30`.
 */
class EmulatorCommandBuilder(emulator: Path, val avd: AvdInfo) {
  /**
   * The path to the emulator executable, something like /home/user/Android/Sdk/emulator/emulator on
   * Linux.
   */
  private val emulator: Path =
    when (val emulatorBinary = avd.userSettings[UserSettingsKey.EMULATOR_BINARY]) {
      null -> emulator
      else -> emulator.resolveSibling(emulatorBinary).normalize()
    }

  var avdHome: Path? = null
  var sdkLocation: Path? = null
  var studioParams: Path? = null
  var launchInToolWindow = false
  var bootMode: BootMode = QuickBoot
  val studioEmuParams = mutableListOf<String>()

  fun build(): GeneralCommandLine {
    val command = GeneralCommandLine()
    command.exePath = emulator.toString()

    if (avdHome != null) {
      command.environment.put("ANDROID_AVD_HOME", avdHome.toString())
    }
    if (sdkLocation != null) {
      command.environment.put("ANDROID_HOME", sdkLocation.toString())
    }

    command.addParametersIfParameter2IsntNull(
      "-netdelay",
      avd.getProperty(ConfigKey.NETWORK_LATENCY),
    )
    command.addParametersIfParameter2IsntNull("-netspeed", avd.getProperty(ConfigKey.NETWORK_SPEED))

    command.addParameters(bootMode.arguments())

    command.addParametersIfParameter2IsntNull("-studio-params", studioParams)
    command.addParameters("-avd", avd.name)

    if (launchInToolWindow) {
      command.addParameter("-qt-hide-window")
      command.addParameter("-grpc-use-token")
      command.addParameters("-idle-grpc-timeout", "300")
    }

    command.addParameters(studioEmuParams)
    avd.userSettings[UserSettingsKey.COMMAND_LINE_OPTIONS]?.let {
      command.addParameters(parseCommandLineOptions(it))
    }

    if (StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.get()) {
      avd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS)?.let {
        command.addParameters(parseCommandLineOptions(it))
      }
    }
    return command
  }
}

private fun parseCommandLineOptions(options: String): List<String> {
  return options.trim().split("\\s+".toRegex())
}

private fun GeneralCommandLine.addParametersIfParameter2IsntNull(
  parameter1: String,
  parameter2: Any?,
) {
  parameter2 ?: return
  addParameters(parameter1, parameter2.toString())
}
