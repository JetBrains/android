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

package com.android.tools.idea.avdmanager;

import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;

import static com.android.sdklib.repository.targets.SystemImage.*;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationForDevice;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationFromParts;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.*;

public class ChooseSystemImagePanelTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private SystemImageDescription myGapiImageDescription;
  private SystemImageDescription myPsImageDescription;
  private SystemImageDescription myWearImageDescription;
  private SystemImageDescription myWearCnImageDescription;
  private Device myGapiPhoneDevice;
  private Device myPlayStorePhoneDevice;
  private Device myWearDevice;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // Google API image
    String gapiPath = "system-images;android-23;google_apis;x86";
    FakePackage.FakeLocalPackage pkgGapi = new FakePackage.FakeLocalPackage(gapiPath);
    DetailsTypes.SysImgDetailsType detailsGapi =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsGapi.setTag(IdDisplay.create("google_apis", "Google APIs"));
    detailsGapi.setAbi("x86");
    detailsGapi.setVendor(IdDisplay.create("google", "Google"));
    detailsGapi.setApiLevel(23);
    pkgGapi.setTypeDetails((TypeDetails) detailsGapi);
    pkgGapi.setInstalledPath(new File(SDK_LOCATION, "23-marshmallow-x86"));
    fileOp.recordExistingFile(new File(pkgGapi.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Play Store image
    String psPath = "system-images;android-24;google_apis_playstore;x86";
    FakePackage.FakeLocalPackage pkgPs = new FakePackage.FakeLocalPackage(psPath);
    DetailsTypes.SysImgDetailsType detailsPs =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsPs.setTag(IdDisplay.create("google_apis_playstore", "Google Play"));
    detailsPs.setAbi("x86");
    detailsPs.setVendor(IdDisplay.create("google", "Google"));
    detailsPs.setApiLevel(24);
    pkgPs.setTypeDetails((TypeDetails) detailsPs);
    pkgPs.setInstalledPath(new File(SDK_LOCATION, "24-nougat-x86"));
    fileOp.recordExistingFile(new File(pkgPs.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Wear image
    String wearPath = "system-images;android-25;android-wear;x86";
    FakePackage.FakeLocalPackage pkgWear = new FakePackage.FakeLocalPackage(wearPath);
    DetailsTypes.SysImgDetailsType detailsWear =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsWear.setTag(IdDisplay.create("android-wear", "Android Wear"));
    detailsWear.setAbi("x86");
    detailsWear.setVendor(IdDisplay.create("google", "Google"));
    detailsWear.setApiLevel(25);
    pkgWear.setTypeDetails((TypeDetails)detailsWear);
    pkgWear.setInstalledPath(new File(SDK_LOCATION, "25-wear-x86"));
    fileOp.recordExistingFile(new File(pkgWear.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Wear for China image
    String wearCnPath = "system-images;android-25;android-wear-cn;x86";
    FakePackage.FakeLocalPackage pkgCnWear = new FakePackage.FakeLocalPackage(wearCnPath);
    DetailsTypes.SysImgDetailsType detailsWearCn =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsWearCn.setTag(IdDisplay.create("android-wear", "Android Wear for China"));
    detailsWearCn.setAbi("x86");
    detailsWearCn.setVendor(IdDisplay.create("google", "Google"));
    detailsWearCn.setApiLevel(25);
    pkgCnWear.setTypeDetails((TypeDetails)detailsWearCn);
    pkgCnWear.setInstalledPath(new File(SDK_LOCATION, "25-wear-cn-x86"));
    fileOp.recordExistingFile(new File(pkgCnWear.getLocation(), SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgGapi, pkgPs, pkgWear, pkgCnWear));

    RepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(new File(SDK_LOCATION), new File(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage gapiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(gapiPath, progress).getLocation());
    ISystemImage playStoreImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(psPath, progress).getLocation());
    ISystemImage wearImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wearPath, progress).getLocation());
    ISystemImage wearCnImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wearCnPath, progress).getLocation());

    myGapiImageDescription = new SystemImageDescription(gapiImage);
    myPsImageDescription = new SystemImageDescription(playStoreImage);
    myWearImageDescription = new SystemImageDescription(wearImage);
    myWearCnImageDescription = new SystemImageDescription(wearCnImage);

    // Make a phone device that does not support Google Play
    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device.Builder devBuilder = new Device.Builder(devMgr.getDevice("Nexus 5", "Google"));
    devBuilder.setPlayStore(false);
    myGapiPhoneDevice = devBuilder.build();

    // Get a phone device that supports Google Play
    myPlayStorePhoneDevice = devMgr.getDevice("Nexus 5", "Google");

    // Get a Wear device
    myWearDevice = devMgr.getDevice("wear_square", "Google");
  }

  public void testClassificationFromParts() throws Exception {
    assertEquals(X86, getClassificationFromParts(Abi.X86, 21, GOOGLE_APIS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 22, GOOGLE_APIS_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 23, DEFAULT_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 24, GOOGLE_APIS_X86_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86_64, 25, GOOGLE_APIS_X86_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, GOOGLE_APIS_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, GOOGLE_APIS_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, GOOGLE_APIS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, WEAR_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 24, WEAR_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, WEAR_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 25, GLASS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, TV_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, TV_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 25, DEFAULT_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, DEFAULT_TAG));
  }

  public void testClassificationForDevice() throws Exception {
    assertEquals(RECOMMENDED, getClassificationForDevice(myGapiImageDescription, myGapiPhoneDevice));
    assertEquals(X86, getClassificationForDevice(myGapiImageDescription, myPlayStorePhoneDevice));
    // Note: Play Store image is not allowed with a non-Play-Store device
    assertEquals(RECOMMENDED, getClassificationForDevice(myPsImageDescription, myPlayStorePhoneDevice));

    assertEquals(RECOMMENDED, getClassificationForDevice(myWearImageDescription, myWearDevice));
    assertEquals(RECOMMENDED, getClassificationForDevice(myWearCnImageDescription, myWearDevice));
  }
}
