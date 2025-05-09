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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.SdkVersionInfo.getCodeName
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AndroidVersionsInfoTest {
  companion object {
    private var oldCompileSdk: Int = 0

    @BeforeClass
    @JvmStatic
    fun setUp() {
      // This is overridden in NewProjectWizardTestSuite, but we want to override it differently
      // here.
      // TODO(b/409977476): We can delete this when all the template tests use the current API.
      oldCompileSdk = StudioFlags.NPW_COMPILE_SDK_VERSION.get()
      StudioFlags.NPW_COMPILE_SDK_VERSION.override(36)
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
      StudioFlags.NPW_COMPILE_SDK_VERSION.override(oldCompileSdk)
    }
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableVersion() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromStableVersion(OLDER_VERSION)
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.buildApiLevel)
    assertEquals("$NPW_CURRENT_VERSION.0", versionItem.buildApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.targetApiLevel)
    assertEquals("$NPW_CURRENT_VERSION", versionItem.targetApiLevelStr)
    assertThat(versionItem.toString()).contains(getCodeName(OLDER_VERSION))
  }

  /** For preview Android target versions, the Build API should be the same as the preview */
  @Test
  fun previewVersion() {
    val version = AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(version)
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("${FUTURE_VERSION - 1}.0-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
  }

  /**
   * For versions without an Android target, the Build API should be the highest known stable API
   */
  @Test
  fun stableAndroidTarget() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(AndroidVersion(OLDER_VERSION, 0))
    assertEquals(OLDER_VERSION, versionItem.minApiLevel)
    assertEquals(OLDER_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.buildApiLevel)
    assertEquals("$NPW_CURRENT_VERSION.0", versionItem.buildApiLevelStr)
    assertEquals(NPW_CURRENT_VERSION, versionItem.targetApiLevel)
    assertEquals("$NPW_CURRENT_VERSION", versionItem.targetApiLevelStr)
  }

  /** For preview Android target versions, the Build API should be the same as the preview */
  @Test
  fun withPreviewAndroidTarget() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(AndroidVersion(FUTURE_VERSION - 1, "TEST_CODENAME"))
    assertEquals("API TEST_CODENAME Preview", versionItem.label)
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals("TEST_CODENAME", versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("${FUTURE_VERSION - 1}.0-TEST_CODENAME", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("TEST_CODENAME", versionItem.targetApiLevelStr)
  }

  /** For future Android target versions, the Build API should be updated too */
  @Test
  fun futureAndroidVersion() {
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(AndroidVersion(FUTURE_VERSION, 0))
    assertEquals(FUTURE_VERSION, versionItem.minApiLevel)
    assertEquals(FUTURE_VERSION.toString(), versionItem.minApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.buildApiLevel)
    assertEquals("${FUTURE_VERSION}.0", versionItem.buildApiLevelStr)
    assertEquals(FUTURE_VERSION, versionItem.targetApiLevel)
    assertEquals("$FUTURE_VERSION", versionItem.targetApiLevelStr)
  }

  @Test
  fun previewTargetShouldReturnPreviewInLabel() {
    val androidVersion = AndroidVersion(NPW_CURRENT_VERSION, "PREVIEW_CODE_NAME")
    val versionItem = AndroidVersionsInfo.VersionItem.fromAndroidVersion(androidVersion)
    assertThat(versionItem.toString()).contains("PREVIEW_CODE_NAME")
  }

  @Test
  fun withCompileSdk() {
    val versionItem = AndroidVersionsInfo.VersionItem(AndroidVersion(31, 0), AndroidVersion(32, 0))
    val withApi30 = versionItem.withCompileSdk(AndroidVersion(30, 0))
    assertThat(withApi30.minApiLevel).isEqualTo(30)
    assertThat(withApi30.buildApiLevel).isEqualTo(30)

    val withApi33 = versionItem.withCompileSdk(AndroidVersion(33, 0))
    assertThat(withApi33.minApiLevel).isEqualTo(31)
    assertThat(withApi33.buildApiLevel).isEqualTo(33)
  }

  @Test
  fun `mobile format has no minimum sdk limit`() {
    val androidVersionsInfo = AndroidVersionsInfo { arrayOf(mockedPlatform(1000)) }
    androidVersionsInfo.loadLocalVersions()
    val targets = androidVersionsInfo.getKnownTargetVersions(FormFactor.MOBILE, 1)
    assertThat(targets.last().minApiLevel).isEqualTo(1000)
  }

  @Test
  fun `non-mobile formats have minimum sdk limit`() {
    val info = AndroidVersionsInfo { arrayOf(mockedPlatform(1000)) }
    info.loadLocalVersions()
    val nonMobileFormats = FormFactor.entries - FormFactor.MOBILE
    for (format in nonMobileFormats) {
      val targets = info.getKnownTargetVersions(format, 1)
      assertThat(targets.last().minApiLevel).isNotEqualTo(1000)
    }
  }

  private fun mockedPlatform(api: Int): IAndroidTarget =
    mock<IAndroidTarget> {
      on { version } doReturn AndroidVersion(api, 0)
      on { isPlatform } doReturn true
    }
}

private val NPW_CURRENT_VERSION: Int = StudioFlags.NPW_COMPILE_SDK_VERSION.get()
private val OLDER_VERSION = NPW_CURRENT_VERSION - 1
private val FUTURE_VERSION = NPW_CURRENT_VERSION + 1
