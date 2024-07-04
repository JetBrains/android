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

package com.android.tools.idea.avdmanager.ui;

import static com.android.sdklib.SystemImageTags.ANDROID_TV_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_PLAY_STORE_TAG;
import static com.android.sdklib.SystemImageTags.AUTOMOTIVE_TAG;
import static com.android.sdklib.SystemImageTags.CHROMEOS_TAG;
import static com.android.sdklib.SystemImageTags.DEFAULT_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_APIS_X86_TAG;
import static com.android.sdklib.SystemImageTags.GOOGLE_TV_TAG;
import static com.android.sdklib.SystemImageTags.TABLET_TAG;
import static com.android.sdklib.SystemImageTags.WEAR_TAG;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.SystemImageClassification.OTHER;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.SystemImageClassification.PERFORMANT;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.SystemImageClassification.RECOMMENDED;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.getClassificationForDevice;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.getClassificationFromParts;
import static com.android.tools.idea.avdmanager.ui.ChooseSystemImagePanel.systemImageMatchesDevice;

import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.avdmanager.HaxmAlert;
import com.android.tools.idea.avdmanager.SystemImageDescription;
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
    sysimgDetails.getAbis().add(abi);
    sysimgDetails.setVendor(vendor);
    sysimgDetails.setApiLevel(apiLevel);
    pkg.setTypeDetails((TypeDetails)sysimgDetails);
    InMemoryFileSystems.recordExistingFile(pkg.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    return pkg;
  }

  private static FakePackage.FakeRemotePackage createRemoteSysimgPackage(String sysimgPath, String abi, IdDisplay tag, IdDisplay vendor,
                                                                         int apiLevel) {
    FakePackage.FakeRemotePackage pkg = new FakePackage.FakeRemotePackage(
      sysimgPath);
    DetailsTypes.SysImgDetailsType sysimgDetails =
      AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    sysimgDetails.getTags().add(tag);
    sysimgDetails.getAbis().add(abi);
    sysimgDetails.setVendor(vendor);
    sysimgDetails.setApiLevel(apiLevel);
    pkg.setTypeDetails((TypeDetails)sysimgDetails);

    return pkg;
  }

  /**
   * Generates a list of system images with a given abi to test with.
   */
  private static final class SystemImageTestList {
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
    // Google API 34 image
    String gapi34Path = "system-images;android-34;google_apis;";
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

    final FakePackage.FakeLocalPackage pkgGapi;
    final FakePackage.FakeLocalPackage pkgGapi29;
    final FakePackage.FakeLocalPackage pkgGapi30;
    final FakePackage.FakeLocalPackage pkgGapi31;
    final FakePackage.FakeLocalPackage pkgGapi32;
    final FakePackage.FakeLocalPackage pkgGapi33;
    final FakePackage.FakeLocalPackage pkgGapi34;
    final FakePackage.FakeLocalPackage pkgPs;
    final FakePackage.FakeLocalPackage pkgWear;
    final FakePackage.FakeLocalPackage pkgWear29;
    final FakePackage.FakeLocalPackage pkgCnWear;
    FakePackage.FakeLocalPackage pkgAutomotive;
    final FakePackage.FakeLocalPackage pkgAutomotivePs;
    final FakePackage.FakeRemotePackage remotePkgAutomotive;
    final FakePackage.FakeLocalPackage pkgTv30;
    final FakePackage.FakeLocalPackage pkgTv31;

    SystemImageDescription gapiImageDescription;
    SystemImageDescription gapi29ImageDescription;
    SystemImageDescription gapi30ImageDescription;
    SystemImageDescription gapi31ImageDescription;
    SystemImageDescription gapi32ImageDescription;
    SystemImageDescription gapi33ImageDescription;
    SystemImageDescription gapi34ImageDescription;
    SystemImageDescription psImageDescription;
    SystemImageDescription wearImageDescription;
    SystemImageDescription wear29ImageDescription;
    SystemImageDescription wearCnImageDescription;
    SystemImageDescription automotiveImageDescription;
    SystemImageDescription automotivePsImageDescription;
    SystemImageDescription tv30ImageDescription;
    SystemImageDescription tv31ImageDescription;
    SystemImageDescription remoteAutoDescription;

    SystemImageTestList(String abi, Path sdkRoot) {
      gapiPath += abi;
      gapi29Path += abi;
      gapi30Path += abi;
      gapi31Path += abi;
      gapi32Path += abi;
      gapi33Path += abi;
      gapi34Path += abi;
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
      pkgGapi34 = createSysimgPackage(gapi34Path, abi, IdDisplay.create("google_apis", "Google APIs"),
                                      IdDisplay.create("google", "Google"), 34, sdkRoot);
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
      remotePkgAutomotive = createRemoteSysimgPackage(automotivePsPath, abi, IdDisplay.create("android-automotive-playstore",
                                                                                              "Android Automotive with Google Play"),
                                                      IdDisplay.create("google", "Google"), 28);
      pkgAutomotive = createSysimgPackage(automotivePath, abi, IdDisplay.create("android-automotive", "Android Automotive"),
                                          IdDisplay.create("google", "Google"), 28, sdkRoot);
      pkgTv30 = createSysimgPackage(tv30Path, abi, IdDisplay.create("android-tv", "Television"),
                                    IdDisplay.create("google", "Google"), 30, sdkRoot);
      pkgTv31 = createSysimgPackage(tv31Path, abi, IdDisplay.create("android-tv", "Television"),
                                    IdDisplay.create("google", "Google"), 31, sdkRoot);
    }

    ImmutableList<FakePackage.FakeLocalPackage> getPackageInfoList() {
      return ImmutableList.of(pkgGapi, pkgGapi29, pkgGapi30, pkgGapi31, pkgGapi32, pkgGapi33, pkgGapi34, pkgPs, pkgWear, pkgWear29, pkgCnWear,
                              pkgAutomotive, pkgAutomotivePs, pkgTv30, pkgTv31);
    }

    void generateSystemImageDescriptions(AndroidSdkHandler sdkHandler) {
      FakeProgressIndicator progress = new FakeProgressIndicator();
      SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

      var gapiImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapiPath, progress);
      var gapi29Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi29Path, progress);
      var gapi30Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi30Path, progress);
      var gapi31Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi31Path, progress);
      var gapi32Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi32Path, progress);
      var gapi33Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi33Path, progress);
      var gapi34Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, gapi34Path, progress);
      var playStoreImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, psPath, progress);
      var wearImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, wearPath, progress);
      var wear29Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, wear29Path, progress);
      var wearCnImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, wearCnPath, progress);
      var automotiveImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, automotivePath, progress);
      var automotivePsImage = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, automotivePsPath, progress);
      var tv30Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, tv30Path, progress);
      var tv31Image = SystemImageManagers.getImageAt(systemImageManager, sdkHandler, tv31Path, progress);

      gapiImageDescription = new SystemImageDescription(gapiImage);
      gapi29ImageDescription = new SystemImageDescription(gapi29Image);
      gapi30ImageDescription = new SystemImageDescription(gapi30Image);
      gapi31ImageDescription = new SystemImageDescription(gapi31Image);
      gapi32ImageDescription = new SystemImageDescription(gapi32Image);
      gapi33ImageDescription = new SystemImageDescription(gapi33Image);
      gapi34ImageDescription = new SystemImageDescription(gapi34Image);
      psImageDescription = new SystemImageDescription(playStoreImage);
      wearImageDescription = new SystemImageDescription(wearImage);
      wear29ImageDescription = new SystemImageDescription(wear29Image);
      wearCnImageDescription = new SystemImageDescription(wearCnImage);
      automotiveImageDescription = new SystemImageDescription(automotiveImage);
      automotivePsImageDescription = new SystemImageDescription(automotivePsImage);
      tv30ImageDescription = new SystemImageDescription(tv30Image);
      tv31ImageDescription = new SystemImageDescription(tv31Image);
      remoteAutoDescription = new SystemImageDescription(remotePkgAutomotive);
    }
  }

  private SystemImageTestList mSysImagesX86_64;
  private SystemImageTestList mSysImagesX86;
  private SystemImageTestList mSysImagesArm;
  private SystemImageTestList mSysImagesArmeabiV7a;
  private SystemImageTestList mSysImagesArm64;

  private Device myFoldable;
  private Device myRollable;
  private Device myResizable;
  private Device myGapiPhoneDevice;
  private Device myPlayStorePhoneDevice;
  private Device myWearDevice;
  private Device myAutomotiveDevice;
  private Device myFreeform;
  private Device my4KTV;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    RepositoryPackages packages = new RepositoryPackages();

    mSysImagesArm = new SystemImageTestList("armeabi", mSdkRoot);
    mSysImagesArmeabiV7a = new SystemImageTestList("armeabi-v7a", mSdkRoot);
    mSysImagesArm64 = new SystemImageTestList("arm64-v8a", mSdkRoot);
    mSysImagesX86 = new SystemImageTestList("x86", mSdkRoot);
    mSysImagesX86_64 = new SystemImageTestList("x86_64", mSdkRoot);

    Collection<LocalPackage> localPackages = new ArrayList<>();
    localPackages.addAll(mSysImagesArm.getPackageInfoList());
    localPackages.addAll(mSysImagesArmeabiV7a.getPackageInfoList());
    localPackages.addAll(mSysImagesArm64.getPackageInfoList());
    localPackages.addAll(mSysImagesX86.getPackageInfoList());
    localPackages.addAll(mSysImagesX86_64.getPackageInfoList());
    packages.setLocalPkgInfos(localPackages);

    RepoManager mgr = new FakeRepoManager(mSdkRoot, packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mSdkRoot, mAvdRoot, mgr);

    mSysImagesArm.generateSystemImageDescriptions(sdkHandler);
    mSysImagesArmeabiV7a.generateSystemImageDescriptions(sdkHandler);
    mSysImagesArm64.generateSystemImageDescriptions(sdkHandler);
    mSysImagesX86.generateSystemImageDescriptions(sdkHandler);
    mSysImagesX86_64.generateSystemImageDescriptions(sdkHandler);

    // Make a phone device that does not support Google Play
    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());

    var device = devMgr.getDevice("Nexus 5", "Google");
    assert device != null;

    var devBuilder = new Device.Builder(device);
    devBuilder.setPlayStore(false);
    myGapiPhoneDevice = devBuilder.build();

    // Get a phone device that supports Google Play
    myPlayStorePhoneDevice = device;

    // Get a Wear device
    myWearDevice = devMgr.getDevice("wearos_square", "Google");

    myFoldable = devMgr.getDevice("7.6in Foldable", "Generic");
    myRollable = devMgr.getDevice("7.4in Rollable", "Generic");
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
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86_64, new AndroidVersion(31), ImmutableList.of(TABLET_TAG, GOOGLE_APIS_TAG), isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31), ImmutableList.of(TABLET_TAG, GOOGLE_APIS_TAG), isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(31, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31, null, 5, false), GOOGLE_APIS_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(31), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(31), GOOGLE_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(33), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationFromParts(Abi.X86, new AndroidVersion(33), GOOGLE_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(31), GOOGLE_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(33), ANDROID_TV_TAG, isArmHostOs));
      assertEquals(isArmHostOs ? PERFORMANT : OTHER,
                   getClassificationFromParts(Abi.ARM64_V8A, new AndroidVersion(33), GOOGLE_TV_TAG, isArmHostOs));
    }
  }

  public void testWarningTextOnX86HostsWithNonX86Images() {
    // Should not get any warning if x86 image on any host os.
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_64));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesX86.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));

    // Should get a warning if non-x86 image on x86 host.
    var text =
      HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm64.gapiImageDescription, ProductDetails.CpuArchitecture.X86);
    assert text != null;

    assertFalse(text.isEmpty());

    text = HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm64.gapiImageDescription, ProductDetails.CpuArchitecture.X86_64);
    assert text != null;

    assertFalse(text.isEmpty());

    text = HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm.gapiImageDescription, ProductDetails.CpuArchitecture.X86);
    assert text != null;

    assertFalse(text.isEmpty());

    text = HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm.gapiImageDescription, ProductDetails.CpuArchitecture.X86_64);
    assert text != null;

    assertFalse(text.isEmpty());

    text =
      HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArmeabiV7a.gapiImageDescription, ProductDetails.CpuArchitecture.X86);
    assert text != null;

    assertFalse(text.isEmpty());

    text =
      HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArmeabiV7a.gapiImageDescription, ProductDetails.CpuArchitecture.X86_64);
    assert text != null;

    assertFalse(text.isEmpty());

    // Shouldn't get warning if non-x86 image on non-x86 host.
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm64.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArm.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArmeabiV7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.X86_ON_ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArmeabiV7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.ARM));
    assertNull(HaxmAlert.getWarningTextForX86HostsUsingNonX86Image(mSysImagesArmeabiV7a.gapiImageDescription,
                                                                   ProductDetails.CpuArchitecture.UNKNOWN_CPU_ARCHITECTURE));
  }

  public void testClassificationForDevice_x86() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationForDevice(mSysImagesX86.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImagesX86.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImagesX86.gapi31ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImagesX86_64.gapi30ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationForDevice(mSysImagesX86_64.gapi31ImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImagesX86_64.gapi31ImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImagesX86.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImagesX86.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImagesX86.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(isArmHostOs ? OTHER : PERFORMANT,
                   getClassificationForDevice(mSysImagesX86.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals((isArmHostOs ? OTHER : RECOMMENDED),
                   getClassificationForDevice(mSysImagesX86.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(isArmHostOs ? OTHER : RECOMMENDED,
                   getClassificationForDevice(mSysImagesX86.remoteAutoDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_arm64() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER),
                   getClassificationForDevice(mSysImagesArm64.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm64.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER),
                   getClassificationForDevice(mSysImagesArm64.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals((isArmHostOs ? RECOMMENDED : OTHER),
                   getClassificationForDevice(mSysImagesArm64.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER),
                   getClassificationForDevice(mSysImagesArm64.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm64.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals((isArmHostOs ? RECOMMENDED : OTHER),
                   getClassificationForDevice(mSysImagesArm64.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(isArmHostOs ? RECOMMENDED : OTHER,
                   getClassificationForDevice(mSysImagesArm64.remoteAutoDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_arm() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArm.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testClassificationForDevice_armeabiV7a() {
    List<Boolean> isArmHostParams = ImmutableList.of(false, true);
    for (boolean isArmHostOs : isArmHostParams) {
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.gapiImageDescription, myGapiPhoneDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.gapiImageDescription, myPlayStorePhoneDevice, isArmHostOs));
      // Note: Play Store image is not allowed with a non-Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.psImageDescription, myPlayStorePhoneDevice, isArmHostOs));

      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.wearImageDescription, myWearDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.wearCnImageDescription, myWearDevice, isArmHostOs));

      // Note: myAutomotiveDevice is Play-Store device
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.automotiveImageDescription, myAutomotiveDevice, isArmHostOs));
      assertEquals(OTHER, getClassificationForDevice(mSysImagesArmeabiV7a.automotivePsImageDescription, myAutomotiveDevice, isArmHostOs));
    }
  }

  public void testImageChosenForDevice() {
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wearImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wear29ImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapiImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapi30ImageDescription, myFoldable));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.gapi34ImageDescription, myFoldable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wearImageDescription, myResizable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapi32ImageDescription, myResizable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapi33ImageDescription, myResizable));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.gapi34ImageDescription, myResizable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wearImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wear29ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapiImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapi30ImageDescription, myRollable));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.gapi34ImageDescription, myRollable));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wearImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.wear29ImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapiImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapi29ImageDescription, myFreeform));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.gapi30ImageDescription, myFreeform));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.tv30ImageDescription, my4KTV));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.tv31ImageDescription, my4KTV));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.wearImageDescription, myWearDevice));
    assertTrue(systemImageMatchesDevice(mSysImagesX86.wear29ImageDescription, myWearDevice));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.gapiImageDescription, myWearDevice));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.automotivePsImageDescription, myWearDevice));
    assertFalse(systemImageMatchesDevice(mSysImagesX86.automotivePsImageDescription, myPlayStorePhoneDevice));
  }
}
