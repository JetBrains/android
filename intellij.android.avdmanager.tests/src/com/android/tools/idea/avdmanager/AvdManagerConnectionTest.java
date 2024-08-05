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
package com.android.tools.idea.avdmanager;

import static com.android.sdklib.internal.avd.ConfigKey.FOLD_AT_POSTURE;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_ANGLES_POSTURE_DEFINITIONS;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_AREAS;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_COUNT;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_DEFAULTS;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_RANGES;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_SUB_TYPE;
import static com.android.sdklib.internal.avd.ConfigKey.HINGE_TYPE;
import static com.android.sdklib.internal.avd.ConfigKey.POSTURE_LISTS;
import static com.android.sdklib.internal.avd.ConfigKey.RESIZABLE_CONFIG;
import static com.android.sdklib.internal.avd.ConfigKey.SKIN_NAME;
import static com.android.sdklib.internal.avd.ConfigKey.SKIN_PATH;
import static com.android.sdklib.internal.avd.UserSettingsKey.PREFERRED_ABI;
import static com.android.sdklib.internal.avd.HardwareProperties.HW_LCD_FOLDED_HEIGHT;
import static com.android.sdklib.internal.avd.HardwareProperties.HW_LCD_FOLDED_WIDTH;
import static com.android.sdklib.internal.avd.HardwareProperties.HW_LCD_FOLDED_X_OFFSET;
import static com.android.sdklib.internal.avd.HardwareProperties.HW_LCD_FOLDED_Y_OFFSET;
import static com.google.common.truth.Truth.assertThat;

import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage.FakeLocalPackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceManager;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.avd.OnDiskSkin;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes.SysImgDetailsType;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.android.testutils.NoErrorsOrWarningsLogger;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;

public class AvdManagerConnectionTest extends AndroidTestCase {
  private Path mSdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
  private Path mPrefsRoot = mSdkRoot.getRoot().resolve("android-home");

  private AvdManager mAvdManager;
  private AvdManagerConnection mAvdManagerConnection;
  private Path mAvdFolder;
  private SystemImage mSystemImage;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    InMemoryFileSystems.recordExistingFile(mSdkRoot.resolve("tools/lib/emulator/snapshots.img"));
    recordGoogleApisSysImg23(mSdkRoot);
    recordEmulatorVersion_23_4_5(mSdkRoot);

    AndroidSdkHandler androidSdkHandler = new AndroidSdkHandler(mSdkRoot, mPrefsRoot);

    mAvdManager =
        AvdManager.createInstance(
            androidSdkHandler,
            mPrefsRoot.resolve("avd"),
            DeviceManager.createInstance(androidSdkHandler, NullLogger.getLogger()),
            NullLogger.getLogger());

    mAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), false);

    mSystemImage = androidSdkHandler.getSystemImageManager(new FakeProgressIndicator()).getImages().iterator().next();

    mAvdManagerConnection = new AvdManagerConnection(androidSdkHandler, mAvdManager, MoreExecutors.newDirectExecutorService());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    AvdManagerConnection.resetConnectionFactory();
  }

  public void testResizableAvd() throws IOException {
    RepositoryPackages packages = new RepositoryPackages();

    // google api31 image
    String g31Path = "system-images;android-31;google_apis;x86_64";
    FakeLocalPackage g31Package = new FakeLocalPackage(g31Path, mSdkRoot.resolve("mySysImg"));
    SysImgDetailsType g31Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    g31Details.getAbis().add(Abi.X86_64.toString());
    g31Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    g31Package.setTypeDetails((TypeDetails)g31Details);
    InMemoryFileSystems.recordExistingFile(g31Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    Files.createDirectories(g31Package.getLocation().resolve("data"));

    packages.setLocalPkgInfos(ImmutableList.of(g31Package));
    FakeRepoManager mgr = new FakeRepoManager(mSdkRoot, packages);
    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mSdkRoot, mAvdFolder, mgr);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage g31Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(g31Path, progress)).getLocation());
    assert g31Image != null;
    SystemImageDescription g31ImageDescription = new SystemImageDescription(g31Image);

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device resizableDevice = devMgr.getDevice("resizable", "Generic");

    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(resizableDevice);

    AvdInfo avdInfo = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "testResizable",
      resizableDevice,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      null,
      false);

    Map<String, String> avdConfig = avdInfo.getProperties();
    assertThat(avdConfig.get(HW_LCD_FOLDED_WIDTH)).isEqualTo("1080");
    assertThat(avdConfig.get(HW_LCD_FOLDED_HEIGHT)).isEqualTo("2092");
    assertThat(avdConfig.get(HW_LCD_FOLDED_X_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HW_LCD_FOLDED_Y_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HINGE)).isEqualTo("yes");
    assertThat(avdConfig.get(HINGE_COUNT)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_SUB_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_RANGES)).isEqualTo("0-180");
    assertThat(avdConfig.get(HINGE_DEFAULTS)).isEqualTo("180");
    assertThat(avdConfig.get(HINGE_AREAS)).isEqualTo("1080-0-0-1840");
    assertThat(avdConfig.get(POSTURE_LISTS)).isEqualTo("1, 2, 3");
    assertThat(avdConfig.get(HINGE_ANGLES_POSTURE_DEFINITIONS)).isEqualTo("0-30, 30-150, 150-180");
    assertThat(avdConfig.get(HINGE_ANGLES_POSTURE_DEFINITIONS)).isEqualTo("0-30, 30-150, 150-180");
    assertThat(avdConfig.get(RESIZABLE_CONFIG)).
      isEqualTo("phone-0-1080-2400-420, foldable-1-2208-1840-420, tablet-2-1920-1200-240, desktop-3-1920-1080-160");
    assertThat(avdConfig.get(SKIN_NAME)).isEqualTo("1080x2400");
    assertThat(avdConfig.get(SKIN_PATH)).isEqualTo("1080x2400");
  }

  public void testFoldableAvds() throws IOException {
    RepositoryPackages packages = new RepositoryPackages();

    // google api31 image
    String g31Path = "system-images;android-31;google_apis;x86_64";
    FakeLocalPackage g31Package = new FakeLocalPackage(g31Path, mSdkRoot.resolve("mySysImg"));
    SysImgDetailsType g31Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    g31Details.getAbis().add(Abi.X86_64.toString());
    g31Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    g31Package.setTypeDetails((TypeDetails)g31Details);
    InMemoryFileSystems.recordExistingFile(g31Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    Files.createDirectories(g31Package.getLocation().resolve("data"));

    packages.setLocalPkgInfos(List.of(g31Package));
    FakeRepoManager mgr = new FakeRepoManager(mSdkRoot, packages);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(mSdkRoot, mAvdFolder, mgr);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage g31Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(g31Path, progress)).getLocation());
    assert g31Image != null;
    SystemImageDescription g31ImageDescription = new SystemImageDescription(g31Image);

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());

    // 7.6 foldable
    Device foldable = devMgr.getDevice("7.6in Foldable", "Generic");

    assert foldable != null;
    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(foldable);

    AvdInfo avdInfo = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "test7p6Foldable",
      foldable,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      null,
      false);

    Map<String, String> avdConfig = avdInfo.getProperties();
    assertThat(avdConfig.get(HW_LCD_FOLDED_WIDTH)).isEqualTo("884");
    assertThat(avdConfig.get(HW_LCD_FOLDED_HEIGHT)).isEqualTo("2208");
    assertThat(avdConfig.get(HW_LCD_FOLDED_X_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HW_LCD_FOLDED_Y_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HINGE)).isEqualTo("yes");
    assertThat(avdConfig.get(HINGE_COUNT)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_SUB_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_RANGES)).isEqualTo("0-180");
    assertThat(avdConfig.get(HINGE_DEFAULTS)).isEqualTo("180");
    assertThat(avdConfig.get(HINGE_AREAS)).isEqualTo("884-0-1-2208");
    assertThat(avdConfig.containsKey(FOLD_AT_POSTURE)).isFalse();
    assertThat(avdConfig.get(POSTURE_LISTS)).isEqualTo("1, 2, 3");
    assertThat(avdConfig.get(HINGE_ANGLES_POSTURE_DEFINITIONS)).isEqualTo("0-30, 30-150, 150-180");

    // 8in foldable
    foldable = devMgr.getDevice("8in Foldable", "Generic");
    assert foldable != null;
    hardwareProperties = DeviceManager.getHardwareProperties(foldable);
    avdInfo = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "test8Foldable",
      foldable,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      null,
      false);
    avdConfig = avdInfo.getProperties();
    assertThat(avdConfig.get(HW_LCD_FOLDED_WIDTH)).isEqualTo("1148");
    assertThat(avdConfig.get(HW_LCD_FOLDED_HEIGHT)).isEqualTo("2480");
    assertThat(avdConfig.get(HW_LCD_FOLDED_X_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HW_LCD_FOLDED_Y_OFFSET)).isEqualTo("0");
    assertThat(avdConfig.get(HINGE)).isEqualTo("yes");
    assertThat(avdConfig.get(HINGE_COUNT)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_SUB_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_RANGES)).isEqualTo("180-360");
    assertThat(avdConfig.get(HINGE_DEFAULTS)).isEqualTo("180");
    assertThat(avdConfig.get(HINGE_AREAS)).isEqualTo("1148-0-1-2480");
    assertThat(avdConfig.get(FOLD_AT_POSTURE)).isEqualTo("4");
    assertThat(avdConfig.get(POSTURE_LISTS)).isEqualTo("3, 4");
    assertThat(avdConfig.get(HINGE_ANGLES_POSTURE_DEFINITIONS)).isEqualTo("180-330, 330-360");

    // 6.7in Foldable
    foldable = devMgr.getDevice("6.7in Foldable", "Generic");
    assert foldable != null;
    hardwareProperties = DeviceManager.getHardwareProperties(foldable);
    avdInfo = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "test6p7Foldable",
      foldable,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      null,
      false);
    avdConfig = avdInfo.getProperties();

    assertThat(avdConfig.get(HINGE)).isEqualTo("yes");
    assertThat(avdConfig.get(HINGE_COUNT)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_TYPE)).isEqualTo("0");
    assertThat(avdConfig.get(HINGE_SUB_TYPE)).isEqualTo("1");
    assertThat(avdConfig.get(HINGE_RANGES)).isEqualTo("0-180");
    assertThat(avdConfig.get(HINGE_DEFAULTS)).isEqualTo("180");
    assertThat(avdConfig.get(HINGE_AREAS)).isEqualTo("0-1318-1080-1");
    assertThat(avdConfig.containsKey(FOLD_AT_POSTURE)).isFalse();
    assertThat(avdConfig.get(POSTURE_LISTS)).isEqualTo("1, 2, 3");
    assertThat(avdConfig.get(HINGE_ANGLES_POSTURE_DEFINITIONS)).isEqualTo("0-30, 30-150, 150-180");
  }

  public void testWipeAvd() {
    MockLog log = new MockLog();
    // Create an AVD
    AvdInfo avd = mAvdManager.createAvd(
      mAvdFolder,
      getName(),
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      false,
      false);

    assertNotNull("Could not create AVD", avd);

    // Make a userdata-qemu.img so we can see if 'wipe-data' deletes it
    Path userQemu = mAvdFolder.resolve(AvdManager.USERDATA_QEMU_IMG);
    InMemoryFileSystems.recordExistingFile(userQemu);
    assertTrue("Could not create " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder, Files.exists(userQemu));
    // Also make a 'snapshots' sub-directory with a file
    Path snapshotsDir = mAvdFolder.resolve(AvdManager.SNAPSHOTS_DIRECTORY);
    Path snapshotFile = snapshotsDir.resolve("aSnapShotFile.txt");
    InMemoryFileSystems.recordExistingFile(snapshotFile, "Some contents for the file");
    assertTrue("Could not create " + snapshotFile, Files.exists(snapshotFile));

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", mAvdManagerConnection.wipeUserData(avd));

    assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after wipe-data", Files.exists(userQemu));
    assertFalse("wipe-data did not remove the '" + AvdManager.SNAPSHOTS_DIRECTORY + "' directory", Files.exists(snapshotsDir));

    Path userData = mAvdFolder.resolve(AvdManager.USERDATA_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder + " after wipe-data", Files.exists(userData));
  }

  public void testDoesSystemImageSupportQemu2() {
    Path avdLocation = mSdkRoot.getRoot().resolve("avd");
    RepositoryPackages packages = new RepositoryPackages();

    // QEMU-1 image
    String q1Path = "system-images;android-q1;google_apis;x86";
    FakeLocalPackage q1Package = new FakeLocalPackage(q1Path, mSdkRoot.resolve("mySysImg1"));
    SysImgDetailsType q1Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q1Details.getAbis().add(Abi.X86.toString());
    q1Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q1Package.setTypeDetails((TypeDetails)q1Details);
    InMemoryFileSystems.recordExistingFile(q1Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));

    // QEMU-2 image
    String q2Path = "system-images;android-q2;google_apis;x86";
    FakeLocalPackage q2Package = new FakeLocalPackage(q2Path, mSdkRoot.resolve("mySysImg2"));
    SysImgDetailsType q2Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2Details.getAbis().add(Abi.X86.toString());
    q2Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q2Package.setTypeDetails((TypeDetails)q2Details);
    InMemoryFileSystems.recordExistingFile(q2Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    InMemoryFileSystems.recordExistingFile(q2Package.getLocation().resolve("kernel-ranchu"));

    // QEMU-2-64 image
    String q2_64Path = "system-images;android-q2-64;google_apis;x86";
    FakeLocalPackage q2_64Package = new FakeLocalPackage(q2_64Path, mSdkRoot.resolve("mySysImg3"));
    SysImgDetailsType q2_64Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2_64Details.getAbis().add(Abi.X86.toString());
    q2_64Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    q2_64Package.setTypeDetails((TypeDetails)q2_64Details);
    InMemoryFileSystems.recordExistingFile(q2_64Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    InMemoryFileSystems.recordExistingFile(q2_64Package.getLocation().resolve("kernel-ranchu-64"));

    packages.setLocalPkgInfos(ImmutableList.of(q1Package, q2Package, q2_64Package));
    FakeRepoManager mgr = new FakeRepoManager(mSdkRoot, packages);

    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(mSdkRoot, avdLocation, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage q1Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q1Path, progress)).getLocation());
    ISystemImage q2Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q2Path, progress)).getLocation());
    ISystemImage q2_64Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(q2_64Path, progress)).getLocation());

    assert q1Image != null;
    SystemImageDescription q1ImageDescription = new SystemImageDescription(q1Image);
    assert q2Image != null;
    SystemImageDescription q2ImageDescription = new SystemImageDescription(q2Image);
    assert q2_64Image != null;
    SystemImageDescription q2_64ImageDescription = new SystemImageDescription(q2_64Image);

    assertFalse("Should not support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q1ImageDescription));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2ImageDescription));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2_64ImageDescription));
  }

  // Note: This only tests a small part of startAvd(). We are not set up
  //       here to actually launch an Emulator instance.
  public void testStartAvdSkinned() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD with a skin
    String skinnyAvdName = "skinnyAvd";
    Path skinnyAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinnyAvdName, false);
    Path skinFolder = mPrefsRoot.resolve("skinFolder");
    Files.createDirectories(skinFolder);

    AvdInfo skinnyAvd = mAvdManager.createAvd(
      skinnyAvdFolder,
      skinnyAvdName,
      mSystemImage,
      new OnDiskSkin(skinFolder),
      null,
      null,
      null,
      null,
      false,
      true,
      false);

    try {
      assert skinnyAvd != null;
      mAvdManagerConnection.startAvd(null, skinnyAvd, RequestType.DIRECT_DEVICE_MANAGER).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      assertTrue(expected.getCause().getMessage().contains("No emulator installed"));
    }
  }

  // Note: This only tests a small part of startAvd(). We are not set up here to actually launch an Emulator instance.
  public void testStartAvdSkinless() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD without a skin
    String skinlessAvdName = "skinlessAvd";
    Path skinlessAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinlessAvdName, false);

    AvdInfo skinlessAvd = mAvdManager.createAvd(
      skinlessAvdFolder,
      skinlessAvdName,
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      true,
      false);

    try {
      assert skinlessAvd != null;
      mAvdManagerConnection.startAvd(null, skinlessAvd, RequestType.DIRECT_DEVICE_MANAGER).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      if (!expected.getCause().getMessage().contains("No emulator installed")) {
        throw new RuntimeException(expected.getCause());
      }
    }
  }

  public void testUserSettings() throws Exception {
    MockLog log = new MockLog();

    RepositoryPackages packages = new RepositoryPackages();

    // google api31 image
    String g31Path = "system-images;android-31;google_apis;x86_64";
    FakeLocalPackage g31Package = new FakeLocalPackage(g31Path, mSdkRoot.resolve("mySysImg"));
    SysImgDetailsType g31Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    g31Details.getTags().add(IdDisplay.create("google_apis", "Google APIs"));
    g31Details.getAbis().add(Abi.X86_64.toString());
    g31Package.setTypeDetails((TypeDetails)g31Details);
    InMemoryFileSystems.recordExistingFile(g31Package.getLocation().resolve(SystemImageManager.SYS_IMG_NAME));
    Files.createDirectories(g31Package.getLocation().resolve("data"));

    packages.setLocalPkgInfos(ImmutableList.of(g31Package));
    FakeRepoManager mgr = new FakeRepoManager(mSdkRoot, packages);
    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(mSdkRoot, mAvdFolder, mgr);
    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage g31Image =
      systemImageManager.getImageAt(Objects.requireNonNull(sdkHandler.getLocalPackage(g31Path, progress)).getLocation());
    assert g31Image != null;
    SystemImageDescription g31ImageDescription = new SystemImageDescription(g31Image);

    DeviceManager devMgr = DeviceManager.createInstance(sdkHandler, new NoErrorsOrWarningsLogger());
    Device device = devMgr.getDevice("medium_phone", "Generic");

    Map<String, String> hardwareProperties = DeviceManager.getHardwareProperties(device);

    AvdInfo info = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "testPhone",
      device,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      null,
      false);

    assertThat(info).isNotNull();
    assertThat(info.getUserSettings().get(PREFERRED_ABI)).isNull();

    Map<String, String> userSettings = new HashMap<>();
    userSettings.put(PREFERRED_ABI, Abi.X86_64.toString());
    info = mAvdManagerConnection.createOrUpdateAvd(
      null,
      "test7p6Foldable",
      device,
      g31ImageDescription,
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      hardwareProperties,
      userSettings,
      false);
    assertThat(info).isNotNull();
    assertThat(info.getUserSettings().get(PREFERRED_ABI)).isEqualTo(Abi.X86_64.toString());
  }

  private static void recordGoogleApisSysImg23(Path sdkRoot) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-23/google_apis/x86_64/system.img"));
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-23/google_apis/x86_64/" + AvdManager.USERDATA_IMG),
                                           "Some dummy info");
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("system-images/android-23/google_apis/x86_64/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns3:sdk-sys-img "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\">"
                           + "<localPackage path=\"system-images;android-23;google_apis;x86_64\">"
                           + "<type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                           + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                           + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                           + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                           + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "</localPackage></ns3:sdk-sys-img>\n");
  }

  private static void recordEmulatorVersion_23_4_5(Path sdkRoot) {
    // This creates two 'package' directories.
    // We do not create a valid Emulator executable, so tests expect
    // a failure when they try to launch the Emulator.
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("emulator/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\""
                           + "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">"
                           + "  <localPackage path=\"emulator\">"
                           + "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                           + "        xsi:type=\"ns3:genericDetailsType\"/>"
                           + "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>"
                           + "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "  </localPackage>"
                           + "</ns2:repository>");
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("tools/package.xml"),
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns2:repository xmlns:ns2=\"http://schemas.android.com/repository/android/common/01\""
                           + "                xmlns:ns3=\"http://schemas.android.com/repository/android/generic/01\">"
                           + "  <localPackage path=\"tools\">"
                           + "    <type-details xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                           + "        xsi:type=\"ns3:genericDetailsType\"/>"
                           + "    <revision><major>23</major><minor>4</minor><micro>5</micro></revision>"
                           + "    <display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "  </localPackage>"
                           + "</ns2:repository>");
  }

  private static void recordEmulatorHardwareProperties(Path sdkRoot) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("emulator/lib/hardware-properties.ini"),
                           "name        = sdcard.size\n"
                           + "type        = diskSize\n"
                           + "default     = 800M\n"
                           + "abstract    = SD Card Image Size\n"
                           + "# Data partition size.\n"
                           + "name        = disk.dataPartition.size\n"
                           + "type        = diskSize\n"
                           + "default     = 2G\n"
                           + "abstract    = Ideal size of data partition\n");
  }
}
