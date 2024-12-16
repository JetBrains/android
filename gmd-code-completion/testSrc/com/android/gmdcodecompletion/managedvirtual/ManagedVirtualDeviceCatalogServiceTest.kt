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

import com.android.gmdcodecompletion.managedVirtualDeviceCatalogTestHelper
import com.android.repository.api.RemotePackage
import com.android.repository.api.RepoManager
import com.android.repository.api.UpdatablePackage
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.repository.generated.sysimg.v1.SysImgDetailsType
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Calendar
import java.util.EnumSet

class ManagedVirtualDeviceCatalogServiceTest : LightPlatformTestCase() {
  private val mockProject: Project = mock()

  private val mockProgressIndicator: ProgressIndicator = mock()

  private val mockDeviceManager: DeviceManager = mock()

  private val mockAndroidSdks: AndroidSdks = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

  private val mockRepoManager: RepoManager = mock(defaultAnswer = Answers.RETURNS_DEEP_STUBS)

  private val mockUpdatablePackage: UpdatablePackage = mock()

  private val mockTypeDetail: SysImgDetailsType = mock()

  private val mockRemotePackage: RemotePackage = mock()


  override fun setUp() {
    super.setUp()
    whenever(mockAndroidSdks.tryToChooseSdkHandler().getSdkManager(any())).thenReturn(mockRepoManager)
    whenever(mockUpdatablePackage.remote).thenReturn(mockRemotePackage)
    whenever(mockRemotePackage.typeDetails).thenReturn(mockTypeDetail)
    TestApplicationManager.getInstance()
  }

  private fun managedVirtualDeviceCatalogTestHelperWrapper(
    deviceManager: DeviceManager? = mockDeviceManager,
    androidSdks: AndroidSdks? = mockAndroidSdks,
    callback: () -> Unit) = managedVirtualDeviceCatalogTestHelper(deviceManager, androidSdks) {
    whenever(mockTypeDetail.apiLevel).thenReturn(23)
    whenever(mockRepoManager.packages.consolidatedPkgs).thenReturn(mapOf(
      "system-images;android-23;android;armeabi-v7a" to mockUpdatablePackage))
    clearInvocations(mockRepoManager)
    callback()
  }

  fun testObtainAndroidDeviceCatalog() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      assertFalse(managedVirtualDeviceCatalogService.state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      assertTrue(managedVirtualDeviceCatalogService.state.isCacheFresh())
      verify(mockDeviceManager).getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR))
      verify(mockRepoManager).packages
    }
  }

  fun testCacheIsFresh() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val calendar = Calendar.getInstance()
      calendar.add(Calendar.DATE, 1)
      val managedVirtualDeviceCatalogService = ManagedVirtualDeviceCatalogService()
      managedVirtualDeviceCatalogService.loadState(ManagedVirtualDeviceCatalogState(calendar.time,
                                                                                    ManagedVirtualDeviceCatalogService.syncDeviceCatalog()))
      assertTrue(managedVirtualDeviceCatalogService.state.isCacheFresh())
      managedVirtualDeviceCatalogService.updateDeviceCatalogTaskAction(mockProject, mockProgressIndicator)
      // The only time we invoked mockDeviceManager and mockRepoManager is when freshManagedVirtualDeviceCatalogState is syncing
      verify(mockDeviceManager).getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR))
      verify(mockRepoManager, times(1)).packages
    }
  }
}