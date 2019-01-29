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
import com.android.ddmlib.InstallException;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.stats.RunStatsService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class SplitApkDeployTaskTest {
  private static final String PACKAGE_NAME = "com.somepackage";
  @Mock private Project myProject;
  @Mock private SplitApkDeployTaskContext myContext;
  @Mock private IDevice myDevice;
  @Mock private IDevice myEmbeddedDevice;
  @Mock private LaunchStatus myLaunchStatus;
  @Mock private ConsolePrinter myPrinter;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    when(myEmbeddedDevice.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true);
    when(myContext.getApplicationId()).thenReturn(PACKAGE_NAME);
  }

  @Test
  public void testPerformOnEmbedded() throws Throwable {
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, myContext);
    answerInstallOptions(myEmbeddedDevice, installOptions -> assertThat(installOptions).containsExactly("-t", "-g"));
    assertTrue(task.perform(myEmbeddedDevice, myLaunchStatus, myPrinter));
  }

  @Test
  public void testPerformOnNonEmbeddedDevice() throws Throwable {
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, myContext);
    answerInstallOptions(myDevice, installOptions -> assertThat(installOptions).containsExactly("-t"));
    assertTrue(task.perform(myDevice, myLaunchStatus, myPrinter));
  }

  @Test
  public void testPerformPartial() throws Throwable {
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, myContext);
    when(myContext.isPatchBuild()).thenReturn(true);
    when(myContext.getArtifacts()).thenReturn(Arrays.asList(new File("foo.apk"), new File("bar.apk")));
    answerInstallOptions(myDevice,
                         apks -> assertThat(apks).containsExactly(new File("foo.apk"), new File("bar.apk")),
                         installOptions -> assertThat(installOptions).containsExactly("-t", "-p", PACKAGE_NAME));
    assertTrue(task.perform(myDevice, myLaunchStatus, myPrinter));
  }

  private static void answerInstallOptions(@NotNull IDevice device,
                                           @NotNull Consumer<List<String>> checkOptions) throws InstallException {
    answerInstallOptions(device, apks -> {
    }, checkOptions);
  }

  private static void answerInstallOptions(@NotNull IDevice device,
                                           @NotNull Consumer<List<File>> checkApks,
                                           @NotNull Consumer<List<String>> checkOptions) throws InstallException {
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        //noinspection unchecked
        List<File> apks = (List<File>)args[0];
        //noinspection unchecked
        List<String> installOptions = (List<String>)args[2];
        checkApks.accept(apks);
        checkOptions.accept(installOptions);
        return null;
      }
    }).when(device).installPackages(anyList(), anyBoolean(), anyList(), anyLong(), any(TimeUnit.class));
  }
}
