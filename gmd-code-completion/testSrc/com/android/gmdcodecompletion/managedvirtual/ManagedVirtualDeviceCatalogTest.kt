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
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.repository.generated.sysimg.v1.SysImgDetailsType
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.openMocks

class ManagedVirtualDeviceCatalogTest : LightPlatformTestCase() {
  @Mock
  private lateinit var mockDeviceManager: DeviceManager

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockAndroidSdks: AndroidSdks

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockRepoManager: RepoManager

  @Mock
  private lateinit var mockUpdatablePackage: UpdatablePackage

  @Mock
  private lateinit var mockTypeDetail: SysImgDetailsType

  @Mock
  private lateinit var mockRemotePackage: RemotePackage

  companion object {
    private val testDeviceState: () -> State = {
      val state = State()
      val hardware = Hardware()
      hardware.screen = Screen()
      state.hardware = hardware
      state.isDefaultState = true
      state
    }

    private const val testDeviceName = "testDevice"
    private const val testBrand = "testBrand"
  }

  override fun setUp() {
    super.setUp()
    openMocks(this)
    whenever(mockAndroidSdks.tryToChooseSdkHandler().getSdkManager(any())).thenReturn(mockRepoManager)
    whenever(mockUpdatablePackage.remote).thenReturn(mockRemotePackage)
    whenever(mockRemotePackage.typeDetails).thenReturn(mockTypeDetail)
    TestApplicationManager.getInstance()
  }

  fun testManagedVirtualDeviceCatalogNoSync() {
    assertTrue(ManagedVirtualDeviceCatalog().isEmpty())
  }

  private fun buildTestDevice(
    deviceName: String = testDeviceName,
    manufacturer: String = testBrand,
    software: Software = Software(),
    state: State = testDeviceState()
  ): Device {
    val deviceBuilder = Device.Builder()
    deviceBuilder.setName(deviceName)
    deviceBuilder.setManufacturer(manufacturer)
    deviceBuilder.addSoftware(software)
    deviceBuilder.addState(state)
    return deviceBuilder.build()
  }

  private fun managedVirtualDeviceCatalogTestHelperWrapper(
    testSystemImageString: String = "",
    testApiLevel: Int = 0,
    deviceManager: DeviceManager? = mockDeviceManager,
    androidSdks: AndroidSdks? = mockAndroidSdks,
    callback: () -> Unit) = managedVirtualDeviceCatalogTestHelper(deviceManager, androidSdks) {
    whenever(mockTypeDetail.apiLevel).thenReturn(testApiLevel)
    whenever(mockRepoManager.packages.consolidatedPkgs).thenReturn(mapOf(testSystemImageString to mockUpdatablePackage))
    callback()
  }

  fun testDeprecatedDevice() {
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-23;android;armeabi-v7a", 23) {
      val deprecatedDevice = buildTestDevice()
      deprecatedDevice.isDeprecated = true
      whenever(mockDeviceManager.getDevices(DeviceManager.ALL_DEVICES)).thenReturn(listOf(deprecatedDevice))
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isEmpty())
      verify(mockDeviceManager).getDevices(DeviceManager.ALL_DEVICES)
    }
  }

  fun testNoSupportedApiRange() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-23;android;armeabi-v7a", testApiLevel) {
      whenever(mockDeviceManager.getDevices(DeviceManager.ALL_DEVICES)).thenReturn(listOf(buildTestDevice()))
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isNotEmpty())
      val deviceInfo = deviceCatalog.devices[testDeviceName]!!
      assertEquals(deviceInfo.supportedApis, (0..testApiLevel).toList())
      assertEquals(deviceInfo.brand, testBrand)
      assertEquals(deviceInfo.deviceName, testDeviceName)
    }
  }

  fun testFullDeviceInformation() {
    val testSoftware = Software()
    val minApiLevel = 12
    val maxApiLevel = 33
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-33;android;armeabi-v7a", maxApiLevel) {
      testSoftware.minSdkLevel = minApiLevel
      testSoftware.maxSdkLevel = maxApiLevel
      val testDevice = buildTestDevice(software = testSoftware)
      whenever(mockDeviceManager.getDevices(DeviceManager.ALL_DEVICES)).thenReturn(listOf(testDevice))
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertEquals(deviceCatalog.devices[testDeviceName]!!.supportedApis, (minApiLevel..maxApiLevel).toList())
    }
  }

  fun testNullDeviceManager() {
    managedVirtualDeviceCatalogTestHelperWrapper(deviceManager = null) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isEmpty())
    }
  }

  fun testNullAndroidSdks() {
    managedVirtualDeviceCatalogTestHelperWrapper(androidSdks = null) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  fun testDoNotCollectNonSysImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("add-ons;addon-google_apis-google-19") {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
      verify(mockRepoManager.packages).consolidatedPkgs
    }
  }

  fun testCollectArmImage() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-23;android;armeabi-v7a", testApiLevel) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "")
      assertEquals(apiLevelEntry.imageSource, "android")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  fun testCollectArm64Image() {
    managedVirtualDeviceCatalogTestHelperWrapper {
      val testApiLevel = 26
      whenever(mockTypeDetail.apiLevel).thenReturn(testApiLevel)
      whenever(mockRepoManager.packages.consolidatedPkgs).thenReturn(mapOf(
        "system-images;android-26;google_apis;arm64-v8a" to mockUpdatablePackage))
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "")
      assertEquals(apiLevelEntry.imageSource, "google_apis")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  fun testCollectX86Image() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-23;google_apis;x86", testApiLevel) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "")
      assertEquals(apiLevelEntry.imageSource, "google_apis")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  fun testCollectX86_64Image() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-23;google_apis;x86_64", testApiLevel) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "")
      assertEquals(apiLevelEntry.imageSource, "google_apis")
      assertEquals(apiLevelEntry.require64Bit, true)
    }
  }

  fun testCollectApiPreviewImage() {
    val testApiLevel = 33
    managedVirtualDeviceCatalogTestHelperWrapper(
      "system-images;android-TiramisuPrivacySandbox;google_apis_playstore;arm64-v8a", testApiLevel) {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "TiramisuPrivacySandbox")
      assertEquals(apiLevelEntry.imageSource, "google_apis_playstore")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  // Remove test after b/272562190 is fixed
  fun testFilterAndroidTvImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-TiramisuPrivacySandbox;android_tv;arm64-v8a") {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  // Remove test after b/272562193 is fixed
  fun testFilterAndroidAutoImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-TiramisuPrivacySandbox;android_auto;arm64-v8a") {
      val deviceCatalog = ManagedVirtualDeviceCatalog().syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }
}