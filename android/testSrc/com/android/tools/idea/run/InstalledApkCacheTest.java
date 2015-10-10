/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("StaticMethodReferencedViaSubclass")
public class InstalledApkCacheTest extends TestCase {
  private InstalledApkCache myService;
  private IDevice myDevice1;
  private IDevice myDevice2;
  private File myFile;
  private String myPkgName;
  private String myDumpSysOutput;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myDevice1 = EasyMock.createMock(IDevice.class);
    EasyMock.expect(myDevice1.getSerialNumber()).andReturn("device-1").anyTimes();

    myDevice2 = EasyMock.createMock(IDevice.class);
    EasyMock.expect(myDevice2.getSerialNumber()).andReturn("device-2").anyTimes();

    myFile = FileUtil.createTempFile("test", ".apk");
    myPkgName = "com.foo.bar";

    myDevice1.pushFile(myFile.getAbsolutePath(), myPkgName);
    // Instead of testing how many times the sync service is called, we use the UploadResult value.
    EasyMock.expectLastCall().anyTimes();

    myDevice2.pushFile(myFile.getAbsolutePath(), myPkgName);
    // Instead of testing how many times the sync service is called, we use the UploadResult value.
    EasyMock.expectLastCall().anyTimes();

    myDumpSysOutput = "Package [com.foo.bar]";
    myService = new InstalledApkCache() {
      @Override
      protected String executeShellCommand(@NotNull IDevice device, @NotNull String cmd, long timeout, @NotNull TimeUnit timeUnit)
        throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, InterruptedException {
        return myDumpSysOutput;
      }
    };

    EasyMock.replay(myDevice1, myDevice2);
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myService);
    super.tearDown();
  }

  public void testCacheHit() throws Exception {
    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
    myService.setInstalled(myDevice1, myFile, myPkgName);
    assertTrue(myService.isInstalled(myDevice1, myFile, myPkgName));
  }

  public void testUploadModifiedApk() throws Exception {
    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
    myService.setInstalled(myDevice1, myFile, myPkgName);
    assertTrue(myService.isInstalled(myDevice1, myFile, myPkgName));

    FileUtil.writeToFile(myFile, "changed!");
    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
  }

  public void testUploadApkAfterDisconnect() throws Exception {
    myService.setInstalled(myDevice1, myFile, myPkgName);
    myService.setInstalled(myDevice2, myFile, myPkgName);

    myService.deviceDisconnected(myDevice2);
    assertTrue(myService.isInstalled(myDevice1, myFile, myPkgName));
    assertFalse(myService.isInstalled(myDevice2, myFile, myPkgName));

    myService.setInstalled(myDevice2, myFile, myPkgName);
    myService.deviceDisconnected(myDevice1);

    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
    assertTrue(myService.isInstalled(myDevice2, myFile, myPkgName));
  }

  public void testUninstallFromCommandLine() throws Exception {
    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
    myService.setInstalled(myDevice1, myFile, myPkgName);
    assertTrue(myService.isInstalled(myDevice1, myFile, myPkgName));

    myDumpSysOutput = "";
    assertFalse(myService.isInstalled(myDevice1, myFile, myPkgName));
  }

  public void testDumpsysParser() {
    myDumpSysOutput = "Packages:\n" +
                    "  Package [com.foo.bar] (423123d0):\n" +
                    "    userId=10096 gids=[3003, 1028, 1015]\n" +
                    "    lastUpdateTime=2014-09-29 11:58:19\n" +
                    "    signatures=PackageSignatures{420cf360 [42139088]}\n";
    assertEquals("lastUpdateTime=2014-09-29 11:58:19", myService.getLastUpdateTime(myDevice1, "com.foo.bar"));
    assertNull(myService.getLastUpdateTime(myDevice1, "com.foo"));
    assertNull(myService.getLastUpdateTime(myDevice1, "xyz"));
  }
}