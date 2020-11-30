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
package com.android.tools.idea.startup

import com.android.SdkConstants
import com.android.SdkConstants.FD_PLATFORMS
import com.android.utils.PathUtils.toSystemIndependentPath
import com.google.common.truth.Truth
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockApplication
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.util.SystemProperties
import org.jetbrains.android.sdk.AndroidSdkType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AndroidSdkInitializerTest {

  @get:Rule
  val tempDir = TemporaryFolder()

  val selectedSdk by lazy { tempDir.root.resolve("selectedSdk").asFakeSdk() }
  val alternativeSdk by lazy { tempDir.root.resolve("alternativeSdk").asFakeSdk() }
  private val myDisposable = Disposer.newDisposable()

  @Before
  fun setUp() {
    // Application is needed to read the ddms.cfg file inside a read action.
    val instance = MockApplication.setUp(myDisposable)
    CoreApplicationEnvironment.registerApplicationExtensionPoint(ExtensionPointName("com.intellij.virtualFileManagerListener"),
                                                                 VirtualFileManagerListener::class.java)
    instance.registerService(VirtualFileManager::class.java, VirtualFileManagerImpl(listOf(CoreLocalFileSystem())))
  }

  @Test
  fun `getAndroidSdkPathOrDefault() should prefer ANDROID_HOME over ANDROID_SDK_ROOT and DDMS config`() {
    setUpDdmsCfg(alternativeSdk)
    val foundSdk = AndroidSdkInitializer.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_HOME_ENV to selectedSdk.absolutePath,
        SdkConstants.ANDROID_SDK_ROOT_ENV to alternativeSdk.absolutePath,
      ),
      AndroidSdkType())
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() should prefer ANDROID_SDK_ROOT over DDMS config`() {
    setUpDdmsCfg(alternativeSdk)
    val foundSdk = AndroidSdkInitializer.getAndroidSdkOrDefault(
      mapOf(
        SdkConstants.ANDROID_SDK_ROOT_ENV to selectedSdk.absolutePath,
      ),
      AndroidSdkType())
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() should get from DDMS config`() {
    setUpDdmsCfg(selectedSdk)
    val foundSdk = AndroidSdkInitializer.getAndroidSdkOrDefault(emptyMap(), AndroidSdkType())
    Truth.assertThat(foundSdk).isEqualTo(selectedSdk)
  }

  @Test
  fun `getAndroidSdkPathOrDefault() should fallback to default`() {
    val foundSdk = AndroidSdkInitializer.getAndroidSdkOrDefault(emptyMap(), AndroidSdkType())
    when {
      SystemInfo.isWindows ->
        Truth.assertThat(foundSdk).isEqualTo(File(System.getenv("LOCALAPPDATA")).resolve("Android/Sdk"))
      SystemInfo.isMac ->
        Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Library/Android/sdk"))
      else -> Truth.assertThat(foundSdk).isEqualTo(File(SystemProperties.getUserHome()).resolve("Android/Sdk"))
    }
  }

  @After
  fun tearDown() {
    // This file is useful for users migrating from Eclipse. It's never useful for Android Studio developers, so deleting it
    // in test environment (under studio-master-dev or in a bazel sandbox) is OK. On the other hand, we need to delete it so
    // the presence of this file won't interfere launching dev instances of studio.
    val ddmsCfg = File(SystemProperties.getUserHome()).resolve(".android/ddms.cfg")
    if (ddmsCfg.exists()) {
      ddmsCfg.delete()
    }

    Disposer.dispose(myDisposable)
  }

  private fun setUpDdmsCfg(sdkPath: File) {
    File(SystemProperties.getUserHome()).resolve(".android").apply {
      mkdirs()
      resolve("ddms.cfg").writeText("""
        lastSdkPath=${toSystemIndependentPath(sdkPath.toPath())}
      """.trimIndent())
    }
  }

  private fun File.asFakeSdk(): File {
    resolve(FD_PLATFORMS).mkdirs()
    return this
  }
}