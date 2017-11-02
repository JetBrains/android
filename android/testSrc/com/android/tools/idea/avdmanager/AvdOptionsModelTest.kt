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
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.Storage
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.IdDisplay
import com.android.sdklib.repository.targets.SystemImageManager
import com.google.common.collect.ImmutableList
import com.google.common.collect.Maps
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase

import java.io.File

import com.android.testutils.NoErrorsOrWarningsLogger

class AvdOptionsModelTest : AndroidTestCase() {

  private var myGooglePlayAvdInfo: AvdInfo? = null
  private var myNonPlayAvdInfo: AvdInfo? = null
  private var myGooglePlayDevice: Device? = null
  private var myNonPlayDevice: Device? = null

  private val myPropertiesMap = Maps.newHashMap<String, String>()

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    val fileOp = MockFileOp()
    val packages = RepositoryPackages()

    // Google Play image
    val googlePlayPath = "system-images;android-23;google_apis_playstore;x86"
    val googlePlayPkg = FakePackage.FakeLocalPackage(googlePlayPath)
    val googlePlayDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    googlePlayDetails.tag = IdDisplay.create("google_apis_playstore", "Google Play")
    googlePlayDetails.abi = "x86"
    googlePlayDetails.apiLevel = 23
    googlePlayPkg.setTypeDetails(googlePlayDetails as TypeDetails)
    googlePlayPkg.setInstalledPath(File(SDK_LOCATION, "23-play-x86"))
    fileOp.recordExistingFile(File(googlePlayPkg.location, SystemImageManager.SYS_IMG_NAME))

    // Non-Google Play image
    val nonPlayPath = "system-images;android-23;google_apis;x86"
    val nonPlayPkg = FakePackage.FakeLocalPackage(nonPlayPath)
    val nonPlayDetails = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType()
    nonPlayDetails.tag = IdDisplay.create("google_apis", "Google APIs")
    nonPlayDetails.abi = "x86"
    nonPlayDetails.apiLevel = 23
    nonPlayPkg.setTypeDetails(nonPlayDetails as TypeDetails)
    nonPlayPkg.setInstalledPath(File(SDK_LOCATION, "23-no-play-x86"))
    fileOp.recordExistingFile(File(nonPlayPkg.location, SystemImageManager.SYS_IMG_NAME))

    val pkgList: MutableList<LocalPackage> = ImmutableList.of(googlePlayPkg, nonPlayPkg)
    packages.setLocalPkgInfos(pkgList)

    val mgr = FakeRepoManager(File(SDK_LOCATION), packages)

    val sdkHandler = AndroidSdkHandler(File(SDK_LOCATION), File(AVD_LOCATION), fileOp, mgr)

    val progress = FakeProgressIndicator()
    val systemImageManager = sdkHandler.getSystemImageManager(progress)

    val googlePlayImage = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(googlePlayPath, progress)!!.location)
    val nonPlayImage = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(nonPlayPath, progress)!!.location)

    myGooglePlayAvdInfo = AvdInfo("name", File("ini"), "folder", googlePlayImage!!, myPropertiesMap)
    myNonPlayAvdInfo = AvdInfo("name", File("ini"), "folder", nonPlayImage!!, myPropertiesMap)

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
    var theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isTrue()

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.isPlayStoreCompatible).isFalse()
  }

  @Throws(Exception::class)
  fun testMinSdCardSize() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(100, Storage.Unit.MiB))

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Storage.Unit.MiB))

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Storage.Unit.MiB))

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minSdCardSize()).isEqualTo(Storage(10, Storage.Unit.MiB))
  }

  @Throws(Exception::class)
  fun testMinInternalMemSize() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(2, Storage.Unit.GiB))

    // Non-Google Play (Google Play image, non-Play device)
    optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(200, Storage.Unit.MiB))

    // Non-Google Play (non-Play image, Google-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(200, Storage.Unit.MiB))

    // Non-Google Play (non-Play image, non-Play device)
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    assertThat(optionsModel.minInternalMemSize()).isEqualTo(Storage(200, Storage.Unit.MiB))
  }

  @Throws(Exception::class)
  fun testEnsureMinimumMemory() {
    ensureSdkManagerAvailable()

    // Google Play
    var optionsModel = AvdOptionsModel(myGooglePlayAvdInfo)
    var theDevice = optionsModel.device();
    theDevice.setNullableValue(myGooglePlayDevice)
    optionsModel.sdCardStorage().setNullableValue(Storage(90, Storage.Unit.MiB))
    optionsModel.internalStorage().set(Storage(3, Storage.Unit.GiB))
    optionsModel.ensureMinimumMemory();
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(100, Storage.Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(3, Storage.Unit.GiB))

    optionsModel.sdCardStorage().setNullableValue(Storage(123, Storage.Unit.MiB))
    optionsModel.internalStorage().set(Storage(1, Storage.Unit.GiB))
    optionsModel.ensureMinimumMemory();
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(123, Storage.Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(2, Storage.Unit.GiB))

    // Non-Google Play
    optionsModel = AvdOptionsModel(myNonPlayAvdInfo)
    theDevice = optionsModel.device();
    theDevice.setNullableValue(myNonPlayDevice)
    optionsModel.sdCardStorage().setNullableValue(Storage(9, Storage.Unit.MiB))
    optionsModel.internalStorage().set(Storage(234, Storage.Unit.MiB))
    optionsModel.ensureMinimumMemory();
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(10, Storage.Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(234, Storage.Unit.MiB))

    optionsModel.sdCardStorage().setNullableValue(Storage(12, Storage.Unit.MiB))
    optionsModel.internalStorage().set(Storage(190, Storage.Unit.MiB))
    optionsModel.ensureMinimumMemory();
    assertThat(optionsModel.sdCardStorage().value).isEqualTo(Storage(12, Storage.Unit.MiB))
    assertThat(optionsModel.internalStorage().get()).isEqualTo(Storage(200, Storage.Unit.MiB))
  }

  companion object {
    private val SDK_LOCATION = "/sdk"
    private val AVD_LOCATION = "/avd"
  }
}
