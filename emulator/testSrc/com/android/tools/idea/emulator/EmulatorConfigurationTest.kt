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

import com.android.emulator.control.Rotation.SkinRotation
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Test

/**
 * Tests for [EmulatorConfiguration].
 */
class EmulatorConfigurationTest {
  private val baseDir = if (SystemInfo.isWindows) "C:/home/janedoe" else "/home/janedoe"
  private val fileSystem = Jimfs.newFileSystem()
  private val baseFolder = fileSystem.getPath(baseDir)
  private val avdParentFolder = baseFolder.resolve(".android/avd")
  private val sdkFolder = baseFolder.resolve("Android/Sdk")

  @Test
  fun testPhone() {
    // Prepare.
    val avdFolder = FakeEmulator.createPhoneAvd(avdParentFolder, sdkFolder)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Pixel 3 XL API 29")
    assertThat(config?.displayWidth).isEqualTo(1440)
    assertThat(config?.displayHeight).isEqualTo(2960)
    assertThat(config?.density).isEqualTo(480)
    assertThat(config?.skinFolder?.toString()?.replace('\\', '/'))
        .endsWith("tools/adt/idea/artwork/resources/device-art-resources/pixel_3_xl")
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientation).isEqualTo(SkinRotation.PORTRAIT)
  }

  @Test
  fun testTablet() {
    // Prepare.
    val avdFolder = FakeEmulator.createTabletAvd(avdParentFolder, sdkFolder)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Nexus 10 API 29")
    assertThat(config?.displayWidth).isEqualTo(2560)
    assertThat(config?.displayHeight).isEqualTo(1600)
    assertThat(config?.density).isEqualTo(320)
    assertThat(config?.skinFolder?.toString()?.replace('\\', '/')).isEqualTo("${baseDir}/Android/Sdk/skins/nexus_10")
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientation).isEqualTo(SkinRotation.LANDSCAPE)
  }
}