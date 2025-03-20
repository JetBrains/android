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
import com.android.sdklib.internal.avd.AvdManagerException;
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
import com.intellij.credentialStore.Credentials;
import com.intellij.util.net.ProxyConfiguration;
import com.intellij.util.net.ProxyCredentialStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import kotlinx.coroutines.Dispatchers;
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

    // We use Dispatchers.Unconfined to show dialogs: this causes MessageDialog to be invoked on the calling thread. We don't simulate
    // user input in these tests; the dialogs just immediately throw an exception.
    mAvdManagerConnection = new AvdManagerConnection(androidSdkHandler, mAvdManager, Dispatchers.getUnconfined());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    AvdManagerConnection.resetConnectionFactory();
  }

  public void testWipeAvd() throws AvdManagerException {
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
      mAvdManagerConnection.asyncStartAvd(null, skinnyAvd, RequestType.DIRECT_DEVICE_MANAGER).get(4, TimeUnit.SECONDS);
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
      mAvdManagerConnection.asyncStartAvd(null, skinlessAvd, RequestType.DIRECT_DEVICE_MANAGER).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      if (!expected.getCause().getMessage().contains("No emulator installed")) {
        throw new RuntimeException(expected.getCause());
      }
    }
  }

  public void testStudioParams() {
    ProxyConfiguration.StaticProxyConfiguration config =
      ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "proxy.com", 80, "");
    ProxyCredentialStore.getInstance().setCredentials("proxy.com", 80, new Credentials("myuser", "hunter2"), false);

    List<String> params = AvdManagerConnectionKt.toStudioParams(config, ProxyCredentialStore.getInstance());
    assertThat(params).containsExactly("http.proxyHost=proxy.com", "http.proxyPort=80", "proxy.authentication.username=myuser",
                                       "proxy.authentication.password=hunter2");
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
