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
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.SystemImageClassification.PERFORMANT;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationForDevice;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.getClassificationFromParts;
import static com.android.tools.idea.avdmanager.ChooseSystemImagePanel.systemImageMatchesDevice;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.testutils.file.InMemoryFileSystems;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.ProductDetails;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;

public class ChooseSystemImagePanelTest extends AndroidTestCase {

  private final Path mSdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
  private final Path mAvdRoot = mSdkRoot.getRoot().resolve("avd");
  private static final String SDK_SEPARATOR = Character.toString(RepoPackage.PATH_SEPARATOR);

  private static FakePackage.FakeLocalPackage createSysimgPackage(String sysimgPath, String abi, IdDisplay tag, IdDisplay vendor,
                                                                  int apiLevel, Path sdkRoot) {
    FakePackage.FakeLocalPackage pkg = new FakePackage.FakeLocalPackage(
      sysimgPath, sdkRoot.resolve(sysimgPath.replaceAll(SDK_SEPARATOR, "/")));
    DetailsTypes.SysImgDetailsType sysimgDetails =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    sysimgDetails.getTags().add(tag);
    sysimgDetails.setAbi(abi);
    sysimgDetails.setVendor(vendor);
    sysimgDetails.setApiLevel(apiLevel);
    pkg.setTypeDetails((TypeDetails) sysimgDetails);
    InMemoryFileSystems.recordExistingFile(pkg.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

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
    // Google API 31 image
    String gapi31Path = "system-images;android-31;google_apis;";
    // Google API 32 image
    String gapi32Path = "system-images;android-32;google_apis;";
    // Google API 33 image
    String gapi33Path = "system-images;android-33;google_apis;";
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
    // TV 30 image
    String tv30Path = "system-images;android-30;android-tv;";
    // TV 31 image
    String tv31Path = "system-images;android-31;android-tv;";

    FakePackage.FakeLocalPackage pkgGapi;
    FakePackage.FakeLocalPackage pkgGapi29;
    FakePackage.FakeLocalPackage pkgGapi30;
    FakePackage.FakeLocalPackage pkgGapi31;
    FakePackage.FakeLocalPackage pkgGapi32;
    FakePackage.FakeLocalPackage pkgGapi33;
    FakePackage.FakeLocalPackage pkgPs;
    FakePackage.FakeLocalPackage pkgWear;
    FakePackage.FakeLocalPackage pkgWear29;
    FakePackage.FakeLocalPackage pkgCnWear;
    FakePackage.FakeLocalPackage pkgAutomotive;
    FakePackage.FakeLocalPackage pkgAutomotivePs;
    FakePackage.FakeLocalPackage pkgTv30;
    FakePackage.FakeLocalPackage pkgTv31;

    SystemImageDescription gapiImageDescription;
    SystemImageDescription gapi29ImageDescription;
    SystemImageDescription gapi30ImageDescription;
    SystemImageDescription gapi31ImageDescription;
    SystemImageDescription gapi32ImageDescription;
    SystemImageDescription gapi33ImageDescription;
    SystemImageDescription psImageDescription;
    SystemImageDescription wearImageDescription;
    SystemImageDescription wear29ImageDescription;
    SystemImageDescription wearCnImageDescription;
    SystemImageDescription automotiveImageDescription;
    SystemImageDescription automotivePsImageDescription;
    SystemImageDescription tv30ImageDescription;
    SystemImageDescription tv31ImageDescription;

    SystemImageTestList(String abi, Path sdkRoot) {
      gapiPath += abi;
      gapi29Path += abi;
      gapi30Path += abi;
      gapi31Path += abi;
      gapi32Path += abi;
      gapi33Path += abi;
      psPath += abi;
      wearPath += abi;
      wear29Path += abi;
      wearCnPath += abi;
      automotivePath += abi;
      automotivePsPath += abi;
      tv30Path += abi;
      tv31Path += abi;

      pkgGapi = createSysimgPackage(gapiPath, abi, IdDisplay.create("google_apis", "Google APIs"),
                                    IdDisplay.create("google", "Google"), 23, sdkRoot);
      pkgGapi29 = createSysimgPackage(gapi29Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 29, sdkRoot);
      pkgGapi30 = createSysimgPackage(gapi30Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 30, sdkRoot);
      pkgGapi31 = createSysimgPackage(gapi31Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 31, sdkRoot);
      pkgGapi32 = createSysimgPackage(gapi32Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 32, sdkRoot);
      pkgGapi33 = createSysimgPackage(gapi33Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 33, sdkRoot);
      pkgPs = createSysimgPackage(psPath, abi, IdDisplay.create("google_apis_playstore", "Google Play"),
                                  IdDisplay.create("google", "Google"), 24, sdkRoot);
      pkgWear = createSysimgPackage(wearPath, abi, IdDisplay.create("android-wear", "Wear OS"),
                                    IdDisplay.create("google", "Google"), 25, sdkRoot);
      pkgWear29 = createSysimgPackage(wear29Path, abi, IdDisplay.create("android-wear", "Wear OS"),
                                      IdDisplay.create("google", "Google"), 29, sdkRoot);
      pkgCnWear = createSysimgPackage(wearCnPath, abi, IdDisplay.create("android-wear", "Wear OS for China"),
                                      IdDisplay.create("google", "Google"), 25, sdkRoot);
      pkgAutomotive = createSysimgPackage(automotivePath, abi, IdDisplay.create("android-automotive", "Android Automotive"),
                                          IdDisplay.create("google", "Google"), 28, sdkRoot);
      pkgAutomotivePs = createSysimgPackage(automotivePsPath, abi, IdDisplay.create("android-automotive-playstore",
                                                                                    "Android Automotive with Google Play"),
                                            IdDisplay.create("google", "Google"), 28, sdkRoot);
      pkgAutomotive = createSysimgPackage(automotivePath, abi, IdDisplay.create("android-automotive", "Android Automotive"),
                                          IdDisplay.create("google", "Google"), 28, sdkRoot);
      pkgTv30 = createSysimgPackage(tv30Path, abi, IdDisplay.create("android-tv", "Android TV"),
                                    IdDisplay.create("google", "Google"), 30, sdkRoot);
      pkgTv31 = createSysimgPackage(tv31Path, abi, IdDisplay.create("android-tv", "Android TV"),
                                    IdDisplay.create("google", "Google"), 31, sdkRoot);
    }

    ImmutableList<FakePackage.FakeLocalPackage> getPackageInfoList() {
      return ImmutableList.of(pkgGapi, pkgGapi29, pkgGapi30, pkgGapi31, pkgGapi32, pkgGapi33, pkgPs, pkgWear, pkgWear29, pkgCnWear, pkgAutomotive,
                              pkgAutomotivePs, pkgTv30, pkgTv31);
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
      ISystemImage gapi31Image = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(gapi31Path, progress).getLocation());
      ISystemImage gapi32Image = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(gapi32Path, progress).getLocation());
      ISystemImage gapi33Image = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(gapi33Path, progress).getLocation());
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
      ISystemImage tv30Image = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(tv30Path, progress).getLocation());
      ISystemImage tv31Image = systemImageManager.getImageAt(
        sdkHandler.getLocalPackage(tv31Path, progress).getLocation());

      gapiImageDescription = new SystemImageDescription(gapiImage);
      gapi29ImageDescription = new SystemImageDescription(gapi29Image);
      gapi30ImageDescription = new SystemImageDescription(gapi30Image);
      gapi31ImageDescription = new SystemImageDescription(gapi31Image);
      gapi32ImageDescription = new SystemImageDescription(gapi32Image);
      gapi33ImageDescription = new SystemImageDescription(gapi33Image);
      psImageDescription = new SystemImageDescription(playStoreImage);
      wearImageDescription = new SystemImageDescription(wearImage);
      wear29ImageDescription = new SystemImageDescription(wear29Image);
      wearCnImageDescription = new SystemImageDescription(wearCnImage);
      automotiveImageDescription = new SystemImageDescription(automotiveImage);
      automotivePsImageDescription = new SystemImageDescription(automotivePsImage);
      tv30ImageDescription = new SystemImageDescription(tv30Image);
      tv31ImageDescription = new SystemImageDescription(tv31Image);
    }
  }

  private SystemImageTestList mSysImgsX86_64;
  private SystemImageTestList mSysImgsX86;
  private SystemImageTestList mSysImgsArm;
  private SystemImageTestList mSysImgsArmv7a;
  private SystemImageTestList mSysImgsArm64;

  private Device myBigPhone;
  private Device myFoldable;
  private Device myRollable;
  private Device myResizable;
  private Device myGapiPhoneDevice;
  private Device myPlayStorePhoneDevice;
  private Device mySmallTablet;
  private Device myWearDevice;
  private Device myAutomotiveDevice;
  private Device myFreeform;
  private Device my4KTV;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RepositoryPackages packages = new RepositoryPackages();

    mSysImgsArm = new SystemImageTestList("armeabi", mSdkRoot);
    mSysImgsArmv7a = new SystemImageTestList("armeabi-v7a", mSdkRoot);
    mSysImgsArm64 = new SystemImageTestList("arm64-v8a", mSdkRoot);
    mSysImgsX86 = new SystemImageTestList("x86", mSdkRoot);
    mSysImgsX86_64 = new SystemImageTestList("x86_64", mSdkRoot);

    Collection<LocalPackage> pkgs = new ArrayList<>();
    pkgs.addAll(mSysImgsArm.getPackageInfoList());
    pkgs.addAll(mSysImgsArmv7a.getPackageInfoList());
    pkgs.addAll(mSysImgsArm64.getPackageInfoList());
    pkgs.addAll(mSysImgsX86.getPackageInfoList());
    pkgs.addAll(mSysImgsX86_64.getPackageInfoList());
    packages.setLocalPkgInfos(pkgs);

    RepoManager mgr = new FakeRepoManager(mSdkRoot, packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mSdkRoot, mAvdRoot, mgr);

    mSysImgsArm.generateSystemImageDescriptions(sdkHandler);
    mSysImgsArmv7a.generateSystemImageDescriptions(sdkHandler);
    mSysImgsArm64.generateSystemImageDescriptions(sdkHandler);
    mSysImgsX86.generateSystemImageDescriptions(sdkHandler);
    mSysImgsX86_64.generateSystemImageDescriptions(sdkHandler);

    // Make a phone device that does not support Google Play
    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device.Builder devBuilder = new Device.Builder(devMgr.getDevice("Nexus 5", "Google"));
    devBuilder.setPlayStore(false);
    myGapiPhoneDevice = devBuilder.build();

    // Get a phone device that supports Google Play
    myPlayStorePhoneDevice = devMgr.getDevice("Nexus 5", "Google");

    // Get a Wear device
    myWearDevice = devMgr.getDevice("wearos_square", "Google");

    // Get a big phone, a bigger foldable, and a small tablet
    myBigPhone = devMgr.getDevice("pixel_3_xl", "Google");
    myFoldable = devMgr.getDevice("7.6in Foldable", "Generic");
    myRollable = devMgr.getDevice("7.4in Rollable", "Generic");
    mySmallTablet = devMgr.getDevice("Nexus 7", "Google");
    myResizable = devMgr.getDevice("resizable", "Generic");

    // Get an Automotive device
    myAutomotiveDevice = devMgr.getDevice("automotive_1024p_landscape", "Google");

    // Get a Freeform device
    myFreeform = devMgr.getDevice("13.5in Freeform", "Generic");

    // Get a 4K TV
    my4KTV = devMgr.getDevice("tv_4k", "Google");
  }

  public void testClassificationFromParts() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(21), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(22), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(23), DEFAULT_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(24), GOOGLE_APIS_X86_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86_64, new AndroidVersion(25), GOOGLE_APIS_X86_TAG, isArmHostOs));
      assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, new AndroidVersion(25), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, new AndroidVersion(25), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(25), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(25), WEAR_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(24), WEAR_TAG, isArmHostOs));
      assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI, new AndroidVersion(25), WEAR_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(25), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, new AndroidVersion(25), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(25), GOOGLE_TV_TAG, isArmHostOs));
      assertEquals(OTHER, getClassificationFromParts(Abi.ARMEABI_V7A, new AndroidVersion(25), GOOGLE_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(25), DEFAULT_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(25), DEFAULT_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(25), CHROMEOS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(28), AUTOMOTIVE_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(28), AUTOMOTIVE_PLAY_STORE_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(28, null, 5, true), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(28, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(28, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(OTHER,
                   getClassificationFromParts(Abi.ARMEABI_V7A, new AndroidVersion(28, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(31), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86_64, new AndroidVersion(31), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(31, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
    }
  }

  public void testWarningTextOnX86HostsWithNonX86Images() {
    // Should not get any warning if x86 image on any host os.
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_64));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));

    // Should get a warning if non-x86 image on x86 host.
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm64.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86).isEmpty());
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm64.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86_64).isEmpty());
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86).isEmpty());
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86_64).isEmpty());
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArmv7a.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86).isEmpty());
    assertFalse(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArmv7a.gapiImageDescription,
                                                                    ProductDetails.CpuArchitecture.X86_64).isEmpty());

    // Shouldn't get warning if non-x86 image on non-x86 host.
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArmv7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArmv7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImgsArmv7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
  }

  public void testClassificationForDevice_x86() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationForDevice(mSysImgsX86.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImgsX86.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImgsX86.gapi31ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImgsX86_64.gapi30ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationForDevice(mSysImgsX86_64.gapi31ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImgsX86_64.gapi31ImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImgsX86.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImgsX86.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
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
    for (boolean isArmHostOs : isArmHostParams) {
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
    for (boolean isArmHostOs : isArmHostParams) {
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
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myResizable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapi32ImageDescription, myResizable));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi33ImageDescription, myResizable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wear29ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapiImageDescription, myRollable));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi30ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wearImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.wear29ImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapiImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.gapi29ImageDescription, myFreeform));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.gapi30ImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.tv30ImageDescription, my4KTV));
    assertTrue(systemImageMatchesDevice(mSysImgsX86.tv31ImageDescription, my4KTV));
    assertFalse(systemImageMatchesDevice(mSysImgsX86.automotivePsImageDescription, myPlayStorePhoneDevice));
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
