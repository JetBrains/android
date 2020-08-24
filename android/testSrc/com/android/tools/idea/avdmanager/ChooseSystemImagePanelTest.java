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
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.systemImageMatchesDevice;

public class ChooseSystemImagePanelTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private SystemImageDescription myGapiImageDescription;
  private SystemImageDescription myGapi29ImageDescription;
  private SystemImageDescription myGapi30ImageDescription;
  private SystemImageDescription myPsImageDescription;
  private SystemImageDescription myWearImageDescription;
  private SystemImageDescription myWear29ImageDescription;
  private SystemImageDescription myWearCnImageDescription;
  private SystemImageDescription myAutomotiveImageDescription;
  private SystemImageDescription myAutomotivePsImageDescription;
  private Device myBigPhone;
  private Device myFoldable;
  private Device myGapiPhoneDevice;
  private Device myPlayStorePhoneDevice;
  private Device mySmallTablet;
  private Device myWearDevice;
  private Device myAutomotiveDevice;
  private Device myFreeform;

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

    // Google API 29 image
    String gapi29Path = "system-images;android-29;google_apis;x86";
    FakePackage.FakeLocalPackage pkgGapi29 = new FakePackage.FakeLocalPackage(gapi29Path);
    DetailsTypes.SysImgDetailsType detailsGapi29 =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsGapi29.setTag(IdDisplay.create("google_apis", "Google APIs"));
    detailsGapi29.setAbi("x86");
    detailsGapi29.setVendor(IdDisplay.create("google", "Google"));
    detailsGapi29.setApiLevel(29);
    pkgGapi29.setTypeDetails((TypeDetails) detailsGapi29);
    pkgGapi29.setInstalledPath(new File(SDK_LOCATION, "29-Q-x86"));
    fileOp.recordExistingFile(new File(pkgGapi29.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Google API 30 image
    String gapi30Path = "system-images;android-30;google_apis;x86";
    FakePackage.FakeLocalPackage pkgGapi30 = new FakePackage.FakeLocalPackage(gapi30Path);
    DetailsTypes.SysImgDetailsType detailsGapi30 =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsGapi30.setTag(IdDisplay.create("google_apis", "Google APIs"));
    detailsGapi30.setAbi("x86");
    detailsGapi30.setVendor(IdDisplay.create("google", "Google"));
    detailsGapi30.setApiLevel(30);
    pkgGapi30.setTypeDetails((TypeDetails)detailsGapi30);
    pkgGapi30.setInstalledPath(new File(SDK_LOCATION, "30-R-x86"));
    fileOp.recordExistingFile(new File(pkgGapi30.getLocation(), SystemImageManager.SYS_IMG_NAME));

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
    detailsWear.setTag(IdDisplay.create("android-wear", "Wear OS"));
    detailsWear.setAbi("x86");
    detailsWear.setVendor(IdDisplay.create("google", "Google"));
    detailsWear.setApiLevel(25);
    pkgWear.setTypeDetails((TypeDetails)detailsWear);
    pkgWear.setInstalledPath(new File(SDK_LOCATION, "25-wear-x86"));
    fileOp.recordExistingFile(new File(pkgWear.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Wear API29 image
    String wear29Path = "system-images;android-29;android-wear;x86";
    FakePackage.FakeLocalPackage pkgWear29 = new FakePackage.FakeLocalPackage(wear29Path);
    DetailsTypes.SysImgDetailsType detailsWear29 =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsWear29.setTag(IdDisplay.create("android-wear", "Wear OS"));
    detailsWear29.setAbi("x86");
    detailsWear29.setVendor(IdDisplay.create("google", "Google"));
    detailsWear29.setApiLevel(29);
    pkgWear29.setTypeDetails((TypeDetails)detailsWear29);
    pkgWear29.setInstalledPath(new File(SDK_LOCATION, "29-wear-x86"));
    fileOp.recordExistingFile(new File(pkgWear29.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Wear for China image
    String wearCnPath = "system-images;android-25;android-wear-cn;x86";
    FakePackage.FakeLocalPackage pkgCnWear = new FakePackage.FakeLocalPackage(wearCnPath);
    DetailsTypes.SysImgDetailsType detailsWearCn =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsWearCn.setTag(IdDisplay.create("android-wear", "Wear OS for China"));
    detailsWearCn.setAbi("x86");
    detailsWearCn.setVendor(IdDisplay.create("google", "Google"));
    detailsWearCn.setApiLevel(25);
    pkgCnWear.setTypeDetails((TypeDetails)detailsWearCn);
    pkgCnWear.setInstalledPath(new File(SDK_LOCATION, "25-wear-cn-x86"));
    fileOp.recordExistingFile(new File(pkgCnWear.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Automotive image
    String automotivePath = "system-images;android-28;android-automotive;x86";
    FakePackage.FakeLocalPackage pkgAutomotive = new FakePackage.FakeLocalPackage(automotivePath);
    DetailsTypes.SysImgDetailsType detailsAutomotive =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsAutomotive.setTag(IdDisplay.create("android-automotive", "Android Automotive"));
    detailsAutomotive.setAbi("x86");
    detailsAutomotive.setVendor(IdDisplay.create("google", "Google"));
    detailsAutomotive.setApiLevel(28);
    pkgAutomotive.setTypeDetails((TypeDetails)detailsAutomotive);
    pkgAutomotive.setInstalledPath(new File(SDK_LOCATION, "28-automotive-x86"));
    fileOp.recordExistingFile(new File(pkgAutomotive.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // Android Automotive with Play Store image
    String automotivePsPath = "system-images;android-28;android-automotive-playstore;x86";
    FakePackage.FakeLocalPackage pkgAutomotivePs = new FakePackage.FakeLocalPackage(automotivePsPath);
    DetailsTypes.SysImgDetailsType detailsAutomotivePs =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    detailsAutomotivePs.setTag(IdDisplay.create("android-automotive-playstore", "Android Automotive with Google Play"));
    detailsAutomotivePs.setAbi("x86");
    detailsAutomotivePs.setVendor(IdDisplay.create("google", "Google"));
    detailsAutomotivePs.setApiLevel(28);
    pkgAutomotivePs.setTypeDetails((TypeDetails)detailsAutomotivePs);
    pkgAutomotivePs.setInstalledPath(new File(SDK_LOCATION, "28-automotive-playstore-x86"));
    fileOp.recordExistingFile(new File(pkgAutomotivePs.getLocation(), SystemImageManager.SYS_IMG_NAME));

    packages.setLocalPkgInfos(ImmutableList.of(pkgGapi, pkgGapi29, pkgGapi30, pkgPs, pkgWear, pkgWear29, pkgCnWear, pkgAutomotive, pkgAutomotivePs));

    RepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(new File(SDK_LOCATION), new File(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage gapiImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(gapiPath, progress).getLocation());
    ISystemImage gapi29Image = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(gapi29Path, progress).getLocation());
    ISystemImage gapi30Image = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(gapi30Path, progress).getLocation());
    ISystemImage playStoreImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(psPath, progress).getLocation());
    ISystemImage wearImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wearPath, progress).getLocation());
    ISystemImage wear29Image = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wear29Path, progress).getLocation());
    ISystemImage wearCnImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(wearCnPath, progress).getLocation());
    ISystemImage automotiveImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(automotivePath, progress).getLocation());
    ISystemImage automotivePsImage = systemImageManager.getImageAt(
      sdkHandler.getLocalPackage(automotivePsPath, progress).getLocation());

    myGapiImageDescription = new SystemImageDescription(gapiImage);
    myGapi29ImageDescription = new SystemImageDescription(gapi29Image);
    myGapi30ImageDescription = new SystemImageDescription(gapi30Image);
    myPsImageDescription = new SystemImageDescription(playStoreImage);
    myWearImageDescription = new SystemImageDescription(wearImage);
    myWear29ImageDescription = new SystemImageDescription(wear29Image);
    myWearCnImageDescription = new SystemImageDescription(wearCnImage);
    myAutomotiveImageDescription = new SystemImageDescription(automotiveImage);
    myAutomotivePsImageDescription = new SystemImageDescription(automotivePsImage);

    // Make a phone device that does not support Google Play
    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device.Builder devBuilder = new Device.Builder(devMgr.getDevice("Nexus 5", "Google"));
    devBuilder.setPlayStore(false);
    myGapiPhoneDevice = devBuilder.build();

    // Get a phone device that supports Google Play
    myPlayStorePhoneDevice = devMgr.getDevice("Nexus 5", "Google");

    // Get a Wear device
    myWearDevice = devMgr.getDevice("wear_square", "Google");

    // Get a big phone, a bigger foldable, and a small tablet
    myBigPhone = devMgr.getDevice("pixel_3_xl", "Google");
    myFoldable = devMgr.getDevice("7.3in Foldable", "Generic");
    mySmallTablet = devMgr.getDevice("Nexus 7", "Google");

    // Get an Automotive device
    myAutomotiveDevice = devMgr.getDevice("automotive_1024p_landscape", "Google");

    // Get a Freeform device
    myFreeform = devMgr.getDevice("13.5in Freeform", "Generic");
  }

  public void testClassificationFromParts() {
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
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, TV_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, TV_TAG));
    assertEquals(X86, getClassificationFromParts(Abi.X86, 25, DEFAULT_TAG));
    assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, DEFAULT_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 25, CHROMEOS_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 28, AUTOMOTIVE_TAG));
    assertEquals(RECOMMENDED, getClassificationFromParts(Abi.X86, 28, AUTOMOTIVE_PLAY_STORE_TAG));
  }

  public void testClassificationForDevice() {
    assertEquals(RECOMMENDED, getClassificationForDevice(myGapiImageDescription, myGapiPhoneDevice));
    assertEquals(X86, getClassificationForDevice(myGapiImageDescription, myPlayStorePhoneDevice));
    // Note: Play Store image is not allowed with a non-Play-Store device
    assertEquals(RECOMMENDED, getClassificationForDevice(myPsImageDescription, myPlayStorePhoneDevice));

    assertEquals(RECOMMENDED, getClassificationForDevice(myWearImageDescription, myWearDevice));
    assertEquals(RECOMMENDED, getClassificationForDevice(myWearCnImageDescription, myWearDevice));

    // Note: myAutomotiveDevice is Play-Store device
    assertEquals(X86, getClassificationForDevice(myAutomotiveImageDescription, myAutomotiveDevice));
    assertEquals(RECOMMENDED, getClassificationForDevice(myAutomotivePsImageDescription, myAutomotiveDevice));
  }

  public void testPhoneVsTablet() {
    assertFalse(DeviceDefinitionList.isTablet(myBigPhone));
    assertFalse(DeviceDefinitionList.isTablet(myFoldable));
    assertTrue(DeviceDefinitionList.isTablet(mySmallTablet));
    assertTrue(DeviceDefinitionList.isTablet(myFreeform));
  }

  public void testImageChosenForDevice() {
      assertFalse(systemImageMatchesDevice(myWearImageDescription, myFoldable));
      assertFalse(systemImageMatchesDevice(myWear29ImageDescription, myFoldable));
      assertFalse(systemImageMatchesDevice(myGapiImageDescription, myFoldable));
      assertTrue(systemImageMatchesDevice(myGapi30ImageDescription, myFoldable));
      assertFalse(systemImageMatchesDevice(myWearImageDescription, myFreeform));
      assertFalse(systemImageMatchesDevice(myWear29ImageDescription, myFreeform));
      assertFalse(systemImageMatchesDevice(myGapiImageDescription, myFreeform));
      assertFalse(systemImageMatchesDevice(myGapi29ImageDescription, myFreeform));
      assertTrue(systemImageMatchesDevice(myGapi30ImageDescription, myFreeform));
  }

  public void testDeviceType() {
    assertEquals("Phone", DeviceDefinitionList.getCategory(myBigPhone));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myFoldable));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myGapiPhoneDevice));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myPlayStorePhoneDevice));
    assertEquals("Tablet", DeviceDefinitionList.getCategory(mySmallTablet));
    assertEquals("Wear OS", DeviceDefinitionList.getCategory(myWearDevice));
    assertEquals("Automotive", DeviceDefinitionList.getCategory(myAutomotiveDevice));
    assertEquals("Tablet", DeviceDefinitionList.getCategory(myFreeform));
  }
}
