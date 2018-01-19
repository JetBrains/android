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

import com.android.ddmlib.IDevice;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.gradle.InstantRunGradleSupport;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.fd.InstantRunManager.MIN_IR_API_VERSION;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.API_TOO_LOW_FOR_INSTANT_RUN;
import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.CANNOT_BUILD_FOR_MULTIPLE_DEVICES;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AndroidRunConfigurationBaseCanInstantRunTest extends AndroidTestCase {
  public static final String ID = "fakeId";

  @Mock AndroidSessionInfo info;
  @Mock Executor executor;
  private AndroidRunConfigurationBase myRunConfig;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myRunConfig = new AndroidTestRunConfiguration(getProject(), mock(ConfigurationFactory.class), false);
  }

  // canInstantRunTests

  public void testMultiDevice() {
    List<AndroidDevice> devices = new ArrayList(2);
    devices.add(mock(AndroidDevice.class));
    devices.add(mock(AndroidDevice.class));

    assertEquals(CANNOT_BUILD_FOR_MULTIPLE_DEVICES, myRunConfig.canInstantRun(myModule, devices));
  }

  public void testApiTooLow() {
    List<AndroidDevice> devices = new ArrayList(2);
    AndroidDevice device = mock(AndroidDevice.class);
    when(device.getVersion()).thenReturn(new AndroidVersion(MIN_IR_API_VERSION - 1));
    devices.add(device);
    assertEquals(API_TOO_LOW_FOR_INSTANT_RUN, myRunConfig.canInstantRun(myModule, devices));
  }

  // prepareInstantRunSession tests
  public void testPrepareInstantRunSession_NotKill() {
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    Messages.setTestDialog(message -> Messages.NO);
    assertNull(myRunConfig.prepareInstantRunSession(info, executor, myFacet, getProject(), null, false));
  }

  public void testPrepareInstantRunSession_Kill() {
    info = mock(AndroidSessionInfo.class, RETURNS_DEEP_STUBS);
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    Messages.setTestDialog(message -> Messages.YES);
    AndroidRunConfigurationBase.PrepareSessionResult result =
      myRunConfig.prepareInstantRunSession(info, executor, myFacet, getProject(), null, false);

    assertNotNull(result);
    assertNull(result.futures);
    assertFalse(result.couldHaveHotswapped);
  }

  public void testPrepareInstantRunSession_Cold_Kill() {
    // Prepare
    info = mock(AndroidSessionInfo.class, RETURNS_DEEP_STUBS);
    AndroidProcessHandler handler = mock(AndroidProcessHandler.class);
    when(info.getProcessHandler()).thenReturn(handler);
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    List<IDevice> devices = new ArrayList(1);
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getVersion()).thenReturn(AndroidVersion.ART_RUNTIME);
    when(mockDevice.getSerialNumber()).thenReturn("abc123");
    devices.add(mockDevice);
    when(info.getDevices()).thenReturn(devices);
    DeviceFutures deviceFutures = DeviceFutures.forDevices(devices);

    InstantRunGradleUtils.setInstantRunGradleSupportOverride(InstantRunGradleSupport.SUPPORTED);

    // Act
    AndroidRunConfigurationBase.PrepareSessionResult result =
      myRunConfig.prepareInstantRunSession(info, executor, myFacet, getProject(), deviceFutures, true);

    // Verify
    assertNotNull(result);
    assertTrue(deviceFutures.allMatch(result.futures));
    assertTrue(result.couldHaveHotswapped);
    verify(handler, times(1)).destroyProcess();
  }

  public void testPrepareInstantRunSession_Cold_NotKill() {
    // Prepare
    info = mock(AndroidSessionInfo.class, RETURNS_DEEP_STUBS);
    AndroidProcessHandler handler = mock(AndroidProcessHandler.class);
    when(info.getProcessHandler()).thenReturn(handler);
    when(info.getExecutorId()).thenReturn(ID);
    when(executor.getId()).thenReturn(ID);

    List<IDevice> devices = new ArrayList(2);
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getVersion()).thenReturn(AndroidVersion.ART_RUNTIME);
    when(mockDevice.getSerialNumber()).thenReturn("abc123");
    devices.add(mockDevice);
    when(info.getDevices()).thenReturn(devices);

    devices.add(mock(IDevice.class));
    DeviceFutures deviceFutures = DeviceFutures.forDevices(devices);

    InstantRunGradleUtils.setInstantRunGradleSupportOverride(InstantRunGradleSupport.SUPPORTED);

    // Act
    AndroidRunConfigurationBase.PrepareSessionResult result =
      myRunConfig.prepareInstantRunSession(info, executor, myFacet, getProject(), deviceFutures, true);

    // Verify
    assertNotNull(result);
    assertFalse(deviceFutures.allMatch(result.futures));
    assertFalse(result.couldHaveHotswapped);
    verify(handler, never()).destroyProcess();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Messages.setTestDialog(TestDialog.DEFAULT);
      InstantRunGradleUtils.setInstantRunGradleSupportOverride(null);
    }
    finally {
      super.tearDown();
    }
  }
}
