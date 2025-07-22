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
package com.android.gmdcodecompletion.managedvirtual

import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.ApplicationServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatform4TestCase
import com.intellij.testFramework.TestApplicationManager
import java.util.Calendar
import org.junit.Rule
import org.junit.Test
import org.mockito.Answers
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class ManagedVirtualDeviceCatalogServiceTest : LightPlatform4TestCase() {
  private val mockProject: Project = mock()

  private val mockProgressIndicator: ProgressIndicator = mock()

  private val mockAndroidSdks: AndroidSdks = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

  private val packages = RepositoryPackages()

  private lateinit var repoManager: FakeRepoManager

  @get:Rule val sdkServiceRule = ApplicationServiceRule(AndroidSdks::class.java, mockAndroidSdks)

  override fun setUp() {
    super.setUp()
    TestApplicationManager.getInstance()
    val pkg =
      FakePackage.FakeRemotePackage("system-images;android-23;default;armeabi-v7a").apply {
        typeDetails =
          AndroidSdkHandler.getSysImgModule()
            .createLatestFactory()
            .createSysImgDetailsType()
            .apply {
              apiLevel = 23
              abis.add("armeabi-v7a")
            } as TypeDetails
      }
    packages.setRemotePkgInfos(listOf(pkg))
    repoManager = spy(FakeRepoManager(packages))
    whenever(mockAndroidSdks.tryToChooseSdkHandler()).thenReturn(AndroidSdkHandler(null, null, repoManager))
  }

  private fun managedVirtualDeviceCatalogTestHelperWrapper(callback: () -> Unit) =
    ProgressManager.getInstance()
      .runProcessWithProgressSynchronously({ callback() }, "", false, null)

  @Test
  fun testObtainAndroidDeviceCatalog() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      assertFalse(managedVirtualDeviceCatalogService.state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(
        mockProject,
        mockProgressIndicator,
      )
      assertTrue(managedVirtualDeviceCatalogService.state.isCacheFresh())
      val deviceCatalog = managedVirtualDeviceCatalogService.state.myDeviceCatalog
      assertThat(deviceCatalog.devices.values).isNotEmpty()
      assertThat(deviceCatalog.apiLevels)
        .containsExactly(ManagedVirtualDeviceCatalog.ApiVersionInfo(23, imageSource = "google"))
      assertThat(deviceCatalog.devices.values.first().supportedApis).containsExactly(23)
    }
  }

  @Test
  fun testCacheIsFresh() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val calendar = Calendar.getInstance()
      calendar.add(Calendar.DATE, 1)
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      managedVirtualDeviceCatalogService.loadState(
        ManagedVirtualDeviceCatalogState(
          calendar.time,
          ManagedVirtualDeviceCatalogService.syncDeviceCatalog(),
        )
      )
      val state = managedVirtualDeviceCatalogService.state
      assertTrue(state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(
        mockProject,
        mockProgressIndicator,
      )
      // We should not have updated the state with a new instance since the cache is fresh
      assertThat(managedVirtualDeviceCatalogService.state).isSameAs(state)
    }
  }
}
