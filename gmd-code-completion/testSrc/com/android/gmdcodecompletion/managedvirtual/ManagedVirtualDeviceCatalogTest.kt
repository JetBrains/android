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
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.AndroidSdks
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.TestApplicationManager
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations.openMocks
import org.mockito.kotlin.whenever
import java.util.EnumSet

class ManagedVirtualDeviceCatalogTest : LightPlatformTestCase() {
  @Mock
  private lateinit var mockDeviceManager: DeviceManager

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private lateinit var mockAndroidSdks: AndroidSdks

  private val packages = RepositoryPackages()

  private val repoManager = FakeRepoManager(packages)

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
    whenever(mockAndroidSdks.tryToChooseSdkHandler()).thenReturn(AndroidSdkHandler(null, null, repoManager))
    TestApplicationManager.getInstance()
  }

  fun testManagedVirtualDeviceCatalogNoSync() {
    assertTrue(ManagedVirtualDeviceCatalog().isEmptyCatalog)
  }

  private fun buildTestDevice(
    deviceName: String = testDeviceName,
    manufacturer: String = testBrand,
    software: Software = Software(),
    state: State = testDeviceState(),
    deprecated: Boolean = false,
  ): Device {
    val deviceBuilder = Device.Builder()
    deviceBuilder.setName(deviceName)
    deviceBuilder.setManufacturer(manufacturer)
    deviceBuilder.addSoftware(software)
    deviceBuilder.addState(state)
    deviceBuilder.setDeprecated(deprecated)
    return deviceBuilder.build()
  }

  private fun managedVirtualDeviceCatalogTestHelperWrapper(
    testSystemImageString: String = "",
    testAndroidVersion: AndroidVersion = AndroidVersion(0, null),
    testAbiInfo: String = "",
    deviceManager: DeviceManager? = mockDeviceManager,
    androidSdks: AndroidSdks? = mockAndroidSdks,
    callback: () -> Unit) = managedVirtualDeviceCatalogTestHelper(deviceManager, androidSdks) {
    if (testSystemImageString.isNotEmpty()) {
      repoManager.packages.setRemotePkgInfos(listOf(
        FakePackage.FakeRemotePackage(testSystemImageString).apply {
          typeDetails =
            AndroidSdkHandler.getSysImgModule()
              .createLatestFactory()
              .createSysImgDetailsType()
              .apply {
                apiLevel = testAndroidVersion.androidApiLevel.majorVersion
                codename = testAndroidVersion.codename
                abis.add(testAbiInfo)
              } as TypeDetails
        }
      ))
    }
    callback()
  }

  fun testDeprecatedDevice() {
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-23;android;armeabi-v7a",
      testAndroidVersion = AndroidVersion(23, null)
    ) {
      val deprecatedDevice = buildTestDevice(deprecated = true)
      whenever(mockDeviceManager.getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR)))
        .thenReturn(listOf(deprecatedDevice))
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isEmpty())
      verify(mockDeviceManager).getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR))
    }
  }

  fun testNoSupportedApiRange() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-23;android;armeabi-v7a",
      testAndroidVersion = AndroidVersion(testApiLevel, null)
    ) {
      whenever(mockDeviceManager.getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR)))
        .thenReturn(listOf(buildTestDevice()))
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isNotEmpty())
      val deviceInfo = deviceCatalog.devices[testDeviceName]!!
      assertEquals(deviceInfo.supportedApis, listOf(23))
      assertEquals(deviceInfo.brand, testBrand)
    }
  }

  fun testFullDeviceInformation() {
    val testSoftware = Software()
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-33;android;armeabi-v7a",
      testAndroidVersion = AndroidVersion(33, null)
    ) {
      val testDevice = buildTestDevice(software = testSoftware)
      whenever(mockDeviceManager.getDevices(EnumSet.of(DeviceManager.DeviceCategory.DEFAULT, DeviceManager.DeviceCategory.VENDOR)))
        .thenReturn(listOf(testDevice))
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertEquals(deviceCatalog.devices[testDeviceName]!!.supportedApis, listOf(33))
    }
  }

  fun testNullDeviceManager() {
    managedVirtualDeviceCatalogTestHelperWrapper(deviceManager = null) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.devices.isEmpty())
    }
  }

  fun testNullAndroidSdks() {
    managedVirtualDeviceCatalogTestHelperWrapper(androidSdks = null) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  fun testDoNotCollectNonSysImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("add-ons;addon-google_apis-google-19") {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  fun testCollectArmImage() {
    val testApiLevel = 23
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-23;android;armeabi-v7a",
      testAndroidVersion = AndroidVersion(testApiLevel, null)
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, "")
      assertEquals(apiLevelEntry.imageSource, "android")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  fun testCollectArm64Image() {
    val testApiLevel = 26
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-26;google_apis;arm64-v8a",
      testAndroidVersion = AndroidVersion(testApiLevel, null)
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
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
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-23;google_apis;x86",
      testAndroidVersion = AndroidVersion(testApiLevel, null)
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
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
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-23;google_apis;x86_64",
      testAndroidVersion = AndroidVersion(testApiLevel, null),
      testAbiInfo = "x86_64"
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
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
    val testCodename = "UpsideDownCakePrivacySandbox"
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;android-UpsideDownCakePrivacySandbox;google_apis_playstore;arm64-v8a",
      testAndroidVersion = AndroidVersion(testApiLevel, testCodename)
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertFalse(deviceCatalog.apiLevels.isEmpty())
      val apiLevelEntry = deviceCatalog.apiLevels[0]
      assertEquals(apiLevelEntry.apiLevel, testApiLevel)
      assertEquals(apiLevelEntry.apiPreview, testCodename)
      assertEquals(apiLevelEntry.imageSource, "google_apis_playstore")
      assertEquals(apiLevelEntry.require64Bit, false)
    }
  }

  // Remove test after b/272562190 is fixed
  fun testFilterAndroidTvImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-UpsideDownCakePrivacySandbox;android_tv;arm64-v8a") {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  // Remove test after b/272562193 is fixed
  fun testFilterAndroidAutoImage() {
    managedVirtualDeviceCatalogTestHelperWrapper("system-images;android-UpsideDownCakePrivacySandbox;android_auto;arm64-v8a") {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }

  fun testSkipSignedInstallIdDueToPathStructure() {
    managedVirtualDeviceCatalogTestHelperWrapper(
      testSystemImageString = "system-images;signed;android-33;android-wear;arm64-v8a",
      testAndroidVersion = AndroidVersion(23, null)
    ) {
      val deviceCatalog = ManagedVirtualDeviceCatalogService.syncDeviceCatalog()
      assertTrue(deviceCatalog.apiLevels.isEmpty())
    }
  }
}