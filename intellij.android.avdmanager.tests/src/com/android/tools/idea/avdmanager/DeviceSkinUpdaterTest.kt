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
package com.android.tools.idea.avdmanager

import com.android.testutils.TestUtils
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Instant

@RunWith(JUnit4::class)
class DeviceSkinUpdaterTest {
  private val myStudioSkins = TestUtils.resolveWorkspacePathUnchecked("tools/adt/idea/artwork/resources/device-art-resources")
  private val myHomeDir = createInMemoryFileSystemAndFolder("home/janedoe")
  private val mySdkSkins: Path = myHomeDir.resolve("Android/Sdk/skins")

  @Test
  fun updateSkinDeviceToStringIsEmpty() {
    // Arrange
    val skin = Paths.get("")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinDeviceIsAbsolute() {
    // Arrange
    val skin = myHomeDir.resolve("Android/Sdk/platforms/android-32/skins/HVGA")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinDeviceEqualsNoSkin() {
    // Arrange
    val skin = Paths.get("_no_skin")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinImageSkinIsPresent() {
    // Arrange
    val imageSkin = myHomeDir.resolve("Android/Sdk/system-images/android-28/android-wear/x86/skins/AndroidWearRound480x480")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(imageSkin.fileName, listOf(imageSkin), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(imageSkin)
  }

  @Test
  fun updateSkinStudioSkinIsNullAndSdkSkinIsNull() {
    // Arrange
    val skin = Paths.get("pixel_4")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinStudioSkinIsNull() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_4")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), null, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skin))
  }

  @Test
  fun updateSkinSdkSkinIsNull() {
    // Arrange
    val skin = myStudioSkins.resolve("pixel_4")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinAbsolutePath() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_fold")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skin.fileName.toString()))).isTrue()
    assertThat(deviceSkin.resolve("default/layout")).exists()
    assertThat(deviceSkin.resolve("closed/layout")).exists()
  }

  @Test
  fun updateSkinRelativePath() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_fold")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(mySdkSkins.parent.relativize(skin), emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skin.fileName.toString()))).isTrue()
    assertThat(deviceSkin.resolve("default/layout")).exists()
    assertThat(deviceSkin.resolve("closed/layout")).exists()
  }

  @Test
  fun updateSkinFilesListThrowsNoSuchFileException() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_4")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skin.fileName.toString()))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinDeviceEqualsWearSmallRound() {
    // Arrange
    val skin = mySdkSkins.resolve("WearSmallRound")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_small_round"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinDeviceEqualsWearLargeRound() {
    // Arrange
    val skin = mySdkSkins.resolve("WearLargeRound")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_large_round"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinDeviceEqualsAndroidWearSquare() {
    // Arrange
    val skin = mySdkSkins.resolve("WearSquare")

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_square"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinSdkLayoutDoesNotExist() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_4")
    Files.createDirectories(skin)

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinStudioLayoutLastModifiedTimeIsBeforeSdkLayoutLastModifiedTime() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_4")
    Files.createDirectories(skin)

    val studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources")
    val studioDeviceSkin = studioSkin.resolve(skin.fileName.toString())
    Files.createDirectories(studioDeviceSkin)

    val studioLayout = studioDeviceSkin.resolve("layout")
    Files.createFile(studioLayout)
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.922Z")))

    val sdkLayout = skin.resolve("layout")
    Files.createFile(sdkLayout)
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")))

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }

  @Test
  fun updateSkinStudioLayoutLastModifiedTimeIsAfterSdkLayoutLastModifiedTime() {
    // Arrange
    val skin = mySdkSkins.resolve("pixel_4")
    Files.createDirectories(skin)

    val studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources")
    val studioDeviceSkin = studioSkin.resolve(skin.fileName.toString())
    Files.createDirectories(studioDeviceSkin)

    val studioLayout = studioDeviceSkin.resolve("layout")
    Files.createFile(studioLayout)
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.924Z")))

    val sdkLayout = skin.resolve("layout")
    Files.createFile(sdkLayout)
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")))

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skin, emptyList(), myStudioSkins, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(skin)
  }
}
