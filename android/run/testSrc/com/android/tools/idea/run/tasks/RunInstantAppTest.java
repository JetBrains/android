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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.run.ApkFileUnit;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IdeComponents;
import com.google.android.instantapps.sdk.api.ExtendedSdk;
import com.google.android.instantapps.sdk.api.RunHandler;
import com.google.android.instantapps.sdk.api.StatusCode;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class RunInstantAppTest {
  private final String DEVICE_ID = "dev1234";
  private final File zipFile = new File("/tmp/fake.zip");
  private final ImmutableList<ApkInfo> apkInfoListForZip = ImmutableList.of(new ApkInfo(zipFile, "com.foo"));

  @Rule
  public AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();

  private InstantAppSdks instantAppSdks;
  @Mock private ExtendedSdk sdkLib;
  @Mock private RunHandler runHandler;
  private Project project;
  @Mock private Executor executor;
  @Mock private IDevice device;
  @Mock private ConsoleView consolePrinter;
  @Mock private ProcessHandler handler;
  @Mock private ProgressIndicator indicator;
  @Mock private ExecutionEnvironment env;

  @Before
  public void setUp() throws Exception {
    project = myProjectRule.getProject();
    MockitoAnnotations.initMocks(this);
    instantAppSdks = new IdeComponents(null, myProjectRule.getTestRootDisposable()).mockApplicationService(InstantAppSdks.class);
    when(instantAppSdks.loadLibrary()).thenReturn(sdkLib);
    when(sdkLib.getRunHandler()).thenReturn(runHandler);
    when(env.getProject()).thenReturn(project);
    when(env.getExecutor()).thenReturn(executor);
    when(device.getSerialNumber()).thenReturn(DEVICE_ID);
  }

  @Test
  public void testPerformWithNoZipFile() {
    RunInstantApp task = new RunInstantApp(ImmutableList.of(), "", Collections.singletonList(""));
    try {
      task.run(consolePrinter, device);
      TestCase.fail("Run should fail");
    }
    catch (ExecutionException e) {
      assertThat(e.getMessage()).isEqualTo("Uploading and launching Instant App: Package not found or not unique");
    }
    verifyNoMoreInteractions(runHandler);
  }

  @Test
  public void testPerformWithEmptyStringUrl() throws ExecutionException {
    RunInstantApp task = new RunInstantApp(apkInfoListForZip, "", Collections.singletonList(""));
    // Note here that an empty string URL should be transformed to null in the call to runInstantApp
    when(runHandler.runZip(
      /* zipFile= */ eq(zipFile),
      /* url= */ isNull(),
      /* adbHost= */ any(),
      /* adbDeviceId= */ eq(DEVICE_ID),
      /* runtimeApkDir= */ isNull(),
      /* resultStream= */ any(),
      /* progressIndicator= */ any()))
      .thenReturn(StatusCode.SUCCESS);
    task.run(consolePrinter, device);
  }

  @Test
  public void testPerformWithSpecifiedUrl() throws Exception {
    RunInstantApp task = new RunInstantApp(apkInfoListForZip, "http://foo.app", Collections.singletonList(""));
    when(runHandler.runZip(
      /* zipFile= */ eq(zipFile),
      /* url= */ eq(new URL("http://foo.app")),
      /* adbHost= */ any(),
      /* adbDeviceId= */ eq(DEVICE_ID),
      /* runtimeApkDir= */ isNull(),
      /* resultStream= */ any(),
      /* progressIndicator= */ any()))
      .thenReturn(StatusCode.SUCCESS);
    task.run(consolePrinter, device);
  }

  @Test
  public void testPerformWithListOfApks() throws Exception {
    File apk1 = new File("one.apk");
    File apk2 = new File("two.apk");
    File excludedApk = new File("excluded.apk");
    ImmutableList<ApkInfo> apkInfos = ImmutableList.of(
      new ApkInfo(ImmutableList.of(
        new ApkFileUnit("one", apk1),
        new ApkFileUnit("two", apk2),
        new ApkFileUnit("excluded", excludedApk)
      ), "com.dontcare"));

    RunInstantApp task = new RunInstantApp(apkInfos, "http://foo.app", ImmutableList.of("excluded"));

    when(runHandler.runApks(
      /* apkFiles= */ eq(ImmutableList.of(apk1, apk2)),
      /* url= */ eq(new URL("http://foo.app")),
      /* adbHost= */ any(),
      /* adbDeviceId= */ eq(DEVICE_ID),
      /* runtimeApkDir= */ isNull(),
      /* resultStream= */ any(),
      /* progressIndicator= */ any()))
      .thenReturn(StatusCode.SUCCESS);

    task.run(consolePrinter, device);

    verify(runHandler).runApks(
      /* apkFiles= */ eq(ImmutableList.of(apk1, apk2)),
      /* url= */ eq(new URL("http://foo.app")),
      /* adbHost= */ any(),
      /* adbDeviceId= */ eq(DEVICE_ID),
      /* runtimeApkDir= */ isNull(),
      /* resultStream= */ any(),
      /* progressIndicator= */ any());
  }
}
