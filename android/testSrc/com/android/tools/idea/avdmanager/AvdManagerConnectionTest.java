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

import com.android.prefs.AndroidLocation;
import com.android.repository.Revision;
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;

import com.google.common.base.Charsets;
import com.intellij.execution.configurations.GeneralCommandLine;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.io.OutputStreamWriter;
import java.util.Map;

import static com.android.sdklib.internal.avd.AvdManager.AVD_INI_FORCE_COLD_BOOT_MODE;

public class AvdManagerConnectionTest extends AndroidTestCase {

  private static final File ANDROID_HOME = new File("/android-home");

  private AndroidSdkHandler mAndroidSdkHandler;
  private AvdManager mAvdManager;
  private AvdManagerConnection mAvdManagerConnection;
  private File mAvdFolder;
  private SystemImage mSystemImage;
  private MockFileOp mFileOp = new MockFileOp();


  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mFileOp.recordExistingFile("/sdk/tools/lib/emulator/snapshots.img");
    recordGoogleApisSysImg23(mFileOp);
    recordEmulatorVersion_23_4_5(mFileOp);

    mAndroidSdkHandler =
      new AndroidSdkHandler(new File("/sdk"), ANDROID_HOME, mFileOp);

    mAvdManager =
      AvdManager.getInstance(
        mAndroidSdkHandler,
        new File(ANDROID_HOME, AndroidLocation.FOLDER_AVD),
        new NullLogger());

    mAvdFolder =
      AvdInfo.getDefaultAvdFolder(mAvdManager, getName(), mFileOp, false);

    mSystemImage = mAndroidSdkHandler.getSystemImageManager(
      new FakeProgressIndicator()).getImages().iterator().next();

    mAvdManagerConnection = new AvdManagerConnection(mAndroidSdkHandler);
  }

  public void testWipeAvd() throws Exception {

    MockLog log = new MockLog();
    // Create an AVD
    AvdInfo avd = mAvdManager.createAvd(
      mAvdFolder,
      this.getName(),
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      false,
      false,
      false,
      log);

    assertNotNull("Could not create AVD", avd);

    // Make a userdata-qemu.img so we can see if 'wipe-data' deletes it
    File userQemu = new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG);
    mFileOp.createNewFile(userQemu);
    assertTrue("Could not create " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
               mFileOp.exists(userQemu));
    // Also make a 'snapshots' sub-directory with a file
    File snapshotsDir = new File(mAvdFolder, AvdManager.SNAPSHOTS_DIRECTORY);
    String snapshotFile = snapshotsDir.getAbsolutePath() + "/aSnapShotFile.txt";
    mFileOp.recordExistingFile(snapshotFile, "Some contents for the file");
    assertTrue("Could not create " + snapshotFile,
               mFileOp.exists(new File (snapshotFile)));

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", mAvdManagerConnection.wipeUserData(avd));

    assertFalse("Expected NO " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after wipe-data",
                mFileOp.exists(userQemu));
    assertFalse("wipe-data did not remove the '" + AvdManager.SNAPSHOTS_DIRECTORY + "' directory",
                mFileOp.exists(snapshotsDir));

    File userData = new File(mAvdFolder, AvdManager.USERDATA_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder + " after wipe-data",
               mFileOp.exists(userData));
  }

  public void testEmulatorVersionIsAtLeast() throws Exception {
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
    final String fastBootName = "fastBootAvd";
    final File fastBootFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, fastBootName, mFileOp, false);
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
      false,
      true,
      false,
      log);

    // Create another AVD
    final String coldBootName = "coldBootAvd";
    final File coldBootFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, coldBootName, mFileOp, false);
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
      false,
      true,
      false,
      log);

    // Modify the second AVD's config.ini file so the AVD does a cold boot
    File coldConfigIniFile = new File(coldBootFolder, "config.ini");
    final FileOpFileWrapper configIniWrapper = new FileOpFileWrapper(coldConfigIniFile,
                                                                     mFileOp, false);
    Map<String, String> iniProperties = ProjectProperties.parsePropertyFile(configIniWrapper, log);
    iniProperties.put(AVD_INI_FORCE_COLD_BOOT_MODE, "yes");

    try (OutputStreamWriter iniWriter = new OutputStreamWriter(mFileOp.newFileOutputStream(coldConfigIniFile), Charsets.UTF_8)) {
      for (Map.Entry<String, String> mapEntry : iniProperties.entrySet()) {
        iniWriter.write(String.format("%1$s=%2$s\n", mapEntry.getKey(), mapEntry.getValue()));
      }
    }
    coldBootAvd = mAvdManager.reloadAvd(coldBootAvd, log);

    // Create a third AVD
    final String coldBootOnceName = "coldBootOnceAvd";
    final File coldBootOnceFolder = AvdInfo.getDefaultAvdFolder(mAvdManager, coldBootOnceName, mFileOp, false);
    AvdInfo coldBootOnceAvd = mAvdManager.createAvd(
      coldBootOnceFolder,
      coldBootOnceName,
      mSystemImage,
      null,
      null,
      null,
      null,
      null,
      false,
      false,
      true,
      false,
      log);

    // Modify the third AVD's config.ini file so the AVD does a single cold boot
    File coldOnceConfigIniFile = new File(coldBootOnceFolder, "config.ini");
    final FileOpFileWrapper coldOnceConfigIniWrapper = new FileOpFileWrapper(coldOnceConfigIniFile,
                                                                     mFileOp, false);
    Map<String, String> coldOnceIniProperties = ProjectProperties.parsePropertyFile(coldOnceConfigIniWrapper, log);
    coldOnceIniProperties.put(AVD_INI_FORCE_COLD_BOOT_MODE, "once");

    try (OutputStreamWriter iniWriter = new OutputStreamWriter(mFileOp.newFileOutputStream(coldOnceConfigIniFile), Charsets.UTF_8)) {
      for (Map.Entry<String, String> mapEntry : coldOnceIniProperties.entrySet()) {
        iniWriter.write(String.format("%1$s=%2$s\n", mapEntry.getKey(), mapEntry.getValue()));
      }
    }
    coldBootOnceAvd = mAvdManager.reloadAvd(coldBootOnceAvd, log);

    // Test all three AVDs using an Emulator that does not support fast boot
    final String COLD_BOOT_COMMAND = "-no-snapstorage";
    final String COLD_BOOT_ONCE_COMMAND = "-no-snapshot-load";
    GeneralCommandLine cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(fastBootAvd, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(coldBootAvd, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(coldBootOnceAvd, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    // Mark the Emulator as supporting fast boot
    recordEmulatorSupportsFastBoot(mFileOp);

    // Re-test all AVDs using an Emulator that DOES support fast boot
    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(fastBootAvd, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(coldBootAvd, cmdLine);
    assertTrue(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));

    cmdLine = new GeneralCommandLine();
    mAvdManagerConnection.addParameters(coldBootOnceAvd, cmdLine);
    assertFalse(cmdLine.getCommandLineString().contains(COLD_BOOT_COMMAND));
    assertTrue(cmdLine.getCommandLineString().contains(COLD_BOOT_ONCE_COMMAND));
  }

  public void testGetHardwareProperties() {
    recordEmulatorHardwareProperties(mFileOp);
    assertEquals("800M", mAvdManagerConnection.getSdCardSizeFromHardwareProperties());
    assertEquals("2G", mAvdManagerConnection.getInternalStorageSizeFromHardwareProperties());
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
