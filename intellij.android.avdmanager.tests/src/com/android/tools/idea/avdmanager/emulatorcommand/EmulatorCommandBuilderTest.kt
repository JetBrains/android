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
import com.android.sdklib.internal.avd.BootSnapshot
import com.android.sdklib.internal.avd.ColdBoot
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.tools.idea.flags.StudioFlags
import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class EmulatorCommandBuilderTest {
  private lateinit var fileSystem: FileSystem
  private lateinit var myEmulator: Path
  private lateinit var avd: AvdInfo

  @Before
  fun initEmulator() {
    fileSystem = Jimfs.newFileSystem(Configuration.unix())
    myEmulator = fileSystem.getPath("/home/user/Android/Sdk/emulator/emulator")
  }

  @Before
  fun initAvd() {
    avd = mock<AvdInfo>()
    whenever(avd.name).thenReturn("Pixel_4_API_30")
  }

  @After
  fun tearDown() {
    try {
      fileSystem.close()
    } catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  @Test
  fun build() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildOnWindows() {
    Jimfs.newFileSystem(Configuration.windows()).use { fileSystem ->
      val emulator =
        fileSystem.getPath("C:\\Users\\user\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe")
      val builder = EmulatorCommandBuilder(emulator, avd)

      val command = builder.build()

      assertEquals(
        "C:\\Users\\user\\AppData\\Local\\Android\\Sdk\\emulator\\emulator.exe -avd Pixel_4_API_30",
        command.commandLineString,
      )
    }
  }

  @Test
  fun buildNetworkLatencyIsNotNull() {
    whenever(avd.getProperty(ConfigKey.NETWORK_LATENCY)).thenReturn("none")
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -netdelay none -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildNetworkSpeedIsNotNull() {
    whenever(avd.getProperty(ConfigKey.NETWORK_SPEED)).thenReturn("full")
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -netspeed full -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildColdBoot() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)
    builder.bootMode = ColdBoot

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -no-snapstorage -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildBootSnapshot() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)
    builder.bootMode = BootSnapshot("snap_123")

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -snapshot snap_123 -no-snapshot-save -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildStudioParamsIsNotNull() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)
    builder.studioParams = fileSystem.getPath("/home/user/temp/emu.tmp")

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -studio-params /home/user/temp/emu.tmp -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildLaunchInToolWindow() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)
    builder.launchInToolWindow = true

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -qt-hide-window -grpc-use-token -idle-grpc-timeout 300",
      command.commandLineString,
    )
  }

  @Test
  fun buildStudioEmuParamsIsNotEmpty() {
    val builder = EmulatorCommandBuilder(myEmulator, avd)
    builder.studioEmuParams.addAll(listOf("-param-1", "-param-2", "-param-3"))

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -param-1 -param-2 -param-3",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineEmulatorBinary() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false)
    whenever(avd.userSettings)
      .thenReturn(mapOf(UserSettingsKey.EMULATOR_BINARY to "../my-package/my-emulator"))
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/my-package/my-emulator -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineOptionsInUserSettings() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false)
    whenever(avd.userSettings)
      .thenReturn(mapOf(UserSettingsKey.COMMAND_LINE_OPTIONS to "   -custom options"))
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -custom options",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineOptionsInCongig_Disabled() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(false)
    whenever(avd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS))
      .thenReturn("-some random -options")
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineOptions_Enabled() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true)
    whenever(avd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS))
      .thenReturn("-some random -options")
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -some random -options",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineOptionsHandlesNullInput() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true)
    whenever(avd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS)).thenReturn(null)
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30",
      command.commandLineString,
    )
  }

  @Test
  fun buildAvdCommandLineOptionsIsSanitized() {
    StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.override(true)
    whenever(avd.getProperty(UserSettingsKey.COMMAND_LINE_OPTIONS))
      .thenReturn("  -some\nrandom  \n unsanitized  -options \n ")
    val builder = EmulatorCommandBuilder(myEmulator, avd)

    val command = builder.build()

    assertEquals(
      "/home/user/Android/Sdk/emulator/emulator -avd Pixel_4_API_30 -some random unsanitized -options",
      command.commandLineString,
    )
  }
}
