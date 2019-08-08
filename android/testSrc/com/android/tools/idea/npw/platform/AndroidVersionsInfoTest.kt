/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.platform

import com.android.sdklib.AndroidTargetHash
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API
import com.android.sdklib.SdkVersionInfo.getCodeName
import com.android.sdklib.internal.androidTarget.MockAddonTarget
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.google.common.truth.Truth
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations.initMocks

class AndroidVersionsInfoTest {
  @Mock
  private val myMockAndroidVersionsInfo: AndroidVersionsInfo? = null

  @Before
  fun setUp() {
    initMocks(this)
    `when`(myMockAndroidVersionsInfo!!.highestInstalledVersion).thenReturn(AndroidVersion(HIGHEST_VERSION, null))
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun noAndroidTarget() {
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(AndroidVersion(DEFAULT_VERSION, null))
    assertEquals(DEFAULT_VERSION, versionItem.minApiLevel)
    assertEquals(HIGHEST_VERSION, versionItem.buildApiLevel)
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  fun withPreviewAndroidTarget() {
    val androidTarget: MockPlatformTarget = object : MockPlatformTarget(PREVIEW_VERSION, 0) {
      override fun getVersion(): AndroidVersion {
        return AndroidVersion(PREVIEW_VERSION - 1, "TEST_CODENAME")
      }
    }
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(androidTarget)
    assertEquals(PREVIEW_VERSION, versionItem.minApiLevel)
    assertEquals(PREVIEW_VERSION, versionItem.buildApiLevel)
  }

  /**
   * For platform Android target versions, the Build API should be the same as the platform target
   */
  @Test
  fun withPlatformAndroidTarget() {
    val baseTarget = MockPlatformTarget(DEFAULT_VERSION, 0)
    val projectTarget = MockAddonTarget("google", baseTarget, 1)
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(projectTarget)
    assertEquals(DEFAULT_VERSION, versionItem.minApiLevel)
    assertEquals(DEFAULT_VERSION, versionItem.buildApiLevel)
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  fun highestInstalledTarget() {
    val androidTarget = MockPlatformTarget(DEFAULT_VERSION, 0)
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(androidTarget)
    assertEquals(DEFAULT_VERSION, versionItem.minApiLevel)
    assertEquals(HIGHEST_VERSION, versionItem.buildApiLevel)
  }

  @Test
  fun previewTargetShouldReturnPreviewInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, "PREVIEW_CODE_NAME")
    val androidTarget: IAndroidTarget = mock(IAndroidTarget::class.java)
    `when`(androidTarget.version).thenReturn(androidVersion)
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(androidTarget)
    Truth.assertThat(versionItem.label).contains("PREVIEW_CODE_NAME")
  }

  @Test
  fun platformTargetShouldReturnAndroidDesertNameInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, null)
    val androidTarget: IAndroidTarget = mock(IAndroidTarget::class.java)
    `when`(androidTarget.version).thenReturn(androidVersion)
    `when`(androidTarget.isPlatform).thenReturn(true)
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(androidTarget)
    Truth.assertThat(versionItem.label).contains(getCodeName(HIGHEST_KNOWN_API))
  }

  /**
   * If an Android Target is not an Android Platform, then its an Android SDK add-on, and it should be displayed using the
   * add-on Vendor/Name values instead of the Android Target name (if add-on description is missing).
   */
  @Test
  fun nonPlatformTargetShouldReturnAddonNameInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, null /*codename*/)
    val androidTarget: IAndroidTarget = mock(IAndroidTarget::class.java)
    `when`(androidTarget.version).thenReturn(androidVersion)
    `when`(androidTarget.isPlatform).thenReturn(false)
    `when`(androidTarget.vendor).thenReturn("AddonVendor")
    `when`(androidTarget.name).thenReturn("AddonName")
    val versionItem = myMockAndroidVersionsInfo!!.VersionItem(androidTarget)
    assertEquals(AndroidTargetHash.getAddonHashString("AddonVendor", "AddonName", androidVersion),
                 versionItem.label)
  }
}

private const val DEFAULT_VERSION = 101
private const val HIGHEST_VERSION = 103
private const val PREVIEW_VERSION = 104
