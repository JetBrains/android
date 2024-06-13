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
package com.android.tools.idea.adddevicedialog.localavd

import com.android.repository.api.RepoPackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageTags
import com.android.sdklib.repository.generated.sysimg.v4.SysImgDetailsType
import com.android.testutils.MockitoKt
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SystemImageTest {
  private val details = MockitoKt.mock<SysImgDetailsType>()
  private val repoPackage = MockitoKt.mock<RepoPackage>()

  init {
    MockitoKt.whenever(repoPackage.typeDetails).thenReturn(details)
  }

  /** system-images;android-29;google_apis_playstore;x86 */
  @Test
  fun systemImageTagsContainsGooglePlay() {
    // Arrange
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.PLAY_STORE_TAG))

    // Act
    val services = SystemImage(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  /** system-images;android-32;android-automotive-playstore;x86_64 */
  @Test
  fun systemImageTagEqualsAndroidAutomotiveWithGooglePlay() {
    // Arrange
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG))

    // Act
    val services = SystemImage(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  /** system-images;android-30;android-wear;arm64-v8a */
  @Test
  fun systemImageTagEqualsWearOsEtc() {
    // Arrange
    MockitoKt.whenever(details.androidVersion).thenReturn(AndroidVersion(30))
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.WEAR_TAG))

    MockitoKt.whenever(repoPackage.path)
      .thenReturn("system-images;android-30;android-wear;arm64-v8a")

    // Act
    val services = SystemImage(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_PLAY_STORE, services)
  }

  /** system-images;android-23;google_apis;x86 */
  @Test
  fun systemImageTagsContainsGoogleApis() {
    // Arrange
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.GOOGLE_APIS_TAG))

    // Act
    val services = SystemImage(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_APIS, services)
  }

  /** system-images;android-31;android-tv;x86 */
  @Test
  fun systemImageTagsSizeEquals1Etc() {
    // Arrange
    MockitoKt.whenever(details.tags).thenReturn(listOf(SystemImageTags.ANDROID_TV_TAG))

    // Act
    val services = SystemImage(repoPackage).services

    // Assert
    assertEquals(Services.GOOGLE_APIS, services)
  }
}
