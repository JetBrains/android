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

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_FORCE_COLD_BOOT_MODE;

import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.FakeRepoManager;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.sdklib.repository.targets.SystemImageManager;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.AndroidTestCase;

public class AvdManagerConnectionTest extends AndroidTestCase {
  private static final File ANDROID_PREFS_ROOT = new File("/android-home");

  private AvdManager mAvdManager;
  private AvdManagerConnection mAvdManagerConnection;
  private File mAvdFolder;
  private SystemImage mSystemImage;
  private final MockFileOp mFileOp = new MockFileOp();

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
    recordGoogleApisSysImg23(mFileOp);
    recordEmulatorVersion_23_4_5(mFileOp);

    AndroidSdkHandler androidSdkHandler = new AndroidSdkHandler(new File("/sdk"), ANDROID_PREFS_ROOT, mFileOp);

    mAvdManager = AvdManager.getInstance(androidSdkHandler, new File(ANDROID_PREFS_ROOT, AndroidLocation.FOLDER_AVD), new NullLogger());

    mAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), mFileOp, false);

    mSystemImage = androidSdkHandler.getSystemImageManager(new FakeProgressIndicator()).getImages().iterator().next();

    mAvdManagerConnection = new AvdManagerConnection(androidSdkHandler, MoreExecutors.newDirectExecutorService());
  }

  public void testWipeAvd() throws Exception {
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
      false,
      log);

    assertNotNull("Could not create AVD", avd);

    // Make a userdata-qemu.img so we can see if 'wipe-data' deletes it
    File userQemu = new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG);
    mFileOp.createNewFile(userQemu);
    assertTrue("Could not create " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder, mFileOp.exists(userQemu));
    // Also make a 'snapshots' sub-directory with a file
    File snapshotsDir = new File(mAvdFolder, AvdManager.SNAPSHOTS_DIRECTORY);
    String snapshotFile = snapshotsDir.getAbsolutePath() + "/aSnapShotFile.txt";
    mFileOp.recordExistingFile(snapshotFile, "Some contents for the file");
    assertTrue("Could not create " + snapshotFile, mFileOp.exists(new File (snapshotFile)));

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", mAvdManagerConnection.wipeUserData(avd));

    assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after wipe-data", mFileOp.exists(userQemu));
    assertFalse("wipe-data did not remove the '" + AvdManager.SNAPSHOTS_DIRECTORY + "' directory", mFileOp.exists(snapshotsDir));

    File userData = new File(mAvdFolder, AvdManager.USERDATA_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder + " after wipe-data", mFileOp.exists(userData));
  }

  public void testEmulatorVersionIsAtLeast() {
    // The emulator was created with version 23.4.5
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(22, 9, 9)));
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 1, 9)));
    assertTrue(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 4, 5)));

    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 4, 6)));
    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(23, 5, 1)));
    assertFalse(mAvdManagerConnection.emulatorVersionIsAtLeast(new Revision(24, 1, 1)));
  }

  public void testAddParameters() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD. Let it default to Fast Boot.
    String fastBootName = "fastBootAvd";
    File fastBootFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, fastBootName, mFileOp, false);
    AvdInfo fastBootAvd = mAvdManager.createAvd(
      fastBootFolder,
      fastBootName,
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      true,
      false,
      log);

    // Create another AVD
    String coldBootName = "coldBootAvd";
    File coldBootFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, coldBootName, mFileOp, false);
    AvdInfo coldBootAvd = mAvdManager.createAvd(
      coldBootFolder,
      coldBootName,
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      true,
      false,
      log);

    // Modify the second AVD's config.ini file so the AVD does a cold boot
    File coldConfigIniFile = new File(coldBootFolder, "config.ini");
    FileOpFileWrapper configIniWrapper = new FileOpFileWrapper(coldConfigIniFile,
                                                                     mFileOp, false);
    Map<String, String> iniProperties = ProjectProperties.parsePropertyFile(configIniWrapper, log);
    iniProperties.put(AVD_INI_FORCE_COLD_BOOT_MODE, "yes");

    try (OutputStreamWriter iniWriter = new OutputStreamWriter(mFileOp.newFileOutputStream(coldConfigIniFile), Charsets.UTF_8)) {
      for (Map.Entry<String, String> mapEntry : iniProperties.entrySet()) {
        iniWriter.write(String.format("%1$s=%2$s\n", mapEntry.getKey(), mapEntry.getValue()));
      }
    }
    coldBootAvd = mAvdManager.reloadAvd(coldBootAvd, log);

    // Test all three AVDs using an Emulator that does not support fast boot
    String COLD_BOOT_COMMAND = "-no-snapstorage";
    String COLD_BOOT_ONCE_COMMAND = "-no-snapshot-load";
    GeneralCommandLine cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), fastBootAvd, false, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), coldBootAvd, false , cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), fastBootAvd, true, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    // Mark the Emulator as supporting fast boot
    recordEmulatorSupportsFastBoot(mFileOp);

    // Re-test all AVDs using an Emulator that DOES support fast boot
    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), fastBootAvd, false, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), coldBootAvd, false, cmdLine);
    assertTrue(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(getProject(), fastBootAvd, true, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertTrue(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));
  }

  public void testGetHardwareProperties() {
    recordEmulatorHardwareProperties(mFileOp);
    assertEquals("800M", mAvdManagerConnection.getSdCardSizeFromHardwareProperties());
    assertEquals("2G", mAvdManagerConnection.getInternalStorageSizeFromHardwareProperties());
  }

  public void testDoesSystemImageSupportQemu2() {
    String AVD_LOCATION = "/avd";
    String SDK_LOCATION = "/sdk";
    MockFileOp fileOp = new MockFileOp();
    RepositoryPackages packages = new RepositoryPackages();

    // QEMU-1 image
    String q1Path = "system-images;android-q1;google_apis;x86";
    FakePackage.FakeLocalPackage q1Package = new FakePackage.FakeLocalPackage(q1Path);
    DetailsTypes.SysImgDetailsType q1Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q1Details.setTag(IdDisplay.create("google_apis", "Google APIs"));
    q1Package.setTypeDetails((TypeDetails)q1Details);
    fileOp.recordExistingFile(new File(q1Package.getLocation(), SystemImageManager.SYS_IMG_NAME));

    // QEMU-2 image
    String q2Path = "system-images;android-q2;google_apis;x86";
    FakePackage.FakeLocalPackage q2Package = new FakePackage.FakeLocalPackage(q2Path);
    DetailsTypes.SysImgDetailsType q2Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2Details.setTag(IdDisplay.create("google_apis", "Google APIs"));
    q2Package.setTypeDetails((TypeDetails)q2Details);
    fileOp.recordExistingFile(new File(q2Package.getLocation(), SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    mFileOp.recordExistingFile(q2Package.getLocation().getPath() + "/kernel-ranchu");

    // QEMU-2-64 image
    String q2_64Path = "system-images;android-q2-64;google_apis;x86";
    FakePackage.FakeLocalPackage q2_64Package = new FakePackage.FakeLocalPackage(q2_64Path);
    DetailsTypes.SysImgDetailsType q2_64Details = AndroidSdkHandler.getSysImgModule().createLatestFactory().createSysImgDetailsType();
    q2_64Details.setTag(IdDisplay.create("google_apis", "Google APIs"));
    q2_64Package.setTypeDetails((TypeDetails)q2_64Details);
    fileOp.recordExistingFile(new File(q2_64Package.getLocation(), SystemImageManager.SYS_IMG_NAME));
    // Add a file that indicates QEMU-2 support
    mFileOp.recordExistingFile(q2_64Package.getLocation().getPath() + "/kernel-ranchu-64");

    packages.setLocalPkgInfos(ImmutableList.of(q1Package, q2Package, q2_64Package));
    FakeRepoManager mgr = new FakeRepoManager(new File(SDK_LOCATION), packages);

    AndroidSdkHandler sdkHandler =
      new AndroidSdkHandler(new File(SDK_LOCATION), new File(AVD_LOCATION), fileOp, mgr);

    FakeProgressIndicator progress = new FakeProgressIndicator();
    SystemImageManager systemImageManager = sdkHandler.getSystemImageManager(progress);

    ISystemImage q1Image = systemImageManager.getImageAt(sdkHandler.getLocalPackage(q1Path, progress).getLocation());
    ISystemImage q2Image = systemImageManager.getImageAt(sdkHandler.getLocalPackage(q2Path, progress).getLocation());
    ISystemImage q2_64Image = systemImageManager.getImageAt(sdkHandler.getLocalPackage(q2_64Path, progress).getLocation());

    SystemImageDescription q1ImageDescr = new SystemImageDescription(q1Image);
    SystemImageDescription q2ImageDescr = new SystemImageDescription(q2Image);
    SystemImageDescription q2_64ImageDescr = new SystemImageDescription(q2_64Image);

    assertFalse("Should not support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q1ImageDescr, mFileOp));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2ImageDescr, mFileOp));
    assertTrue("Should support QEMU2", AvdManagerConnection.doesSystemImageSupportQemu2(q2_64ImageDescr, mFileOp));
  }

  // Note: This only tests a small part of startAvd(). We are not set up
  //       here to actually launch an Emulator instance.
  public void testStartAvdSkinned() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD with a skin
    String skinnyAvdName = "skinnyAvd";
    File skinnyAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinnyAvdName, mFileOp, false);
    File skinFolder = new File(ANDROID_PREFS_ROOT, "skinFolder");
    mFileOp.mkdirs(skinFolder);

    AvdInfo skinnyAvd = mAvdManager.createAvd(
      skinnyAvdFolder,
      skinnyAvdName,
      mSystemImage,
      skinFolder,
      "skinName",
      null,
      null,
      null,
      false,
      true,
      false,
      log);

    try {
      mAvdManagerConnection.startAvd(null, skinnyAvd).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      assertTrue(expected.getCause().getMessage().contains("No emulator installed"));
    }
  }

  public void testFindEmulator() {
    // Create files that looks like Emulator binaries
    String binaryName = SystemInfo.isWindows ? "emulator.exe" : "emulator";
    mFileOp.recordExistingFile("/sdk/emulator/" + binaryName);
    mFileOp.recordExistingFile("/sdk/tools/" + binaryName);

    File emulatorFile = mAvdManagerConnection.getEmulatorBinary();
    assertNotNull("Could not find Emulator", emulatorFile);
    File emulatorDirectory = emulatorFile.getParentFile();
    assertTrue("Found invalid Emulator", mFileOp.isDirectory(emulatorDirectory));
    String emulatorDirectoryPath = mFileOp.getAgnosticAbsPath(emulatorDirectory);
    assertEquals("Found wrong emulator", "/sdk/emulator", emulatorDirectoryPath);

    // Remove the emulator package
    File emulatorPackage = new File("/sdk/emulator/package.xml");
    mFileOp.delete(emulatorPackage);

    // Create a new AvdManagerConnection that doesn't remember the
    // previous list of packages
    AndroidSdkHandler androidSdkHandler = new AndroidSdkHandler(new File("/sdk"), ANDROID_PREFS_ROOT, mFileOp);
    AvdManagerConnection managerConnection = new AvdManagerConnection(androidSdkHandler, MoreExecutors.newDirectExecutorService());

    File bogusEmulatorFile = managerConnection.getEmulatorBinary();
    if (bogusEmulatorFile != null) {
      // An emulator binary was found. It should not be anything that
      // we created (especially not anything in /sdk/tools/).
      String bogusEmulatorPath = bogusEmulatorFile.getAbsolutePath();
      assertFalse("Should not have found Emulator", bogusEmulatorPath.startsWith("/sdk"));
    }
  }

  // Note: This only tests a small part of startAvd(). We are not set up here to actually launch an Emulator instance.
  public void testStartAvdSkinless() throws Exception {
    MockLog log = new MockLog();

    // Create an AVD without a skin
    String skinlessAvdName = "skinlessAvd";
    File skinlessAvdFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, skinlessAvdName, mFileOp, false);

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
      false,
      log);

    try {
      mAvdManagerConnection.startAvd(null, skinlessAvd).get(4, TimeUnit.SECONDS);
      fail();
    }
    catch (ExecutionException expected) {
      assertTrue(expected.getCause().getMessage().contains("No emulator installed"));
    }
  }

  public void testNewEmulatorCommandHasWorkingDirectory() {
    MockLog log = new MockLog();
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
      false,
      log);
    assertNotNull("Could not create AVD", avd);
    File emulator = new File("sdk/emulator/emulator.exe");

    GeneralCommandLine command = mAvdManagerConnection.newEmulatorCommand(null, emulator, avd, false, Collections.emptyList());

    assertEquals(emulator.getPath(), command.getExePath());
    assertEquals("Emulator command should have a working directory. See IDEA-231313.", emulator.getParentFile(), command.getWorkDirectory());
  }

  private static void recordGoogleApisSysImg23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/system.img");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/"
                           + AvdManager.USERDATA_IMG, "Some dummy info");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/package.xml",
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

  private static void recordEmulatorVersion_23_4_5(MockFileOp fop) {
    // This creates two 'package' directories.
    // We do not create a valid Emulator executable, so tests expect
    // a failure when they try to launch the Emulator.
    fop.recordExistingFile("/sdk/emulator/package.xml",
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
    fop.recordExistingFile("/sdk/tools/package.xml",
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

  private static void recordEmulatorSupportsFastBoot(MockFileOp fop) {
    fop.recordExistingFile("/sdk/emulator/lib/advancedFeatures.ini",
                           "FastSnapshotV1=on\n");
  }

  private static void recordEmulatorHardwareProperties(MockFileOp fop) {
    fop.recordExistingFile("/sdk/emulator/lib/hardware-properties.ini",
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
