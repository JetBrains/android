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
import com.android.repository.testframework.FakeProgressIndicator;
import com.android.repository.testframework.MockFileOp;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.testutils.MockLog;
import com.android.utils.NullLogger;

import junit.framework.TestCase;

import java.io.File;

public class AvdManagerConnectionTest extends TestCase {

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

    mAndroidSdkHandler =
      new AndroidSdkHandler(new File("/sdk"), ANDROID_HOME, mFileOp);

    mAvdManager =
      AvdManager.getInstance(
        mAndroidSdkHandler,
        new File(ANDROID_HOME, AndroidLocation.FOLDER_AVD),
        new NullLogger(),
        mFileOp);

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
      log);

    assertNotNull("Could not create AVD", avd);

    // Make a different userdata-qemu.img so we can see if 'wipe-data'
    // recreates it correctly.
    File userQemu = new File(mAvdFolder, AvdManager.USERDATA_QEMU_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after creation",
               mFileOp.exists(userQemu));
    mFileOp.delete(userQemu);
    assertFalse(AvdManager.USERDATA_QEMU_IMG + " exists in " + mAvdFolder + " after attempted deletion",
               mFileOp.exists(userQemu));
    mFileOp.createNewFile(userQemu);
    assertTrue("Could not create dummy " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder,
               mFileOp.exists(userQemu));

    // Do the "wipe-data"
    assertTrue("Could not wipe data from AVD", mAvdManagerConnection.wipeUserData(avd));

    assertTrue("Expected " + AvdManager.USERDATA_QEMU_IMG + " in " + mAvdFolder + " after wipe-data",
               mFileOp.exists(userQemu));

    File userData = new File(mAvdFolder, AvdManager.USERDATA_IMG);
    assertTrue("Expected " + AvdManager.USERDATA_IMG + " in " + mAvdFolder + " after wipe-data",
               mFileOp.exists(userData));

    // If the file sizes match, assume that we have a good copy.
    assertEquals("Size of " + AvdManager.USERDATA_QEMU_IMG + " is wrong after wipe-data",
                 mFileOp.length(userData), mFileOp.length(userQemu));
  }


  private static void recordGoogleApisSysImg23(MockFileOp fop) {
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/system.img");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/"
                           + AvdManager.USERDATA_IMG, "Some dummy info");
    fop.recordExistingFile("/sdk/system-images/android-23/google_apis/x86_64/package.xml",
                           "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                           + "<ns3:sdk-sys-img "
                           + "xmlns:ns2=\"http://schemas.android.com/sdk/android/repo/repository2/01\" "
                           + "xmlns:ns3=\"http://schemas.android.com/sdk/android/repo/sys-img2/01\" "
                           + "xmlns:ns4=\"http://schemas.android.com/repository/android/common/01\" "
                           + "xmlns:ns5=\"http://schemas.android.com/sdk/android/repo/addon2/01\">"
                           + "<license id=\"license-9A5C00D5\" type=\"text\">Terms and Conditions\n"
                           + "</license><localPackage "
                           + "path=\"system-images;android-23;google_apis;x86_64\" "
                           + "obsolete=\"false\"><type-details "
                           + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                           + "xsi:type=\"ns3:sysImgDetailsType\"><api-level>23</api-level>"
                           + "<tag><id>google_apis</id><display>Google APIs</display></tag>"
                           + "<vendor><id>google</id><display>Google Inc.</display></vendor>"
                           + "<abi>x86_64</abi></type-details><revision><major>9</major></revision>"
                           + "<display-name>Google APIs Intel x86 Atom_64 System Image</display-name>"
                           + "<uses-license ref=\"license-9A5C00D5\"/></localPackage>"
                           + "</ns3:sdk-sys-img>\n");
  }
}
