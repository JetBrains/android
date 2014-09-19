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

import com.android.ddmlib.IDevice;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.TestCase;
import org.easymock.EasyMock;

import java.io.File;

@SuppressWarnings("StaticMethodReferencedViaSubclass")
public class ApkUploaderServiceTest extends TestCase {
  private ApkUploaderService myService;
  private IDevice myDevice1;
  private IDevice myDevice2;
  private File myFile;
  private String myRemotePath;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myDevice1 = EasyMock.createMock(IDevice.class);
    EasyMock.expect(myDevice1.getSerialNumber()).andReturn("device-1").anyTimes();

    myDevice2 = EasyMock.createMock(IDevice.class);
    EasyMock.expect(myDevice2.getSerialNumber()).andReturn("device-2").anyTimes();

    myFile = FileUtil.createTempFile("test", ".apk");
    myRemotePath = "/remote/path";

    myDevice1.pushFile(myFile.getAbsolutePath(), myRemotePath);
    // Instead of testing how many times the sync service is called, we use the UploadResult value.
    EasyMock.expectLastCall().anyTimes();

    myDevice2.pushFile(myFile.getAbsolutePath(), myRemotePath);
    // Instead of testing how many times the sync service is called, we use the UploadResult value.
    EasyMock.expectLastCall().anyTimes();

    myService = new ApkUploaderService();

    EasyMock.replay(myDevice1, myDevice2);
  }

  public void testUploadApkTwice() throws Exception {
    assertTrue(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
    assertFalse(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
  }

  public void testUploadModifiedApkTwice() throws Exception {
    assertTrue(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
    FileUtil.writeToFile(myFile, "changed!");
    assertTrue(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
  }

  public void testUploadApkAfterDisconnect() throws Exception {
    assertTrue(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
    assertTrue(myService.uploadApk(myDevice2, myFile.getAbsolutePath(), myRemotePath));

    myService.deviceDisconnected(myDevice2);

    assertFalse(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
    assertTrue(myService.uploadApk(myDevice2, myFile.getAbsolutePath(), myRemotePath));

    myService.deviceDisconnected(myDevice1);

    assertTrue(myService.uploadApk(myDevice1, myFile.getAbsolutePath(), myRemotePath));
    assertFalse(myService.uploadApk(myDevice2, myFile.getAbsolutePath(), myRemotePath));
  }
}