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

import com.android.emulator.control.DisplayModeValue
import com.android.emulator.control.Posture.PostureValue
import com.android.sdklib.AndroidVersion
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.DisplayMode
import com.android.tools.idea.streaming.emulator.EmulatorConfiguration.PostureDescriptor
import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Test
import java.awt.Dimension

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
    val androidVersion = AndroidVersion(35, "Baklava", 3, true)
    val avdFolder = FakeEmulator.createPhoneAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Pixel 3 XL API Baklava")
    assertThat(config?.deviceType).isEqualTo(DeviceType.HANDHELD)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(1440)
    assertThat(config?.displayHeight).isEqualTo(2960)
    assertThat(config?.density).isEqualTo(480)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder?.toString()?.replace('\\', '/'))
        .endsWith("tools/adt/idea/artwork/resources/device-art-resources/pixel_3_xl")
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(0)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).isEmpty()
  }

  @Test
  fun testTablet() {
    // Prepare.
    val androidVersion = AndroidVersion(29)
    val avdFolder = FakeEmulator.createTabletAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Nexus 10 API 29")
    assertThat(config?.deviceType).isEqualTo(DeviceType.HANDHELD)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(1600)
    assertThat(config?.displayHeight).isEqualTo(2560)
    assertThat(config?.density).isEqualTo(320)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder?.toString()?.replace('\\', '/')).isEqualTo("${baseDir}/Android/Sdk/skins/nexus_10")
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(1)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).isEmpty()
  }

  @Test
  fun testAutomotive() {
    // Prepare.
    val androidVersion = AndroidVersion(32)
    val avdFolder = FakeEmulator.createAutomotiveAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Automotive 1024p landscape API 32")
    assertThat(config?.deviceType).isEqualTo(DeviceType.AUTOMOTIVE)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(1024)
    assertThat(config?.displayHeight).isEqualTo(768)
    assertThat(config?.density).isEqualTo(160)
    assertThat(config?.additionalDisplays).containsExactly(6, Dimension(400, 600), 7, Dimension(3000, 600))
    assertThat(config?.skinFolder).isNull()
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isFalse()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(1)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).isEmpty()
  }

  @Test
  fun testWatch() {
    // Prepare.
    val androidVersion = AndroidVersion(30)
    val avdFolder = FakeEmulator.createWatchAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Android Wear Round API 30")
    assertThat(config?.deviceType).isEqualTo(DeviceType.WEAR)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(320)
    assertThat(config?.displayHeight).isEqualTo(320)
    assertThat(config?.density).isEqualTo(240)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder?.toString()).isEqualTo(FakeEmulator.getSkinFolder("wearos_small_round").toString())
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(0)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).isEmpty()
  }

  @Test
  fun testXr() {
    // Prepare.
    val androidVersion = AndroidVersion(34)
    val avdFolder = FakeEmulator.createXrAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("XR Device API 34")
    assertThat(config?.deviceType).isEqualTo(DeviceType.XR)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(2560)
    assertThat(config?.displayHeight).isEqualTo(2368)
    assertThat(config?.density).isEqualTo(320)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder?.toString()).isNull()
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(1)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).isEmpty()
  }

  @Test
  fun testFoldable() {
    // Prepare.
    val avdFolder = FakeEmulator.createFoldableAvd(avdParentFolder, sdkFolder)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Pixel Fold API 33")
    assertThat(config?.deviceType).isEqualTo(DeviceType.HANDHELD)
    assertThat(config?.androidVersion).isEqualTo(AndroidVersion(33))
    assertThat(config?.displayWidth).isEqualTo(2208)
    assertThat(config?.displayHeight).isEqualTo(1840)
    assertThat(config?.density).isEqualTo(420)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder?.toString()).isEqualTo(FakeEmulator.getSkinFolder("pixel_fold").toString())
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(0)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).containsExactly(
        PostureDescriptor(PostureValue.POSTURE_CLOSED, PostureDescriptor.ValueType.HINGE_ANGLE, 0.0, 30.0),
        PostureDescriptor(PostureValue.POSTURE_HALF_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 30.0, 150.0),
        PostureDescriptor(PostureValue.POSTURE_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 150.0, 180.0))
  }

  @Test
  fun testRollable() {
    // Prepare.
    val androidVersion = AndroidVersion(31)
    val avdFolder = FakeEmulator.createRollableAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("7.4 Rollable API 31")
    assertThat(config?.deviceType).isEqualTo(DeviceType.HANDHELD)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(1600)
    assertThat(config?.displayHeight).isEqualTo(2428)
    assertThat(config?.density).isEqualTo(420)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder).isNull()
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(0)
    assertThat(config?.displayModes).isEmpty()
    assertThat(config?.postures).containsExactly(
        PostureDescriptor(PostureValue.POSTURE_CLOSED, PostureDescriptor.ValueType.ROLL_PERCENTAGE, 58.55, 76.45),
        PostureDescriptor(PostureValue.POSTURE_HALF_OPENED, PostureDescriptor.ValueType.ROLL_PERCENTAGE, 76.45, 94.35),
        PostureDescriptor(PostureValue.POSTURE_OPENED, PostureDescriptor.ValueType.ROLL_PERCENTAGE, 94.35, 100.0))
  }

  @Test
  fun testResizable() {
    // Prepare.
    val androidVersion = AndroidVersion(32)
    val avdFolder = FakeEmulator.createResizableAvd(avdParentFolder, sdkFolder, androidVersion = androidVersion)

    // Act.
    val config = EmulatorConfiguration.readAvdDefinition(avdFolder.fileName.toString().substringBeforeLast("."), avdFolder)

    // Assert.
    assertThat(config).isNotNull()
    assertThat(config?.avdFolder).isEqualTo(avdFolder)
    assertThat(config?.avdName).isEqualTo("Resizable API 32")
    assertThat(config?.deviceType).isEqualTo(DeviceType.HANDHELD)
    assertThat(config?.androidVersion).isEqualTo(androidVersion)
    assertThat(config?.displayWidth).isEqualTo(1080)
    assertThat(config?.displayHeight).isEqualTo(2340)
    assertThat(config?.density).isEqualTo(420)
    assertThat(config?.additionalDisplays).isEmpty()
    assertThat(config?.skinFolder).isNull()
    assertThat(config?.hasAudioOutput).isTrue()
    assertThat(config?.hasOrientationSensors).isTrue()
    assertThat(config?.initialOrientationQuadrants).isEqualTo(0)
    assertThat(config?.displayModes).containsExactly(
        DisplayMode(DisplayModeValue.PHONE, 1080, 2340, false),
        DisplayMode(DisplayModeValue.FOLDABLE, 1768, 2208, true),
        DisplayMode(DisplayModeValue.TABLET, 1920, 1200, false),
        DisplayMode(DisplayModeValue.DESKTOP, 1920, 1080, false))
    assertThat(config?.postures).containsExactly(
        PostureDescriptor(PostureValue.POSTURE_CLOSED, PostureDescriptor.ValueType.HINGE_ANGLE, 0.0, 30.0),
        PostureDescriptor(PostureValue.POSTURE_HALF_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 30.0, 150.0),
        PostureDescriptor(PostureValue.POSTURE_OPENED, PostureDescriptor.ValueType.HINGE_ANGLE, 150.0, 180.0))
  }
}