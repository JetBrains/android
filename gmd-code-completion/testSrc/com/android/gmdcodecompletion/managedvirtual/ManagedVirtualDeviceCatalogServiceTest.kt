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

import com.android.gmdcodecompletion.freshManagedVirtualDeviceCatalogState
import com.android.gmdcodecompletion.managedVirtualDeviceCatalogTestHelper
import com.android.repository.api.RepoManager
import com.android.sdklib.devices.DeviceManager
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.openMocks

class ManagedVirtualDeviceCatalogServiceTest : LightPlatformTestCase() {
  @Mock
  private lateinit var mockProject: Project

  @Mock
  private lateinit var mockProgressIndicator: ProgressIndicator

  @Mock
  private lateinit var mockDeviceManager: DeviceManager

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockAndroidSdks: AndroidSdks

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockRepoManager: RepoManager

  override fun setUp() {
    super.setUp()
    openMocks(this)

    whenever(mockAndroidSdks.tryToChooseSdkHandler().getSdkManager(MockitoKt.any())).thenReturn(mockRepoManager)
    TestApplicationManager.getInstance()
  }

  private fun managedVirtualDeviceCatalogTestHelperWrapper(
    deviceManager: DeviceManager? = mockDeviceManager,
    androidSdks: AndroidSdks? = mockAndroidSdks,
    callback: () -> Unit) = managedVirtualDeviceCatalogTestHelper(deviceManager, androidSdks, callback)

  fun testObtainAndroidDeviceCatalog() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      assertFalse(managedVirtualDeviceCatalogService.state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      assertTrue(managedVirtualDeviceCatalogService.state.isCacheFresh())
      verify(mockDeviceManager).getDevices(DeviceManager.ALL_DEVICES)
      verify(mockRepoManager).packages
    }
  }

  fun testCacheIsFresh() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      managedVirtualDeviceCatalogService.loadState(freshManagedVirtualDeviceCatalogState())
      assertTrue(managedVirtualDeviceCatalogService.state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      // The only time we invoked mockDeviceManager and mockRepoManager is when freshManagedVirtualDeviceCatalogState is syncing
      verify(mockDeviceManager, times(1)).getDevices(DeviceManager.ALL_DEVICES)
      verify(mockRepoManager, times(1)).packages
    }
  }
}