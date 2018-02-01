/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.testing.IdeComponents;
import com.google.android.instantapps.sdk.api.RunHandler;
import com.google.android.instantapps.sdk.api.Sdk;
import com.google.android.instantapps.sdk.api.StatusCode;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.AndroidTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.io.File;
import java.net.URL;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class RunInstantAppTaskTest extends AndroidTestCase {
  private final String DEVICE_ID = "dev1234";
  private final File zipFile = new File("/tmp/fake.zip");
  private final ApkInfo apkInfo = new ApkInfo(zipFile, "com.foo");
  private final ImmutableList<ApkInfo> apkInfos = ImmutableList.of(apkInfo);

  private IdeComponents ideComponents = new IdeComponents(null);
  private InstantAppSdks instantAppSdks;
  @Mock private Sdk sdkLib;
  @Mock private RunHandler runHandler;
  @Mock private IDevice device;
  @Mock private LaunchStatus launchStatus;
  @Mock private ConsolePrinter consolePrinter;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    instantAppSdks = ideComponents.mockService(InstantAppSdks.class);
    when(instantAppSdks.loadLibrary()).thenReturn(sdkLib);
    when(sdkLib.getRunHandler()).thenReturn(runHandler);

    when(device.getSerialNumber()).thenReturn(DEVICE_ID);
    when(launchStatus.isLaunchTerminated()).thenReturn(false);
    when(instantAppSdks.shouldUseSdkLibraryToRun()).thenReturn(true);
  }

  @Override
  @After
  public void tearDown() throws Exception {
    try {
      ideComponents.restore();
    } finally {
      super.tearDown();
    }
  }

  @Test
  public void testPerformWithlaunchTerminated() {
    when(launchStatus.isLaunchTerminated()).thenReturn(true);

    RunInstantAppTask task = new RunInstantAppTask(apkInfos, "");
    assertThat(task.perform(device, launchStatus, consolePrinter)).isFalse();
    verifyNoMoreInteractions(runHandler);
  }

  @Test
  public void testPerformWithSdkLibraryDisabled() {
    when(instantAppSdks.shouldUseSdkLibraryToRun()).thenReturn(false);

    RunInstantAppTask task = new RunInstantAppTask(apkInfos, "");
    assertThat(task.perform(device, launchStatus, consolePrinter)).isFalse();
    verifyNoMoreInteractions(runHandler);
  }

  @Test
  public void testPerformWithNoZipFile() {
    RunInstantAppTask task = new RunInstantAppTask(ImmutableList.of(), "");
    assertThat(task.perform(device, launchStatus, consolePrinter)).isFalse();
    verifyNoMoreInteractions(runHandler);
  }

  @Test
  public void testPerformWithEmptyStringUrl() {
    RunInstantAppTask task = new RunInstantAppTask(apkInfos, "");
    // Note here that an empty string URL should be transformed to null in the call to runInstantApp
    when(runHandler.runInstantApp(isNull(), eq(zipFile), eq(DEVICE_ID),
                                  eq(RunHandler.SetupBehavior.SET_UP_IF_NEEDED), any(), any()))
      .thenReturn(StatusCode.SUCCESS);
    assertThat(task.perform(device, launchStatus, consolePrinter)).isTrue();
  }

  @Test
  public void testPerformWithSpecifiedUrl() throws Exception {
    RunInstantAppTask task = new RunInstantAppTask(apkInfos, "http://foo.app");
    when(runHandler
           .runInstantApp(eq(new URL("http://foo.app")), eq(zipFile), eq(DEVICE_ID), eq(RunHandler.SetupBehavior.SET_UP_IF_NEEDED), any(),
                          any()))
      .thenReturn(StatusCode.SUCCESS);
    assertThat(task.perform(device, launchStatus, consolePrinter)).isTrue();
  }
}