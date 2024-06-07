/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoPackage
import com.android.repository.impl.meta.Archive
import com.android.repository.impl.meta.Archive.CompleteType
import com.android.repository.impl.meta.TypeDetails
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Abi
import com.android.sdklib.devices.Storage
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.generated.addon.v3.AddonDetailsType
import com.android.sdklib.repository.generated.common.v3.ApiDetailsType
import com.android.sdklib.repository.generated.common.v3.IdDisplayType
import com.android.sdklib.repository.generated.sysimg.v4.SysImgDetailsType
import com.android.testutils.MockitoKt
import com.google.common.collect.Range
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SystemImageTest {
  @Test
  fun matchesApiLevelEqualsApiRangeUpperEndpoint() {
    // Arrange
    val image =
      SystemImage(
        true,
        "system-images;android-34-ext10;google_apis_playstore;arm64-v8a",
        "Google Play ARM 64 v8a System Image",
        AndroidVersion(34, null, 10, false),
        Services.GOOGLE_PLAY_STORE,
        setOf(Abi.ARM64_V8A).toImmutableSet(),
        setOf<Abi>().toImmutableSet(),
        Storage(1_549_122_970),
      )

    val device = MockitoKt.mock<VirtualDevice>()
    MockitoKt.whenever(device.apiRange).thenReturn(Range.closed(34, 34))

    // Act
    val matches = image.matches(device)

    // Assert
    assertTrue(matches)
  }

  @Test
  fun matchesDeviceIsFoldableEtc() {
    // Arrange
    val image =
      SystemImage(
        true,
        "system-images;android-33-ext4;google_apis_playstore;arm64-v8a",
        "Google Play ARM 64 v8a System Image",
        AndroidVersion(33, null, 4, false),
        Services.GOOGLE_PLAY_STORE,
        setOf(Abi.ARM64_V8A).toImmutableSet(),
        setOf<Abi>().toImmutableSet(),
        Storage(1_474_557_980),
      )

    val device = MockitoKt.mock<VirtualDevice>()
    MockitoKt.whenever(device.apiRange).thenReturn(Range.closed(33, 33))
    MockitoKt.whenever(device.isFoldable).thenReturn(true)

    // Act
    val matches = image.matches(device)

    // Assert
    assertFalse(matches)
  }

  @Test
  fun matches() {
    // Arrange
    val image =
      SystemImage(
        true,
        "add-ons;addon-google_apis-google-10",
        "Google APIs",
        AndroidVersion(10),
        Services.GOOGLE_APIS,
        setOf(Abi.ARMEABI).toImmutableSet(),
        setOf<Abi>().toImmutableSet(),
        Storage(65_781_578),
      )

    val device = MockitoKt.mock<VirtualDevice>()
    MockitoKt.whenever(device.apiRange).thenReturn(Range.closed(34, 34))

    // Act
    val matches = image.matches(device)

    // Assert
    assertFalse(matches)
  }

  @Test
  fun testToStringIsRemote() {
    // Arrange
    val image = SystemImage.from(mockGooglePlayIntelX86AtomSystemImage())

    // Act
    val string = image.toString()

    // Assert
    assertEquals("Google Play Intel x86 Atom System Image (1.1 GB)", string)
  }

  @Test
  fun testToString() {
    // Arrange
    val details = MockitoKt.mock<SysImgDetailsType>()
    MockitoKt.whenever(details.androidVersion).thenReturn(AndroidVersion(34, null, 7, true))

    val repoPackage = MockitoKt.mock<LocalPackage>()
    MockitoKt.whenever(repoPackage.typeDetails).thenReturn(details)
    MockitoKt.whenever(repoPackage.path)
      .thenReturn("system-images;android-34;google_apis_playstore;x86_64")
    MockitoKt.whenever(repoPackage.displayName)
      .thenReturn("Google Play Intel x86_64 Atom System Image")

    val image = SystemImage.from(repoPackage)

    // Act
    val string = image.toString()

    // Assert
    assertEquals("Google Play Intel x86_64 Atom System Image", string)
  }

  @Test
  fun systemImageTagsContainsGooglePlay() {
    // Arrange
    val repoPackage = mockGooglePlayIntelX86AtomSystemImage()

    // Act
    val services = SystemImage.from(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  @Test
  fun systemImageTagEqualsAndroidAutomotiveWithGooglePlay() {
    // Arrange
    val repoPackage =
      mockRepoPackage(
        1_012_590_435,
        mockSysImgDetailsType(AndroidVersion(32), SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG),
        "system-images;android-32;android-automotive-playstore;x86_64",
        "Automotive with Play Store Intel x86_64 Atom System Image",
      )

    // Act
    val services = SystemImage.from(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  @Test
  fun systemImageTagEqualsWearOsEtc() {
    // Arrange
    val details = MockitoKt.mock<SysImgDetailsType>()
    MockitoKt.whenever(details.androidVersion).thenReturn(AndroidVersion(30))
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.WEAR_TAG))

    val repoPackage =
      mockRepoPackage(
        827_418_923,
        details,
        "system-images;android-30;android-wear;arm64-v8a",
        "Wear OS 3 ARM 64 v8a System Image",
      )

    // Act
    val services = SystemImage.from(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  @Test
  fun systemImageTagsContainsGoogleApis() {
    // Arrange
    val repoPackage =
      mockRepoPackage(
        499_428_151,
        mockSysImgDetailsType(AndroidVersion(23), SystemImageTags.GOOGLE_APIS_TAG),
        "system-images;android-23;google_apis;x86",
        "Google APIs Intel x86 Atom System Image",
      )

    // Act
    val services = SystemImage.from(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_APIS, services)
  }

  @Test
  fun systemImageTagsSizeEquals1Etc() {
    // Arrange
    val repoPackage =
      mockRepoPackage(
        772_250_386,
        mockSysImgDetailsType(AndroidVersion(31), SystemImageTags.ANDROID_TV_TAG),
        "system-images;android-31;android-tv;x86",
        "Android TV Intel x86 Atom System Image",
      )

    // Act
    val services = SystemImage.from(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_APIS, services)
  }

  @Test
  fun systemImageTagsDoesntContainGoogleApisX86() {
    // Arrange
    val details = MockitoKt.mock<AddonDetailsType>()
    MockitoKt.whenever(details.androidVersion).thenReturn(AndroidVersion(15))
    MockitoKt.whenever(details.tag).thenReturn(SystemImageTags.GOOGLE_APIS_TAG as IdDisplayType)
    MockitoKt.whenever(details.abis).thenReturn(listOf("armeabi"))

    val repoPackage =
      mockRepoPackage(106_624_396, details, "add-ons;addon-google_apis-google-15", "Google APIs")

    // Act
    val abis = SystemImage.from(repoPackage).abis

    // Assert
    assertEquals(setOf(Abi.ARMEABI), abis)
  }

  private companion object {
    private fun mockGooglePlayIntelX86AtomSystemImage() =
      mockRepoPackage(
        1_153_916_727,
        mockSysImgDetailsType(AndroidVersion(29), SystemImageTags.PLAY_STORE_TAG),
        "system-images;android-29;google_apis_playstore;x86",
        "Google Play Intel x86 Atom System Image",
      )

    private fun mockRepoPackage(
      size: Long,
      details: TypeDetails,
      path: String,
      name: String,
    ): RepoPackage {
      val complete = MockitoKt.mock<CompleteType>()
      MockitoKt.whenever(complete.size).thenReturn(size)

      val archive = MockitoKt.mock<Archive>()
      MockitoKt.whenever(archive.complete).thenReturn(complete)

      val repoPackage = MockitoKt.mock<RemotePackage>()
      MockitoKt.whenever(repoPackage.archive).thenReturn(archive)
      MockitoKt.whenever(repoPackage.typeDetails).thenReturn(details)
      MockitoKt.whenever(repoPackage.path).thenReturn(path)
      MockitoKt.whenever(repoPackage.displayName).thenReturn(name)

      return repoPackage
    }

    private fun mockSysImgDetailsType(version: AndroidVersion, tag: IdDisplay): ApiDetailsType {
      val details = MockitoKt.mock<SysImgDetailsType>()
      MockitoKt.whenever(details.androidVersion).thenReturn(version)
      MockitoKt.whenever(details.tags).thenReturn(listOf(tag))

      return details
    }
  }
}
