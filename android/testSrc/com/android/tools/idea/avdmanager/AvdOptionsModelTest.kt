/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.idea.avdmanager

import com.android.repository.api.LocalPackage
import com.android.repository.impl.meta.RepositoryPackages
import com.android.repository.impl.meta.TypeDetails
import com.android.repository.testframework.FakePackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.repository.testframework.FakeRepoManager
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Storage
import com.android.sdklib.devices.Storage.Unit
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImageManager
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.NoErrorsOrWarningsLogger
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito
import java.io.File
import java.nio.file.Paths

class AvdOptionsModelTest : AndroidTestCase() {

  private var myGooglePlayAvdInfo: AvdInfo? = null
  private var myNonPlayAvdInfo: AvdInfo? = null
  private var myGooglePlayDevice: Device? = null
  private var myNonPlayDevice: Device? = null

  private val myPropertiesMap = Maps.newHashMap<String, String>()

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    val packages = RepositoryPackages()
    val sdkRoot = createInMemoryFileSystemAndFolder("sdk")

    // Google Play image
    val googlePlayPath = "system-images;android-23;google_apis_playstore;x86"
    val googlePlayPkg = FakePackage.FakeLocalPackage(googlePlayPath, sdkRoot.resolve("playSysImg"))
    val googlePlayDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    googlePlayDetails.tags.add(IdDisplay.create("google_apis_playstore", "Google Play"))
    googlePlayDetails.abi = "x86"
    googlePlayDetails.apiLevel = 23
    googlePlayPkg.typeDetails = googlePlayDetails as TypeDetails
    googlePlayPkg.location.resolve(SystemImageManager.SYS_IMG_NAME).recordExistingFile()

    // Non-Google Play image
    val nonPlayPath = "system-images;android-23;google_apis;x86"
    val nonPlayPkg = FakePackage.FakeLocalPackage(nonPlayPath, sdkRoot.resolve("gapiSysImg"))
    val nonPlayDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    nonPlayDetails.tags.add(IdDisplay.create("google_apis", "Google APIs"))
    nonPlayDetails.abi = "x86"
    nonPlayDetails.apiLevel = 23
    nonPlayPkg.typeDetails = nonPlayDetails as TypeDetails
    nonPlayPkg.location.resolve(SystemImageManager.SYS_IMG_NAME).recordExistingFile()

    val pkgList: MutableList<LocalPackage> = ImmutableList.of(googlePlayPkg, nonPlayPkg)
    packages.setLocalPkgInfos(pkgList)

    val mgr = FakeRepoManager(sdkRoot, packages)
    val sdkHandler = AndroidSdkHandler(sdkRoot, sdkRoot.root.resolve("avd"), mgr)

    val progress = FakeProgressIndicator()
    val systemImageManager = sdkHandler.getSystemImageManager(progress)

    val googlePlayImage = systemImageManager.getImageAt(sdkHandler.getLocalPackage(googlePlayPath, progress)!!.location)
    val nonPlayImage = systemImageManager.getImageAt(sdkHandler.getLocalPackage(nonPlayPath, progress)!!.location)

    myGooglePlayAvdInfo = AvdInfo("name", Paths.get("ini"), Paths.get("folder"), googlePlayImage!!, myPropertiesMap)
    myNonPlayAvdInfo = AvdInfo("name", Paths.get("ini"), Paths.get("folder"), nonPlayImage!!, myPropertiesMap)

    // Get a phone device that supports Google Play
    val devMgr = DeviceManager.createInstance(sdkHandler, NoErrorsOrWarningsLogger())
    myGooglePlayDevice = devMgr.getDevice("Nexus 5", "Google")

    // Make a phone device that does not support Google Play
    val devBuilder = Device.Builder(devMgr.getDevice("Nexus 5", "Google"))
    devBuilder.setPlayStore(false)
    myNonPlayDevice = devBuilder.build()
  }

  @Throws(Exception::class)
  fun testIsPlayStoreCompatible() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isTrue()

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()
  }

  @Throws(Exception::class)
  fun testMinSdCardSize() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(100, Unit.MiB))

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Unit.MiB))

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Unit.MiB))

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Unit.MiB))


    // For the device without Sdcard
    myNonPlayDevice?.defaultHardware?.setSdCard(false)
    myGooglePlayDevice?.defaultHardware?.setSdCard(false)

    // Google Play
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(0, Unit.MiB))

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(0, Unit.MiB))

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(0, Unit.MiB))

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(0, Unit.MiB))
  }

  @Throws(Exception::class)
  fun testMinInternalMemSize() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(2, Unit.GiB))

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(2, Unit.GiB))

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(2, Unit.GiB))

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(2, Unit.GiB))
  }

  @Throws(Exception::class)
  fun testEnsureMinimumMemory() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device()
    theDevice.setNullableValue(myGooglePlayDevice)
    optionsModel.sdCardStorage().setNullableValue(Storage(90, Unit.MiB))
    optionsModel.internalStorage().set(Storage(3, Unit.GiB))
    optionsModel.ensureMinimumMemory()
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(100, Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(3, Unit.GiB))

    optionsModel.sdCardStorage().setNullableValue(Storage(123, Unit.MiB))
    optionsModel.internalStorage().set(Storage(1, Unit.GiB))
    optionsModel.ensureMinimumMemory()
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(123, Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(2, Unit.GiB))

    // Non-Google Play
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device()
    theDevice.setNullableValue(myNonPlayDevice)
    optionsModel.sdCardStorage().setNullableValue(Storage(9, Unit.MiB))
    optionsModel.internalStorage().set(Storage(3, Unit.GiB))
    optionsModel.ensureMinimumMemory()
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(10, Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(3, Unit.GiB))

    optionsModel.sdCardStorage().setNullableValue(Storage(12, Unit.MiB))
    optionsModel.internalStorage().set(Storage(1, Unit.GiB))
    optionsModel.ensureMinimumMemory()
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(12, Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(2, Unit.GiB))
  }

  fun testAvdOptionsModelEnableDeviceFrameCheckboxIsntSelected() {
    // Arrange
    val avd = Mockito.mock(AvdInfo::class.java)

    whenever(avd.deviceManufacturer).thenReturn("Google")
    whenever(avd.deviceName).thenReturn("pixel_3")
    whenever(avd.displayName).thenReturn("Pixel 3 API 30")
    whenever(avd.properties).thenReturn(hashMapOf(AvdWizardUtils.CUSTOM_SKIN_FILE_KEY to SkinUtils.NO_SKIN))

    // Act
    val model = AvdOptionsModel(avd)

    // Assert
    assertThat(model.avdDeviceData.customSkinFile().value).isEqualTo(File(
      SkinUtils.NO_SKIN))
  }
}
