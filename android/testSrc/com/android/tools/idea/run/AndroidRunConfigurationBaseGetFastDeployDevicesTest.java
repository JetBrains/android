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

import com.android.builder.model.InstantRun;
import com.android.ddmlib.IDevice;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.execution.Executor;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_APP;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class AndroidRunConfigurationBaseGetFastDeployDevicesTest extends AndroidTestCase {
  @Mock private Executor myExecutor;
  @Mock private AndroidSessionInfo myInfo;
  @Mock private AndroidModuleModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  @Test
  public void testGetFastDeployDevicesIdMismatch() {
    when(myExecutor.getId()).thenReturn("1");
    when(myInfo.getExecutorId()).thenReturn("2");
    assertNull(AndroidRunConfigurationBase.getFastDeployDevices(myExecutor, myModel, myInfo));
  }

  @Test
  public void testGetFastDeployDevicesNoDevices() {
    when(myExecutor.getId()).thenReturn("1");
    when(myInfo.getExecutorId()).thenReturn("1");

    when(myInfo.getDevices()).thenReturn(Collections.emptyList());

    assertNull(AndroidRunConfigurationBase.getFastDeployDevices(myExecutor, myModel, myInfo));
  }

  @Test
  public void testGetFastDeployDevicesMultipleDevices() {
    when(myExecutor.getId()).thenReturn("1");
    when(myInfo.getExecutorId()).thenReturn("1");

    List<IDevice> devices = new ArrayList<>(2);
    devices.add(mock(IDevice.class));
    devices.add(mock(IDevice.class));
    when(myInfo.getDevices()).thenReturn(devices);

    assertNull(AndroidRunConfigurationBase.getFastDeployDevices(myExecutor, myModel, myInfo));
  }

  @Test
  public void testGetFastDeployDevicesIRNotSupported() {
    when(myExecutor.getId()).thenReturn("1");
    when(myInfo.getExecutorId()).thenReturn("1");

    List<IDevice> devices = new ArrayList<>(1);
    devices.add(mock(IDevice.class));
    when(myInfo.getDevices()).thenReturn(devices);
    // version too old for Instant Run
    when(myModel.getModelVersion()).thenReturn(new GradleVersion(2, 2));

    assertNull(AndroidRunConfigurationBase.getFastDeployDevices(myExecutor, myModel, myInfo));
  }

  @Test
  public void testGetFastDeployDevicesHappy() {
    when(myExecutor.getId()).thenReturn("1");
    when(myInfo.getExecutorId()).thenReturn("1");

    List<IDevice> devices = new ArrayList<>(1);
    IDevice device = mock(IDevice.class);
    devices.add(device);
    when(myInfo.getDevices()).thenReturn(devices);

    myModel = mock(AndroidModuleModel.class, RETURNS_DEEP_STUBS); // need to be deep stub for mocking call chains
    when(myModel.getModelVersion()).thenReturn(InstantRunGradleUtils.MINIMUM_GRADLE_PLUGIN_VERSION);
    when(myModel.getAndroidProject().getProjectType()).thenReturn(PROJECT_TYPE_APP);
    when(myModel.getSelectedVariant().getMainArtifact().getInstantRun().getSupportStatus()).thenReturn(InstantRun.STATUS_SUPPORTED);

    assertEquals(1, AndroidRunConfigurationBase.getFastDeployDevices(myExecutor, myModel, myInfo).getDevices().size());
  }
}