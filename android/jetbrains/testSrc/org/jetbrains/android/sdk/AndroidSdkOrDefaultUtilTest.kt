/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android.sdk

import com.android.SdkConstants
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.idea.IdeInfo
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class AndroidSdkOrDefaultUtilTest {

  @get:Rule
  val tempDir = TemporaryFolder()

  private val selectedSdk by lazy { tempDir.root.resolve("selectedSdk").asFakeSdk() }
  private val alternativeSdk by lazy { tempDir.root.resolve("alternativeSdk").asFakeSdk() }

  @Test
  fun `getAndroidSdkPathOrDefault() should prefer ANDROID_HOME over ANDROID_SDK_ROOT and default`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_HOME_ENV to selectedSdk.absolutePath,
        SdkConstants.ANDROID_SDK_ROOT_ENV to alternativeSdk.absolutePath,
      ),
      AndroidSdkType(),
      getAndroidStudioIde()
    )
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() should prefer ANDROID_SDK_ROOT over default`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_SDK_ROOT_ENV to selectedSdk.absolutePath,
      ),
      AndroidSdkType(),
      getAndroidStudioIde()
    )
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @get:Rule
  val ignoreTests = IgnoreTestRule()

  @Test
  fun `getAndroidSdkPathOrDefault() should fallback to default`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      emptyMap(),
      AndroidSdkType(),
      getAndroidStudioIde()
    )
    when {
      SystemInfo.isWindows ->
        Truth.assertThat(foundSdk).isEqualTo(File(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk"))
      SystemInfo.isMac ->
        Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Library/Android/sdk"))
      else -> Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Android/Sdk"))
    }
  }

  @Test
  fun `getAndroidSdkPathOrDefault() for game tools should prioritize ANDROID_HOME over ANDROID_SDK_ROOT`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_HOME_ENV to selectedSdk.absolutePath,
        SdkConstants.ANDROID_SDK_ROOT_ENV to alternativeSdk.absolutePath,
      ),
      AndroidSdkType(),
      getGameToolsIde()
    )
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() for game tools should prioritize ANDROID_SDK_ROOT over default`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_SDK_ROOT_ENV to selectedSdk.absolutePath,
      ),
      AndroidSdkType(),
      getGameToolsIde()
    )
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() for game tools should return default if no env var set`() {
    val foundSdk = AndroidSdkUtils.getAndroidSdkOrDefault(
      emptyMap(),
      AndroidSdkType(),
      getGameToolsIde()
    )
    when {
      SystemInfo.isWindows ->
        Truth.assertThat(foundSdk).isEqualTo(File(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk"))
      SystemInfo.isMac ->
        Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Library/Android/sdk"))
      else -> Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Android/Sdk"))
    }
  }

  private fun File.asFakeSdk(): File {
    resolve(SdkConstants.FD_PLATFORMS).mkdirs()
    return this
  }

  private fun getAndroidStudioIde(): IdeInfo {
    val mockIdeInfo = mock<IdeInfo>()
    whenever(mockIdeInfo.isGameTools).thenReturn(false)
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(true)
    return mockIdeInfo;
  }

  private fun getGameToolsIde(): IdeInfo {
    val mockIdeInfo = mock<IdeInfo>()
    whenever(mockIdeInfo.isGameTools).thenReturn(true)
    whenever(mockIdeInfo.isAndroidStudio).thenReturn(false)
    return mockIdeInfo;
  }
}