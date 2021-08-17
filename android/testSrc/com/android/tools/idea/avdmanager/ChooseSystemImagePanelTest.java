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

import static com.android.sdklib.repository.targets.SystemImage.ANDROID_TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.repository.targets.SystemImage.AUTOMOTIVE_TAG;
import static com.android.sdklib.repository.targets.SystemImage.CHROMEOS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.DEFAULT_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.repository.targets.SystemImage.GOOGLE_TV_TAG;
import static com.android.sdklib.repository.targets.SystemImage.WEAR_TAG;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.OTHER;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.RECOMMENDED;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.X86;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationForDevice;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationFromParts;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.systemImageMatchesDevice;

import com.android.repository.api.LocalPackage;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;

public class ChooseSystemImagePanelTest extends AndroidTestCase {

  private static final String SDK_LOCATION = "/sdk";
  private static final String AVD_LOCATION = "/avd";

  private static FakePackage.FakeLocalPackage createSysimgPackage(String sysimgPath, String abi, IdDisplay tag, IdDisplay vendor,
                                                                  int apiLevel, MockFileOp fileOp) {
    FakePackage.FakeLocalPackage pkg = new FakePackage.FakeLocalPackage(sysimgPath, fileOp);
    DetailsTypes.SysImgDetailsType sysimgDetails =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    sysimgDetails.getTags().add(tag);
    sysimgDetails.setAbi(abi);
    sysimgDetails.setVendor(vendor);
    sysimgDetails.setApiLevel(apiLevel);
    pkg.setTypeDetails((TypeDetails) sysimgDetails);
    fileOp.recordExistingFile(pkg.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    return pkg;
  }

  /**
   * Generates a list of system images with a given abi to test with.
   */
  private class SystemImageTestList {
    // Google API image
    String gapiPath = "system-images;android-23;google_apis;";
    // Google API 29 image
    String gapi29Path = "system-images;android-29;google_apis;";
    // Google API 30 image
    String gapi30Path = "system-images;android-30;google_apis;";
    // Play Store image
    String psPath = "system-images;android-24;google_apis_playstore;";
    // Android Wear image
    String wearPath = "system-images;android-25;android-wear;";
    // Android Wear API29 image
    String wear29Path = "system-images;android-29;android-wear;";
    // Android Wear for China image
    String wearCnPath = "system-images;android-25;android-wear-cn;";
    // Android Automotive image
    String automotivePath = "system-images;android-28;android-automotive;";
    // Android Automotive with Play Store image
    String automotivePsPath = "system-images;android-28;android-automotive-playstore;";

    FakePackage.FakeLocalPackage pkgGapi;
    FakePackage.FakeLocalPackage pkgGapi29;
    FakePackage.FakeLocalPackage pkgGapi30;
    FakePackage.FakeLocalPackage pkgPs;
    FakePackage.FakeLocalPackage pkgWear;
    FakePackage.FakeLocalPackage pkgWear29;
    FakePackage.FakeLocalPackage pkgCnWear;
    FakePackage.FakeLocalPackage pkgAutomotive;
    FakePackage.FakeLocalPackage pkgAutomotivePs;

    SystemImageDescription gapiImageDescription;
    SystemImageDescription gapi29ImageDescription;
    SystemImageDescription gapi30ImageDescription;
    SystemImageDescription psImageDescription;
    SystemImageDescription wearImageDescription;
    SystemImageDescription wear29ImageDescription;
    SystemImageDescription wearCnImageDescription;
    SystemImageDescription automotiveImageDescription;
    SystemImageDescription automotivePsImageDescription;

    SystemImageTestList(String abi, MockFileOp fileOp) {
      gapiPath += abi;
      gapi29Path += abi;
      gapi30Path += abi;
      psPath += abi;
      wearPath += abi;
      wear29Path += abi;
      wearCnPath += abi;
      automotivePath += abi;
      automotivePsPath += abi;

      pkgGapi = createSysimgPackage(gapiPath, abi, IdDisplay.create("google_apis", "Google APIs"),
                                    IdDisplay.create("google", "Google"), 23, fileOp);
      pkgGapi29 = createSysimgPackage(gapi29Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 29, fileOp);
      pkgGapi30 = createSysimgPackage(gapi30Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 30, fileOp);
      pkgPs = createSysimgPackage(psPath, abi, IdDisplay.create("google_apis_playstore", "Google Play"),
                                  IdDisplay.create("google", "Google"), 24, fileOp);
      pkgWear = createSysimgPackage(wearPath, abi, IdDisplay.create("android-wear", "Wear OS"),
                                    IdDisplay.create("google", "Google"), 25, fileOp);
      pkgWear29 = createSysimgPackage(wear29Path, abi, IdDisplay.create("android-wear", "Wear OS"),
                                      IdDisplay.create("google", "Google"), 29, fileOp);
      pkgCnWear = createSysimgPackage(wearCnPath, abi, IdDisplay.create("android-wear", "Wear OS for China"),
                                      IdDisplay.create("google", "Google"), 25, fileOp);
      pkgAutomotive = createSysimgPackage(automotivePath, abi, IdDisplay.create("android-automotive", "Android Automotive"),
                                          IdDisplay.create("google", "Google"), 28, fileOp);
      pkgAutomotivePs = createSysimgPackage(automotivePsPath, abi, IdDisplay.create("android-automotive-playstore",
                                                                                    "Android Automotive with Google Play"),
                                            IdDisplay.create("google", "Google"), 28, fileOp);
    }

    ImmutableList<FakePackage.FakeLocalPackage> getPackageInfoList() {
      return ImmutableList.of(pkgGapi, pkgGapi29, pkgGapi30, pkgPs, pkgWear, pkgWear29, pkgCnWear, pkgAutomotive, pkgAutomotivePs);
    }

    void generateSystemImageDescriptions(AndroidSdkHandler sdkHandler) {
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

      gapiImageDescription = new SystemImageDescription(gapiImage);
      gapi29ImageDescription = new SystemImageDescription(gapi29Image);
      gapi30ImageDescription = new SystemImageDescription(gapi30Image);
      psImageDescription = new SystemImageDescription(playStoreImage);
      wearImageDescription = new SystemImageDescription(wearImage);
      wear29ImageDescription = new SystemImageDescription(wear29Image);
      wearCnImageDescription = new SystemImageDescription(wearCnImage);
      automotiveImageDescription = new SystemImageDescription(automotiveImage);
      automotivePsImageDescription = new SystemImageDescription(automotivePsImage);
    }
  }

  private SystemImageTestList mSysImgsX86;
  private SystemImageTestList mSysImgsArm;
  private SystemImageTestList mSysImgsArmv7a;
  private SystemImageTestList mSysImgsArm64;

  private Device myBigPhone;
  private Device myFoldable;
  private Device myRollable;
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

    mSysImgsArm = new SystemImageTestList("armeabi", fileOp);
    mSysImgsArmv7a = new SystemImageTestList("armeabi-v7a", fileOp);
    mSysImgsArm64 = new SystemImageTestList("arm64-v8a", fileOp);
    mSysImgsX86 = new SystemImageTestList("x86", fileOp);

    Collection<LocalPackage> pkgs = new ArrayList<>();
    pkgs.addAll(mSysImgsArm.getPackageInfoList());
    pkgs.addAll(mSysImgsArmv7a.getPackageInfoList());
    pkgs.addAll(mSysImgsArm64.getPackageInfoList());
    pkgs.addAll(mSysImgsX86.getPackageInfoList());
    packages.setLocalPkgInfos(pkgs);

    RepoManager mgr = new FakeRepoManager(fileOp.toPath(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(fileOp.toPath(SDK_LOCATION), fileOp.toPath(AVD_LOCATION), fileOp, mgr);

    mSysImgsArm.generateSystemImageDescriptions(sdkHandler);
    mSysImgsArmv7a.generateSystemImageDescriptions(sdkHandler);
    mSysImgsArm64.generateSystemImageDescriptions(sdkHandler);
    mSysImgsX86.generateSystemImageDescriptions(sdkHandler);

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
    myFoldable = devMgr.getDevice("7.6in Foldable", "Generic");
    myRollable = devMgr.getDevice("7.4in Rollable", "Generic");
    mySmallTablet = devMgr.getDevice("Nexus 7", "Google");

    // Get an Automotive device
    myAutomotiveDevice = devMgr.getDevice("automotive_1024p_landscape", "Google");

    // Get a Freeform device
    myFreeform = devMgr.getDevice("13.5in Freeform", "Generic");
  }

  public void testClassificationFromParts() {
      List<Boolean> isArmHostParams = ImmutableList.of(false, true);
      for (boolean p : isArmHostParams) {
        boolean isArmHostOs = p;
        assertEquals(X86, getClassificationFromParts(Abi.X86, 21, GOOGLE_APIS_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 22, GOOGLE_APIS_TAG, isArmHostOs));
        assertEquals(X86, getClassificationFromParts(Abi.X86, 23, DEFAULT_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 24, GOOGLE_APIS_X86_TAG, isArmHostOs));
        assertEquals(X86, getClassificationFromParts(Abi.X86_64, 25, GOOGLE_APIS_X86_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, GOOGLE_APIS_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, GOOGLE_APIS_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationFromParts(Abi.ARM64_V8A, 25, GOOGLE_APIS_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 25, WEAR_TAG, isArmHostOs));
        assertEquals(X86, getClassificationFromParts(Abi.X86, 24, WEAR_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, 25, WEAR_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 25, ANDROID_TV_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, ANDROID_TV_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 25, GOOGLE_TV_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, 25, GOOGLE_TV_TAG, isArmHostOs));
        assertEquals(X86, getClassificationFromParts(Abi.X86, 25, DEFAULT_TAG, isArmHostOs));
        assertEquals(OTHER, getClassificationFromParts(Abi.ARM64_V8A, 25, DEFAULT_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 25, CHROMEOS_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 28, AUTOMOTIVE_TAG, isArmHostOs));
        assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationFromParts(Abi.X86, 28, AUTOMOTIVE_PLAY_STORE_TAG, isArmHostOs));
      }
  }

  public void testClassificationForDevice_x86() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean p : isArmHostParams) {
      boolean isArmHostOs = p;

      assertEquals((isArmHostOs ? X86 : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(X86, getClassificationForDevice(mSysImgsX86.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals((isArmHostOs ? X86 : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationForDevice(mSysImgsX86.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals((isArmHostOs ? X86 : RECOMMENDED), getClassificationForDevice(mSysImgsX86.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(X86, getClassificationForDevice(mSysImgsX86.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals((isArmHostOs ? X86 : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_arm64() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean p : isArmHostParams) {
      boolean isArmHostOs = p;
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationForDevice(mSysImgsArm64.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm64.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationForDevice(mSysImgsArm64.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationForDevice(mSysImgsArm64.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationForDevice(mSysImgsArm64.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm64.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER), getClassificationForDevice(mSysImgsArm64.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_arm() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean p : isArmHostParams) {
      boolean isArmHostOs = p;
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArm.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_armv7a() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean p : isArmHostParams) {
      boolean isArmHostOs = p;
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImgsArmv7a.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testPhoneVsTablet() {
    assertFalse(DeviceDefinitionList.isTablet(myBigPhone));
    assertFalse(DeviceDefinitionList.isTablet(myFoldable));
    assertFalse(DeviceDefinitionList.isTablet(myRollable));
    assertTrue(DeviceDefinitionList.isTablet(mySmallTablet));
    assertTrue(DeviceDefinitionList.isTablet(myFreeform));
  }

  public void testImageChosenForDevice() {
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wear29ImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapiImageDescription, myFoldable));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi30ImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wear29ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapiImageDescription, myRollable));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi30ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wear29ImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapiImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapi29ImageDescription, myFreeform));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi30ImageDescription, myFreeform));
  }

  public void testDeviceType() {
    assertEquals("Phone", DeviceDefinitionList.getCategory(myBigPhone));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myFoldable));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myRollable));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myGapiPhoneDevice));
    assertEquals("Phone", DeviceDefinitionList.getCategory(myPlayStorePhoneDevice));
    assertEquals("Tablet", DeviceDefinitionList.getCategory(mySmallTablet));
    assertEquals("Wear OS", DeviceDefinitionList.getCategory(myWearDevice));
    assertEquals("Automotive", DeviceDefinitionList.getCategory(myAutomotiveDevice));
    assertEquals("Tablet", DeviceDefinitionList.getCategory(myFreeform));
  }
}
