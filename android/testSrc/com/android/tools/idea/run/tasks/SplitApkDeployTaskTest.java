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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.fd.BuildSelection;
import com.android.tools.idea.fd.InstantRunContext;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.ir.client.InstantRunBuildInfo;
import com.intellij.openapi.project.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.fd.BuildCause.APP_NOT_INSTALLED;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

public class SplitApkDeployTaskTest {
  private static final String PACKAGE_NAME = "com.somepackage";
  @Mock private Project myProject;
  @Mock private InstantRunContext myContext;
  @Mock private InstantRunBuildInfo myBuildInfo;
  @Mock private IDevice myDevice;
  @Mock private IDevice myEmbeddedDevice;
  @Mock private LaunchStatus myLaunchStatus;
  @Mock ConsolePrinter myPrinter;

  @Before
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    when(myEmbeddedDevice.supportsFeature(IDevice.HardwareFeature.EMBEDDED)).thenReturn(true);
    when(myContext.getInstantRunBuildInfo()).thenReturn(myBuildInfo);
    when(myContext.getApplicationId()).thenReturn(PACKAGE_NAME);
    when(myContext.getBuildSelection()).thenReturn(new BuildSelection(APP_NOT_INSTALLED, false));
  }

  @Test
  public void testPerformOnEmbedded() throws Throwable {
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, myContext);
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        //noinspection unchecked
        List<String> installOptions = (List<String>)args[2];
        assertThat(installOptions).containsExactly("-t", "-g");
        return null;
      }
    }).when(myEmbeddedDevice).installPackages(anyList(), anyBoolean(), anyList(), anyLong(), any(TimeUnit.class));
    try {
      assertTrue(task.perform(myEmbeddedDevice, myLaunchStatus, myPrinter));
    }
    catch (Exception e) {
      // Expected because we did not mock InstantRunStatsService.
    }
  }

  @Test
  public void testPerformOnNonEmbeddedDevice() throws Throwable {
    SplitApkDeployTask task = new SplitApkDeployTask(myProject, myContext);
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        //noinspection unchecked
        List<String> installOptions = (List<String>)args[2];
        assertThat(installOptions).containsExactly("-t");
        return null;
      }
    }).when(myDevice).installPackages(anyList(), anyBoolean(), anyList(), anyLong(), any(TimeUnit.class));
    try {
      assertTrue(task.perform(myDevice, myLaunchStatus, myPrinter));
    }
    catch (Exception e) {
      // Expected because we did not mock InstantRunStatsService.
    }
  }
}
