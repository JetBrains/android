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
    val skinName = ""

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName))
  }

  @Test
  fun updateSkinDeviceIsAbsolute() {
    // Arrange
    val skinName = myHomeDir.resolve("Android/Sdk/platforms/android-32/skins/HVGA").toString()

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName))
  }

  @Test
  fun updateSkinDeviceEqualsNoSkin() {
    // Arrange
    val skinName = "_no_skin"

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName))
  }

  @Test
  fun updateSkinImageSkinIsPresent() {
    // Arrange
    val imageSkin = myHomeDir.resolve("Android/Sdk/system-images/android-28/android-wear/x86/skins/AndroidWearRound480x480")
    val skinName = imageSkin.fileName.toString()

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, listOf(imageSkin), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(imageSkin)
  }

  @Test
  fun updateSkinStudioSkinIsNullAndSdkSkinIsNull() {
    // Arrange
    val skinName = "pixel_4"

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), null, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(Paths.get(skinName))
  }

  @Test
  fun updateSkinStudioSkinIsNull() {
    // Arrange
    val skinName = "pixel_4"

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), null, mySdkSkins)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
  }

  @Test
  fun updateSkinSdkSkinIsNull() {
    // Arrange
    val skinName = "pixel_4"

    // Act
    val deviceSkin = DeviceSkinUpdater.updateSkin(skinName, emptyList(), myStudioSkins, null)

    // Assert
    assertThat(deviceSkin).isEqualTo(myStudioSkins.resolve(skinName))
  }

  @Test
  fun updateSkinImpl() {
    // Arrange
    val skinName = "pixel_fold"
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skinName))).isTrue()
    assertThat(deviceSkin.resolve("default/layout")).exists()
    assertThat(deviceSkin.resolve("closed/layout")).exists()
  }

  @Test
  fun updateSkinImplFilesListThrowsNoSuchFileException() {
    // Arrange
    val skinName = "pixel_4"
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve(skinName))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinImplDeviceEqualsWearSmallRound() {
    // Arrange
    val skinName = "WearSmallRound"
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_small_round"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinImplDeviceEqualsWearLargeRound() {
    // Arrange
    val skinName = "WearLargeRound"
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_large_round"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinImplDeviceEqualsAndroidWearSquare() {
    // Arrange
    val skinName = "WearSquare"
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
    assertThat(DeviceSkinUpdater.areAllFilesUpToDate(deviceSkin, myStudioSkins.resolve("wearos_square"))).isTrue()
    assertThat(deviceSkin.resolve("layout")).exists()
  }

  @Test
  fun updateSkinImplSdkLayoutDoesntExist() {
    // Arrange
    val skinName = "pixel_4"
    val sdkDeviceSkin = mySdkSkins.resolve(skinName)
    Files.createDirectories(sdkDeviceSkin)
    val updater = DeviceSkinUpdater(myStudioSkins, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(mySdkSkins.resolve(skinName))
  }

  @Test
  fun updateSkinImplStudioLayoutLastModifiedTimeIsBeforeSdkLayoutLastModifiedTime() {
    // Arrange
    val skinName = "pixel_4"
    val sdkDeviceSkin = mySdkSkins.resolve(skinName)
    Files.createDirectories(sdkDeviceSkin)

    val studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources")
    val studioDeviceSkin = studioSkin.resolve(skinName)
    Files.createDirectories(studioDeviceSkin)

    val studioLayout = studioDeviceSkin.resolve("layout")
    Files.createFile(studioLayout)
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.922Z")))

    val sdkLayout = sdkDeviceSkin.resolve("layout")
    Files.createFile(sdkLayout)
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")))

    val updater = DeviceSkinUpdater(studioSkin, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(sdkDeviceSkin)
  }

  @Test
  fun updateSkinImplStudioLayoutLastModifiedTimeIsAfterSdkLayoutLastModifiedTime() {
    // Arrange
    val skinName = "pixel_4"
    val sdkDeviceSkin = mySdkSkins.resolve(skinName)
    Files.createDirectories(sdkDeviceSkin)

    val studioSkin = myHomeDir.resolve("android-studio/plugins/android/resources/device-art-resources")
    val studioDeviceSkin = studioSkin.resolve(skinName)
    Files.createDirectories(studioDeviceSkin)

    val studioLayout = studioDeviceSkin.resolve("layout")
    Files.createFile(studioLayout)
    Files.setLastModifiedTime(studioLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.924Z")))

    val sdkLayout = sdkDeviceSkin.resolve("layout")
    Files.createFile(sdkLayout)
    Files.setLastModifiedTime(sdkLayout, FileTime.from(Instant.parse("2020-08-26T20:39:22.923Z")))

    val updater = DeviceSkinUpdater(studioSkin, mySdkSkins)

    // Act
    val deviceSkin = updater.updateSkinImpl(skinName)

    // Assert
    assertThat(deviceSkin).isEqualTo(sdkDeviceSkin)
  }
}
