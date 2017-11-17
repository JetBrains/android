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
package com.android.tools.idea.run;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.editor.ProfilerState;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.fd.InstantRunManager.MIN_IR_API_VERSION;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.API_TOO_LOW_FOR_INSTANT_RUN;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.CANNOT_BUILD_FOR_MULTIPLE_DEVICES;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.DISABLE_INSTANT_RUN_WHEN_PROFILING;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AndroidRunConfigurationBaseCanInstantRunTest extends AndroidTestCase {
  public static final String ID = "fakeId";

  @Mock Module module;
  @Mock AndroidSessionInfo info;
  @Mock AndroidFacet facet;
  @Mock Executor executor;
  private AndroidRunConfigurationBase myRunConfig;
  private ProfilerState myProfilerState;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myRunConfig = new AndroidTestRunConfiguration(getProject(), mock(ConfigurationFactory.class), false);
    myProfilerState = new ProfilerState();
  }

  // canInstantRunTests

  public void testMultiDevice() {
    List<AndroidDevice> devices = new ArrayList(2);
    devices.add(mock(AndroidDevice.class));
    devices.add(mock(AndroidDevice.class));

    assertEquals(CANNOT_BUILD_FOR_MULTIPLE_DEVICES, myRunConfig.canInstantRun(module, devices, executor, myProfilerState));
  }

  public void testApiTooLow() {
    List<AndroidDevice> devices = new ArrayList(1);
    AndroidDevice device = mock(AndroidDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(MIN_IR_API_VERSION - 1));
    devices.add(device);
    assertEquals(API_TOO_LOW_FOR_INSTANT_RUN, myRunConfig.canInstantRun(module, devices, executor, myProfilerState));
  }

  public void testDisabledForProfilingExecutor() {
    when(executor.getId()).thenReturn(ProfilerState.PROFILER_EXECUTOR_ID);
    List<AndroidDevice> devices = new ArrayList(1);
    AndroidDevice device = mock(AndroidDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(25));
    devices.add(device);
    assertEquals(DISABLE_INSTANT_RUN_WHEN_PROFILING, myRunConfig.canInstantRun(module, devices, executor, myProfilerState));
  }

  public void testDisabledForProfilingEnabled() {
    when(executor.getId()).thenReturn(ID);
    myProfilerState.ADVANCED_PROFILING_ENABLED = true;
    List<AndroidDevice> devices = new ArrayList(1);
    AndroidDevice device = mock(AndroidDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(25));
    devices.add(device);
    assertEquals(DISABLE_INSTANT_RUN_WHEN_PROFILING, myRunConfig.canInstantRun(module, devices, executor, myProfilerState));
  }

  // prepareInstantRunSession tests
  public void testPrepareInstantRunSession_NotKill() {
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    Messages.setTestDialog(message -> Messages.NO);
    assertNull(myRunConfig.prepareInstantRunSession(info, executor, facet, getProject(), null, false));
  }

  public void testPrepareInstantRunSession_Kill() {
    info = mock(AndroidSessionInfo.class, RETURNS_DEEP_STUBS);
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    Messages.setTestDialog(message -> Messages.YES);
    AndroidRunConfigurationBase.PrepareSessionResult result =
      myRunConfig.prepareInstantRunSession(info, executor, facet, getProject(), null, false);

    assertNotNull(result);
    assertNull(result.futures);
    assertFalse(result.couldHaveHotswapped);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Messages.setTestDialog(TestDialog.DEFAULT);
    }
    finally {
      super.tearDown();
    }
  }
}
