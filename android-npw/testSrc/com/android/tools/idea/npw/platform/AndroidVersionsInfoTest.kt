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
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.sdklib.SdkVersionInfo.getCodeName
import com.android.sdklib.internal.androidTarget.MockAddonTarget
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.testutils.MockitoKt.whenever
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import kotlin.test.assertNull
import kotlin.test.assertSame

class AndroidVersionsInfoTest {

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableVersion() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromStableVersion(OLDER_VERSION)
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(HIGHEST_KNOWN_STABLE_API, versionItem.buildApiLevel)
    assertEquals(HIGHEST_KNOWN_STABLE_API.toString(), versionItem.buildApiLevelStr)
    assertEquals(HIGHEST_KNOWN_STABLE_API, versionItem.targetApiLevel)
    assertEquals(HIGHEST_KNOWN_STABLE_API.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  fun previewVersion() {
    val version = AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(version)
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("android-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableAndroidTarget() {
    val androidTarget: MockPlatformTarget = object : MockPlatformTarget(OLDER_VERSION, 0) {
      override fun getVersion(): AndroidVersion = AndroidVersion(OLDER_VERSION)
    }
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(HIGHEST_KNOWN_STABLE_API, versionItem.buildApiLevel)
    assertEquals(HIGHEST_KNOWN_STABLE_API.toString(), versionItem.buildApiLevelStr)
    assertEquals(HIGHEST_KNOWN_STABLE_API, versionItem.targetApiLevel)
    assertEquals(HIGHEST_KNOWN_STABLE_API.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  /**
   * For preview Android target versions, the Build API should be the same as the preview
   */
  @Test
  fun withPreviewAndroidTarget() {
    val androidTarget: MockPlatformTarget = object : MockPlatformTarget(FUTURE_VERSION, 0) {
      override fun getVersion(): AndroidVersion = AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME")
    }
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("android-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
    assertSame(androidTarget, versionItem.androidTarget)
  }

  /**
   * For addon Android target versions, the Build API should be the same as the platform target
   */
  @Test
  fun withAddonAndroidTarget() {
    val baseTarget = MockPlatformTarget(26, 0)
    val projectTarget = MockAddonTarget("google", baseTarget, 1)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(projectTarget)
    assertEquals("vendor 26:google:26", versionItem.label)
    assertEquals(26, versionItem.minApiLevel)
    assertEquals("26", versionItem.minApiLevelStr)
    assertEquals(26, versionItem.buildApiLevel)
    assertEquals("vendor 26:google:26", versionItem.buildApiLevelStr)
    assertEquals(26, versionItem.targetApiLevel)
    assertEquals("26", versionItem.targetApiLevelStr)
    assertSame(projectTarget, versionItem.androidTarget)
  }

  /**
   * For future Android target versions, the Build API should be updated too
   */
  @Test
  fun futureAndroidVersion() {
    val androidTarget = MockPlatformTarget(FUTURE_VERSION, 0)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.targetApiLevelStr)
    assertNull(versionItem.androidTarget)
  }

  @Test
  fun previewTargetShouldReturnPreviewInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, "PREVIEW_CODE_NAME")
    val androidTarget: IAndroidTarget = mock(IAndroidTarget::class.java)
    whenever(androidTarget.version).thenReturn(androidVersion)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString()).contains("PREVIEW_CODE_NAME")
  }

  @Test
  fun platformTargetShouldReturnAndroidDesertNameInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, null)
    val androidTarget: IAndroidTarget = mock(IAndroidTarget::class.java)
    whenever(androidTarget.version).thenReturn(androidVersion)
    whenever(androidTarget.isPlatform).thenReturn(true)
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString()).contains(getCodeName(HIGHEST_KNOWN_API))
  }

  /**
   * If an Android Target is not an Android Platform, then its an Android SDK add-on, and it should be displayed using the
   * add-on Vendor/Name values instead of the Android Target name (if add-on description is missing).
   */
  @Test
  fun nonPlatformTargetShouldReturnAddonNameInLabel() {
    val androidVersion = AndroidVersion(HIGHEST_KNOWN_API, null /*codename*/)
    val androidTarget = mock(IAndroidTarget::class.java)
    whenever(androidTarget.version).thenReturn(androidVersion)
    whenever(androidTarget.isPlatform).thenReturn(false)
    whenever(androidTarget.vendor).thenReturn("AddonVendor")
    whenever(androidTarget.name).thenReturn("AddonName")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidTarget(androidTarget)
    assertThat(versionItem.toString())
      .isEqualTo(AndroidTargetHash.getAddonHashString("AddonVendor", "AddonName", androidVersion))
  }
}

private const val OLDER_VERSION = HIGHEST_KNOWN_API - 1
private const val FUTURE_VERSION = HIGHEST_KNOWN_API + 1
