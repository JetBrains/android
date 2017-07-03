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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class DeployInstantAppTaskTest {

  @Rule public TemporaryFolder myFolder = new TemporaryFolder();
  @Mock private LaunchStatus myLaunchStatus;
  @Mock private ConsolePrinter myPrinter;
  @Mock private IDevice myDevice;

  private File createDummyZipFile() throws Exception {
    File apk = myFolder.newFile("dummy.apk");
    File zip = myFolder.newFile("dummy.zip");
    ZipUtil.compressFile(apk, zip);
    return zip;
  }

  @Before
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(myLaunchStatus.isLaunchTerminated()).thenReturn(false);
    when(myDevice.isOnline()).thenReturn(true);
  }

  @Test
  public void testPerformFailsWhenBadFile() throws Throwable {
    ApkInfo dummyInfo = new ApkInfo(new File("badFile"), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo));
    assertFalse(task.perform(myDevice, myLaunchStatus, myPrinter));
  }

  @Test
  public void testPerformFailsMoreThanOneFile() throws Throwable {
    ApkInfo dummyInfo = new ApkInfo(new File("goodFile.zip"), "applicationId");
    ApkInfo dummyInfo2 = new ApkInfo(new File("goodFile2.zip"), "applicationId2");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo, dummyInfo2));
    assertFalse(task.perform(myDevice, myLaunchStatus, myPrinter));
  }

  @Test
  public void testPerformSucceeds() throws Throwable {
    ApkInfo dummyInfo = new ApkInfo(createDummyZipFile(), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo)) {
      @Override
      boolean install(@NotNull IDevice device,
                      @NotNull LaunchStatus launchStatus,
                      @NotNull ConsolePrinter printer,
                      @NotNull String appId,
                      @NotNull File zipFile) {
        return true;
      }
    };
    assertTrue(task.perform(myDevice, myLaunchStatus, myPrinter));
  }
}